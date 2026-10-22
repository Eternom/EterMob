package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Petite explosion, puis se fragmente : des éclats retombent autour et explosent l'un après l'autre. Une fumée marque
 * chaque point d'impact dès la première explosion, pour laisser le temps de s'écarter. Le nombre d'éclats et leur
 * dispersion grandissent avec le ratio (3 éclats sur 4 blocs au ratio 1, 7 sur 9 au ratio 5). Les éclats ne cassent
 * jamais de blocs.
 */
public class FragmentCreeper extends CreeperVariant {

    private static final double FRAGMENTS = 3;
    /** Distance maximale d'un éclat au creeper (blocs). */
    private static final double SPREAD = 4;
    /** Puissance de l'explosion d'un éclat (1 = un tiers de creeper) : environ 3 de dégâts en netherite Protection IV. */
    private static final float FRAGMENT_POWER = 1;
    /** Premier éclat après la première explosion, puis un toutes les FRAGMENT_GAP (ticks). */
    private static final int FIRST_DELAY = 15;
    private static final int FRAGMENT_GAP = 5;

    public FragmentCreeper() {
        super("creeper_fragment", Rarity.RARE, "Fractus", 2, 12);
    }

    @Override
    public void onExplode(ActiveMob mob, EntityExplodeEvent event) {
        Location center = event.getLocation();
        World world = center.getWorld();
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(FragmentCreeper.class);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = mob.scaleInt(FRAGMENTS);
        double spread = mob.scale(SPREAD);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = 1.5 + random.nextDouble() * Math.max(0, spread - 1.5);
            Location impact = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            world.spawnParticle(Particle.LARGE_SMOKE, impact, 8, 0.2, 0.3, 0.2, 0.01);
            Bukkit.getScheduler().runTaskLater(plugin, () -> world.createExplosion(impact, FRAGMENT_POWER, false, false),
                    FIRST_DELAY + (long) i * FRAGMENT_GAP);
        }
    }
}
