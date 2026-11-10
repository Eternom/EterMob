package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * wave : onde sismique : rings anneaux (3) partent de lui l'un après l'autre et s'élargissent jusqu'à radius (9) ; un
 * joueur au sol touché par un anneau prend ses dégâts et ses états, une fois par anneau. On les saute. L'IA la lance
 * quand on est groupé autour de lui.
 */
public final class Wave extends Ability {

    private final int rings;

    public Wave(ConfigurationSection config) {
        super("wave", config, new Defaults(Trigger.ACTIVE, 14, 1, 0, 6, 1, 8, 9, 0.6, 0.45));
        this.rings = Math.clamp(config.getInt("rings", 3), 1, 6);
    }

    @Override
    public double score(AbilityContext context) {
        int near = context.situation().playersNearSelf();
        return near == 0 ? 0 : clamp01(0.3 + 0.25 * near);
    }

    @Override
    public void begin(AbilityContext context) {
        context.spot(context.self().getLocation());
        context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.4f);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        dangerRing(context.spot(), 1.5 + progress, progress);
    }

    @Override
    public void cast(AbilityContext context) {
        Location center = context.self().getLocation();
        for (int ring = 0; ring < rings; ring++) {
            Set<UUID> touched = new HashSet<>();
            new BukkitRunnable() {
                private double size = 0.5;

                @Override
                public void run() {
                    if (size > radius || !context.self().isValid()) {
                        cancel();
                        return;
                    }
                    ring(center, size, Particle.CLOUD);
                    center.getWorld().playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 0.6f);
                    for (Player player : players(center, size + 0.6)) {
                        double distance = horizontal(player.getLocation(), center);
                        // Seulement sur l'anneau, et au sol : un joueur qui saute passe par-dessus
                        if (distance >= size - 0.8 && player.isOnGround() && touched.add(player.getUniqueId())) {
                            hit(context, player, damage, center);
                        }
                    }
                    size += 0.6;
                }
            }.runTaskTimer(Services.plugin(), ring * 14L, 1);
        }
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public double value() {
        return super.value() * rings * 0.6;
    }
}
