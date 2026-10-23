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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pluie de flèches, rare. Toutes les 7 s, quand il tire sur un joueur, un nuage se forme au-dessus de lui (on le voit
 * venir : sortir de la zone ou se mettre à couvert), puis une seconde plus tard des flèches en tombent une à une (3 au
 * ratio 1, 7 au ratio 5) dans un rayon de 2,5 blocs. Ces flèches ne se ramassent pas.
 * <ul>
 *   <li>Ratio 3 et plus : la pluie suit le joueur (chaque flèche vise sa position du moment).</li>
 *   <li>Ratio 5 : les flèches sont enflammées.</li>
 * </ul>
 * Biomes à ciel ouvert : plaines, savanes, déserts, badlands, rivières, neige.
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
    /** La pluie suit le joueur à partir de ce ratio ; flèches enflammées au ratio le plus fort (durée du feu, ticks). */
    private static final int FOLLOW_RATIO = 3;
    private static final int FIRE_TICKS = 100;
    /** Le nuage : hauteur au-dessus du joueur (blocs) et rafraîchissement (ticks). */
    private static final double CLOUD_HEIGHT = 4;
    private static final int CLOUD_STEP = 5;

    private final Map<Integer, Long> nextRain = new HashMap<>();

    public RainSkeleton() {
        super("skeleton_rain", Rarity.RARE, "Imber");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Pluie de flèches sous un nuage.",
                "Suit dès le ratio 3, enflammée au Fléau.",
                "Biomes : plaines, secs, rivières, neige.");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.PLAINS, Spawns.DRY, Spawns.RIVERS, Spawns.COLD);
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
        boolean follow = mob.ratio() >= FOLLOW_RATIO;
        boolean burning = mob.ratio() >= service().ratios().max();
        Location fixed = target.getLocation();
        long duration = FIRST_DELAY + (long) (arrows - 1) * GAP;
        cloud(plugin, skeleton, target, follow ? null : fixed, duration);
        for (int i = 0; i < arrows; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> drop(skeleton, target, follow ? null : fixed, burning),
                    FIRST_DELAY + (long) i * GAP);
        }
    }

    /** Nuage au-dessus de la zone de pluie (fixe, ou au-dessus du joueur si center est null), jusqu'à la dernière flèche. */
    private static void cloud(JavaPlugin plugin, Skeleton skeleton, Player target, Location center, long duration) {
        long[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += CLOUD_STEP;
            if (elapsed[0] > duration || !skeleton.isValid() || !target.isOnline() || target.getWorld() != skeleton.getWorld()) {
                task[0].cancel();
                return;
            }
            Location at = (center != null ? center.clone() : target.getLocation()).add(0, CLOUD_HEIGHT, 0);
            at.getWorld().spawnParticle(Particle.CLOUD, at, 15, RADIUS / 2, 0.2, RADIUS / 2, 0.01);
        }, 0, CLOUD_STEP);
    }

    /** Une flèche tombe dans la zone (fixe, ou autour du joueur si center est null). */
    private static void drop(Skeleton skeleton, Player target, Location center, boolean burning) {
        if (!skeleton.isValid() || !target.isOnline() || target.getWorld() != skeleton.getWorld()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location from = (center != null ? center.clone() : target.getLocation())
                .add(random.nextDouble(-RADIUS, RADIUS), HEIGHT, random.nextDouble(-RADIUS, RADIUS));
        Arrow arrow = from.getWorld().spawnArrow(from, new Vector(0, -1, 0), 1.2f, 2);
        arrow.setShooter(skeleton);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        if (burning) {
            arrow.setFireTicks(FIRE_TICKS);
        }
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
