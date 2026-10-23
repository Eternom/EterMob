package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Embuscade, rare, plus solide qu'un zombie normal. Quand le joueur qu'il chasse est entre 6 et 20 blocs, il s'enfonce
 * dans le sol, puis :
 * <ol>
 *   <li>sous terre (1,5 s) : invisible et intouchable, un sillon de terre jaillit du sol de lui jusqu'au joueur ;</li>
 *   <li>le sol tremble 0,5 s à côté du joueur, là où il va sortir ;</li>
 *   <li>il remonte du sol en 0,5 s. Frappé pendant qu'il sort (de près ou avec un projectile), il est étourdi 2 s et
 *   n'a pas d'éruption ; sinon, en sortant, il projette la terre : petits dégâts et les joueurs proches décollent
 *   (rayon 2,5 blocs, environ 5,5 au ratio 5).</li>
 * </ol>
 * Il recommence toutes les 12 s au ratio 1, environ 5,5 s au ratio 5. Si son chunk se décharge sous terre, il ressort à
 * son retour. Grottes, déserts, badlands, montagnes.
 */
public class BurrowZombie extends ZombieVariant {

    private static final int CHECK_TICKS = 20;
    private static final double MIN_DISTANCE = 6;
    private static final double MAX_DISTANCE = 20;
    private static final double COOLDOWN_MILLIS = 12_000;
    /** Durées des phases (ticks) : sous terre, tremblement, remontée ; pas de l'animation. */
    private static final int TUNNEL_TICKS = 30;
    private static final int TREMOR_TICKS = 10;
    private static final int RISE_TICKS = 10;
    private static final int STEP = 2;
    /** Profondeur d'où il remonte (blocs). */
    private static final double DEPTH = 2;
    private static final int STUN_TICKS = 40;
    /** Terre projetée en sortant : rayon (blocs) et dégâts de base, multipliés par le ratio. */
    private static final double ERUPTION_RADIUS = 2.5;
    private static final double ERUPTION_DAMAGE = 3;

    private final Map<Integer, Long> nextBurrow = new HashMap<>();
    /** Sous terre (sillon, tremblement, remontée), et en train de remonter (touchable). */
    private final Set<Integer> burrowing = new HashSet<>();
    private final Set<Integer> rising = new HashSet<>();
    /** Frappés pendant la remontée : étourdis à la sortie. */
    private final Set<Integer> interrupted = new HashSet<>();

