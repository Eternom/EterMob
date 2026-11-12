package fr.eternom.eterMob.module.variant.book;

import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Aura;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Curse;
import fr.eternom.eterMob.module.ability.kind.Evade;
import fr.eternom.eterMob.module.ability.kind.Leap;
import fr.eternom.eterMob.module.ability.kind.Pillar;
import fr.eternom.eterMob.module.ability.kind.Strike;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * La famille du Livre : les morts-vivants liés à Mortarius et à ses disciples. Le ratio débloque leur kit :
 * 1 Ordinaire (base), 2 Robuste (passif), 3 Dangereux (2e capacité), 4 Élite (3e), 5 Fléau (signature).
 * Où ils sortent : config.yml > spawns.
 */
public final class BookFamily {

    private static final int ORDINARY = 1;
    private static final int STURDY = 2;
    private static final int DANGEROUS = 3;
    private static final int ELITE = 4;
    private static final int SCOURGE = 5;

    private BookFamily() {
    }

    public static List<MobVariant> variants() {
        return List.of(ghoul(), page(), chainedSoul(), boneGuard(), boneArcher(), acolyte(), miner());
    }

    /** Goule : mêlée qui fait saigner, rapide sur une cible qui saigne. */
    static MobVariant ghoul() {
        return MobVariant.builder("goule", EntityType.HUSK)
                .name("Goule").rarity(Rarity.COMMUN)
                .lore("Mêlée qui fait saigner.", "La garder à distance, soigner le saignement.")
                .stats(1.2, 1.1, 1.1, 0, 0, 0, 1)
                .ai(new AiProfile(20, 1, 0.8, 0.9, 0.1, 0.3, 0, 1.3, 1500, 3, 0.3, false))
                .ability(ORDINARY, new Strike(Spec.of("id", "griffes", "chance", 0.15, "damage", 0, "cooldown", 1, "states", List.of("bleed 1 4"))))
                .ability(STURDY, new BookAbilities.Hungry(Spec.of("id", "affamee")))
                .ability(DANGEROUS, new Leap(Spec.of("id", "bond", "range", List.of(5.0, 10.0), "damage", 4, "cooldown", 10, "cast", 0.5)))
                .ability(ELITE, new BookAbilities.Devour(Spec.of("id", "devorer")))
                .ability(SCOURGE, new BookAbilities.Frenzy(Spec.of("id", "frenesie")))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ROTTEN_FLESH, 1, 3, 1, 1, null),
                        new LootTable.Entry(Material.BONE, 1, 2, 0.4, 1, null))))
                .xp(5)
                .build();
    }

    /** Page volante : harcèlement qui marque ; trois Pages Fléau se relient en Livre volant. */
    static MobVariant page() {
        return MobVariant.builder("page-volante", EntityType.VEX)
                .name("Page volante").rarity(Rarity.PEU_COMMUN)
                .lore("Harcèlement qui marque.", "La tuer vite, en zone.")
                .stats(0.8, 0.8, 1, 0, 0, 0, 1)
                .onLoad(entity -> {
                    if (entity instanceof Vex vex) {
                        vex.setLimitedLifetime(false);
                    }
                })
                .ability(ORDINARY, new Strike(Spec.of("id", "coupure", "chance", 1, "damage", 0, "cooldown", 2, "states", List.of("mark 0.1 4"))))
                .stat(STURDY, Stat.DODGE, 0.15)
                .ability(DANGEROUS, new Charge(Spec.of("id", "tourbillon", "range", List.of(4.0, 12.0), "damage", 3, "speed", 1.2,
                        "cooldown", 10, "cast", 0.6)))
                .ability(ELITE, new Strike(Spec.of("id", "encre", "chance", 1, "damage", 0, "cooldown", 2, "states", List.of("slow 0.2 2"))))
                .ability(SCOURGE, new BookAbilities.Binding(Spec.of("id", "reliure")))
                .ability(SCOURGE, new BookAbilities.BookRay(Spec.of("id", "rayon-du-livre", "damage", 6, "length", 14, "cooldown", 10, "cast", 1,
                        "states", List.of("shatter 0.2 4"))))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.PAPER, 1, 3, 0.8, 1, null),
                        new LootTable.Entry(Material.INK_SAC, 1, 1, 0.3, 1, null))))
                .xp(4)
                .build();
    }

    /** Âme enchaînée : soutien qui protège les morts-vivants autour d'elle, à tuer en premier. */
    static MobVariant chainedSoul() {
        return MobVariant.builder("ame-enchainee", EntityType.VEX)
                .name("Âme enchaînée").rarity(Rarity.PEU_COMMUN)
                .lore("Protège les morts-vivants autour d'elle.", "La viser en premier.")
                .stats(1.3, 0.5, 0.6, 0, 0, 0, 1.1)
                .onLoad(entity -> {
                    if (entity instanceof Vex vex) {
                        vex.setLimitedLifetime(false);
                        vex.setGlowing(true);
                    }
                })
                .ability(ORDINARY, new BookAbilities.UndeadWard(Spec.of("id", "garde-des-morts", "radius", 6, "part", 0.15)))
                .ability(STURDY, new Aura(Spec.of("id", "plainte", "radius", 4, "damage", 1, "cooldown", 3, "particle", "SOUL")))
                .ability(DANGEROUS, new BookAbilities.MinorLink(Spec.of("id", "lien")))
                .ability(ELITE, new Strike(Spec.of("id", "hantise", "chance", 1, "damage", 0, "cooldown", 4, "states", List.of("fear 1 1"))))
                .ability(SCOURGE, new BookAbilities.Deliverance(Spec.of("id", "delivrance")))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.SOUL_SAND, 1, 2, 0.5, 1, null))))
                .xp(6)
                .build();
    }

    /** Garde d'os : tank de mêlée qui protège les siens. */
    static MobVariant boneGuard() {
        return MobVariant.builder("garde-os", EntityType.SKELETON)
                .name("Garde d'os").rarity(Rarity.PEU_COMMUN)
                .lore("Tank de mêlée qui protège les siens.", "Le contourner, l'attirer contre un mur.")
                .stats(1.5, 1.2, 1, 6, 0.3, 0.1, 1.05)
                .equip(EquipmentSlot.HAND, Material.IRON_SWORD)
                .equip(EquipmentSlot.HEAD, Material.IRON_HELMET)
                .ability(ORDINARY, new Strike(Spec.of("id", "coup-d-epee", "chance", 0.35, "damage", 0, "cooldown", 2,
                        "states", List.of("fragile 0.15 4"))))
                // Bouclier : les tirs venus de face perdent 30 %
                .onDamaged(STURDY, (mob, event) -> {
                    if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile projectile
                            && fromFront(mob.entity(), projectile)) {
                        event.setDamage(event.getDamage() * 0.7);
                        mob.entity().getWorld().playSound(mob.entity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
                    }
                })
                .ability(DANGEROUS, new Charge(Spec.of("id", "charge", "range", List.of(6.0, 14.0), "damage", 6, "wall-stun", 2, "cooldown", 14)))
                .ability(ELITE, new BookAbilities.Interpose(Spec.of("id", "interposition", "damage", 4)))
                .ability(SCOURGE, new Pillar(Spec.of("id", "mur-d-os", "mode", "wall", "block", "BONE_BLOCK", "seconds", 6, "cooldown", 18)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.BONE, 1, 3, 1, 1, null),
                        new LootTable.Entry(Material.IRON_NUGGET, 2, 5, 0.4, 3, null))))
                .xp(6)
                .build();
    }

    /** Archer d'os : distance, recule quand on le colle. */
    static MobVariant boneArcher() {
        return MobVariant.builder("archer-os", EntityType.SKELETON)
                .name("Archer d'os").rarity(Rarity.COMMUN)
                .lore("Distance, recule quand on le colle.", "Aller au contact, se couvrir.")
                .stats(1.1, 1, 1, 0, 0, 0, 1)
                .equip(EquipmentSlot.HAND, Material.BOW)
                .ai(new AiProfile(24, 1, 0.3, 0.6, 0.5, 0.6, -0.2, 1.3, 1500, 3, 0.3, false))
                // Flèches magiques : traînée bleue, dégâts magiques (résistance magique, pas l'armure)
                .magicShots()
                .ability(ORDINARY, new Volley(Spec.of("id", "salve", "count", 3, "spread", 6, "damage", 3, "cooldown", 7, "cast", 0.5, "damage-type", "magic")))
                .stat(STURDY, Stat.CRIT_CHANCE, 0.15)
                .ability(DANGEROUS, new Evade(Spec.of("id", "recul", "chance", 0.4, "cooldown", 6)))
                .ability(ELITE, new Volley(Spec.of("id", "fleche-clouante", "count", 1, "spread", 0, "damage", 4, "cooldown", 12,
                        "weight", 1.1, "states", List.of("root 1 1"), "damage-type", "magic")))
                .ability(SCOURGE, new Zone(Spec.of("id", "pluie-de-fleches", "mode", "impact", "count", 5, "radius", 2, "damage", 5,
                        "damage-type", "magic", "particle", "CRIT", "cooldown", 18, "cast", 1.2)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.ARROW, 2, 6, 1, 1, null),
                        new LootTable.Entry(Material.BONE, 1, 2, 0.6, 1, null))))
                .xp(5)
                .build();
    }

    /** Acolyte du Livre : soutien des morts, à interrompre et tuer en premier. */
    static MobVariant acolyte() {
        return MobVariant.builder("acolyte", EntityType.EVOKER)
                .name("Acolyte du Livre").rarity(Rarity.RARE)
                .lore("Soigne et bénit les morts-vivants.", "L'interrompre, le tuer en premier.")
                .stats(1.4, 1, 1, 0, 0, 0.1, 1)
                .ai(new AiProfile(24, 1, -0.2, 0.5, 0.5, 0.8, -0.3, 1.25, 2000, 3, 0.3, false))
                // Pas de crocs ni de vex de Minecraft (il ne lance que les sorts du Livre), et il ne fuit plus les joueurs
                .onLoad(entity -> {
                    if (entity instanceof Mob mob) {
                        for (var goal : List.copyOf(Bukkit.getMobGoals().getAllGoals(mob))) {
                            if (goal.getKey().getNamespacedKey().getKey().contains("spell") || goal.getKey().getNamespacedKey().getKey().contains("avoid")) {
                                Bukkit.getMobGoals().removeGoal(mob, goal);
                            }
                        }
                    }
                })
                .ability(ORDINARY, new BookAbilities.BlackPrayer(Spec.of("id", "priere-noire")))
                .ability(STURDY, new Blink(Spec.of("id", "fuyant", "mode", "escape", "escape-health", 0.5, "trigger", "on-damaged", "chance", 0.2, "cooldown", 18)))
                .ability(DANGEROUS, new Curse(Spec.of("id", "silence", "damage", 2, "cast", 1, "cooldown", 14, "states", List.of("silence 1 2"))))
                .ability(ELITE, new Summon(Spec.of("id", "relever", "mobs", List.of("SKELETON"), "count", List.of(2, 2), "max-alive", 4,
                        "cooldown", 25, "cast", 1.5)))
                .ability(SCOURGE, new BookAbilities.Blessing(Spec.of("id", "benediction-du-livre")))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.BOOK, 1, 1, 0.5, 1, null),
                        new LootTable.Entry(Material.EMERALD, 1, 3, 0.6, 1, null),
                        new LootTable.Entry(Material.PAPER, 1, 1, 0.1, 4, "page-livre-des-morts"))))
                .xp(10)
                .build();
    }

    /** Zombie mineur : grottes, éboulements. */
    static MobVariant miner() {
        return MobVariant.builder("zombie-mineur", EntityType.ZOMBIE)
                .name("Zombie mineur").rarity(Rarity.COMMUN)
                .lore("Dans les grottes : éboulements.", "Ne pas rester sous lui.")
                .stats(1.2, 1.1, 1, 0, 0.1, 0, 1)
                .equip(EquipmentSlot.HAND, Material.IRON_PICKAXE)
                .equip(EquipmentSlot.HEAD, Material.CHAINMAIL_HELMET)
                .ability(ORDINARY, new Strike(Spec.of("id", "coup-de-pioche", "chance", 0.4, "damage", 0, "cooldown", 2,
                        "states", List.of("fragile 0.1 4"))))
                .stat(STURDY, Stat.ARMOR, 40)
                .ability(DANGEROUS, new Pillar(Spec.of("id", "eboulement", "mode", "spike", "block", "COBBLESTONE", "damage", 6, "cooldown", 12)))
                .ability(ELITE, new Strike(Spec.of("id", "lanterne", "chance", 0.3, "damage", 0, "cooldown", 6, "blind", 1)))
                .ability(SCOURGE, new Zone(Spec.of("id", "effondrement", "mode", "impact", "count", 4, "radius", 2, "damage", 6,
                        "damage-type", "physical", "particle", "LARGE_SMOKE", "reach", 10, "cooldown", 18, "cast", 1.2)))
                .loot(LootTable.of(List.of(new LootTable.Entry(Material.RAW_IRON, 1, 2, 0.3, 1, null),
                        new LootTable.Entry(Material.COAL, 1, 3, 0.5, 1, null),
                        new LootTable.Entry(Material.RAW_GOLD, 1, 1, 0.1, 3, null))))
                .xp(5)
                .build();
    }

    /** Le tir vient-il de devant lui (moins de 70° de son regard) ? */
    private static boolean fromFront(org.bukkit.entity.LivingEntity self, Projectile projectile) {
        Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : projectile;
        Vector look = self.getLocation().getDirection().setY(0);
        Vector toShooter = shooter.getLocation().toVector().subtract(self.getLocation().toVector()).setY(0);
        return look.lengthSquared() > 0 && toShooter.lengthSquared() > 0 && look.angle(toShooter) < Math.toRadians(70);
    }
}
