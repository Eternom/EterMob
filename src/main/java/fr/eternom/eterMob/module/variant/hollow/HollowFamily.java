package fr.eternom.eterMob.module.variant.hollow;

import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Boulder;
import fr.eternom.eterMob.module.ability.kind.Burrow;
import fr.eternom.eterMob.module.ability.kind.Burst;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Curse;
import fr.eternom.eterMob.module.ability.kind.Evade;
import fr.eternom.eterMob.module.ability.kind.Leap;
import fr.eternom.eterMob.module.ability.kind.Pillar;
import fr.eternom.eterMob.module.ability.kind.Pull;
import fr.eternom.eterMob.module.ability.kind.Slam;
import fr.eternom.eterMob.module.ability.kind.Strike;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Vortex;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * La famille d'Orveth (Hollow Dungeon) : la mine, la mousse, les cristaux. Elle couvre les mobs les plus courants de la
 * survie (creepers, araignées, squelettes des grottes, slimes, noyés, zombies de surface, husks, sorcières). Le ratio
 * débloque leur kit : 1 Ordinaire (base), 2 Robuste, 3 Dangereux, 4 Élite, 5 Fléau. Où ils sortent : config.yml > spawns.
 */
public final class HollowFamily {

    private static final int ORDINARY = 1;
    private static final int STURDY = 2;
    private static final int DANGEROUS = 3;
    private static final int ELITE = 4;
    private static final int SCOURGE = 5;

    private HollowFamily() {
    }

    public static List<MobVariant> variants() {
        return List.of(mossCreeper(), tunnelSpider(), crystalLurker(), crystalShooter(), mossSlime(), tunnelDrowned(), digger(),
                sandRaider(), herbalist());
    }

