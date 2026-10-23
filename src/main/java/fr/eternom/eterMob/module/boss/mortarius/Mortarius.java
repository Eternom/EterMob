package fr.eternom.eterMob.module.boss.mortarius;

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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
 *   <li>Passifs : pas lourds (poussière et son sourd) ; aura de pourriture (à moins de 5 blocs, 1 PV toutes les 2 s à
 *   travers l'armure, qui le soigne) ; seigneur de la horde (les zombies à moins de 16 blocs chassent sa cible, plus
 *   vite).</li>
 *   <li>Phase 1, le Seigneur : coup de masse en arc (il lève le bras 0,8 s avant) ; levée des morts toutes les 20 s
 *   (4 zombies sortent du sol, sillons 1 s avant).</li>
 *   <li>Phase 2, le Bouclier d'os : intouchable tant que 4 piliers d'os tiennent (on les casse en les frappant ; il les
 *   relève 30 s après) ; charge sur le joueur le plus éloigné (il racle le sol 1 s avant).</li>
 *   <li>Phase 3, la Fin : onde sismique (un anneau s'élargit : il faut sauter quand il passe) ; drain de vie à moins de
 *   10 blocs (il brille 1 s avant) ; rage (+20 % de vitesse).</li>
 * </ul>
 * Chaque phase garde les capacités des précédentes. Les zombies levés ne lâchent rien et meurent avec lui.
 */
public class Mortarius extends Boss {

    private static final double SCALE = 2.5;
    private static final double ATTACK_DAMAGE = 8;

    private static final double AURA_RADIUS = 5;
    private static final double AURA_DAMAGE = 1;
    private static final double HORDE_RADIUS = 16;
    private static final double TARGET_RANGE = 32;

    private static final double SLAM_REACH = 4.5;
    private static final double SLAM_DAMAGE = 12;
    private static final double SLAM_ANGLE = Math.toRadians(60);
    private static final int SLAM_WINDUP = 16;

    private static final int RAISED = 4;
    private static final int RAISED_MAX = 8;
    private static final int FURROW_TICKS = 20;

    private static final int PILLARS = 4;
    private static final double PILLAR_HEALTH = 60;
    private static final double PILLAR_RADIUS = 5;
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

    /** Un pilier d'os : le bloc affiché, son support invisible (qui reçoit les coups) et ses PV. */
    private static final class Pillar {
        final BlockDisplay display;
        final ArmorStand stand;
        double health = PILLAR_HEALTH;

        Pillar(BlockDisplay display, ArmorStand stand) {
            this.display = display;
            this.stand = stand;
        }
    }

    /** Ce que Mortarius garde pour un combat. */
    private static final class State {
        boolean busy;
        long ticks;
        long shieldBackAt;
        Location lastStep;
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

    /** Rechargé en plein coup (IA coupée, lueur) : il reprend normalement. */
    @Override
    public void onLoad(BossFight fight) {
        LivingEntity boss = fight.entity();
        boss.setAI(true);
        boss.setGlowing(false);
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
        Player target = target(boss);
        if (target == null) {
            return;
        }
        double distance = target.getLocation().distance(boss.getLocation());
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

    /** Sa cible : le joueur qu'il chasse, sinon le plus proche à moins de 32 blocs. */
    private static Player target(Zombie boss) {
        if (boss.getTarget() instanceof Player player && Hits.fighting(player) && player.getWorld() == boss.getWorld()) {
            return player;
        }
        Player nearest = null;
        double best = TARGET_RANGE * TARGET_RANGE;
        for (Player player : nearby(boss, TARGET_RANGE)) {
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            boss.setTarget(nearest);
        }
        return nearest;
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
                        new Vector3f(1, 2.5f, 1), new AxisAngle4f()));
            });
            ArmorStand stand = spot.getWorld().spawn(spot, ArmorStand.class, support -> {
                support.setInvisible(true);
                support.setGravity(false);
                support.setBasePlate(false);
                support.setPersistent(false);
                support.setSilent(true);
            });
            service().own(fight, display);
            service().own(fight, stand);
            state.pillars.put(stand.getUniqueId(), new Pillar(display, stand));
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
        Location at = pillar.stand.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 50, 0.4, 1, 0.4, 0.1, Material.BONE_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1.5f, 0.6f);
        pillar.display.remove();
        pillar.stand.remove();
    }

    /** Bouclier levé : le boss ne prend aucun dégât. */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
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
