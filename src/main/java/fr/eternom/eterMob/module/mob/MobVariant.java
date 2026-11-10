package fr.eternom.eterMob.module.mob;

import fr.eternom.eterMob.module.ability.Abilities;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.loot.LootTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Un variant ou un meneur, décrit dans plugins/EterMob/variants/*.yml (aucun code à écrire) : son mob, son rang, ses
 * stats de base (× le ratio), son équipement, son IA, ses capacités (briques d'Ability) et son butin. Les règles
 * d'apparition (spawns) disent où et quand il sort. Un vanilla « avec ratio » (natural-ratios) a un MobVariant
 * implicite, sans capacité (vanilla()).
 */
public final class MobVariant {

    private final String id;
    private final EntityType type;
    private final Tier tier;
    private final Rarity rarity;
    private final String name;
    private final List<String> lore;
    private final double health;
    private final double damage;
    private final double speed;
    private final double armor;
    private final double knockback;
    private final double tenacity;
    private final double scale;
    private final Map<EquipmentSlot, Material> equipment;
    private final AiProfile ai;
    private final List<Ability> abilities;
    private final LootTable loot;
    private final int xp;
    private final Map<String, Integer> followers;
    private final int followersMin;
    private final int followersMax;
    private final boolean vanilla;

    private MobVariant(String id, EntityType type, Tier tier, Rarity rarity, String name, List<String> lore, double health,
                       double damage, double speed, double armor, double knockback, double tenacity, double scale,
                       Map<EquipmentSlot, Material> equipment, AiProfile ai, List<Ability> abilities, LootTable loot, int xp,
                       Map<String, Integer> followers, int followersMin, int followersMax, boolean vanilla) {
        this.id = id;
        this.type = type;
        this.tier = tier;
        this.rarity = rarity;
        this.name = name;
        this.lore = List.copyOf(lore);
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.armor = armor;
        this.knockback = knockback;
        this.tenacity = tenacity;
        this.scale = scale;
        this.equipment = Collections.unmodifiableMap(equipment);
        this.ai = ai;
        this.abilities = List.copyOf(abilities);
        this.loot = loot;
        this.xp = xp;
        this.followers = Collections.unmodifiableMap(followers);
        this.followersMin = followersMin;
        this.followersMax = followersMax;
        this.vanilla = vanilla;
    }

    /** Le variant implicite d'un mob de Minecraft avec ratio (natural-ratios) : ses stats de base, son IA de ciblage. */
    public static MobVariant vanilla(EntityType type) {
        String id = "vanilla:" + type.name().toLowerCase(Locale.ROOT);
        return new MobVariant(id, type, Tier.VARIANT, Rarity.COMMUN, "", List.of(), 1, 1, 1, 0, 0, 0, 1,
                new EnumMap<>(EquipmentSlot.class), AiProfile.defaults(Tier.VARIANT), List.of(), LootTable.EMPTY, 0,
                Map.of(), 0, 0, true);
    }

