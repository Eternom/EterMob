package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ombre : quand un joueur s'approche à moins de 4 blocs, il se téléporte à 8-12 blocs de lui et recommence à tirer.
 * Toutes les 8 s au ratio 1, environ 3,5 s au ratio 5. Il faut le coincer ou le tuer de loin. Grottes et forêts sombres.
 */
public class BlinkSkeleton extends SkeletonVariant {

    private static final int CHECK_TICKS = 10;
    private static final double TRIGGER = 4;
    private static final double MIN_JUMP = 8;
    private static final double MAX_JUMP = 12;
    private static final double COOLDOWN_MILLIS = 8_000;

    private final Map<Integer, Long> nextBlink = new HashMap<>();

    public BlinkSkeleton() {
        super("skeleton_blink", Rarity.PEU_COMMUN, "Umbra");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.DARK);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Skeleton skeleton = (Skeleton) mob.entity();
        if (!(skeleton.getTarget() instanceof Player target) || target.getWorld() != skeleton.getWorld()
                || target.getLocation().distanceSquared(skeleton.getLocation()) > TRIGGER * TRIGGER) {
            return;
        }
        long now = System.currentTimeMillis();
        int id = skeleton.getEntityId();
        if (now < nextBlink.getOrDefault(id, 0L)) {
            return;
        }
        Location spot = spotAway(target.getLocation());
        if (spot == null) {
            return;
        }
        nextBlink.put(id, now + (long) (COOLDOWN_MILLIS / mob.power()));
        portal(skeleton.getLocation());
        skeleton.teleport(spot.setDirection(target.getLocation().toVector().subtract(spot.toVector())));
        portal(spot);
        skeleton.setTarget(target);
    }

    /** Un endroit sûr à 8-12 blocs du joueur, en cherchant un peu plus haut et plus bas ; null si rien trouvé. */
    private static Location spotAway(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = random.nextDouble(MIN_JUMP, MAX_JUMP);
            Location base = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            for (int dy = 0; dy <= 6; dy++) {
                int offset = dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2;
                Block feet = base.clone().add(0, offset, 0).getBlock();
                if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                        && feet.getRelative(0, -1, 0).isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private static void portal(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextBlink.remove(mob.entity().getEntityId());
    }

    /** Fléau : particules de portail autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.PORTAL, 6, 1, 0.4);
    }
}
