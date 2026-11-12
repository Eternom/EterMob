package fr.eternom.eterMob.module.mob;

import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.ai.PlayerIndex;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fait d'un mob un monstre d'EterMob (variant, meneur, ou vanilla avec ratio) et suit ceux qui sont chargés. Variant,
 * ratio, meneur et sbire sont écrits sur le mob (données persistantes) : ils survivent aux redémarrages sans base de
 * données. Toutes les 5 ticks, l'IA de chaque monstre proche d'un joueur réfléchit (Brain). Thread principal.
 */
public class MobService {

    /** Période de la tâche de l'IA (ticks). */
    public static final int TICK_PERIOD = 5;
    /** L'IA ne tourne que si un joueur est à moins de cette distance (blocs). */
    public static final double TICK_RANGE = 48;
    /** Un suiveur sans cible rejoint son meneur s'il en est à plus de cette distance. */
    private static final double FOLLOW_RANGE = 10;

    private final JavaPlugin plugin;
    private final Variants variants;
    private final Ratios ratios;
    private final String tagFormat;
    private final PlayerIndex players = new PlayerIndex();
    private final NamespacedKey variantKey;
    private final NamespacedKey ratioKey;
    private final NamespacedKey leaderKey;
    private final NamespacedKey minionKey;
    /** Modificateurs de stats posés par EterMob (2.0.0) ; « ratio » : ceux d'avant, retirés à la migration. */
    private final NamespacedKey modifierKey;
    private final NamespacedKey legacyModifierKey;
    private final Map<EntityType, MobVariant> vanilla = new EnumMap<>(EntityType.class);
    private final Map<Integer, ActiveMob> active = new HashMap<>();
    private Function<Entity, Caster> bossCasters = entity -> null;

    public MobService(JavaPlugin plugin, Variants variants, Ratios ratios, String tagFormat) {
        this.plugin = plugin;
        this.variants = variants;
        this.ratios = ratios;
        this.tagFormat = tagFormat;
        this.variantKey = new NamespacedKey(plugin, "variant");
        this.ratioKey = new NamespacedKey(plugin, "ratio");
        this.leaderKey = new NamespacedKey(plugin, "leader");
        this.minionKey = new NamespacedKey(plugin, "minion");
        this.modifierKey = new NamespacedKey(plugin, "stats");
        this.legacyModifierKey = new NamespacedKey(plugin, "ratio");
    }

    public Variants variants() {
        return variants;
    }

    public Ratios ratios() {
        return ratios;
    }

    public PlayerIndex players() {
        return players;
    }

    /** Les boss (BossService) se déclarent ici : caster() les trouve aussi. */
    public void setBossCasters(Function<Entity, Caster> lookup) {
        this.bossCasters = lookup;
    }

    public MobVariant vanilla(EntityType type) {
        return vanilla.computeIfAbsent(type, MobVariant::vanilla);
    }

    /** Suit les mobs déjà chargés, puis lance la tâche de l'IA. */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                track(entity);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    // ---------- Création ----------

