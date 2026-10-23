package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Sauteuse : quand le joueur qu'elle chasse est entre 4 et 10 blocs, elle bondit sur lui, et son atterrissage fait une
 * onde de choc : dégâts et recul pour les joueurs autour (rayon 2,5 blocs, environ 5,5 au ratio 5). Toutes les 6 s au
 * ratio 1, environ 2,7 s au ratio 5. Plaines, savanes, déserts, badlands, montagnes, rivières.
 */
public class JumpSpider extends SpiderVariant {

    private static final int CHECK_TICKS = 10;
    private static final double MIN_DISTANCE = 4;
    private static final double MAX_DISTANCE = 10;
    private static final double COOLDOWN_MILLIS = 6_000;
    /** Élan horizontal par bloc de distance, et hauteur du saut (blocs par tick). */
    private static final double LEAP_PER_BLOCK = 0.17;
    private static final double LEAP_HEIGHT = 0.5;
    /** Onde de choc à l'atterrissage : rayon (blocs) et dégâts de base, multipliés par le ratio. */
    private static final double WAVE_RADIUS = 2.5;
    private static final double WAVE_DAMAGE = 4;
    /** Vérification de l'atterrissage toutes les 2 ticks, abandonnée après 2 s en l'air. */
    private static final int LANDING_CHECK = 2;
    private static final int LANDING_TIMEOUT = 40;

    private final Map<Integer, Long> nextLeap = new HashMap<>();

    public JumpSpider() {
        super("spider_jump", Rarity.PEU_COMMUN, "Saltatrix");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.PLAINS, Spawns.DRY, Spawns.MOUNTAINS, Spawns.RIVERS);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Spider spider = (Spider) mob.entity();
        if (!spider.isOnGround() || !(spider.getTarget() instanceof Player target) || target.getWorld() != spider.getWorld()) {
            return;
        }
        Vector toward = target.getLocation().toVector().subtract(spider.getLocation().toVector());
        double distance = toward.length();
        long now = System.currentTimeMillis();
        int id = spider.getEntityId();
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || now < nextLeap.getOrDefault(id, 0L) || !spider.hasLineOfSight(target)) {
            return;
        }
        nextLeap.put(id, now + (long) (COOLDOWN_MILLIS / mob.power()));
        spider.setVelocity(toward.setY(0).normalize().multiply(distance * LEAP_PER_BLOCK).setY(LEAP_HEIGHT));
        spider.getWorld().spawnParticle(Particle.CLOUD, spider.getLocation(), 8, 0.4, 0.1, 0.4, 0.02);
        watchLanding(mob);
    }

    /** Attend qu'elle retouche le sol (après avoir décollé), puis onde de choc. */
    private void watchLanding(ActiveMob mob) {
        LivingEntity spider = mob.entity();
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(JumpSpider.class), () -> {
            elapsed[0] += LANDING_CHECK;
            if (!spider.isValid() || elapsed[0] > LANDING_TIMEOUT) {
                task[0].cancel();
            } else if (elapsed[0] > 4 && spider.isOnGround()) {
                task[0].cancel();
                land(mob);
            }
        }, LANDING_CHECK, LANDING_CHECK);
    }

    private static void land(ActiveMob mob) {
        Location center = mob.entity().getLocation();
        double radius = mob.scale(WAVE_RADIUS);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().spawnParticle(Particle.DUST_PLUME, center, 30, radius / 2, 0.1, radius / 2, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        shockwave(mob.entity(), center, radius, mob.scale(WAVE_DAMAGE), 0.6, 0.35);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextLeap.remove(mob.entity().getEntityId());
    }

    /** Fléau : poussière soulevée à ses pattes. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.DUST_PLUME, 2, 0.1, 0.4);
    }
}
