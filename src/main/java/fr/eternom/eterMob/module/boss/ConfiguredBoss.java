package fr.eternom.eterMob.module.boss;

import fr.eternom.eterLib.module.quest.QuestBridge;
import fr.eternom.eterMob.module.ability.Abilities;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.ai.Brain;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Tier;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Un boss décrit dans plugins/EterMob/bosses/*.yml (aucun code à écrire) : son mob, sa vie, ses phases (chacune avec ses
 * capacités, ses répliques, sa couleur), son IA, son butin. Le socle (BossService) fait la barre, les phases, la
 * résistance de groupe, la laisse, la remise à zéro, la rage ; l'IA commune (Brain) choisit sa cible et ses capacités.
 * Butin personnel à chaque joueur qui a fait au moins loot-share des dégâts (dans son inventaire, sinon à ses pieds,
 * ramassable par lui seul).
 */
public final class ConfiguredBoss extends Boss {

    private record Phase(double below, String name, BossBar.Color color, String line, List<Ability> abilities) {
    }

    private final Class<? extends LivingEntity> type;
    private final double health;
    private final double toughness;
    private final double height;
    private final double scale;
    private final double piercing;
    private final double enrage;
    private final Material icon;
    private final List<String> lore;
    private final Map<EquipmentSlot, Material> equipment = new EnumMap<>(EquipmentSlot.class);
    private final AiProfile ai;
    private final List<Phase> phases;
    private final String spawnLine;
    private final String deathLine;
    private final LootTable loot;
    private final double lootShare;
    private final Map<UUID, BossCaster> casters = new HashMap<>();

    /** Ce boss pendant un combat : il lance ses capacités et réfléchit (Brain). */
    public final class BossCaster implements Caster {

        private final BossFight fight;
        private final Brain brain;

        BossCaster(BossFight fight) {
            this.fight = fight;
            this.brain = new Brain(this, ai, phase(fight.phase()).abilities());
        }

        @Override
        public LivingEntity entity() {
            return fight.entity();
        }

        @Override
        public Tier tier() {
            return Tier.BOSS;
        }

        @Override
        public double abilityPower() {
            return fight.enraged() ? BossService.ENRAGE_DAMAGE : 1;
        }

        @Override
        public double piercing() {
            return piercing;
        }

        @Override
        public Brain brain() {
            return brain;
        }

        @Override
        public String displayName() {
            return name();
        }
    }

    private ConfiguredBoss(String id, ConfigurationSection section, Class<? extends LivingEntity> type, Logger logger) {
        super(id, section.getString("name", id), section.getString("title", ""));
        this.type = type;
        this.health = Math.clamp(section.getDouble("health", 400), 20, 1024);
        this.toughness = Math.clamp(section.getDouble("toughness", 1.5), 0.5, 10);
        this.height = Math.max(1, section.getDouble("height", 3));
        this.scale = Math.clamp(section.getDouble("scale", 1.5), 0.5, 4);
        this.piercing = Math.clamp(section.getDouble("piercing", 0.3), 0, 1);
        this.enrage = Math.max(1, section.getDouble("enrage-minutes", 15));
        Material material = Material.matchMaterial(section.getString("icon", "WITHER_SKELETON_SKULL"));
        this.icon = material == null ? Material.WITHER_SKELETON_SKULL : material;
        this.lore = section.getStringList("lore");
        ConfigurationSection gear = section.getConfigurationSection("equipment");
        if (gear != null) {
            for (String key : gear.getKeys(false)) {
                Material item = Material.matchMaterial(gear.getString(key, ""));
                EquipmentSlot slot = switch (key.toLowerCase(Locale.ROOT)) {
                    case "main-hand", "hand" -> EquipmentSlot.HAND;
                    case "off-hand" -> EquipmentSlot.OFF_HAND;
                    case "helmet", "head" -> EquipmentSlot.HEAD;
                    case "chestplate", "chest" -> EquipmentSlot.CHEST;
                    case "leggings", "legs" -> EquipmentSlot.LEGS;
                    case "boots", "feet" -> EquipmentSlot.FEET;
                    default -> null;
                };
                if (item != null && slot != null) {
                    equipment.put(slot, item);
                }
            }
        }
        this.ai = AiProfile.load(section.getConfigurationSection("ai"), Tier.BOSS);
        List<Phase> list = new ArrayList<>();
        List<Map<?, ?>> raw = section.getMapList("phases");
        for (int i = 0; i < raw.size(); i++) {
            org.bukkit.configuration.MemoryConfiguration phase = new org.bukkit.configuration.MemoryConfiguration();
            raw.get(i).forEach((key, value) -> phase.set(String.valueOf(key), value));
            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(phase.getString("color", "RED").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                color = BossBar.Color.RED;
            }
            list.add(new Phase(i == 0 ? 1 : Math.clamp(phase.getDouble("below", 0.5), 0.01, 0.99), phase.getString("name", "Phase " + (i + 1)),
                    color, phase.getString("line"), Abilities.load(phase, "abilities", "Boss " + id + " phase " + (i + 1), logger)));
        }
        if (list.isEmpty()) {
            list.add(new Phase(1, "", BossBar.Color.RED, null, List.of()));
        }
        this.phases = List.copyOf(list);
        ConfigurationSection lines = section.getConfigurationSection("lines");
        this.spawnLine = lines == null ? null : lines.getString("spawn");
        this.deathLine = lines == null ? null : lines.getString("death");
        this.loot = LootTable.load(section, "loot", "Boss " + id, logger);
        this.lootShare = Math.clamp(section.getDouble("loot-share", 0.05), 0, 1);
    }

