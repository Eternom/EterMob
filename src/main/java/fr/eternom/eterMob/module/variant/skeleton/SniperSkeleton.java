package fr.eternom.eterMob.module.variant.skeleton;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tireur d'élite. Il tire de loin (jusqu'à 24 blocs, 15 pour un squelette normal) et garde ses distances : il recule
 * quand on approche à moins de 8 blocs, s'approche au-delà de 22. Toutes les 2 s, il vise : une ligne rouge le relie à
 * sa cible (c'est le moment de se mettre à couvert), puis 0,75 s plus tard sa flèche part droit sur elle, très vite, en
 * compensant la chute : elle ne rate presque jamais, fait 1,5 fois les dégâts d'une flèche normale et repousse. Plus
 * fragile. Montagnes, badlands, plaines, neige : là où l'on voit loin.
 */
public class SniperSkeleton extends SkeletonVariant {

    private static final int CHECK_TICKS = 5;
    private static final double RANGE = 24;
    private static final double MIN_DISTANCE = 8;
    private static final double MAX_DISTANCE = 22;
    /** Distance de repérage des joueurs (16 pour un squelette normal). */
    private static final double FOLLOW_RANGE = 32;
    private static final double RETREAT = 6;
    private static final double RETREAT_SPEED = 1.2;
    private static final double FOLLOW_SPEED = 1;
    /** Entre deux tirs, et entre la visée et le tir. */
    private static final long SHOT_COOLDOWN_MILLIS = 2_000;
    private static final int AIM_TICKS = 15;
    /** Vitesse du tir (blocs par tick ; environ 1,6 pour un squelette normal) et chute d'une flèche par tick². */
    private static final double SPEED = 3;
    private static final double GRAVITY = 0.05;
    private static final double KNOCKBACK = 0.6;
    private static final Color AIM = Color.fromRGB(0xD01010);

    private final Map<Integer, Long> nextShot = new HashMap<>();
    /** En train de viser, et tir visé en cours (onShoot oriente la flèche). */
    private final Set<Integer> aiming = new HashSet<>();
    private final Map<Integer, Player> shooting = new HashMap<>();

    public SniperSkeleton() {
        super("skeleton_sniper", Rarity.PEU_COMMUN, "Sagitta");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Tire de 24 blocs après une ligne de visée.",
                "Recule quand on approche.",
                "Biomes : montagnes, secs, neige, plaines.");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.MOUNTAINS, Spawns.DRY, Spawns.COLD, Spawns.PLAINS);
    }

    @Override
    public double health() {
        return 0.8;
    }

    /** La vitesse multiplie déjà les dégâts par environ 1,9 : ramenés à 1,5. */
    @Override
    protected double arrowDamage() {
        return 0.8;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        AttributeInstance follow = mob.entity().getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(FOLLOW_RANGE);
        }
    }

    /** Pas de tir à l'arc de Minecraft (portée 15 blocs) : il se place et tire lui-même (tick). */
    @Override
    public void onLoad(ActiveMob mob) {
        Bukkit.getMobGoals().removeGoal((Skeleton) mob.entity(), VanillaGoal.RANGED_BOW_ATTACK);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Skeleton skeleton = (Skeleton) mob.entity();
        int id = skeleton.getEntityId();
        if (!(skeleton.getTarget() instanceof Player target) || target.getWorld() != skeleton.getWorld()) {
            return;
        }
        double distance = target.getLocation().distance(skeleton.getLocation());
        if (aiming.contains(id)) {
            skeleton.getPathfinder().stopPathfinding();
            skeleton.lookAt(target);
            return;
        }
        if (distance < MIN_DISTANCE) {
            Vector away = skeleton.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                skeleton.getPathfinder().moveTo(skeleton.getLocation().add(away.normalize().multiply(RETREAT)), RETREAT_SPEED);
            }
        } else if (distance > MAX_DISTANCE) {
            skeleton.getPathfinder().moveTo(target.getLocation(), FOLLOW_SPEED);
        } else {
            skeleton.getPathfinder().stopPathfinding();
            skeleton.lookAt(target);
        }
        long now = System.currentTimeMillis();
        if (distance <= RANGE && now >= nextShot.getOrDefault(id, 0L) && skeleton.hasLineOfSight(target)) {
            nextShot.put(id, now + SHOT_COOLDOWN_MILLIS);
            aim(skeleton, target);
        }
    }

    /** Ligne rouge vers la cible, puis tir. */
    private void aim(Skeleton skeleton, Player target) {
        int id = skeleton.getEntityId();
        aiming.add(id);
        skeleton.getPathfinder().stopPathfinding();
        aimLine(skeleton.getEyeLocation(), target.getEyeLocation());
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(SniperSkeleton.class), () -> {
            aiming.remove(id);
            if (!skeleton.isValid() || !target.isOnline() || target.getWorld() != skeleton.getWorld()) {
                return;
            }
            shooting.put(id, target);
            skeleton.rangedAttack(target, 1f);
            shooting.remove(id);
        }, AIM_TICKS);
    }

    private static void aimLine(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.min(60, step.length() * 2);
        step.multiply(1.0 / Math.max(1, points));
        Location point = from.clone();
        Particle.DustOptions red = new Particle.DustOptions(AIM, 0.7f);
        for (int i = 0; i < points; i++) {
            point.add(step);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, red);
        }
    }

    /** Tir visé : la flèche part droit sur la cible, en compensant la chute. */
    @Override
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
        Player target = shooting.get(mob.entity().getEntityId());
        if (target == null) {
            return;
        }
        Entity arrow = event.getProjectile();
        Location from = arrow.getLocation();
        Vector delta = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(from.toVector());
        double time = Math.hypot(delta.getX(), delta.getZ()) / SPEED;
        delta.setY(delta.getY() + 0.5 * GRAVITY * time * time);
        arrow.setVelocity(delta.normalize().multiply(SPEED));
        from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (!(event.getDamager() instanceof AbstractArrow) || !(victim instanceof Player)) {
            return;
        }
        LivingEntity skeleton = mob.entity();
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SniperSkeleton.class), () -> {
            if (!victim.isValid() || victim.getWorld() != skeleton.getWorld()) {
                return;
            }
            Vector away = victim.getLocation().toVector().subtract(skeleton.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                victim.setVelocity(victim.getVelocity().add(away.normalize().multiply(KNOCKBACK).setY(0.25)));
            }
        });
    }

    @Override
    public void onRemove(ActiveMob mob) {
        int id = mob.entity().getEntityId();
        nextShot.remove(id);
        aiming.remove(id);
        shooting.remove(id);
    }

    /** Fléau : éclats de visée. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.CRIT, 3, 1.4, 0.3);
    }
}
