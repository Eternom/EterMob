package fr.eternom.eterMob.module.mob;

import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.loot.LootTable;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Un variant ou un meneur, codé en dur (module/variant) avec un Builder : son mob, son rang, ses stats de base (× le
 * ratio), son équipement, son IA, son butin, et son kit débloqué par le ratio : chaque capacité, statistique de combat
 * (esquive, critique, armure...) ou crochet de dégâts a un ratio minimal (1 Ordinaire, 2 Robuste, 3 Dangereux, 4 Élite,
 * 5 Fléau). Un Ordinaire n'a que sa base, un Fléau tout son kit. Un vanilla « avec ratio » (natural-ratios) a un
 * MobVariant implicite, sans capacité (vanilla()).
 */
public final class MobVariant {

    /** Une capacité et le ratio à partir duquel il l'a. */
    public record Gated(int minRatio, Ability ability) {
    }

    /** Une statistique de combat (EterLib) et le ratio à partir duquel il l'a. */
    public record GatedStat(int minRatio, Stat stat, double value) {
    }

    /** Un crochet sur les dégâts qu'il reçoit (avant le calcul du combat) et le ratio à partir duquel il l'a. */
    public record GatedHook(int minRatio, BiConsumer<ActiveMob, EntityDamageEvent> hook) {
    }

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
    private final List<Gated> abilities;
    private final List<GatedStat> stats;
    private final List<GatedHook> damagedHooks;
    private final Consumer<LivingEntity> onLoad;
    private final LootTable loot;
    private final int xp;
    /** Ses flèches sont magiques : traînée bleue, dégâts magiques, jamais ramassées (MagicShots). */
    private final boolean magicShots;
    private final Map<String, Integer> followers;
    private final int followersMin;
    private final int followersMax;
    private final boolean vanilla;
    /** Un creeper qui n'explose pas (son attaque est une capacité) : il ne meurt pas en attaquant. */
    private final boolean explodes;

