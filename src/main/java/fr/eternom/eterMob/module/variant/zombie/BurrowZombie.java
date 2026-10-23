package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Embuscade : quand le joueur qu'il chasse est entre 6 et 20 blocs, il s'enfonce dans le sol (invisible, immobile,
 * intouchable pendant 1,5 s) puis ressort juste à côté de lui. Il recommence toutes les 12 s, moins souvent aux petits
 * ratios : 12 s au ratio 1, environ 5,5 s au ratio 5. Si son chunk se décharge sous terre, il ressort à son retour.
 */
public class BurrowZombie extends ZombieVariant {

    private static final int CHECK_TICKS = 20;
    private static final double MIN_DISTANCE = 6;
    private static final double MAX_DISTANCE = 20;
    private static final int UNDERGROUND_TICKS = 30;
    private static final double COOLDOWN_MILLIS = 12_000;

    private final Map<Integer, Long> nextBurrow = new HashMap<>();
    private final Set<Integer> underground = new HashSet<>();

    public BurrowZombie() {
        super("zombie_burrow", Rarity.RARE, "Fossor");
    }

    @Override
    public void onLoad(ActiveMob mob) {
        // Déchargé pendant qu'il était sous terre : il ressort
        emerge(mob.entity());
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Zombie zombie = (Zombie) mob.entity();
        int id = zombie.getEntityId();
        if (underground.contains(id) || !zombie.isOnGround()) {
            return;
        }
        if (!(zombie.getTarget() instanceof Player target) || target.getWorld() != zombie.getWorld()
                || target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double distance = target.getLocation().distance(zombie.getLocation());
        long now = System.currentTimeMillis();
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || now < nextBurrow.getOrDefault(id, 0L)) {
            return;
        }
        nextBurrow.put(id, now + (long) (COOLDOWN_MILLIS / mob.power()));
        underground.add(id);
        dust(zombie.getLocation());
        zombie.setAI(false);
        zombie.setInvisible(true);
        zombie.setInvulnerable(true);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BurrowZombie.class), () -> {
            underground.remove(id);
            if (!zombie.isValid()) {
                return;
            }
            if (target.isOnline() && target.getWorld() == zombie.getWorld()) {
                zombie.teleport(spotNear(target.getLocation()));
            }
            emerge(zombie);
            zombie.setTarget(target);
            dust(zombie.getLocation());
        }, UNDERGROUND_TICKS);
    }

    private static void emerge(LivingEntity zombie) {
        zombie.setAI(true);
        zombie.setInvisible(false);
        zombie.setInvulnerable(false);
    }

    /** Terre qui vole : particules du bloc sous ses pieds. */
    private static void dust(Location location) {
        Block ground = location.getBlock().getRelative(0, -1, 0);
        if (ground.getType().isAir()) {
            return;
        }
        location.getWorld().spawnParticle(Particle.BLOCK, location.clone().add(0, 0.3, 0), 40, 0.4, 0.2, 0.4, 0.1,
                ground.getBlockData());
    }

    /** Un endroit libre à 1 ou 2 blocs du joueur ; sinon sa place. */
    private static Location spotNear(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = random.nextInt(-2, 3);
            int dz = random.nextInt(-2, 3);
            if (dx == 0 && dz == 0) {
                continue;
            }
            Block feet = center.clone().add(dx, 0, dz).getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return center.clone();
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextBurrow.remove(mob.entity().getEntityId());
        underground.remove(mob.entity().getEntityId());
    }

    /** Fléau : terre qui tombe de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        dust(mob.entity().getLocation());
    }
}
