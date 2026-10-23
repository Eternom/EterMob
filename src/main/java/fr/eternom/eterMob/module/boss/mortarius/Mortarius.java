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
 * +35 % par joueur en plus dans le combat, recompté en continu) : environ 10 minutes seul en diamant Tranchant V, il faut venir préparé. Ses coups sont
 * réglés pour un joueur en diamant. Il ne brûle pas au soleil. Aucun vrai bloc n'est posé ni cassé : piliers, sillons et ondes sont des
 * affichages et des particules.
 * <ul>
 *   <li>Combat : il choisit sa cible à la menace (les dégâts reçus, plus pour les tireurs, et la proximité ; il ne change
 *   que pour une menace nettement plus forte), frappe à 4,5 blocs (sa taille), et s'il ne peut pas atteindre sa cible
 *   pendant 3 s (perchée, coincée), il bondit sur elle ou en change. Il bondit aussi sur qui le tient à distance : une
 *   flèche qui le touche de plus de 10 blocs, ou une cible à plus de 14 blocs pendant 3 s. Le bond se voit venir : il
 *   s'accroupit 0,6 s et un cercle rouge marque au sol le point visé (il ne suit pas en l'air) ; à l'atterrissage, les
 *   joueurs dans le cercle prennent des dégâts et décollent.</li>
 *   <li>Passifs : pas lourds (poussière et son sourd) ; aura de pourriture (à moins de 5 blocs, 1 PV toutes les 2 s à
 *   travers l'armure, qui le soigne) ; seigneur de la horde (les zombies à moins de 16 blocs chassent sa cible, plus
 *   vite).</li>
 *   <li>Phase 1, le Seigneur : coup de masse en arc (il lève le bras 0,8 s avant) ; levée des morts toutes les 20 s
 *   (4 zombies sortent du sol, sillons 1 s avant : sans équipement, 8 PV, 25 % plus rapides).</li>
 *   <li>Phase 2, le Bouclier d'os : intouchable tant que 4 piliers d'os tiennent (on les casse en les frappant ; il les
 *   relève 30 s après) ; charge sur le joueur le plus éloigné (il racle le sol 1 s avant).</li>
 *   <li>Phase 3, la Fin : onde sismique (un anneau s'élargit : il faut sauter quand il passe) ; drain de vie à moins de
 *   10 blocs (il brille 1 s avant) ; rage (+20 % de vitesse).</li>
 * </ul>
 * Chaque phase garde les capacités des précédentes. Les zombies levés ne lâchent rien et meurent avec lui.
 */
public class Mortarius extends Boss {

    private static final double SCALE = 2.5;
    private static final double ATTACK_DAMAGE = 12;
    /** Allonge de ses coups (blocs) : celle d'un mob de sa taille, et délai entre deux coups. */
    private static final double MELEE_REACH = 4.5;
    private static final long MELEE_COOLDOWN_MILLIS = 1_000;
    /** Menace : part gardée à chaque tour (5 ticks), bonus des tireurs, marge pour changer de cible. */
    private static final double THREAT_DECAY = 0.98;
    private static final double RANGED_THREAT = 1.5;
    private static final double THREAT_SWITCH = 1.3;
    /** Inaccessible depuis ce nombre de vérifications (une par seconde) : il bondit ou change de cible. */
    private static final int UNREACHABLE_CHECKS = 3;
    private static final double LEAP_DAMAGE = 10;
    private static final double LEAP_RADIUS = 3.5;
    private static final long LEAP_COOLDOWN_MILLIS = 8_000;
    private static final int LEAP_WINDUP = 12;
    /** Tenu à distance : flèche reçue de plus de FAR_HIT blocs (récente : moins de 3 s), ou cible à plus de FAR_TARGET. */
    private static final double FAR_HIT = 10;
    private static final double FAR_TARGET = 14;
    private static final long FAR_HIT_MEMORY_MILLIS = 3_000;

    private static final double AURA_RADIUS = 5;
    private static final double AURA_DAMAGE = 1;
    private static final double HORDE_RADIUS = 16;
    private static final double TARGET_RANGE = 32;

    private static final double SLAM_REACH = 4.5;
    private static final double SLAM_DAMAGE = 16;
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

    private static final double CHARGE_MIN = 6;
    private static final double CHARGE_RANGE = 24;
    private static final double CHARGE_DAMAGE = 10;
    private static final double CHARGE_STEP = 0.9;
    private static final int CHARGE_TICKS = 16;
    private static final int CHARGE_WINDUP = 20;

    private static final double WAVE_RADIUS = 14;
    private static final double WAVE_DAMAGE = 8;
    private static final int WAVE_WINDUP = 20;

    private static final double DRAIN_RANGE = 10;
    private static final double DRAIN_DAMAGE = 3;
    private static final int DRAIN_WINDUP = 20;

    private static final double RAGE_SPEED = 0.2;
    private static final Color ROT = Color.fromRGB(0x5E8C31);
    private static final Color BLOOD = Color.fromRGB(0x8A0303);

    /** Un pilier d'os : le bloc affiché, son corps invisible (qui reçoit les coups) et ses PV. */
    private static final class Pillar {
        final BlockDisplay display;
        final LivingEntity body;
        double health = PILLAR_HEALTH;

        Pillar(BlockDisplay display, LivingEntity body) {
            this.display = display;
            this.body = body;
        }
    }

    /** Ce que Mortarius garde pour un combat. */
    private static final class State {
        boolean busy;
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

    private final Map<UUID, State> states = new HashMap<>();
    private NamespacedKey rageKey;

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
                "Levée des morts, bouclier d'os, charge,",
                "onde sismique (sauter !), drain de vie.");
    }

    @Override
    public double baseHealth() {
        return 1000;
    }

    @Override
    public double toughness() {
        return 2.5;
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
        base(zombie, Attribute.ATTACK_DAMAGE, ATTACK_DAMAGE);
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
        LivingEntity boss = fight.entity();
        rage(boss, false);
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
            return;
        }
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
        if (distance <= MELEE_REACH && boss.hasLineOfSight(target) && fight.ready("melee", MELEE_COOLDOWN_MILLIS)) {
            boss.swingMainHand();
            boss.attack(target);
        }
        int phase = fight.phase();
        if (phase >= 3) {
            if (!nearby(boss, DRAIN_RANGE).isEmpty() && fight.ready("drain", 8_000)) {
                drain(fight, state);
                return;
            }
            if (fight.ready("wave", 12_000)) {
                wave(fight, state);
                return;
            }
        }
        if (phase == 2 && state.pillars.isEmpty() && System.currentTimeMillis() >= state.shieldBackAt) {
            raiseShield(fight, state);
        }
        if (phase >= 2) {
            Player farthest = farthest(boss);
            if (farthest != null && fight.ready("charge", 10_000)) {
                charge(fight, state, farthest);
                return;
            }
        }
        if (distance <= SLAM_REACH && fight.ready("slam", 5_000)) {
            slam(fight, state, target);
            return;
        }
        if (state.raised.size() + RAISED <= RAISED_MAX && fight.ready("raise", 20_000)) {
            raiseDead(fight, state, target);
        }
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
        return state.threat.getOrDefault(player.getUniqueId(), 0.0) + 6 / (1 + distance);
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
     * moment), puis saute sur ce point fixe ; à l'atterrissage, les joueurs dans le cercle prennent des dégâts et décollent.
     */
    private void leap(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        Location landing = target.getLocation();
        boss.setAI(false);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.9f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0] += 2;
            if (!boss.isValid() || elapsed[0] > LEAP_WINDUP + 60) {
                task[0].cancel();
                release(boss, target, state);
                return;
            }
            if (elapsed[0] < LEAP_WINDUP) {
                circle(landing, LEAP_RADIUS);
                dust(boss.getLocation(), 10, 1);
                return;
            }
            if (elapsed[0] == LEAP_WINDUP) {
                boss.setAI(true);
                boss.setVelocity(leapVelocity(boss.getLocation(), landing));
                dust(boss.getLocation(), 40, 1.5);
                return;
            }
            circle(landing, LEAP_RADIUS);
            if (elapsed[0] > LEAP_WINDUP + 6 && boss.isOnGround()) {
                task[0].cancel();
                Location at = boss.getLocation();
                boss.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 1, 0, 1, 0);
                dust(at, 60, 2);
                boss.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
                Hits.shockwave(boss, at, LEAP_RADIUS, LEAP_DAMAGE, 0.5, 0.9);
                release(boss, target, state);
            }
        }, 2, 2);
    }

    /** Élan pour atterrir à peu près sur ce point : hauteur selon le dénivelé, vitesse selon la distance. */
    private static Vector leapVelocity(Location from, Location landing) {
        Vector to = landing.toVector().subtract(from.toVector());
        double lift = Math.min(1.6, 0.9 + Math.max(0, to.getY()) * 0.12);
        double airTicks = 2 * lift / 0.08;
        Vector flat = to.clone().setY(0);
        double horizontal = flat.length();
        Vector push = horizontal < 0.1 ? new Vector() : flat.normalize().multiply(Math.min(1.8, horizontal / (airTicks * 0.8)));
        return push.setY(lift);
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

    private static void heal(LivingEntity boss, double amount) {
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && boss.isValid() && !boss.isDead()) {
            boss.setHealth(Math.min(max.getValue(), boss.getHealth() + amount));
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
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8;
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * AURA_RADIUS, 0.2,
                        Math.sin(angle) * AURA_RADIUS), 1, 0.1, 0.05, 0.1, 0, new Particle.DustOptions(ROT, 1.5f));
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
            for (Player player : nearby(boss, AURA_RADIUS)) {
                Hits.trueDamage(player, AURA_DAMAGE, boss);
                heal(boss, AURA_DAMAGE);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.7f);
            }
        }
        if (state.ticks % 20 == 0) {
            horde(boss);
        }
        if (state.ticks % 200 == 0) {
            at.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_AMBIENT, 2f, 0.4f);
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
                return;
            }
            task[0].cancel();
            Location front = boss.getLocation().add(forward.clone().multiply(2.5));
            boss.swingMainHand();
            for (Player player : nearby(boss, SLAM_REACH + 1)) {
                Vector to = player.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                if (to.length() <= SLAM_REACH && (to.lengthSquared() < 0.01 || to.angle(forward) <= SLAM_ANGLE)) {
                    player.damage(SLAM_DAMAGE, boss);
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

    /** Levée des morts : sillons 1 s, puis 4 zombies remontent du sol. */
    private void raiseDead(BossFight fight, State state, Player target) {
        Zombie boss = (Zombie) fight.entity();
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < RAISED; i++) {
            Location spot = groundNear(boss.getLocation(), 3, 6);
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
        for (int i = 0; i < PILLARS; i++) {
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
            state.pillars.put(body.getUniqueId(), new Pillar(display, body));
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
        addThreat(state(fight), fight.entity(), event);
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
        if (state.pillars.isEmpty()) {
            breakShield(fight, state, true);
        }
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
                release(boss, target, state);
                return;
            }
            boss.teleport(next);
            boss.getWorld().spawnParticle(Particle.CLOUD, boss.getLocation(), 6, 0.6, 0.1, 0.6, 0.02);
            for (Player player : nearby(boss, 2.5)) {
                if (hit.add(player.getUniqueId())) {
                    player.damage(CHARGE_DAMAGE, boss);
                    Hits.knock(player, boss.getLocation(), 1.8, 0.6);
                    boss.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
                }
            }
        }, 1, 1);
    }

    // ---- Phase 3 ----

    /** Onde sismique : il lève les bras 1 s, frappe le sol, et un anneau s'élargit ; il faut sauter quand il passe. */
    private void wave(BossFight fight, State state) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
        boss.setAI(false);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
        int[] elapsed = {0};
        Set<UUID> hit = new HashSet<>();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            elapsed[0]++;
            if (!boss.isValid()) {
                task[0].cancel();
                return;
            }
            Location center = boss.getLocation();
            if (elapsed[0] < WAVE_WINDUP) {
                if (elapsed[0] % 4 == 0) {
                    boss.getWorld().spawnParticle(Particle.ASH, center.clone().add(0, boss.getHeight() + 0.5, 0), 20, 0.8, 0.3, 0.8, 0);
                }
                return;
            }
            if (elapsed[0] == WAVE_WINDUP) {
                boss.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0, 0.5, 0);
                boss.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                boss.setAI(true);
            }
            double radius = elapsed[0] - WAVE_WINDUP + 1;
            Block ground = center.getBlock().getRelative(0, -1, 0);
            BlockData data = ground.getType().isAir() ? Material.DIRT.createBlockData() : ground.getBlockData();
            int points = (int) (8 + radius * 6);
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2 / points;
                center.getWorld().spawnParticle(Particle.BLOCK, center.clone().add(Math.cos(angle) * radius, 0.2,
                        Math.sin(angle) * radius), 2, 0.1, 0.1, 0.1, 0, data);
            }
            for (Player player : nearby(boss, radius + 1)) {
                Location feet = player.getLocation();
                double distance = Math.hypot(feet.getX() - center.getX(), feet.getZ() - center.getZ());
                if (Math.abs(distance - radius) <= 0.8 && Math.abs(feet.getY() - center.getY()) < 2
                        && ((org.bukkit.entity.Entity) player).isOnGround() && hit.add(player.getUniqueId())) {
                    player.damage(WAVE_DAMAGE, boss);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    Hits.knock(player, center, 0.4, 0.4);
                }
            }
            if (radius >= WAVE_RADIUS) {
                task[0].cancel();
                state.busy = false;
            }
        }, 1, 1);
    }

    /** Drain de vie : il se fige et brille 1 s, puis aspire la vie des joueurs à moins de 10 blocs. */
    private void drain(BossFight fight, State state) {
        Zombie boss = (Zombie) fight.entity();
        state.busy = true;
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
                Hits.trueDamage(player, DRAIN_DAMAGE, boss);
                heal(boss, DRAIN_DAMAGE);
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