    /** Lit un variant (section variants.<id> d'un fichier) ; null et un avertissement s'il est illisible. */
    public static MobVariant load(String id, ConfigurationSection section, Logger logger) {
        String owner = "Variant " + id;
        EntityType type;
        try {
            type = EntityType.valueOf(section.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning(owner + " : type de mob inconnu « " + section.getString("type") + " »");
            return null;
        }
        if (type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
            logger.warning(owner + " : " + type + " n'est pas un monstre");
            return null;
        }
        Tier tier = Tier.parse(section.getString("tier"), Tier.VARIANT);
        if (tier == Tier.BOSS) {
            logger.warning(owner + " : les boss vont dans bosses/, pas dans variants/");
            return null;
        }
        Rarity rarity = Rarity.parse(section.getString("rarity"), Rarity.COMMUN);
        ConfigurationSection stats = section.getConfigurationSection("stats");
        Map<EquipmentSlot, Material> equipment = new EnumMap<>(EquipmentSlot.class);
        ConfigurationSection gear = section.getConfigurationSection("equipment");
        if (gear != null) {
            for (String key : gear.getKeys(false)) {
                EquipmentSlot slot = slot(key);
                Material material = Material.matchMaterial(gear.getString(key, ""));
                if (slot == null || material == null) {
                    logger.warning(owner + " : équipement illisible " + key + ": " + gear.getString(key));
                } else {
                    equipment.put(slot, material);
                }
            }
        }
        Map<String, Integer> followers = new LinkedHashMap<>();
        int followersMin = 0;
        int followersMax = 0;
        ConfigurationSection group = section.getConfigurationSection("followers");
        if (group != null) {
            ConfigurationSection variants = group.getConfigurationSection("variants");
            if (variants != null) {
                variants.getKeys(false).forEach(key -> followers.put(key.toLowerCase(Locale.ROOT), Math.max(1, variants.getInt(key, 1))));
            }
            List<Integer> size = group.getIntegerList("size");
            followersMin = size.size() == 2 ? Math.max(0, size.get(0)) : 2;
            followersMax = size.size() == 2 ? Math.max(followersMin, size.get(1)) : 4;
        }
        return new MobVariant(id, type, tier, rarity, section.getString("name", id), section.getStringList("lore"),
                stat(stats, "health", 1), stat(stats, "damage", 1), Math.clamp(stat(stats, "speed", 1), 0.3, 2.5),
                Math.max(0, stat(stats, "armor", 0)), Math.clamp(stat(stats, "knockback-resistance", 0), 0, 1),
                Math.clamp(stat(stats, "tenacity", 0), 0, 0.9), Math.clamp(stat(stats, "scale", 1), 0.4, 3),
                equipment, AiProfile.load(section.getConfigurationSection("ai"), tier),
                Abilities.load(section, "abilities", owner, logger), LootTable.load(section, "loot", owner, logger),
                Math.max(0, section.getInt("xp", 0)), followers, followersMin, followersMax, false);
    }

    private static double stat(ConfigurationSection stats, String key, double fallback) {
        return stats == null ? fallback : stats.getDouble(key, fallback);
    }

    private static EquipmentSlot slot(String key) {
        return switch (key.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "main-hand", "hand" -> EquipmentSlot.HAND;
            case "off-hand" -> EquipmentSlot.OFF_HAND;
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    /** Pose l'équipement (jamais lâché à la mort : le butin est dans loot). */
    void equip(LivingEntity entity) {
        if (entity.getEquipment() == null || equipment.isEmpty()) {
            return;
        }
        equipment.forEach((slot, material) -> {
            entity.getEquipment().setItem(slot, org.bukkit.inventory.ItemStack.of(material));
            entity.getEquipment().setDropChance(slot, 0);
        });
    }

    public String id() {
        return id;
    }

    public EntityType type() {
        return type;
    }

    public Tier tier() {
        return tier;
    }

    public Rarity rarity() {
        return rarity;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public double speed() {
        return speed;
    }

    public double armor() {
        return armor;
    }

    public double knockback() {
        return knockback;
    }

    public double tenacity() {
        return tenacity;
    }

    public double scale() {
        return scale;
    }

    public AiProfile ai() {
        return ai;
    }

    public List<Ability> abilities() {
        return abilities;
    }

    public LootTable loot() {
        return loot;
    }

    public int xp() {
        return xp;
    }

    public Map<String, Integer> followers() {
        return followers;
    }

    public int followersMin() {
        return followersMin;
    }

    public int followersMax() {
        return followersMax;
    }

    public boolean vanilla() {
        return vanilla;
    }

    /** Valeur d'équilibrage de ses capacités par seconde (somme), pour le budget de sa rareté. */
    public double abilityValuePerSecond() {
        double total = 0;
        for (Ability ability : abilities) {
            total += ability.valuePerSecond();
        }
        return total;
    }

    /** Valeur d'équilibrage totale : vie et dégâts de base, plus ses capacités (budget de la rareté). */
    public double value() {
        return health * 10 + damage * 10 + abilityValuePerSecond() * 10;
    }

    /** Copie de la liste des capacités (pour une IA). */
    public List<Ability> abilityList() {
        return new ArrayList<>(abilities);
    }
}
