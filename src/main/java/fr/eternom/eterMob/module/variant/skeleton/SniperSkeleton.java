package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Tireur d'élite : sur un joueur, il ne tire qu'un coup sur deux. Le premier sert à viser (une ligne rouge le relie à
 * sa cible : c'est le moment de se mettre à couvert), le second part droit sur elle, très vite, en compensant la chute :
 * il ne rate presque jamais, fait 1,5 fois les dégâts d'une flèche normale et repousse. Plus fragile. Montagnes,
 * badlands, plaines, neige : là où l'on voit loin.
 */
public class SniperSkeleton extends SkeletonVariant {

    /** Vitesse du tir visé (blocs par tick ; environ 1,6 pour un squelette normal) et chute d'une flèche par tick². */
    private static final double SPEED = 3;
    private static final double GRAVITY = 0.05;
    private static final double KNOCKBACK = 0.6;
    private static final Color AIM = Color.fromRGB(0xD01010);

    /** Squelettes qui ont visé : leur prochain tir est le tir visé. */
    private final Set<Integer> aiming = new HashSet<>();

    public SniperSkeleton() {
        super("skeleton_sniper", Rarity.PEU_COMMUN, "Sagitta");
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
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
        Skeleton skeleton = (Skeleton) mob.entity();
        if (!(skeleton.getTarget() instanceof Player target) || target.getWorld() != skeleton.getWorld()) {
            return;
        }
        int id = skeleton.getEntityId();
        if (aiming.add(id)) {
            // Tir de visée : pas de flèche, la ligne prévient le joueur
            event.setCancelled(true);
            aimLine(skeleton.getEyeLocation(), target.getEyeLocation());
            return;
        }
        aiming.remove(id);
        Entity arrow = event.getProjectile();
        Location from = arrow.getLocation();
        Vector delta = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(from.toVector());
        double time = Math.hypot(delta.getX(), delta.getZ()) / SPEED;
        delta.setY(delta.getY() + 0.5 * GRAVITY * time * time);
        arrow.setVelocity(delta.normalize().multiply(SPEED));
        from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
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
        aiming.remove(mob.entity().getEntityId());
    }

    /** Fléau : éclats de visée. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.CRIT, 3, 1.4, 0.3);
    }
}