    /** Creeper moussu : nuage de spores à sa mort ; le Fléau tire sa cible vers lui avant d'exploser. */
    static MobVariant mossCreeper() {
        return MobVariant.builder("creeper-moussu", EntityType.CREEPER)
                .name("Creeper moussu").rarity(Rarity.COMMUN)
                .lore("Il n'explose pas : il souffle des spores (s'écarter du cercle).", "Nuage de spores à sa mort.")
                .noExplosion()
                .ability(ORDINARY, new Burst(Spec.of("id", "souffle-de-spores", "trigger", "active", "radius", 3, "damage", 3,
                        "damage-type", "magic", "cooldown", 4, "cast", 1.1, "range", List.of(0.0, 3.0), "particle", "SPORE_BLOSSOM_AIR",
                        "knockback", 0.4, "lift", 0.1, "states", List.of("weakness 0.15 3"))))
                .stats(1.2, 1, 1, 0, 0, 0, 1)
                .ability(STURDY, new Burst(Spec.of("id", "nuage-de-spores", "trigger", "on-death", "radius", 3.5, "damage", 2,
                        "damage-type", "magic", "particle", "SPORE_BLOSSOM_AIR", "knockback", 0, "lift", 0, "states", List.of("weakness 0.15 4"))))
                .ability(DANGEROUS, new Curse(Spec.of("id", "racines", "damage", 2, "cooldown", 12,
                        "cast", 1, "states", List.of("root 1 1.5"))))
                .ability(ELITE, new Leap(Spec.of("id", "bond-moussu", "range", List.of(4.0, 10.0), "damage", 2, "radius", 2, "cooldown", 12,
                        "cast", 0.5)))
                .ability(SCOURGE, new Pull(Spec.of("id", "liane", "range", List.of(4.0, 14.0), "damage", 0, "cooldown", 10, "cast", 0.6)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.MOSS_BLOCK, 1, 2, 0.5, 1, null))))
                .xp(5)
                .build();
    }

    /** Araignée des galeries : toiles et couvée. */
    static MobVariant tunnelSpider() {
        return MobVariant.builder("araignee-galeries", EntityType.SPIDER)
                .name("Araignée des galeries").rarity(Rarity.COMMUN)
                .lore("Toiles et couvée.", "Couper la toile, tuer les petits.")
                .stats(1.2, 1.1, 1.05, 0, 0, 0, 1.1)
                .ability(ORDINARY, new Strike(Spec.of("id", "morsure", "chance", 0.35, "damage", 0, "cooldown", 2, "states", List.of("bleed 1 3"))))
                .stat(STURDY, Stat.DODGE, 0.1)
                .ability(DANGEROUS, new Leap(Spec.of("id", "bond", "range", List.of(4.0, 10.0), "damage", 3, "radius", 2, "cooldown", 10,
                        "cast", 0.4, "states", List.of("slow 0.25 3"))))
                .ability(ELITE, new Pillar(Spec.of("id", "toile", "mode", "cage", "block", "COBWEB", "health", 6, "seconds", 4,
                        "cooldown", 16, "cast", 0.8)))
                .ability(SCOURGE, new Summon(Spec.of("id", "couvee", "trigger", "on-death", "mobs", List.of("CAVE_SPIDER"), "count", List.of(3, 3),
                        "max-alive", 6)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.STRING, 1, 3, 1, 1, null),
                        new LootTable.Entry(Material.SPIDER_EYE, 1, 1, 0.4, 1, null))))
                .xp(5)
                .build();
    }

    /** Rôdeuse cristalline : petite, rapide, dans le dos ; éclate en mourant. */
    static MobVariant crystalLurker() {
        return MobVariant.builder("rodeuse-cristalline", EntityType.CAVE_SPIDER)
                .name("Rôdeuse cristalline").rarity(Rarity.PEU_COMMUN)
                .lore("Rapide, dans le dos ; éclate en mourant.", "Rester dos à dos.")
                .stats(1.3, 1, 1.1, 0, 0, 0, 1)
                .ability(ORDINARY, new Strike(Spec.of("id", "eclat", "chance", 0.5, "damage", 0, "cooldown", 2, "states", List.of("bleed 1 3"))))
                .stat(STURDY, Stat.DODGE, 0.2)
                .ability(DANGEROUS, new Blink(Spec.of("id", "pas-de-l-ombre", "mode", "behind", "cooldown", 10, "cast", 0.3)))
                .ability(ELITE, new Strike(Spec.of("id", "paralysie", "chance", 0.25, "damage", 0, "cooldown", 6, "states", List.of("root 1 1"))))
                .ability(SCOURGE, new Burst(Spec.of("id", "eclatement", "trigger", "on-death", "radius", 3, "damage", 4, "damage-type", "true",
                        "particle", "END_ROD", "knockback", 0.4, "lift", 0.2, "states", List.of("bleed 1 4"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.AMETHYST_SHARD, 1, 2, 0.6, 1, null))))
                .xp(5)
                .build();
    }

    /** Tireur de cristal : squelette des grottes, éclats qui font saigner. */
    static MobVariant crystalShooter() {
        return MobVariant.builder("tireur-de-cristal", EntityType.SKELETON)
                .name("Tireur de cristal").rarity(Rarity.PEU_COMMUN)
                .lore("Éclats qui font saigner, pics de cristal.", "Aller au contact.")
                .stats(1.15, 1, 1, 0, 0, 0, 1)
                .equip(EquipmentSlot.HAND, Material.BOW)
                .ability(ORDINARY, new Volley(Spec.of("id", "eclats", "projectile", "snowball", "count", 2, "spread", 5, "damage", 3,
                        "cooldown", 6, "cast", 0.5, "states", List.of("bleed 1 2"))))
                .stat(STURDY, Stat.CRIT_CHANCE, 0.15)
                .ability(DANGEROUS, new Evade(Spec.of("id", "recul", "chance", 0.4, "cooldown", 6)))
                .ability(ELITE, new Pillar(Spec.of("id", "pique-de-cristal", "mode", "spike", "block", "AMETHYST_BLOCK", "damage", 5,
                        "cooldown", 12, "states", List.of("root 1 1"))))
                .ability(SCOURGE, new Zone(Spec.of("id", "pluie-de-cristaux", "mode", "impact", "count", 4, "radius", 2, "damage", 5,
                        "damage-type", "physical", "particle", "END_ROD", "cooldown", 18, "cast", 1.2)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ARROW, 2, 5, 1, 1, null),
                        new LootTable.Entry(Material.AMETHYST_SHARD, 1, 2, 0.5, 1, null))))
                .xp(5)
                .build();
    }

    /** Limon moussu : colle, racines, spores ; le Fléau attire. */
    static MobVariant mossSlime() {
        return MobVariant.builder("limon-moussu", EntityType.SLIME)
                .name("Limon moussu").rarity(Rarity.COMMUN)
                .lore("Colle, racines et spores.", "S'en écarter.")
                .stats(1.2, 1, 1, 0, 0, 0, 1)
                .ability(ORDINARY, new Strike(Spec.of("id", "colle", "chance", 0.5, "damage", 0, "cooldown", 2, "states", List.of("slow 0.25 2"))))
                .stat(STURDY, Stat.ARMOR, 30)
                .ability(DANGEROUS, new Curse(Spec.of("id", "racines", "damage", 2, "cooldown", 12,
                        "cast", 1, "states", List.of("root 1 1.5"))))
                .ability(ELITE, new Leap(Spec.of("id", "rebond", "range", List.of(3.0, 8.0), "damage", 3, "radius", 2, "cooldown", 10,
                        "cast", 0.4, "states", List.of("slow 0.25 2"))))
                .ability(SCOURGE, new Pull(Spec.of("id", "liane", "range", List.of(4.0, 14.0), "damage", 0, "cooldown", 12, "cast", 0.6)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.SLIME_BALL, 1, 2, 0.7, 1, null),
                        new LootTable.Entry(Material.MOSS_CARPET, 1, 3, 0.5, 1, null))))
                .xp(4)
                .build();
    }

    /** Noyé des galeries : les galeries inondées ; harpon, courant, tourbillon. */
    static MobVariant tunnelDrowned() {
        return MobVariant.builder("noye-des-galeries", EntityType.DROWNED)
                .name("Noyé des galeries").rarity(Rarity.COMMUN)
                .lore("Les galeries inondées : harpon, courant.", "Rester hors de l'eau.")
                .stats(1.2, 1.1, 1, 0, 0.1, 0, 1)
                .ability(ORDINARY, new Strike(Spec.of("id", "noyade", "chance", 0.4, "damage", 0, "cooldown", 3, "states", List.of("slow 0.2 2"))))
                .stat(STURDY, Stat.ARMOR, 20)
                .ability(DANGEROUS, new Pull(Spec.of("id", "harpon", "range", List.of(5.0, 18.0), "damage", 3, "cooldown", 12, "cast", 0.7)))
                .ability(ELITE, new Charge(Spec.of("id", "courant", "range", List.of(5.0, 12.0), "damage", 5, "speed", 1.0, "cooldown", 12)))
                .ability(SCOURGE, new Vortex(Spec.of("id", "tourbillon", "radius", 8, "damage", 4, "cooldown", 22, "states", List.of("slow 0.4 3"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ROTTEN_FLESH, 1, 2, 1, 1, null),
                        new LootTable.Entry(Material.COPPER_INGOT, 1, 2, 0.4, 1, null))))
                .xp(5)
                .build();
    }

    /** Terrassier : zombie de surface qui creuse, jette de la terre, s'enfouit. */
    static MobVariant digger() {
        return MobVariant.builder("terrassier", EntityType.ZOMBIE)
                .name("Terrassier").rarity(Rarity.COMMUN)
                .lore("Creuse, jette de la terre, s'enfouit.", "Sortir des cercles.")
                .stats(1.2, 1.1, 1, 0, 0.1, 0, 1)
                .equip(EquipmentSlot.HAND, Material.IRON_SHOVEL)
                .ability(ORDINARY, new Strike(Spec.of("id", "pelletee", "chance", 0.3, "damage", 0, "cooldown", 6, "blind", 1)))
                .stat(STURDY, Stat.ARMOR, 30)
                .ability(DANGEROUS, new Boulder(Spec.of("id", "jet-de-terre", "mode", "throw", "block", "COARSE_DIRT", "size", 0.8,
                        "damage", 5, "radius", 2.5, "range", List.of(5.0, 20.0), "cooldown", 10, "cast", 0.5, "states", List.of("slow 0.3 2"))))
                .ability(ELITE, new Burrow(Spec.of("id", "galerie", "damage", 5, "radius", 2.5, "cooldown", 16)))
                .ability(SCOURGE, new Slam(Spec.of("id", "eboulement", "radius", 4, "damage", 6, "cooldown", 14, "states", List.of("fragile 0.2 4"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ROTTEN_FLESH, 1, 2, 1, 1, null),
                        new LootTable.Entry(Material.RAW_COPPER, 1, 3, 0.4, 1, null))))
                .xp(5)
                .build();
    }

    /** Pilleur des sables : husk des vieilles mines du désert ; tempête de sable, sables mouvants. */
    static MobVariant sandRaider() {
        return MobVariant.builder("pilleur-des-sables", EntityType.HUSK)
                .name("Pilleur des sables").rarity(Rarity.PEU_COMMUN)
                .lore("Tempête de sable, sables mouvants.", "Bouger, garder ses distances.")
                .stats(1.4, 1.2, 1, 0, 0.2, 0.1, 1.05)
                .equip(EquipmentSlot.HAND, Material.IRON_AXE)
                .ability(ORDINARY, new Cleave(Spec.of("id", "coup-lourd", "angle", 90, "radius", 3.5, "damage", 6, "cooldown", 8, "cast", 0.6)))
                .stat(STURDY, Stat.ARMOR, 40)
                .ability(DANGEROUS, new Zone(Spec.of("id", "tempete-de-sable", "mode", "linger", "count", 2, "radius", 3, "seconds", 6,
                        "damage", 1, "damage-type", "physical", "color", "E2C890", "blind", 1, "cooldown", 18, "cast", 0.8,
                        "states", List.of("slow 0.3 2"))))
                .ability(ELITE, new Burrow(Spec.of("id", "enfouissement", "damage", 6, "radius", 2.5, "cooldown", 16)))
                .ability(SCOURGE, new Zone(Spec.of("id", "sables-mouvants", "mode", "linger", "count", 3, "radius", 2.5, "seconds", 5,
                        "damage", 2, "color", "C2A25A", "cooldown", 18, "cast", 1.2, "states", List.of("slow 0.4 2"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ROTTEN_FLESH, 1, 2, 1, 1, null),
                        new LootTable.Entry(Material.GOLD_NUGGET, 2, 6, 0.6, 1, null))))
                .xp(6)
                .build();
    }

    /** Herboriste : sorcière des marais ; maléfices, fioles de spores, limons. */
    static MobVariant herbalist() {
        return MobVariant.builder("herboriste", EntityType.WITCH)
                .name("Herboriste").rarity(Rarity.PEU_COMMUN)
                .lore("Maléfices et fioles de spores.", "L'interrompre, la tuer vite.")
                .stats(1.2, 1, 1, 0, 0, 0, 1)
                .ability(ORDINARY, new Curse(Spec.of("id", "malefice", "damage", 2, "cast", 0.8, "cooldown", 10, "states", List.of("weakness 0.15 4"))))
                .ability(STURDY, new Blink(Spec.of("id", "fuite", "mode", "escape", "escape-health", 0.5, "trigger", "on-damaged", "chance", 0.2, "cooldown", 18)))
                .ability(DANGEROUS, new Zone(Spec.of("id", "fiole-de-spores", "mode", "linger", "count", 1, "radius", 2.5, "seconds", 6,
                        "damage", 1, "color", "6B8E23", "cooldown", 14, "cast", 0.8, "states", List.of("slow 0.2 2", "weakness 0.1 2"))))
                .ability(ELITE, new Summon(Spec.of("id", "limons", "mobs", List.of("limon-moussu"), "count", List.of(1, 2), "max-alive", 3,
                        "cooldown", 22, "cast", 1.2)))
                .ability(SCOURGE, new Zone(Spec.of("id", "pluie-de-fioles", "mode", "impact", "count", 4, "radius", 2, "damage", 5,
                        "particle", "WITCH", "cooldown", 18, "cast", 1.2, "states", List.of("slow 0.3 2"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.GLASS_BOTTLE, 1, 2, 0.6, 1, null),
                        new LootTable.Entry(Material.GLOWSTONE_DUST, 1, 3, 0.5, 1, null),
                        new LootTable.Entry(Material.MOSS_BLOCK, 1, 2, 0.4, 1, null))))
                .xp(8)
                .build();
    }
}
