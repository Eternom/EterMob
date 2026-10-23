package fr.eternom.eterMob.module.boss.mortarius;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import fr.eternom.eterMob.module.boss.Boss;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mortarius, Seigneur des Tombes : un zombie 2,5 fois plus grand, 1 000 PV et une résistance de base de 2,5 (en groupe,
 * +35 % par joueur en plus dans le combat, recompté en continu) : environ 10 minutes seul en diamant Tranchant V, il faut
 * venir préparé. Il ne brûle pas au soleil. Aucun vrai bloc n'est posé ni cassé : piliers, sillons, tombes et ondes
 * sont des affichages et des particules.
 * <ul>
 *   <li>Combat : il choisit sa cible à la menace (les dégâts reçus, plus pour les tireurs, la proximité, et les joueurs
 *   presque morts qu'il veut achever ; il ne change que pour une menace nettement plus forte), frappe à 4,5 blocs, et
 *   choisit ses attaques selon la situation (joueurs groupés autour de lui, dispersés, au loin). Il bondit sur une cible
 *   inaccessible ou qui le tient à distance (flèche de plus de 10 blocs, ou cible à plus de 14 blocs pendant 3 s) : il
 *   s'accroupit 0,6 s et un cercle rouge marque le point visé ; à l'atterrissage, ceux dans le cercle décollent.</li>
 *   <li>Passifs : pas lourds ; aura de pourriture qui monte avec les phases (1, 2, puis 3 PV toutes les 2 s à travers
 *   l'armure ; Faiblesse en phase 2 ; Wither et 7 blocs en phase 3, 10 au dernier souffle) ; seigneur de la horde.</li>
 *   <li>Phase 1, le Seigneur : coup de masse en arc (bras levé 0,8 s avant) ; levée des morts (4 zombies fragiles et
 *   rapides sortent du sol, sillons 1 s avant).</li>
 *   <li>Phase 2, le Bouclier d'os : intouchable tant que 4 piliers d'os tiennent (relevés 30 s après) ; charge sur un
 *   joueur au loin (il racle le sol 1 s avant).</li>
 *   <li>Phase 3, la Fin : rage (plus rapide, frappe toutes les 0,7 s, coups de masse deux fois plus fréquents) ; triple
 *   onde sismique de plus en plus rapide (sauter à chaque anneau) ; les tombes (un cercle sous chaque joueur, des os en
 *   jaillissent 1,5 s après) ; drain de vie qui attire vers lui ; dernier souffle à 10 % (6 zombies levés, aura à 10
 *   blocs).</li>
 * </ul>
 * Chaque phase garde les capacités des précédentes. Les zombies levés ne lâchent rien et meurent avec lui.
 */
public class Mortarius extends Boss {

    private static final double SCALE = 2.5;
    /** Allonge de ses coups (blocs) : celle d'un mob de sa taille, et délai entre deux coups. */
    private static final double MELEE_REACH = 4.5;
    private static final long MELEE_COOLDOWN_MILLIS = 1_000;
    private static final long RAGE_MELEE_COOLDOWN_MILLIS = 700;
    /** Garde-fou : une attaque qui le garde occupé plus longtemps est abandonnée. */
    private static final long BUSY_LIMIT_MILLIS = 6_000;
    /** Achever : un joueur à moins de ce nombre de PV attire davantage son attention. */
    private static final double FINISH_HEALTH = 6;
    private static final double FINISH_THREAT = 8;
    /** Menace : part gardée à chaque tour (5 ticks), bonus des tireurs, marge pour changer de cible. */
    private static final double THREAT_DECAY = 0.98;
    private static final double RANGED_THREAT = 1.5;
    private static final double THREAT_SWITCH = 1.3;
    /** Inaccessible depuis ce nombre de vérifications (une par seconde) : il bondit ou change de cible. */
    private static final int UNREACHABLE_CHECKS = 3;
    private static final double LEAP_RADIUS = 3.5;
    private static final long LEAP_COOLDOWN_MILLIS = 8_000;
    private static final int LEAP_WINDUP = 12;
    /** Tenu à distance : flèche reçue de plus de FAR_HIT blocs (récente : moins de 3 s), ou cible à plus de FAR_TARGET. */
    private static final double FAR_HIT = 10;
    private static final double FAR_TARGET = 14;
    private static final long FAR_HIT_MEMORY_MILLIS = 3_000;

    /** Aura de pourriture par phase (1 à 3) : rayon et dégâts toutes les 2 s ; elle le soigne d'AURA_HEAL par joueur. */
    private static final double[] AURA_RADIUS = {5, 5, 7};
    private static final double AURA_HEAL = 1;
    private static final double LAST_STAND_AURA = 10;
    private static final double HORDE_RADIUS = 16;
    private static final double TARGET_RANGE = 32;

    private static final double SLAM_REACH = 4.5;
    private static final double SLAM_ANGLE = Math.toRadians(60);
    private static final int SLAM_WINDUP = 16;

    private static final int RAISED = 4;
    private static final int RAISED_MAX = 8;
    private static final int FURROW_TICKS = 20;
    /** Zombies levés : fragiles et rapides, sans équipement (ils encombrent, ils ne tankent pas). */
    private static final double RAISED_HEALTH = 8;
    private static final double RAISED_SPEED = 1.25;

    private static final int PILLARS = 4;
    private static final double PILLAR_HEALTH = 60;
    private static final double PILLAR_RADIUS = 5;
    /** Corps invisible du pilier (squelette agrandi, 1 × 3,4 blocs) : la taille du pilier affiché, pour prendre les coups. */
    private static final double PILLAR_SCALE = 1.7;
    private static final float PILLAR_HEIGHT = 3.4f;
    private static final long SHIELD_BACK_MILLIS = 30_000;
    /** Bonus des piliers tant qu'ils tiennent : soin (toutes les 2 s), vitesse, force, aura (dégâts et rayon). */
    private static final double PILLAR_HEAL = 3;
    private static final double PILLAR_SPEED = 0.2;
    private static final double PILLAR_STRENGTH = 0.3;
    private static final double PILLAR_AURA_DAMAGE = 1;
    private static final double PILLAR_AURA_RADIUS = 2;
    /** Il titube quand un pilier tombe (ticks). */
    private static final int PILLAR_STAGGER = 20;

    /** Charge finie contre un mur : sonné (ticks), et dégâts reçus multipliés pendant ce temps. */
    private static final int STUN_TICKS = 60;
    private static final double STUN_DAMAGE = 1.5;

    /** Entrée : il sort de terre depuis cette profondeur, en tant de ticks. */
    private static final double INTRO_DEPTH = 5;
    private static final int INTRO_TICKS = 60;

    /** Coup de masse bloqué au bouclier : bouclier désactivé (ticks). */
    private static final int SHIELD_DISABLE_TICKS = 100;

    private static final double CHARGE_MIN = 6;
    private static final double CHARGE_RANGE = 24;
    private static final double CHARGE_STEP = 0.9;
    private static final int CHARGE_TICKS = 16;
    private static final int CHARGE_WINDUP = 20;

    /** Onde sismique : 3 anneaux, de plus en plus rapides (blocs par tick), épais (on ne passe pas entre), espacés. */
    private static final double WAVE_RADIUS = 14;
    private static final double[] WAVE_SPEEDS = {0.8, 1.1, 1.4};
    private static final int WAVE_GAP = 16;
    private static final double WAVE_THICKNESS = 1.3;
    private static final int WAVE_WINDUP = 20;

    /** Les tombes : un cercle sous chaque joueur, des os en jaillissent après TOMB_WINDUP ticks. */
    private static final double TOMB_RADIUS = 1.8;
    private static final int TOMB_WINDUP = 30;

    /** Dernier souffle : sous cette part de vie, une fois. */
    private static final double LAST_STAND = 0.1;
    private static final int LAST_STAND_RAISED = 6;

    private static final double DRAIN_RANGE = 10;
    /** Le drain attire les joueurs vers lui (blocs par tick). */
    private static final double DRAIN_PULL = 1.1;
    private static final int DRAIN_WINDUP = 20;

    private static final double RAGE_SPEED = 0.2;
    private static final Color ROT = Color.fromRGB(0x5E8C31);
    private static final Color BLOOD = Color.fromRGB(0x8A0303);

    /** Rôle d'un pilier : le bonus qu'il donne à Mortarius tant qu'il tient, et sa couleur (lueur et rayon). */
    private enum Role {
        HEAL("soin", Color.fromRGB(0x3FD15A)),
        SPEED("vitesse", Color.fromRGB(0x3FB8E0)),
        STRENGTH("force", Color.fromRGB(0xE03F3F)),
        AURA("aura", Color.fromRGB(0x9B3FE0));

        final String label;
        final Color color;

        Role(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    /** Un pilier d'os : le bloc affiché, son corps invisible (qui reçoit les coups), son rôle et ses PV. */
    private static final class Pillar {
        final BlockDisplay display;
        final LivingEntity body;
        final Role role;
        double health = PILLAR_HEALTH;

        Pillar(BlockDisplay display, LivingEntity body, Role role) {
            this.display = display;
            this.body = body;
            this.role = role;
        }
    }

    /** Ce que Mortarius garde pour un combat. */
    private static final class State {
        boolean busy;
        long busySince;
        long stunnedUntil;
        boolean lastStand;
        long ticks;
        long shieldBackAt;
        Location lastStep;
        int unreachable;
        int farChecks;
        UUID farShooter;
        long farHitAt;
        final Map<UUID, Double> threat = new HashMap<>();
        final Map<UUID, Pillar> pillars = new HashMap<>();
        final List<Zombie> raised = new ArrayList<>();
    }

    /** Réglages (config.yml, bosses.mortarius) : valeurs par défaut ci-dessous. Dégâts avant armure et difficulté. */
    private double health = 1000;
    private double toughness = 2.5;
    private double enrageMinutes = 15;
    private double attackDamage = 12;
    private double slamDamage = 16;
    private double chargeDamage = 10;
    private double leapDamage = 10;
    private double waveDamage = 14;
    private double tombDamage = 16;
    private double drainDamage = 5;
    private double[] auraDamage = {1, 2, 3};

    private final Map<UUID, State> states = new HashMap<>();
    private NamespacedKey rageKey;
    private NamespacedKey pillarSpeedKey;
    private NamespacedKey pillarStrengthKey;

    public Mortarius() {
        super("mortarius", "Mortarius", "Seigneur des Tombes");
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    private NamespacedKey rageKey() {
        if (rageKey == null) {
            rageKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(Mortarius.class), "rage");
        }
        return rageKey;
    }

    private NamespacedKey pillarKey(boolean speed) {
        if (pillarSpeedKey == null) {
            pillarSpeedKey = new NamespacedKey(plugin(), "pillar_speed");
            pillarStrengthKey = new NamespacedKey(plugin(), "pillar_strength");
        }
        return speed ? pillarSpeedKey : pillarStrengthKey;
    }

    private static JavaPlugin plugin() {
        return JavaPlugin.getProvidingPlugin(Mortarius.class);
    }

    // ---- Définition ----

    @Override
    public Material icon() {
        return Material.ZOMBIE_HEAD;
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Zombie géant, 3 phases : ~10 min seul en diamant.",
                "Levée des morts, bouclier d'os, charge, bonds,",
                "triple onde (sauter !), tombes, drain qui attire,",
                "dernier souffle à 10 %.");
    }

    @Override
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        health = Math.clamp(section.getDouble("health", health), 1, 1024);
        toughness = Math.max(0.1, section.getDouble("toughness", toughness));
        enrageMinutes = section.getDouble("enrage-minutes", enrageMinutes);
        attackDamage = section.getDouble("damage.attack", attackDamage);
        slamDamage = section.getDouble("damage.slam", slamDamage);
        chargeDamage = section.getDouble("damage.charge", chargeDamage);
        leapDamage = section.getDouble("damage.leap", leapDamage);
        waveDamage = section.getDouble("damage.wave", waveDamage);
        tombDamage = section.getDouble("damage.tombs", tombDamage);
        drainDamage = section.getDouble("damage.drain", drainDamage);
        List<Double> aura = section.getDoubleList("damage.aura");
        if (aura.size() == 3) {
            auraDamage = new double[]{aura.get(0), aura.get(1), aura.get(2)};
        }
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
    public double enrageMinutes() {
        return enrageMinutes;
    }

    @Override
    public double height() {
        return 5;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return Zombie.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        Zombie zombie = (Zombie) entity;
        zombie.setAdult();
        zombie.setCanBreakDoors(false);
        zombie.setShouldBurnInDay(false);
        Minions.noReinforcements(zombie);
        base(zombie, Attribute.SCALE, SCALE);
        base(zombie, Attribute.KNOCKBACK_RESISTANCE, 1);
        base(zombie, Attribute.FOLLOW_RANGE, TARGET_RANGE + 16);
        base(zombie, Attribute.ATTACK_DAMAGE, attackDamage);
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            wear(equipment, EquipmentSlot.HEAD, Material.NETHERITE_HELMET);
            wear(equipment, EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE);
            wear(equipment, EquipmentSlot.HAND, Material.MACE);
        }
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void wear(EntityEquipment equipment, EquipmentSlot slot, Material material) {
        equipment.setItem(slot, new ItemStack(material));
        equipment.setDropChance(slot, 0);
    }

    @Override
    public String phaseName(int phase) {
        return switch (phase) {
            case 1 -> "Le Seigneur";
            case 2 -> "Le Bouclier d'os";
            default -> "La Fin";
        };
    }

    @Override
    public BossBar.Color color(int phase) {
        return switch (phase) {
            case 1 -> BossBar.Color.RED;
            case 2 -> BossBar.Color.WHITE;
            default -> BossBar.Color.PURPLE;
        };
    }

    @Override
    public String spawnLine() {
        return "Les tombes s'ouvrent. Agenouillez-vous.";
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Mes os sont mon rempart !" : "Si je tombe, vous tomberez avec moi !";
    }

    @Override
    public String deathLine() {
        return "Les tombes... se referment...";
    }

    // ---- Cycle du combat ----

    /**
     * Rechargé en plein coup (IA coupée, lueur) : il reprend normalement. L'attaque de Minecraft lui est retirée : il se
     * déplace et frappe lui-même (cible à la menace, allonge à sa taille).
     */
    @Override
    public void onLoad(BossFight fight) {
        Zombie boss = (Zombie) fight.entity();
        boss.setAI(true);
        boss.setGlowing(false);
        Bukkit.getMobGoals().removeGoal(boss, VanillaGoal.ZOMBIE_ATTACK);
    }

    /** Entrée : il sort de terre en 3 s (intouchable pendant ce temps), dans la poussière et le grondement. */
    @Override
    public int intro(BossFight fight) {
        Zombie boss = (Zombie) fight.entity();
        Location top = boss.getLocation();
        boss.setAI(false);
        boss.setGravity(false);
        boss.teleport(top.clone().subtract(0, INTRO_DEPTH, 0));
        boss.getWorld().playSound(top, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.6f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 2;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            boss.teleport(top.clone().subtract(0, INTRO_DEPTH * (1 - (double) elapsed[0] / INTRO_TICKS), 0));
            dust(top, 30, 1.5);
            if (elapsed[0] % 20 == 0) {
                boss.getWorld().playSound(top, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
            }
            if (elapsed[0] >= INTRO_TICKS) {
                task[0].cancel();
                boss.setGravity(true);
                boss.setAI(true);
                boss.getWorld().playSound(top, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.5f);
            }
        }, 2, 2);
        return INTRO_TICKS;
    }

    /** Personne ne le frappe depuis 60 s : il lève des morts autour des joueurs pour relancer le combat. */
    @Override
    public void onStall(BossFight fight) {
        List<Player> players = nearby(fight.entity(), TARGET_RANGE);
        if (!players.isEmpty()) {
            service().say(fight, "Vous fuyez ? Mes morts vous trouveront.");
            raiseDead(fight, state(fight), players.get(0));
        }
    }

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        LivingEntity boss = fight.entity();
        if (!resumed) {
            boss.getWorld().spawnParticle(Particle.BLOCK, boss.getLocation().add(0, 1, 0), 120, 2, 1, 2, 0.2,
                    Material.BONE_BLOCK.createBlockData());
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 2f, 0.5f);
        }
        if (phase == 2) {
            raiseShield(fight, state(fight));
        } else if (phase >= 3) {
            breakShield(fight, state(fight), false);
            rage(boss, true);
        }
    }

    @Override
    public void onReset(BossFight fight) {
        State state = state(fight);
        state.pillars.clear();
        state.raised.clear();
        state.busy = false;
        state.shieldBackAt = 0;
        state.lastStand = false;
        state.threat.clear();
        state.unreachable = 0;
        state.farChecks = 0;
        state.farShooter = null;
        state.stunnedUntil = 0;
        LivingEntity boss = fight.entity();
        rage(boss, false);
        pillarBuffs(boss, state);
        boss.setAI(true);
        boss.setGlowing(false);
    }

    @Override
    public void onDeath(BossFight fight) {
        LivingEntity boss = fight.entity();
        Location chest = boss.getLocation().add(0, boss.getHeight() / 2, 0);
        for (Zombie zombie : state(fight).raised) {
            if (zombie.isValid()) {
                zombie.getWorld().spawnParticle(Particle.ASH, zombie.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
            }
        }
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, chest, 2);
        boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, chest, 80, 1.2, 1.5, 1.2, 0.05);
        boss.getWorld().playSound(chest, Sound.ENTITY_WITHER_DEATH, 1.2f, 0.6f);
    }

    @Override
    public void onEnd(BossFight fight) {
        states.remove(fight.id());
    }

    // ---- Tour de combat ----

    @Override
    public void tick(BossFight fight) {
        State state = state(fight);
        Zombie boss = (Zombie) fight.entity();
        state.ticks += 5;
        state.raised.removeIf(zombie -> !zombie.isValid() || zombie.isDead());
        passives(fight, state, boss);
        if (state.busy) {
            if (System.currentTimeMillis() - state.busySince > BUSY_LIMIT_MILLIS) {
                // Garde-fou : une attaque interrompue (chunk, téléportation...) ne le bloque jamais
                release(boss, null, state);
            }
            return;
        }
        state.busySince = System.currentTimeMillis();
        Player target = chooseTarget(state, boss);
        if (target == null) {
            return;
        }
        double distance = target.getLocation().distance(boss.getLocation());
        Player shooter = farShooter(state, boss);
        if (shooter != null && fight.ready("leap", LEAP_COOLDOWN_MILLIS)) {
            boss.setTarget(shooter);
            leap(fight, state, shooter);
            return;
        }
        if (state.ticks % 20 == 0) {
            state.farChecks = distance > FAR_TARGET ? state.farChecks + 1 : 0;
            if (state.farChecks >= UNREACHABLE_CHECKS && fight.ready("leap", LEAP_COOLDOWN_MILLIS)) {
                state.farChecks = 0;
                leap(fight, state, target);
                return;
            }
        }
        if (state.ticks % 20 == 0 && unreachable(state, boss, target)) {
            if (fight.ready("leap", LEAP_COOLDOWN_MILLIS)) {
                leap(fight, state, target);
                return;
            }
            // Pas de saut possible : il se rabat sur un autre joueur, s'il y en a un
            state.threat.computeIfPresent(target.getUniqueId(), (id, value) -> value * 0.5);
        }
        move(boss, target, distance);
        int phase = fight.phase();
        boolean raging = phase >= 3;
        if (distance <= MELEE_REACH && boss.hasLineOfSight(target)
                && fight.ready("melee", raging ? RAGE_MELEE_COOLDOWN_MILLIS : MELEE_COOLDOWN_MILLIS)) {
            boss.swingMainHand();
            boss.attack(target);
        }
        if (raging && !state.lastStand && healthPart(boss) <= LAST_STAND) {
            lastStand(fight, state, target);
            return;
        }
        if (raging && phaseThree(fight, state, boss)) {
            return;
        }
        if (phase == 2 && state.pillars.isEmpty() && System.currentTimeMillis() >= state.shieldBackAt) {
            raiseShield(fight, state);
        }
        if (phase >= 2) {
            // La charge sert à rattraper un joueur au loin, pas à frapper celui qui est déjà au contact
            Player farthest = farthest(boss);
            if (farthest != null && farthest.getLocation().distance(boss.getLocation()) > 10 && fight.ready("charge", 10_000)) {
                charge(fight, state, farthest);
                return;
            }
        }
        if (distance <= SLAM_REACH && fight.ready("slam", raging ? 2_500 : 5_000)) {
            slam(fight, state, target);
            return;
        }
        if (state.raised.size() + RAISED <= RAISED_MAX && fight.ready("raise", 20_000)) {
            raiseDead(fight, state, target);
        }
    }

    /**
     * Phase 3 : l'attaque qui convient à la situation. Joueurs groupés près de lui : onde sismique ; joueurs dispersés ou
     * au loin : les tombes ; quelqu'un à portée de drain : drain. True si une attaque est lancée.
     */
    private boolean phaseThree(BossFight fight, State state, Zombie boss) {
        List<Player> close = nearby(boss, 8);
        List<Player> all = nearby(boss, TARGET_RANGE);
        boolean spread = all.stream().anyMatch(player -> player.getLocation().distance(boss.getLocation()) > 8);
        if ((close.size() >= 2 || !close.isEmpty() && all.size() == 1) && fight.ready("wave", 12_000)) {
            wave(fight, state);
            return true;
        }
        if ((spread || all.size() >= 2) && fight.ready("tombs", 14_000)) {
            tombs(fight, all);
            return false;
        }
        if (!nearby(boss, DRAIN_RANGE).isEmpty() && fight.ready("drain", 8_000)) {
            drain(fight, state);
            return true;
        }
        return false;
    }

    private static double healthPart(LivingEntity boss) {
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 1 : boss.getHealth() / max.getValue();
    }

    /**
     * Sa cible à la menace : dégâts qu'on lui a faits (×1,5 pour les tireurs), qui s'estompent, et proximité. Il ne change
     * de cible que pour une menace nettement plus forte (pas de va-et-vient).
     */
    private static Player chooseTarget(State state, Zombie boss) {
        List<Player> candidates = nearby(boss, TARGET_RANGE);
        state.threat.keySet().removeIf(id -> candidates.stream().noneMatch(player -> player.getUniqueId().equals(id)));
        state.threat.replaceAll((id, value) -> value * THREAT_DECAY);
        if (candidates.isEmpty()) {
            return null;
        }
        Player best = null;
        double bestScore = -1;
        for (Player player : candidates) {
            double score = score(state, boss, player);
            if (score > bestScore) {
                bestScore = score;
                best = player;
            }
        }
        Player current = boss.getTarget() instanceof Player player && candidates.contains(player) ? player : null;
        if (current != null && current != best && bestScore <= score(state, boss, current) * THREAT_SWITCH + 2) {
            return current;
        }
        if (best != current) {
            boss.setTarget(best);
        }
        return best;
    }

    private static double score(State state, LivingEntity boss, Player player) {
        double distance = player.getLocation().distance(boss.getLocation());
        double finish = player.getHealth() <= FINISH_HEALTH ? FINISH_THREAT : 0;
        return state.threat.getOrDefault(player.getUniqueId(), 0.0) + 6 / (1 + distance) + finish;
    }

    /** Il va vers sa cible et s'arrête à portée de coup, face à elle. */
    private static void move(Zombie boss, Player target, double distance) {
        if (distance > MELEE_REACH - 1) {
            boss.getPathfinder().moveTo(target, 1.0);
        } else {
            boss.getPathfinder().stopPathfinding();
            boss.lookAt(target);
        }
    }

    /** Une vérification par seconde : aucun chemin jusqu'à la cible (perchée, coincée) ; true après 3 de suite. */
    private static boolean unreachable(State state, Zombie boss, Player target) {
        if (target.getLocation().distance(boss.getLocation()) <= MELEE_REACH) {
            state.unreachable = 0;
            return false;
        }
        Pathfinder.PathResult path = boss.getPathfinder().findPath(target);
        boolean blocked = path == null || path.getFinalPoint() == null
                || path.getFinalPoint().distanceSquared(target.getLocation()) > 2.5 * 2.5;
        state.unreachable = blocked ? state.unreachable + 1 : 0;
        if (state.unreachable >= UNREACHABLE_CHECKS) {
            state.unreachable = 0;
            return true;
        }
        return false;
    }

    /**
     * Bond : il s'accroupit 0,6 s pendant qu'un cercle rouge marque au sol le point visé (la position de la cible à ce
     * moment), puis suit un arc calculé jusqu'au centre de ce cercle (point fixe : il ne suit pas en l'air) ; à
     * l'atterrissage, les joueurs dans le cercle prennent des dégâts et décollent. Arc tracé point par point plutôt
     * qu'une impulsion : le frottement et son IA le freinaient, il retombait trop court.
     */
    private void leap(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        Location aimed = target.getLocation();
        Location ground = ground(aimed);
        Location landing = ground != null ? ground : aimed;
        boss.setAI(false);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.9f);
        Location[] from = {null};
        int[] elapsed = {0};
        int[] flight = {0};
        double[] peak = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0]++;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            circle(landing, LEAP_RADIUS);
            if (elapsed[0] < LEAP_WINDUP) {
                if (elapsed[0] % 2 == 0) {
                    dust(boss.getLocation(), 10, 1);
                }
                return;
            }
            if (elapsed[0] == LEAP_WINDUP) {
                from[0] = boss.getLocation();
                double distance = Math.hypot(landing.getX() - from[0].getX(), landing.getZ() - from[0].getZ());
                flight[0] = (int) Math.clamp(10 + distance * 0.8, 14, 30);
                peak[0] = 3 + distance * 0.15 + Math.max(0, landing.getY() - from[0].getY());
                boss.setGravity(false);
                dust(from[0], 40, 1.5);
            }
            double progress = Math.min(1, (double) (elapsed[0] - LEAP_WINDUP) / flight[0]);
            Location point = from[0].clone().add(landing.toVector().subtract(from[0].toVector()).multiply(progress));
            point.add(0, 4 * peak[0] * progress * (1 - progress), 0);
            Vector heading = landing.toVector().subtract(from[0].toVector()).setY(0);
            if (heading.lengthSquared() > 0.01) {
                point.setDirection(heading);
            }
            boss.teleport(point);
            if (progress < 1) {
                return;
            }
            task[0].cancel();
            boss.setGravity(true);
            Location at = boss.getLocation();
            boss.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 1, 0, 1, 0);
            dust(at, 60, 2);
            boss.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            Hits.shockwave(boss, at, LEAP_RADIUS, leapDamage, 0.5, 0.9);
            release(boss, target, state);
        }, 1, 1);
    }

    /** Cercle rouge au sol : la zone d'atterrissage. */
    private static void circle(Location center, double radius) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(0xD01010), 1.4f);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, 0.15,
                    Math.sin(angle) * radius), 1, 0, 0, 0, 0, red);
        }
    }

    /** Le tireur qui l'a touché de loin il y a moins de 3 s, s'il est encore dans le combat ; null sinon. */
    private static Player farShooter(State state, LivingEntity boss) {
        if (state.farShooter == null || System.currentTimeMillis() - state.farHitAt > FAR_HIT_MEMORY_MILLIS) {
            return null;
        }
        Player shooter = Bukkit.getPlayer(state.farShooter);
        state.farShooter = null;
        return shooter != null && Hits.fighting(shooter) && shooter.getWorld() == boss.getWorld()
                && shooter.getLocation().distance(boss.getLocation()) <= TARGET_RANGE ? shooter : null;
    }

    /** Menace : qui le frappe attire son attention (les tireurs un peu plus). */
    private static void addThreat(State state, LivingEntity boss, EntityDamageEvent event) {
        if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (byEntity.getDamager() instanceof Player player) {
            state.threat.merge(player.getUniqueId(), event.getDamage(), Double::sum);
        } else if (byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            state.threat.merge(player.getUniqueId(), event.getDamage() * RANGED_THREAT, Double::sum);
            if (player.getWorld() == boss.getWorld() && player.getLocation().distance(boss.getLocation()) > FAR_HIT) {
                state.farShooter = player.getUniqueId();
                state.farHitAt = System.currentTimeMillis();
            }
        }
    }

    private static List<Player> nearby(LivingEntity boss, double range) {
        List<Player> players = new ArrayList<>();
        for (Player player : boss.getLocation().getNearbyPlayers(range)) {
            if (Hits.fighting(player)) {
                players.add(player);
            }
        }
        return players;
    }

    /** Le joueur le plus éloigné, entre 6 et 24 blocs, ou null. */
    private static Player farthest(LivingEntity boss) {
        Player farthest = null;
        double best = CHARGE_MIN * CHARGE_MIN;
        for (Player player : nearby(boss, CHARGE_RANGE)) {
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance >= best) {
                best = distance;
                farthest = player;
            }
        }
        return farthest;
    }

    /** Soin plafonné au seuil de sa phase (il ne remonte jamais dans la phase précédente). */
    private void heal(BossFight fight, double amount) {
        LivingEntity boss = fight.entity();
        if (boss.isValid() && !boss.isDead()) {
            boss.setHealth(Math.min(service().healthCap(fight), boss.getHealth() + amount));
        }
    }

    private static Location chest(LivingEntity entity) {
        return entity.getLocation().add(0, entity.getHeight() * 0.6, 0);
    }

    // ---- Passifs ----

    private void passives(BossFight fight, State state, Zombie boss) {
        Location at = boss.getLocation();
        if (state.ticks % 10 == 0) {
            // Pas lourds
            if (state.lastStep != null && state.lastStep.getWorld() == at.getWorld() && state.lastStep.distanceSquared(at) > 0.04) {
                dust(at, 25, 1.2);
                at.getWorld().playSound(at, Sound.ENTITY_RAVAGER_STEP, 1f, 0.5f);
            }
            state.lastStep = at;
            // Aura de pourriture : brume verte au sol
            double aura = auraRadius(fight, state);
            int points = (int) (aura * 3.2);
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2 / points;
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * aura, 0.2,
                        Math.sin(angle) * aura), 1, 0.1, 0.05, 0.1, 0, new Particle.DustOptions(ROT, 1.5f));
            }
            pillarBuffs(boss, state);
            for (Pillar pillar : state.pillars.values()) {
                tether(pillar.body.getLocation().add(0, PILLAR_HEIGHT - 0.3, 0), chest(boss), pillar.role.color);
            }
            if (fight.phase() == 2 && !state.pillars.isEmpty()) {
                // Bouclier : anneau d'os autour de lui
                for (int i = 0; i < 10; i++) {
                    double angle = i * Math.PI / 5 + state.ticks * 0.05;
                    at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(Math.cos(angle) * 1.8, 1 + (i % 3),
                            Math.sin(angle) * 1.8), 2, 0.05, 0.05, 0.05, 0, Material.BONE_BLOCK.createBlockData());
                }
            }
            if (fight.phase() >= 3) {
                at.getWorld().spawnParticle(Particle.DUST, chest(boss), 12, 0.6, 1, 0.6, 0, new Particle.DustOptions(BLOOD, 2f));
            }
        }
        if (state.ticks % 40 == 0) {
            int phase = Math.min(3, fight.phase());
            double damage = auraDamage[phase - 1] + (has(state, Role.AURA) ? PILLAR_AURA_DAMAGE : 0);
            if (has(state, Role.HEAL)) {
                heal(fight, PILLAR_HEAL);
                boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, boss.getHeight() + 0.3, 0), 2, 0.4, 0.2, 0.4, 0);
            }
            for (Player player : nearby(boss, auraRadius(fight, state))) {
                Hits.trueDamage(player, damage, boss);
                fight.taken(player, damage);
                heal(fight, AURA_HEAL);
                if (phase == 2) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0));
                } else if (phase >= 3) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 0));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f + 0.2f * phase, 0.7f);
            }
        }
        if (state.ticks % 20 == 0) {
            horde(boss);
        }
        if (state.ticks % 200 == 0) {
            at.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_AMBIENT, 2f, 0.4f);
        }
    }

    private static double auraRadius(BossFight fight, State state) {
        double radius = state.lastStand ? LAST_STAND_AURA : AURA_RADIUS[Math.min(3, fight.phase()) - 1];
        return radius + (has(state, Role.AURA) ? PILLAR_AURA_RADIUS : 0);
    }

    private static boolean has(State state, Role role) {
        return state.pillars.values().stream().anyMatch(pillar -> pillar.role == role);
    }

    /** Bonus de vitesse et de force tant que le pilier correspondant tient. */
    private void pillarBuffs(LivingEntity boss, State state) {
        buff(boss.getAttribute(Attribute.MOVEMENT_SPEED), pillarKey(true), has(state, Role.SPEED) ? PILLAR_SPEED : 0);
        buff(boss.getAttribute(Attribute.ATTACK_DAMAGE), pillarKey(false), has(state, Role.STRENGTH) ? PILLAR_STRENGTH : 0);
    }

    private static void buff(AttributeInstance attribute, NamespacedKey key, double amount) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(key);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        attribute.removeModifier(key);
        if (amount != 0) {
            attribute.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Rayon coloré du sommet d'un pilier jusqu'à lui. */
    private static void tether(Location from, Location to, Color color) {
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.max(1, step.length() * 1.5);
        step.multiply(1.0 / points);
        Location point = from.clone();
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9f);
        for (int i = 0; i < points; i++) {
            point.add(step);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    /** Seigneur de la horde : les zombies proches chassent sa cible, plus vite, avec une lueur rouge. */
    private static void horde(Zombie boss) {
        LivingEntity target = boss.getTarget();
        for (Zombie zombie : boss.getLocation().getNearbyEntitiesByType(Zombie.class, HORDE_RADIUS)) {
            if (zombie == boss || zombie.isDead()) {
                continue;
            }
            if (target != null && zombie.getTarget() == null) {
                zombie.setTarget(target);
            }
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
            zombie.getWorld().spawnParticle(Particle.DUST, zombie.getLocation().add(0, zombie.getHeight() + 0.3, 0), 3,
                    0.15, 0.05, 0.15, 0, new Particle.DustOptions(BLOOD, 1f));
        }
    }

    private void rage(LivingEntity boss, boolean on) {
        AttributeInstance speed = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        NamespacedKey key = rageKey();
        speed.removeModifier(key);
        if (on) {
            speed.addTransientModifier(new AttributeModifier(key, RAGE_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Poussière du bloc sous ce point. */
    private static void dust(Location at, int count, double spread) {
        Block ground = at.getBlock().getRelative(0, -1, 0);
        if (ground.getType().isAir()) {
            return;
        }
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), count, spread, 0.1, spread, 0.1, ground.getBlockData());
    }

    /** Fige le boss face à sa cible pendant une préparation. */
    private static void freeze(LivingEntity boss, Location facing) {
        boss.setAI(false);
        Location look = boss.getLocation();
        look.setDirection(facing.toVector().subtract(look.toVector()).setY(0));
        boss.setRotation(look.getYaw(), 0);
    }

    private static void release(Zombie boss, Player target, State state) {
        state.busy = false;
        if (boss.isValid()) {
            boss.setAI(true);
            if (target != null && target.isOnline()) {
                boss.setTarget(target);
            }
        }
    }

    // ---- Phase 1 ----

    /** Coup de masse : il lève le bras 0,8 s, puis frappe en arc devant lui. */
    private void slam(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        freeze(boss, target.getLocation());
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.5f, 0.5f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 4;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            Vector forward = boss.getLocation().getDirection().setY(0).normalize();
            Location arm = boss.getLocation().add(forward.clone().multiply(0.8)).add(0, boss.getHeight() * 0.9, 0);
            boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, arm, 6, 0.2, 0.2, 0.2, 0.01);
            if (elapsed[0] < SLAM_WINDUP) {
                crack(boss.getLocation(), forward);
                return;
            }
            task[0].cancel();
            Location front = boss.getLocation().add(forward.clone().multiply(2.5));
            boss.swingMainHand();
            for (Player player : nearby(boss, SLAM_REACH + 1)) {
                Vector to = player.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                if (to.length() <= SLAM_REACH && (to.lengthSquared() < 0.01 || to.angle(forward) <= SLAM_ANGLE)) {
                    if (blocks(player, boss)) {
                        // Bloqué au bouclier : pas de dégâts, mais le bouclier est hors d'usage 5 s (comme une hache)
                        player.setCooldown(Material.SHIELD, SHIELD_DISABLE_TICKS);
                        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.8f);
                        Hits.knock(player, boss.getLocation(), 0.8, 0.3);
                        continue;
                    }
                    player.damage(slamDamage * (has(state, Role.STRENGTH) ? 1 + PILLAR_STRENGTH : 1), boss);
                    Hits.knock(player, boss.getLocation(), 1.4, 0.5);
                }
            }
            for (int i = 1; i <= 3; i++) {
                boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(forward.clone().multiply(i)).add(0, 1, 0), 1);
            }
            boss.getWorld().spawnParticle(Particle.EXPLOSION, front, 2);
            dust(front, 30, 1);
            boss.getWorld().playSound(front, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
            boss.getWorld().playSound(front, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            release(boss, target, state);
        }, 0, 4);
    }

    /** Fissure rouge au sol : l'arc que va balayer le coup de masse. */
    private static void crack(Location center, Vector forward) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(0x8A1010), 1.2f);
        double base = Math.atan2(forward.getZ(), forward.getX());
        for (double angle = -SLAM_ANGLE; angle <= SLAM_ANGLE; angle += SLAM_ANGLE / 4) {
            for (double distance = 1.5; distance <= SLAM_REACH; distance += 1) {
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(base + angle) * distance, 0.15,
                        Math.sin(base + angle) * distance), 1, 0, 0, 0, 0, red);
            }
        }
    }

    /** Le joueur bloque au bouclier, face au boss. */
    private static boolean blocks(Player player, LivingEntity boss) {
        if (!player.isBlocking()) {
            return false;
        }
        Vector toBoss = boss.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector facing = player.getLocation().getDirection().setY(0);
        return toBoss.lengthSquared() < 0.01 || facing.lengthSquared() < 0.01 || facing.angle(toBoss) < Math.PI / 2;
    }

    /** Levée des morts : sillons 1 s, puis 4 zombies remontent du sol. */
    private void raiseDead(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        // Autour des joueurs, pour les encercler (autour de lui s'il n'y a pas la place)
        List<Player> players = nearby(boss, TARGET_RANGE);
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < RAISED; i++) {
            Location around = players.isEmpty() ? boss.getLocation() : players.get(i % players.size()).getLocation();
            Location spot = groundNear(around, 2, 4);
            if (spot == null) {
                spot = groundNear(boss.getLocation(), 3, 6);
            }
            if (spot != null) {
                spots.add(spot);
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 2f, 0.5f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 4;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            for (Location spot : spots) {
                dust(spot, 12, 0.4);
                if (elapsed[0] % 8 == 0) {
                    spot.getWorld().playSound(spot, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 0.6f);
                }
            }
            if (elapsed[0] < FURROW_TICKS) {
                return;
            }
            task[0].cancel();
            for (Location spot : spots) {
                rise(fight, state, spot, target);
            }
        }, 0, 4);
    }

    /** Un zombie placé 2 blocs sous le sol remonte en 0,5 s (intouchable pendant ce temps : pas d'étouffement). */
    private void rise(BossFight fight, State state, Location spot, Player target) {
        Zombie zombie = Minions.spawn(spot.clone().subtract(0, 2, 0), Zombie.class, minion -> {
            minion.setAdult();
            minion.setAI(false);
            minion.setInvulnerable(true);
            minion.setGravity(false);
            minion.setShouldBurnInDay(false);
            if (minion.getEquipment() != null) {
                minion.getEquipment().clear();
            }
            base(minion, Attribute.MAX_HEALTH, RAISED_HEALTH);
            minion.setHealth(RAISED_HEALTH);
            AttributeInstance speed = minion.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * RAISED_SPEED);
            }
        });
        service().own(fight, zombie);
        state.raised.add(zombie);
        int[] step = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            step[0]++;
            if (!zombie.isValid()) {
                task[0].cancel();
                return;
            }
            zombie.teleport(spot.clone().subtract(0, 2 - step[0] * 0.2, 0));
            dust(spot, 6, 0.3);
            if (step[0] < 10) {
                return;
            }
            task[0].cancel();
            zombie.setAI(true);
            zombie.setInvulnerable(false);
            zombie.setGravity(true);
            if (target.isOnline()) {
                zombie.setTarget(target);
            }
        }, 1, 1);
    }

    /** Un sol libre (2 blocs d'air sur un bloc solide) entre min et max blocs du centre, ou null. */
    private static Location groundNear(Location center, double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = random.nextDouble(min, max);
            Location base = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Location ground = ground(base);
            if (ground != null) {
                return ground;
            }
        }
        return null;
    }

    /** Le sol à cet endroit (3 blocs plus haut ou plus bas au plus), ou null. */
    private static Location ground(Location point) {
        for (int dy = 0; dy <= 6; dy++) {
            int offset = dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2;
            Block feet = point.clone().add(0, offset, 0).getBlock();
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    // ---- Phase 2 ----

    /** Le bouclier d'os : 4 piliers (affichages, pas de vrais blocs) autour de lui ; intouchable tant qu'il en reste. */
    private void raiseShield(BossFight fight, State state) {
        if (!state.pillars.isEmpty()) {
            return;
        }
        LivingEntity boss = fight.entity();
        BlockData bone = Material.BONE_BLOCK.createBlockData();
        List<Role> roles = new ArrayList<>(List.of(Role.values()));
        java.util.Collections.shuffle(roles);
        service().say(fight, "Mes piliers me portent : soin, vitesse, force, aura. Choisissez bien.");
        for (int i = 0; i < PILLARS; i++) {
            Role role = roles.get(i % roles.size());
            double angle = Math.PI / 4 + i * Math.PI / 2;
            Location spot = ground(boss.getLocation().add(Math.cos(angle) * PILLAR_RADIUS, 0, Math.sin(angle) * PILLAR_RADIUS));
            if (spot == null) {
                continue;
            }
            BlockDisplay display = spot.getWorld().spawn(spot, BlockDisplay.class, block -> {
                block.setBlock(bone);
                block.setPersistent(false);
                block.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(),
                        new Vector3f(1, PILLAR_HEIGHT, 1), new AxisAngle4f()));
                block.setGlowing(true);
                block.setGlowColorOverride(role.color);
            });
            Skeleton body = spot.getWorld().spawn(spot, Skeleton.class, support -> {
                support.setInvisible(true);
                support.setSilent(true);
                support.setAI(false);
                support.setGravity(false);
                support.setCollidable(false);
                support.setPersistent(false);
                support.setShouldBurnInDay(false);
                if (support.getEquipment() != null) {
                    support.getEquipment().clear();
                }
                base(support, Attribute.SCALE, PILLAR_SCALE);
            });
            service().own(fight, display);
            service().own(fight, body);
            state.pillars.put(body.getUniqueId(), new Pillar(display, body, role));
            spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.1, bone);
            spot.getWorld().playSound(spot, Sound.BLOCK_BONE_BLOCK_PLACE, 1.5f, 0.6f);
        }
    }

    private void breakShield(BossFight fight, State state, boolean announce) {
        for (Pillar pillar : new ArrayList<>(state.pillars.values())) {
            destroy(pillar);
        }
        state.pillars.clear();
        if (announce) {
            state.shieldBackAt = System.currentTimeMillis() + SHIELD_BACK_MILLIS;
            LivingEntity boss = fight.entity();
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);
            service().say(fight, "Mon rempart... !");
        }
    }

    private static void destroy(Pillar pillar) {
        Location at = pillar.body.getLocation().add(0, 1.5, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 50, 0.4, 1, 0.4, 0.1, Material.BONE_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1.5f, 0.6f);
        pillar.display.remove();
        pillar.body.remove();
    }

    /** Bouclier levé : le boss ne prend aucun dégât. */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        State current = state(fight);
        addThreat(current, fight.entity(), event);
        if (System.currentTimeMillis() < current.stunnedUntil) {
            event.setDamage(event.getDamage() * STUN_DAMAGE);
            fight.entity().getWorld().spawnParticle(Particle.CRIT, chest(fight.entity()), 12, 0.6, 0.8, 0.6, 0.2);
        }
        if (fight.phase() == 2 && !state(fight).pillars.isEmpty()) {
            event.setCancelled(true);
            LivingEntity boss = fight.entity();
            boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, chest(boss), 10, 0.6, 0.8, 0.6, 0.1);
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BONE_BLOCK_HIT, 1f, 0.6f);
        }
    }

    /** Coup sur un pilier : il perd des PV ; le dernier cassé fait tomber le bouclier pour 30 s. */
    @Override
    public void onOwnedDamaged(BossFight fight, Entity owned, EntityDamageEvent event) {
        State state = state(fight);
        Pillar pillar = state.pillars.get(owned.getUniqueId());
        if (pillar == null) {
            return;
        }
        event.setCancelled(true);
        if (!byPlayer(event)) {
            return;
        }
        pillar.health -= event.getDamage() / fight.toughness();
        Location at = owned.getLocation().add(0, 1.2, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.3, 0.5, 0.3, 0.05, Material.BONE_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_HIT, 1f, 0.8f);
        if (pillar.health > 0) {
            return;
        }
        state.pillars.remove(owned.getUniqueId());
        destroy(pillar);
        Player breaker = breaker(event);
        if (breaker != null) {
            fight.count(breaker, "piliers");
        }
        service().say(fight, "Mon pilier de " + pillar.role.label + "... !");
        stagger(fight, state);
        if (state.pillars.isEmpty()) {
            breakShield(fight, state, true);
        }
    }

    private static Player breaker(EntityDamageEvent event) {
        if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        return byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player
                ? player : null;
    }

    /** Il titube 1 s quand un pilier tombe (s'il n'est pas déjà en pleine attaque). */
    private void stagger(BossFight fight, State state) {
        Zombie boss = (Zombie) fight.entity();
        if (state.busy) {
            return;
        }
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        boss.setAI(false);
        boss.getWorld().spawnParticle(Particle.CRIT, chest(boss), 20, 0.6, 0.8, 0.6, 0.1);
        Bukkit.getScheduler().runTaskLater(plugin(), () -> release(boss, null, state), PILLAR_STAGGER);
    }

    /** Coup d'un joueur (de près ou avec un projectile) : seuls ceux-là abîment les piliers (pas le soleil ni les mobs). */
    private static boolean byPlayer(EntityDamageEvent event) {
        return event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
                && (byEntity.getDamager() instanceof Player
                || byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player);
    }

    /** Charge : il racle le sol 1 s, puis fonce en ligne droite sur le joueur le plus éloigné. */
    private void charge(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        freeze(boss, target.getLocation());
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.5f, 0.4f);
        int[] elapsed = {0};
        Vector[] direction = {null};
        Set<UUID> hit = new HashSet<>();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0]++;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            if (elapsed[0] <= CHARGE_WINDUP) {
                if (elapsed[0] % 4 == 0) {
                    dust(boss.getLocation(), 15, 0.8);
                }
                if (elapsed[0] == CHARGE_WINDUP) {
                    Vector to = target.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                    direction[0] = to.lengthSquared() < 0.01 ? boss.getLocation().getDirection().setY(0).normalize() : to.normalize();
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.7f);
                }
                return;
            }
            Location next = boss.getLocation().add(direction[0].clone().multiply(CHARGE_STEP));
            boolean blocked = !next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable();
            if (blocked || elapsed[0] > CHARGE_WINDUP + CHARGE_TICKS) {
                task[0].cancel();
                dust(boss.getLocation(), 40, 1.5);
                if (blocked) {
                    stun(boss, target, state);
                } else {
                    release(boss, target, state);
                }
                return;
            }
            boss.teleport(next);
            boss.getWorld().spawnParticle(Particle.CLOUD, boss.getLocation(), 6, 0.6, 0.1, 0.6, 0.02);
            for (Player player : nearby(boss, 2.5)) {
                if (hit.add(player.getUniqueId())) {
                    player.damage(chargeDamage, boss);
                    Hits.knock(player, boss.getLocation(), 1.8, 0.6);
                    boss.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
                }
            }
        }, 1, 1);
    }

    /** Charge finie contre un mur : sonné 3 s (étoiles), il prend 50 % de dégâts en plus pendant ce temps. */
    private static void stun(Zombie boss, Player target, State state) {
        state.stunnedUntil = System.currentTimeMillis() + STUN_TICKS * 50L;
        state.busySince = System.currentTimeMillis();
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 2f, 0.5f);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.5f, 0.6f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 5;
            if (!boss.isValid() || elapsed[0] > STUN_TICKS) {
                task[0].cancel();
                release(boss, target, state);
                return;
            }
            Location head = boss.getLocation().add(0, boss.getHeight() + 0.4, 0);
            for (int i = 0; i < 5; i++) {
                double angle = elapsed[0] * 0.3 + i * Math.PI * 2 / 5;
                boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, head.clone().add(Math.cos(angle) * 0.8, 0, Math.sin(angle) * 0.8), 1);
            }
        }, 0, 5);
    }

    // ---- Phase 3 ----

    /**
     * Onde sismique : il lève les bras 1 s, frappe le sol, et trois anneaux épais s'élargissent l'un après l'autre, de plus
     * en plus vite ; il faut sauter quand chacun passe (au sol : dégâts, décollage, Lenteur).
     */
    private void wave(BossFight fight, State state) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        boss.setAI(false);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
        Location center = boss.getLocation();
        int rings = WAVE_SPEEDS.length;
        List<Set<UUID>> hit = new ArrayList<>();
        for (int i = 0; i < rings; i++) {
            hit.add(new HashSet<>());
        }
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0]++;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            if (elapsed[0] < WAVE_WINDUP) {
                if (elapsed[0] % 4 == 0) {
                    boss.getWorld().spawnParticle(Particle.ASH, boss.getLocation().add(0, boss.getHeight() + 0.5, 0), 20, 0.8, 0.3, 0.8, 0);
                }
                return;
            }
            Block ground = center.getBlock().getRelative(0, -1, 0);
            BlockData data = ground.getType().isAir() ? Material.DIRT.createBlockData() : ground.getBlockData();
            boolean running = false;
            for (int ring = 0; ring < rings; ring++) {
                int started = elapsed[0] - WAVE_WINDUP - ring * WAVE_GAP;
                if (started < 0) {
                    running = true;
                    continue;
                }
                if (started == 0) {
                    boss.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0, 0.5, 0);
                    boss.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f + ring * 0.15f);
                }
                double radius = 1 + started * WAVE_SPEEDS[ring];
                if (radius > WAVE_RADIUS) {
                    continue;
                }
                running = true;
                int points = (int) (8 + radius * 6);
                for (int i = 0; i < points; i++) {
                    double angle = i * Math.PI * 2 / points;
                    center.getWorld().spawnParticle(Particle.BLOCK, center.clone().add(Math.cos(angle) * radius, 0.2,
                            Math.sin(angle) * radius), 3, 0.15, 0.15, 0.15, 0, data);
                }
                for (Player player : center.getNearbyPlayers(radius + WAVE_THICKNESS)) {
                    Location feet = player.getLocation();
                    double distance = Math.hypot(feet.getX() - center.getX(), feet.getZ() - center.getZ());
                    if (Hits.fighting(player) && Math.abs(distance - radius) <= WAVE_THICKNESS
                            && Math.abs(feet.getY() - center.getY()) < 2.5 && ((Entity) player).isOnGround()
                            && hit.get(ring).add(player.getUniqueId())) {
                        player.damage(waveDamage, boss);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                        Hits.knock(player, center, 0.5, 0.7);
                    }
                }
            }
            if (elapsed[0] == WAVE_WINDUP) {
                boss.setAI(true);
            }
            if (!running) {
                task[0].cancel();
                state.busy = false;
            }
        }, 1, 1);
    }

    /** Les tombes : un cercle rouge sous chaque joueur ; 1,5 s plus tard, des os en jaillissent (dégâts, éjection). */
    private void tombs(BossFight fight, List<Player> players) {
        LivingEntity boss = fight.entity();
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.5f, 0.6f);
        List<Location> graves = new ArrayList<>();
        players.forEach(player -> graves.add(player.getLocation()));
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 2;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            if (elapsed[0] < TOMB_WINDUP) {
                for (Location grave : graves) {
                    circle(grave, TOMB_RADIUS);
                    dust(grave, 4, 0.6);
                }
                return;
            }
            task[0].cancel();
            BlockData bone = Material.BONE_BLOCK.createBlockData();
            for (Location grave : graves) {
                grave.getWorld().spawnParticle(Particle.BLOCK, grave.clone().add(0, 1, 0), 60, 0.4, 1.2, 0.4, 0.2, bone);
                grave.getWorld().playSound(grave, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.6f);
                grave.getWorld().playSound(grave, Sound.BLOCK_BONE_BLOCK_BREAK, 1.5f, 0.5f);
                for (Player player : grave.getNearbyPlayers(TOMB_RADIUS)) {
                    if (Hits.fighting(player) && Math.abs(player.getLocation().getY() - grave.getY()) < 2.5) {
                        player.damage(tombDamage, boss);
                        Hits.knock(player, grave, 0.3, 1.0);
                    }
                }
            }
        }, 0, 2);
    }

    /** Dernier souffle (10 % de vie, une fois) : cri, 6 zombies levés d'un coup, aura à 10 blocs jusqu'à la fin. */
    private void lastStand(BossFight fight, State state, Player target) {
        state.lastStand = true;
        LivingEntity boss = fight.entity();
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, chest(boss), 100, 2, 2, 2, 0.05);
        service().say(fight, "Levez-vous, tous !");
        for (int i = 0; i < LAST_STAND_RAISED; i++) {
            Location spot = groundNear(boss.getLocation(), 3, 8);
            if (spot != null) {
                rise(fight, state, spot, target);
            }
        }
    }

    /** Drain de vie : il se fige et brille 1 s, puis aspire la vie des joueurs à moins de 10 blocs et les attire à lui. */
    private void drain(BossFight fight, State state) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        state.busySince = System.currentTimeMillis();
        boss.setAI(false);
        boss.setGlowing(true);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!boss.isValid()) {
                return;
            }
            boss.setGlowing(false);
            Location heart = chest(boss);
            Collection<Player> victims = nearby(boss, DRAIN_RANGE);
            for (Player player : victims) {
                beam(chest(player), heart);
                Hits.trueDamage(player, drainDamage, boss);
                fight.taken(player, drainDamage);
                heal(fight, drainDamage);
                // Il les attire vers lui, dans son aura
                Vector toward = boss.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (toward.lengthSquared() > 1) {
                    player.setVelocity(toward.normalize().multiply(DRAIN_PULL).setY(0.3));
                }
            }
            boss.getWorld().playSound(heart, Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.5f);
            state.busy = false;
            boss.setAI(true);
        }, DRAIN_WINDUP);
    }

    /** Rayon de sang d'un point à un autre. */
    private static void beam(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.max(1, step.length() * 2);
        step.multiply(1.0 / points);
        Location point = from.clone();
        Particle.DustOptions blood = new Particle.DustOptions(BLOOD, 1.2f);
        for (int i = 0; i < points; i++) {
            point.add(step);
            point.getWorld().spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0, blood);
        }
    }
}
