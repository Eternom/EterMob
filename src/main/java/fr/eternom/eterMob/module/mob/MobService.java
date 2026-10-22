package fr.eternom.eterMob.module.mob;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforme un mob en variant et suit les variants chargés. Le variant et le ratio sont écrits sur le mob (données
 * persistantes) : ils survivent aux redémarrages sans base de données. Les variants chargés sont gardés en mémoire par
 * identifiant d'entité, pour que les événements les retrouvent sans relire le mob. Thread principal.
 */
public class MobService {

    /** Période de la tâche des capacités périodiques (ticks). */
    public static final int TICK_PERIOD = 5;
    /** Une capacité périodique ne tourne que si un joueur est à moins de cette distance (blocs). */
    public static final double TICK_RANGE = 32;

    private final JavaPlugin plugin;
    private final Variants variants;
    private final Ratios ratios;
    private final String tagFormat;
    private final NamespacedKey variantKey;
    private final NamespacedKey ratioKey;
    /** Clé des modificateurs de vie et de dégâts posés par EterMob. */
    private final NamespacedKey modifierKey;

    private final Map<Integer, ActiveMob> active = new HashMap<>();
    /** Sous-ensemble de active : les variants qui ont une capacité périodique. */
    private final Map<Integer, ActiveMob> ticking = new HashMap<>();
    private long ticks;

    public MobService(JavaPlugin plugin, Variants variants, Ratios ratios, String tagFormat) {
        this.plugin = plugin;
        this.variants = variants;
        this.ratios = ratios;
        this.tagFormat = tagFormat;
        this.variantKey = new NamespacedKey(plugin, "variant");
        this.ratioKey = new NamespacedKey(plugin, "ratio");
        this.modifierKey = new NamespacedKey(plugin, "ratio");
    }

    public Variants variants() {
        return variants;
    }

    public Ratios ratios() {
        return ratios;
    }

    /** Suit les mobs déjà chargés (serveur démarré avant le plugin), puis lance la tâche des capacités périodiques. */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                track(entity);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /**
     * Fait de ce mob un variant de ce ratio : données, vie et dégâts, tag, puis MobVariant#onSpawn. Appelé avant que le
     * mob entre dans le monde : il sera suivi par track (EntityAddToWorldEvent).
     */
    public void apply(LivingEntity entity, MobVariant variant, int ratio) {
        ratio = ratios.clamp(ratio);
        double power = ratios.multiplier(ratio);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(variantKey, PersistentDataType.STRING, variant.id());
        data.set(ratioKey, PersistentDataType.INTEGER, ratio);

        scale(entity, Attribute.MAX_HEALTH, variant.health() * power);
        scale(entity, Attribute.ATTACK_DAMAGE, variant.damage() * power);
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            entity.setHealth(health.getValue());
        }

        entity.customName(MiniMessage.miniMessage().deserialize(tagFormat,
                Placeholder.parsed("status", ratios.status(ratio)), Placeholder.unparsed("name", variant.name())));
        // Visible seulement en visant le mob : rien à afficher ni à calculer pour les autres, jamais à travers les blocs
        entity.setCustomNameVisible(false);
        // Un nom ne doit pas le rendre permanent : il disparaît loin des joueurs comme un mob normal
        entity.setRemoveWhenFarAway(true);

        variant.onSpawn(new ActiveMob(entity, variant, ratio, power));
    }

    /** Multiplie l'attribut par factor (rien si le mob ne l'a pas : un creeper n'a pas de dégâts d'attaque). */
    private void scale(LivingEntity entity, Attribute attribute, double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierKey);
        if (factor != 1) {
            instance.addModifier(new AttributeModifier(modifierKey, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Mob entré dans le monde (apparu ou chargé) : suivi s'il est un variant connu. */
    public void track(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        PersistentDataContainer data = living.getPersistentDataContainer();
        String id = data.get(variantKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        MobVariant variant = variants.get(id);
        if (variant == null) {
            return;
        }
        int ratio = ratios.clamp(data.getOrDefault(ratioKey, PersistentDataType.INTEGER, 1));
        // Variants d'EterMob 1.0.0 à 1.0.2 : tag toujours visible, à travers les blocs
        living.setCustomNameVisible(false);
        ActiveMob mob = new ActiveMob(living, variant, ratio, ratios.multiplier(ratio));
        active.put(living.getEntityId(), mob);
        if (variant.tickInterval() > 0) {
            ticking.put(living.getEntityId(), mob);
        }
    }

    /** Mob sorti du monde (mort, déchargé) : plus suivi. */
    public void untrack(Entity entity) {
        active.remove(entity.getEntityId());
        ticking.remove(entity.getEntityId());
    }

    /** Variant chargé de cette entité, ou null. */
    public ActiveMob get(Entity entity) {
        return entity == null ? null : active.get(entity.getEntityId());
    }

    private void tick() {
        ticks += TICK_PERIOD;
        if (ticking.isEmpty()) {
            return;
        }
        // Copie : une capacité peut faire apparaître ou tuer des mobs pendant le tour
        for (ActiveMob mob : new ArrayList<>(ticking.values())) {
            int interval = mob.variant().tickInterval();
            if (ticks % interval != 0 || !mob.entity().isValid()) {
                continue;
            }
            if (!mob.entity().getLocation().getNearbyPlayers(TICK_RANGE).isEmpty()) {
                mob.variant().tick(mob);
            }
        }
    }
}
