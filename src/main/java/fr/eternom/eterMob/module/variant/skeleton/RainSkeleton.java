package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pluie de flèches : toutes les 7 s, quand il tire sur un joueur, des flèches tombent du ciel autour de lui une seconde
 * plus tard, une à une (3 au ratio 1, 7 au ratio 5) : il faut bouger. Ces flèches ne se ramassent pas. Biomes à ciel
 * ouvert : plaines, savanes, déserts, badlands.
 */
public class RainSkeleton extends SkeletonVariant {

    private static final long COOLDOWN_MILLIS = 7_000;
    private static final double ARROWS = 3;
    /** Rayon de la pluie autour du joueur, et hauteur d'où tombent les flèches (blocs). */
    private static final double RADIUS = 2.5;
    private static final double HEIGHT = 10;
    private static final int FIRST_DELAY = 20;
    /** Une flèche toutes les GAP ticks : au-delà de l'invulnérabilité après un coup, chacune peut toucher. */
    private static final int GAP = 8;

    private final Map<Integer, Long> nextRain = new HashMap<>();

    public RainSkeleton() {
        super("skeleton_rain", Rarity.RARE, "Imber");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.PLAINS, Spawns.DRY);
    }

    @Override
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
        Skeleton skeleton = (Skeleton) mob.entity();
        if (!(skeleton.getTarget() instanceof Player target)) {
            return;
        }
        long now = System.currentTimeMillis();
        int id = skeleton.getEntityId();
        if (now < nextRain.getOrDefault(id, 0L)) {
            return;
        }
        nextRain.put(id, now + COOLDOWN_MILLIS);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(RainSkeleton.class);
        int arrows = mob.scaleInt(ARROWS);
        for (int i = 0; i < arrows; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> drop(skeleton, target), FIRST_DELAY + (long) i * GAP);
        }
    }

    private static void drop(Skeleton skeleton, Player target) {
        if (!skeleton.isValid() || !target.isOnline() || target.getWorld() != skeleton.getWorld()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location from = target.getLocation().add(random.nextDouble(-RADIUS, RADIUS), HEIGHT, random.nextDouble(-RADIUS, RADIUS));
        Arrow arrow = from.getWorld().spawnArrow(from, new Vector(0, -1, 0), 1.2f, 2);
        arrow.setShooter(skeleton);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextRain.remove(mob.entity().getEntityId());
    }

    /** Fléau : gouttes qui tombent de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.FALLING_WATER, 4, 1.8, 0.4);
    }
}