    /**
     * Fait de ce mob un monstre d'EterMob, avant son entrée dans le monde (il sera suivi par track) : données, stats
     * (× ratio), équipement, tag. leader : son meneur (groupe) ; minion : sbire d'une capacité.
     */
    public void apply(LivingEntity entity, MobVariant variant, int ratio, UUID leader, boolean minion) {
        ratio = ratios.clamp(ratio);
        Ratios.Level level = ratios.level(ratio);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(variantKey, PersistentDataType.STRING, variant.id());
        data.set(ratioKey, PersistentDataType.INTEGER, ratio);
        if (leader != null) {
            data.set(leaderKey, PersistentDataType.STRING, leader.toString());
        }
        if (minion) {
            data.set(minionKey, PersistentDataType.BYTE, (byte) 1);
            entity.setPersistent(false);
        }
        multiply(entity, Attribute.MAX_HEALTH, variant.health() * level.health());
        multiply(entity, Attribute.ATTACK_DAMAGE, variant.damage() * level.damage());
        multiply(entity, Attribute.MOVEMENT_SPEED, variant.speed());
        add(entity, Attribute.ARMOR, variant.armor());
        add(entity, Attribute.KNOCKBACK_RESISTANCE, Math.max(variant.knockback(), level.knockback()));
        multiply(entity, Attribute.SCALE, variant.scale());
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            entity.setHealth(health.getValue());
        }
        // Un variant est toujours adulte (Minecraft fait parfois naître les zombies bébés)
        if (!variant.vanilla() && entity instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult()) {
            ageable.setAdult();
        }
        variant.equip(entity);
        // Un mob de Minecraft ordinaire (ratio 1) garde son nom ; les autres ont leur statut (et leur nom de variant)
        if (!variant.vanilla() || ratio > 1) {
            entity.customName(name(variant, ratio, entity));
        }
        // Visible seulement en le visant : jamais à travers les blocs
        entity.setCustomNameVisible(false);
        // Un nom ne doit pas le rendre permanent : il disparaît loin des joueurs comme un mob normal
        entity.setRemoveWhenFarAway(true);
        Minions.noReinforcements(entity);
    }

    /** Fait apparaître ce variant ici (commande, apparition d'EterMob, groupe). */
    public LivingEntity spawn(MobVariant variant, int ratio, Location location, UUID leader) {
        int clamped = ratios.clamp(ratio);
        return (LivingEntity) location.getWorld().spawnEntity(location, variant.type(), CreatureSpawnEvent.SpawnReason.CUSTOM,
                entity -> apply((LivingEntity) entity, variant, clamped, leader, false));
    }

    /** Un sbire d'une capacité, sous la forme d'un variant (ni butin, ni expérience, jamais sauvegardé). */
    public LivingEntity spawnMinion(MobVariant variant, int ratio, Location location) {
        int clamped = ratios.clamp(ratio);
        return (LivingEntity) location.getWorld().spawnEntity(location, variant.type(), CreatureSpawnEvent.SpawnReason.CUSTOM,
                entity -> apply((LivingEntity) entity, variant, clamped, null, true));
    }

    public int casterRatio(Caster caster) {
        return caster instanceof ActiveMob mob ? mob.ratio() : 1;
    }

    private void multiply(LivingEntity entity, Attribute attribute, double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierKey);
        if (factor != 1) {
            instance.addModifier(new AttributeModifier(modifierKey, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void add(LivingEntity entity, Attribute attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || amount == 0) {
            return;
        }
        instance.removeModifier(modifierKey);
        instance.addModifier(new AttributeModifier(modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ---------- Suivi ----------

    /** Mob entré dans le monde (apparu ou chargé) : suivi s'il est à EterMob ; un ancien variant disparu redevient vanilla. */
    public void track(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        PersistentDataContainer data = living.getPersistentDataContainer();
        String id = data.get(variantKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        MobVariant variant = id.startsWith("vanilla:") ? vanilla(living.getType()) : variants.get(id);
        if (variant == null || variant.type() != living.getType()) {
            migrate(living);
            return;
        }
        // Un arrêt pendant un télégraphe l'a laissé figé (IA coupée) : il se réveille
        if (living instanceof Mob mob && !mob.isAware()) {
            mob.setAware(true);
        }
        int ratio = ratios.clamp(data.getOrDefault(ratioKey, PersistentDataType.INTEGER, 1));
        String leader = data.get(leaderKey, PersistentDataType.STRING);
        ActiveMob mob = new ActiveMob(living, variant, ratio, ratios.level(ratio), leader == null ? null : UUID.fromString(leader),
                data.has(minionKey, PersistentDataType.BYTE));
        active.put(living.getEntityId(), mob);
        variant.load(living);
        // Son modèle Blockbench (BetterModel), s'il en a un : même nom que le variant. Au tick suivant : pendant l'ajout
        // au monde, l'entité n'est pas encore prête et BetterModel ne peut pas s'y attacher
        if (!variant.vanilla()) {
            fr.eternom.eterMob.module.model.Models.attachLater(living, variant.id());
        }
    }

    /**
     * Variant qui n'existe plus (EterMob 1.x, ou retiré de variants/) : le mob redevient celui de Minecraft (données,
     * stats, nom retirés) au lieu de rester à moitié transformé.
     */
    private void migrate(LivingEntity living) {
        PersistentDataContainer data = living.getPersistentDataContainer();
        data.remove(variantKey);
        data.remove(ratioKey);
        data.remove(leaderKey);
        for (Attribute attribute : new Attribute[]{Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE, Attribute.MOVEMENT_SPEED,
                Attribute.ARMOR, Attribute.KNOCKBACK_RESISTANCE, Attribute.SCALE}) {
            AttributeInstance instance = living.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifierKey);
                instance.removeModifier(legacyModifierKey);
            }
        }
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && living.getHealth() > max.getValue()) {
            living.setHealth(max.getValue());
        }
        living.customName(null);
        living.setCustomNameVisible(false);
    }

    /** Mob sorti du monde (mort, déchargé) : plus suivi. */
    public void untrack(Entity entity) {
        ActiveMob mob = active.remove(entity.getEntityId());
        if (mob != null) {
            mob.brain().interrupt(false);
        }
    }

    /** Monstre d'EterMob chargé de cette entité, ou null. */
    /** Rang à partir duquel le nom porte une jauge de vie (Élite, Fléau). */
    private static final int HEALTH_BAR_RATIO = 4;
    private static final int BAR = 10;

    /** Le nom affiché : « Nom · Rang », et pour une Élite ou un Fléau une jauge de vie à jour. */
    private net.kyori.adventure.text.Component name(MobVariant variant, int ratio, LivingEntity entity) {
        net.kyori.adventure.text.Component name = MiniMessage.miniMessage().deserialize(variant.vanilla() ? "<status>" : tagFormat,
                Placeholder.parsed("status", ratios.status(ratio)), Placeholder.unparsed("name", variant.name()));
        var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (ratio < HEALTH_BAR_RATIO || max == null || max.getValue() <= 0) {
            return name;
        }
        // À l'apparition, la vie peut dépasser un instant le maximum (il est posé après) : 0 à BAR traits
        int filled = Math.clamp((int) Math.ceil(BAR * Math.max(0, entity.getHealth()) / max.getValue()), 0, BAR);
        return name.append(MiniMessage.miniMessage().deserialize(" <dark_gray>[<red>" + "|".repeat(filled) + "<dark_gray>"
                + "|".repeat(BAR - filled) + "]"));
    }

    /** Après un coup : la jauge de vie d'une Élite ou d'un Fléau (au tick suivant, la vie est à jour). */
    public void refreshName(ActiveMob mob) {
        if (mob.ratio() < HEALTH_BAR_RATIO) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mob.entity().isValid()) {
                mob.entity().customName(name(mob.variant(), mob.ratio(), mob.entity()));
            }
        });
    }

    public ActiveMob get(Entity entity) {
        return entity == null ? null : active.get(entity.getEntityId());
    }

    /** Ce qui lance des capacités : un monstre d'EterMob ou un boss, ou null. */
    public Caster caster(Entity entity) {
        if (entity == null) {
            return null;
        }
        ActiveMob mob = active.get(entity.getEntityId());
        return mob != null ? mob : bossCasters.apply(entity);
    }

    public Collection<ActiveMob> active() {
        return active.values();
    }

    /** Monstres d'EterMob (hors sbires) à moins de range blocs. */
    public int countNear(Location at, double range) {
        double max = range * range;
        int count = 0;
        for (ActiveMob mob : active.values()) {
            Location there = mob.entity().getLocation();
            if (!mob.minion() && there.getWorld().equals(at.getWorld()) && there.distanceSquared(at) <= max) {
                count++;
            }
        }
        return count;
    }

    /** Un monstre de ce ratio (ou plus) à moins de range blocs ? (un seul Fléau à la fois dans un coin). */
    public boolean ratioNear(Location at, double range, int ratio) {
        double max = range * range;
        for (ActiveMob mob : active.values()) {
            Location there = mob.entity().getLocation();
            if (mob.ratio() >= ratio && there.getWorld().equals(at.getWorld()) && there.distanceSquared(at) <= max) {
                return true;
            }
        }
        return false;
    }

    public int countIn(World world) {
        int count = 0;
        for (ActiveMob mob : active.values()) {
            if (mob.entity().getWorld().equals(world) && !mob.minion()) {
                count++;
            }
        }
        return count;
    }

    void damagedBy(ActiveMob mob, UUID player, double amount) {
        mob.damagedBy(player, amount);
    }

    void otherDamage(ActiveMob mob, double amount) {
        mob.otherDamage(amount);
    }

    private void tick() {
        players.refresh();
        if (active.isEmpty()) {
            return;
        }
        // Copie : une capacité peut faire apparaître ou tuer des mobs pendant le tour
        for (ActiveMob mob : new ArrayList<>(active.values())) {
            LivingEntity entity = mob.entity();
            if (!entity.isValid() || !players.any(entity.getLocation(), TICK_RANGE)) {
                continue;
            }
            mob.brain().tick(players);
            if (mob.leader() != null && mob.brain().target() == null && entity instanceof Mob follower) {
                Entity leader = Bukkit.getEntity(mob.leader());
                if (leader != null && leader.isValid() && leader.getWorld().equals(entity.getWorld())
                        && leader.getLocation().distanceSquared(entity.getLocation()) > FOLLOW_RANGE * FOLLOW_RANGE) {
                    follower.getPathfinder().moveTo(leader.getLocation(), 1.1);
                }
            }
        }
    }
}