    /** Lit un boss (section bosses.<id> d'un fichier) ; null et un avertissement s'il est illisible. */
    public static ConfiguredBoss load(String id, ConfigurationSection section, Logger logger) {
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(section.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Boss " + id + " : type de mob inconnu « " + section.getString("type") + " »");
            return null;
        }
        Class<? extends Entity> entityClass = entityType.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            logger.warning("Boss " + id + " : " + entityType + " n'est pas un monstre");
            return null;
        }
        @SuppressWarnings("unchecked") Class<? extends LivingEntity> living = (Class<? extends LivingEntity>) entityClass;
        return new ConfiguredBoss(id, section, living, logger);
    }

    private Phase phase(int number) {
        return phases.get(Math.clamp(number, 1, phases.size()) - 1);
    }

    /** Le lanceur de ce combat (créé à la demande). */
    public BossCaster caster(BossFight fight) {
        return casters.computeIfAbsent(fight.id(), id -> new BossCaster(fight));
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public List<String> lore() {
        return lore;
    }

    @Override
    public double baseHealth() {
        return health;
    }

    @Override
    public double toughness() {
        return toughness;
    }

    @Override
    public double height() {
        return height * scale;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return type;
    }

    @Override
    public void setup(LivingEntity entity) {
        AttributeInstance size = entity.getAttribute(Attribute.SCALE);
        if (size != null) {
            size.setBaseValue(scale);
        }
        AttributeInstance knockback = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(0.8);
        }
        if (entity.getEquipment() != null) {
            equipment.forEach((slot, material) -> {
                entity.getEquipment().setItem(slot, ItemStack.of(material));
                entity.getEquipment().setDropChance(slot, 0);
            });
        }
    }

    @Override
    public double[] phaseThresholds() {
        double[] thresholds = new double[phases.size() - 1];
        for (int i = 1; i < phases.size(); i++) {
            thresholds[i - 1] = phases.get(i).below();
        }
        return thresholds;
    }

    @Override
    public String phaseName(int phase) {
        return phase(phase).name();
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase(phase).color();
    }

    @Override
    public String phaseLine(int phase) {
        return phase(phase).line();
    }

    @Override
    public String spawnLine() {
        return spawnLine;
    }

    @Override
    public String deathLine() {
        return deathLine;
    }

    @Override
    public double enrageMinutes() {
        return enrage;
    }

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        caster(fight).brain().setAbilities(phase(phase).abilities());
    }

    @Override
    public void onLoad(BossFight fight) {
        caster(fight);
        if (fight.entity() instanceof Mob mob && !mob.isAware()) {
            mob.setAware(true);
        }
    }

    @Override
    public void tick(BossFight fight) {
        if (!fight.dying()) {
            caster(fight).brain().tick(Services.mobs().players());
        }
    }

    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        Brain brain = caster(fight).brain();
        Entity source = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        boolean ranged = source instanceof Projectile;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (source instanceof Player player) {
            brain.damagedBy(player, event.getDamage(), ranged);
        }
        if (Services.states() == null || !Services.states().isStateDamage()) {
            brain.onDamaged(source instanceof LivingEntity living ? living : null);
        }
    }

    @Override
    public void onReset(BossFight fight) {
        BossCaster caster = casters.get(fight.id());
        if (caster != null) {
            caster.brain().interrupt(false);
            caster.brain().threat().clear();
        }
        Summon.removeMinionsOf(fight.id());
    }

    @Override
    public void onDeath(BossFight fight) {
        caster(fight).brain().onDeath(fight.entity().getKiller());
        giveLoot(fight);
    }

    @Override
    public void onEnd(BossFight fight) {
        BossCaster caster = casters.remove(fight.id());
        if (caster != null) {
            caster.brain().interrupt(false);
        }
        Summon.removeMinionsOf(fight.id());
    }

    /** Butin personnel : chaque joueur qui a fait au moins loot-share des dégâts tire le sien ; quêtes prévenues. */
    private void giveLoot(BossFight fight) {
        double total = fight.allStats().values().stream().mapToDouble(stats -> stats.dealt).sum();
        if (total <= 0) {
            return;
        }
        fight.allStats().forEach((uuid, stats) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || stats.dealt / total < lootShare) {
                return;
            }
            QuestBridge.signal(player, "etermob:boss", Map.of("boss", id()), 1);
            for (ItemStack item : loot.roll(1, 1)) {
                player.getInventory().addItem(item).values().forEach(left -> {
                    Item dropped = player.getWorld().dropItem(player.getLocation(), left);
                    dropped.setOwner(uuid);
                });
            }
        });
    }
}