    public BurrowZombie() {
        super("zombie_burrow", Rarity.RARE, "Fossor");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Creuse vers toi (sillon visible) et sort du sol.",
                "Frappé en sortant : étourdi 2 s.",
                "Biomes : grottes, secs, montagnes.");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.DRY, Spawns.MOUNTAINS);
    }

    @Override
    public double health() {
        return 1.4;
    }

    @Override
    public void onLoad(ActiveMob mob) {
        // Déchargé pendant qu'il était sous terre : il ressort
        surface(mob.entity());
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Zombie zombie = (Zombie) mob.entity();
        int id = zombie.getEntityId();
        if (burrowing.contains(id) || !zombie.isOnGround()) {
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
        burrow(mob, target);
    }

    private void burrow(ActiveMob mob, Player target) {
        Zombie zombie = (Zombie) mob.entity();
        int id = zombie.getEntityId();
        burrowing.add(id);
        Location start = zombie.getLocation();
        dust(start, 40);
        zombie.getWorld().playSound(start, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.6f);
        zombie.setAI(false);
        zombie.setInvisible(true);
        zombie.setInvulnerable(true);
        zombie.setGravity(false);
        int[] elapsed = {0};
        Location[] exit = {null};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(BurrowZombie.class), () -> {
            elapsed[0] += STEP;
            if (!zombie.isValid() || !target.isOnline() || target.getWorld() != zombie.getWorld()) {
                task[0].cancel();
                finish(mob, null);
                return;
            }
            if (elapsed[0] <= TUNNEL_TICKS) {
                // Sillon : de la terre jaillit du sol, de son départ jusqu'au joueur
                double progress = (double) elapsed[0] / TUNNEL_TICKS;
                Location point = start.clone().add(target.getLocation().toVector().subtract(start.toVector()).multiply(progress));
                dust(groundAt(point), 6);
                if (elapsed[0] % 6 == 0) {
                    point.getWorld().playSound(point, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 0.8f);
                }
            } else if (elapsed[0] <= TUNNEL_TICKS + TREMOR_TICKS) {
                if (exit[0] == null) {
                    exit[0] = spotNear(target.getLocation());
                }
                dust(exit[0], 12);
                exit[0].getWorld().playSound(exit[0], Sound.BLOCK_GRAVEL_BREAK, 0.8f, 0.6f);
            } else if (elapsed[0] <= TUNNEL_TICKS + TREMOR_TICKS + RISE_TICKS) {
                int risen = elapsed[0] - TUNNEL_TICKS - TREMOR_TICKS;
                if (!rising.contains(id)) {
                    // Il devient visible et touchable sous le sol, puis remonte
                    rising.add(id);
                    zombie.setInvisible(false);
                    zombie.setInvulnerable(false);
                }
                double depth = DEPTH * (1 - (double) risen / RISE_TICKS);
                zombie.teleport(exit[0].clone().subtract(0, depth, 0).setDirection(
                        target.getLocation().toVector().subtract(exit[0].toVector())));
                dust(exit[0], 10);
            } else {
                task[0].cancel();
                finish(mob, target);
            }
        }, STEP, STEP);
    }

    /** Sortie : étourdi s'il a été frappé en remontant, sinon éruption. */
    private void finish(ActiveMob mob, Player target) {
        Zombie zombie = (Zombie) mob.entity();
        int id = zombie.getEntityId();
        burrowing.remove(id);
        rising.remove(id);
        boolean stunned = interrupted.remove(id);
        if (!zombie.isValid()) {
            return;
        }
        if (target == null) {
            surface(zombie);
            return;
        }
        zombie.setInvisible(false);
        zombie.setInvulnerable(false);
        zombie.setGravity(true);
        if (stunned) {
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BurrowZombie.class), () -> {
                if (zombie.isValid()) {
                    zombie.setAI(true);
                    zombie.setTarget(target);
                }
            }, STUN_TICKS);
            stars(zombie);
            return;
        }
        zombie.setAI(true);
        zombie.setTarget(target);
        dust(zombie.getLocation(), 40);
        dust(zombie.getLocation().add(0, 0.5, 0), 40);
        shockwave(zombie, zombie.getLocation(), mob.scale(ERUPTION_RADIUS), mob.scale(ERUPTION_DAMAGE), 0.3, 0.6);
    }

    /** Étoiles au-dessus de sa tête pendant l'étourdissement. */
    private static void stars(LivingEntity zombie) {
        int[] left = {STUN_TICKS / 5};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(BurrowZombie.class), () -> {
            if (left[0]-- <= 0 || !zombie.isValid()) {
                task[0].cancel();
                return;
            }
            zombie.getWorld().spawnParticle(Particle.ENCHANTED_HIT, zombie.getLocation().add(0, 2.2, 0), 4, 0.3, 0.05, 0.3, 0);
        }, 0, 5);
    }

    /** Frappé par un joueur pendant qu'il remonte : il sera étourdi. Pas d'étouffement tant qu'il est dans le sol. */
    @Override
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
        int id = mob.entity().getEntityId();
        if (!burrowing.contains(id)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
        } else if (rising.contains(id) && event instanceof EntityDamageByEntityEvent byEntity && byPlayer(byEntity.getDamager())) {
            interrupted.add(id);
        }
    }

    private static boolean byPlayer(Entity damager) {
        return damager instanceof Player || damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    private static void surface(LivingEntity zombie) {
        zombie.setAI(true);
        zombie.setInvisible(false);
        zombie.setInvulnerable(false);
        zombie.setGravity(true);
    }

    /** Le premier bloc d'air au-dessus du sol à cet endroit (3 blocs plus haut ou plus bas au plus). */
    private static Location groundAt(Location point) {
        Block block = point.getBlock();
        for (int dy = 0; dy <= 3; dy++) {
            Block up = block.getRelative(0, dy, 0);
            if (up.isPassable() && up.getRelative(0, -1, 0).isSolid()) {
                return up.getLocation().add(0.5, 0, 0.5);
            }
            Block down = block.getRelative(0, -dy, 0);
            if (down.isPassable() && down.getRelative(0, -1, 0).isSolid()) {
                return down.getLocation().add(0.5, 0, 0.5);
            }
        }
        return point;
    }

    /** Terre qui vole : particules du bloc sous ce point. */
    private static void dust(Location location, int count) {
        Block ground = location.getBlock().getRelative(0, -1, 0);
        if (ground.getType().isAir()) {
            return;
        }
        location.getWorld().spawnParticle(Particle.BLOCK, location.clone().add(0, 0.2, 0), count, 0.3, 0.1, 0.3, 0.1,
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
        return center.getBlock().getLocation().add(0.5, 0, 0.5);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        int id = mob.entity().getEntityId();
        nextBurrow.remove(id);
        burrowing.remove(id);
        rising.remove(id);
        interrupted.remove(id);
    }

    /** Fléau : terre qui tombe de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        dust(mob.entity().getLocation(), 6);
    }
}