    private MobVariant(Builder builder, boolean vanilla) {
        this.id = builder.id;
        this.type = builder.type;
        this.tier = builder.tier;
        this.rarity = builder.rarity;
        this.name = builder.name;
        this.lore = List.copyOf(builder.lore);
        this.health = builder.health;
        this.damage = builder.damage;
        this.speed = builder.speed;
        this.armor = builder.armor;
        this.knockback = builder.knockback;
        this.tenacity = builder.tenacity;
        this.scale = builder.scale;
        this.equipment = Collections.unmodifiableMap(new EnumMap<>(builder.equipment));
        this.ai = builder.ai == null ? AiProfile.defaults(builder.tier) : builder.ai;
        this.abilities = List.copyOf(builder.abilities);
        this.stats = List.copyOf(builder.stats);
        this.damagedHooks = List.copyOf(builder.damagedHooks);
        this.onLoad = builder.onLoad;
        this.loot = builder.loot;
        this.xp = builder.xp;
        this.magicShots = builder.magicShots;
        this.followers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.followers));
        this.followersMin = builder.followersMin;
        this.followersMax = builder.followersMax;
        this.vanilla = vanilla;
        this.explodes = builder.explodes;
    }

    public static Builder builder(String id, EntityType type) {
        return new Builder(id, type);
    }

    /** Le variant implicite d'un mob de Minecraft avec ratio (natural-ratios) : ses stats de base, son IA de ciblage. */
    public static MobVariant vanilla(EntityType type) {
        return new MobVariant(new Builder("vanilla:" + type.name().toLowerCase(Locale.ROOT), type).name(""), true);
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

    /** À chaque chargement (ce que Minecraft ne garde pas : buts de l'IA retirés...). */
    void load(LivingEntity entity) {
        if (onLoad != null) {
            onLoad.accept(entity);
        }
    }

    /** Ses capacités à ce ratio. */
    public List<Ability> abilitiesFor(int ratio) {
        List<Ability> list = new ArrayList<>();
        for (Gated gated : abilities) {
            if (ratio >= gated.minRatio()) {
                list.add(gated.ability());
            }
        }
        return list;
    }

    /** Ses statistiques de combat à ce ratio. */
    public List<GatedStat> statsFor(int ratio) {
        return stats.stream().filter(stat -> ratio >= stat.minRatio()).toList();
    }

    /** Ses crochets de dégâts reçus à ce ratio. */
    public List<GatedHook> damagedHooksFor(int ratio) {
        return damagedHooks.stream().filter(hook -> ratio >= hook.minRatio()).toList();
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

    /** Tout son kit, avec le ratio de chaque capacité. */
    public List<Gated> gated() {
        return abilities;
    }

    /** Tout son kit (ratio le plus fort). */
    public List<Ability> abilities() {
        return abilities.stream().map(Gated::ability).toList();
    }

    public List<GatedStat> stats() {
        return stats;
    }

    public LootTable loot() {
        return loot;
    }

    public int xp() {
        return xp;
    }

    public boolean magicShots() {
        return magicShots;
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

    public boolean explodes() {
        return explodes;
    }

    /** Valeur d'équilibrage de ses capacités par seconde à ce ratio. */
    public double abilityValuePerSecond(int ratio) {
        double total = 0;
        for (Ability ability : abilitiesFor(ratio)) {
            total += ability.valuePerSecond();
        }
        return total;
    }

    /** Valeur d'équilibrage à ce ratio : vie et dégâts de base, plus ses capacités (budget de la rareté). */
    public double value(int ratio) {
        return health * 10 + damage * 10 + abilityValuePerSecond(ratio) * 10;
    }

    /** Valeur avec tout son kit (Fléau). */
    public double value() {
        return value(Integer.MAX_VALUE);
    }

    public static final class Builder {

        private final String id;
        private final EntityType type;
        private Tier tier = Tier.VARIANT;
        private Rarity rarity = Rarity.COMMUN;
        private String name;
        private final List<String> lore = new ArrayList<>();
        private double health = 1;
        private double damage = 1;
        private double speed = 1;
        private double armor;
        private double knockback;
        private double tenacity;
        private double scale = 1;
        private final Map<EquipmentSlot, Material> equipment = new EnumMap<>(EquipmentSlot.class);
        private AiProfile ai;
        private final List<Gated> abilities = new ArrayList<>();
        private final List<GatedStat> stats = new ArrayList<>();
        private final List<GatedHook> damagedHooks = new ArrayList<>();
        private Consumer<LivingEntity> onLoad;
        private LootTable loot = LootTable.EMPTY;
        private int xp;
        private boolean magicShots;
        private final Map<String, Integer> followers = new LinkedHashMap<>();
        private int followersMin;
        private int followersMax;
        private boolean explodes = true;

        private Builder(String id, EntityType type) {
            this.id = id;
            this.type = type;
            this.name = id;
        }

        public Builder tier(Tier tier) {
            this.tier = tier;
            return this;
        }

        public Builder rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(String... lines) {
            this.lore.addAll(List.of(lines));
            return this;
        }

        /** × la vie, × les dégâts, × la vitesse du mob ; armure en plus (points de Minecraft) ; recul ; ténacité ; taille. */
        public Builder stats(double health, double damage, double speed, double armor, double knockback, double tenacity, double scale) {
            this.health = health;
            this.damage = damage;
            this.speed = Math.clamp(speed, 0.3, 2.5);
            this.armor = armor;
            this.knockback = Math.clamp(knockback, 0, 1);
            this.tenacity = Math.clamp(tenacity, 0, 0.9);
            this.scale = Math.clamp(scale, 0.4, 3);
            return this;
        }

        public Builder equip(EquipmentSlot slot, Material material) {
            equipment.put(slot, material);
            return this;
        }

        public Builder ai(AiProfile ai) {
            this.ai = ai;
            return this;
        }

        /** Une capacité, à partir de ce ratio (1 Ordinaire ... 5 Fléau). */
        public Builder ability(int minRatio, Ability ability) {
            abilities.add(new Gated(minRatio, ability));
            return this;
        }

        /** Une statistique de combat (EterLib), à partir de ce ratio. */
        public Builder stat(int minRatio, Stat stat, double value) {
            stats.add(new GatedStat(minRatio, stat, value));
            return this;
        }

        /** Un crochet sur les dégâts reçus (avant le calcul du combat), à partir de ce ratio. */
        public Builder onDamaged(int minRatio, BiConsumer<ActiveMob, EntityDamageEvent> hook) {
            damagedHooks.add(new GatedHook(minRatio, hook));
            return this;
        }

        /** À chaque chargement du mob (buts de l'IA de Minecraft à retirer...). */
        public Builder onLoad(Consumer<LivingEntity> onLoad) {
            this.onLoad = onLoad;
            return this;
        }

        public Builder loot(LootTable loot) {
            this.loot = loot;
            return this;
        }

        public Builder xp(int xp) {
            this.xp = xp;
            return this;
        }

        /** Un creeper qui n'explose jamais : son attaque est une capacité, il reste en vie. */
        public Builder noExplosion() {
            this.explodes = false;
            return this;
        }

        /** Flèches magiques : traînée bleue, dégâts magiques (résistance magique, pas l'armure), jamais ramassées. */
        public Builder magicShots() {
            this.magicShots = true;
            return this;
        }

        public Builder followers(Map<String, Integer> variants, int min, int max) {
            followers.putAll(variants);
            followersMin = min;
            followersMax = max;
            return this;
        }

        public MobVariant build() {
            return new MobVariant(this, false);
        }
    }
}
