package fr.eternom.eterMob.module.variant.zombie;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nécromancien, rare. Il ne frappe pas : il garde 6 à 10 blocs entre lui et le joueur qu'il chasse (il recule quand on
 * approche) et laisse ses sbires venir au contact.
 * <ul>
 *   <li>Rituel : toutes les 8 s, quand le joueur est à moins de 16 blocs, il s'arrête 1,5 s pour incanter ; des cercles
 *   d'âmes marquent au sol où ses sbires vont sortir. Le frapper pendant le rituel (de près ou avec un projectile)
 *   l'annule.</li>
 *   <li>Sbires : des zombies à demi-vie (10 PV), 1 par appel (2 à partir du ratio 3), jusqu'à 2 en vie (3 au ratio 2
 *   et 3, 4 au ratio 4 et 5). Ils ne lâchent rien, n'appellent pas de renforts et meurent avec lui.</li>
 * </ul>
 * Lui non plus n'appelle pas de renforts. Un peu plus fragile qu'un zombie normal. Forêts, taïgas, neige.
 */
public class SummonerZombie extends ZombieVariant {

    private static final int CHECK_TICKS = 10;
    private static final double MIN_DISTANCE = 6;
    private static final double MAX_DISTANCE = 10;
    /** Recul quand on approche (blocs), et vitesses de marche (en fois sa vitesse). */
    private static final double RETREAT = 5;
    private static final double RETREAT_SPEED = 1.3;
    private static final double FOLLOW_SPEED = 1;

    private static final double RANGE = 16;
    private static final long COOLDOWN_MILLIS = 8_000;
    /** Rituel interrompu : il peut recommencer après ce délai. */
    private static final long INTERRUPTED_COOLDOWN_MILLIS = 4_000;
    private static final int RITUAL_TICKS = 30;
    private static final int RITUAL_STEP = 5;
    private static final double PER_WAVE = 1;
    private static final double MAX_ALIVE = 2;
    private static final double MINION_HEALTH = 10;

    /** Rituel en cours : où sortiront les sbires, et la tâche qui l'anime. */
    private record Ritual(List<Location> spots, BukkitTask task) {
    }

    private final Map<Integer, List<LivingEntity>> minions = new HashMap<>();
    private final Map<Integer, Long> nextSummon = new HashMap<>();
    private final Map<Integer, Ritual> rituals = new HashMap<>();

    public SummonerZombie() {
        super("zombie_summoner", Rarity.RARE, "Vocator");
    }

    /** Forêts, taïgas, neige. */
    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.FORESTS, Spawns.COLD);
    }

    @Override
    public double health() {
        return 0.9;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        Minions.noReinforcements(mob.entity());
    }

    /** Pas d'attaque au corps à corps (elle le ferait foncer sur le joueur) : il se place lui-même (tick). */
    @Override
    public void onLoad(ActiveMob mob) {
        Bukkit.getMobGoals().removeGoal((Zombie) mob.entity(), VanillaGoal.ZOMBIE_ATTACK);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Zombie summoner = (Zombie) mob.entity();
        int id = summoner.getEntityId();
        List<LivingEntity> alive = minions.computeIfAbsent(id, key -> new ArrayList<>());
        alive.removeIf(minion -> !minion.isValid() || minion.isDead());
        if (rituals.containsKey(id)) {
            summoner.getPathfinder().stopPathfinding();
            return;
        }
        if (!(summoner.getTarget() instanceof Player target) || target.getWorld() != summoner.getWorld()) {
            return;
        }
        double distance = target.getLocation().distance(summoner.getLocation());
        keepDistance(summoner, target, distance);
        long now = System.currentTimeMillis();
        int wave = Math.min(mob.scaleInt(MAX_ALIVE) - alive.size(), mob.scaleInt(PER_WAVE));
        if (distance <= RANGE && wave > 0 && now >= nextSummon.getOrDefault(id, 0L)) {
            startRitual(mob, target, wave);
        }
    }

    /** Recule quand le joueur est trop près, s'approche quand il est trop loin, sinon reste et le regarde. */
    private static void keepDistance(Zombie summoner, Player target, double distance) {
        if (distance < MIN_DISTANCE) {
            Vector away = summoner.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                summoner.getPathfinder().moveTo(summoner.getLocation().add(away.normalize().multiply(RETREAT)), RETREAT_SPEED);
            }
        } else if (distance > MAX_DISTANCE) {
            summoner.getPathfinder().moveTo(target.getLocation(), FOLLOW_SPEED);
        } else {
            summoner.getPathfinder().stopPathfinding();
            summoner.lookAt(target);
        }
    }

    private void startRitual(ActiveMob mob, Player target, int wave) {
        Zombie summoner = (Zombie) mob.entity();
        int id = summoner.getEntityId();
        summoner.getPathfinder().stopPathfinding();
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < wave; i++) {
            spots.add(spotNear(summoner.getLocation()));
        }
        summoner.getWorld().playSound(summoner.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.8f);
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(SummonerZombie.class), () -> {
            if (!summoner.isValid()) {
                task[0].cancel();
                rituals.remove(id);
                return;
            }
            elapsed[0] += RITUAL_STEP;
            summoner.lookAt(target);
            summoner.getWorld().spawnParticle(Particle.SCULK_SOUL, summoner.getLocation().add(0, 1.8, 0), 3, 0.3, 0.2, 0.3, 0.01);
            for (Location spot : spots) {
                circle(spot);
            }
            if (elapsed[0] >= RITUAL_TICKS) {
                task[0].cancel();
                rituals.remove(id);
                summon(mob, spots, target);
            }
        }, 0, RITUAL_STEP);
        rituals.put(id, new Ritual(spots, task[0]));
        nextSummon.put(id, System.currentTimeMillis() + COOLDOWN_MILLIS);
    }

    /** Cercle d'âmes au sol. */
    private static void circle(Location spot) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            spot.getWorld().spawnParticle(Particle.SOUL, spot.clone().add(Math.cos(angle) * 0.6, 0.1, Math.sin(angle) * 0.6),
                    1, 0, 0.05, 0, 0.01);
        }
    }

    private void summon(ActiveMob mob, List<Location> spots, Player target) {
        List<LivingEntity> alive = minions.computeIfAbsent(mob.entity().getEntityId(), key -> new ArrayList<>());
        for (Location spot : spots) {
            spot.getWorld().spawnParticle(Particle.LARGE_SMOKE, spot, 10, 0.3, 0.5, 0.3, 0.01);
            alive.add(Minions.spawn(spot, Zombie.class, minion -> {
                minion.setAdult();
                AttributeInstance max = minion.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(MINION_HEALTH);
                    minion.setHealth(MINION_HEALTH);
                }
                if (target.isValid()) {
                    minion.setTarget(target);
                }
            }));
        }
    }

    /** Un endroit libre à 1 ou 2 blocs, au même niveau ; sinon la place de l'invocateur. */
    private static Location spotNear(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 6; attempt++) {
            Location spot = center.clone().add(random.nextInt(-2, 3), 0, random.nextInt(-2, 3));
            Block feet = spot.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return center.clone();
    }

    /** Frappé par un joueur (de près ou avec un projectile) pendant le rituel : l'appel est annulé. */
    @Override
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
        int id = mob.entity().getEntityId();
        if (!rituals.containsKey(id) || !(event instanceof EntityDamageByEntityEvent byEntity) || !byPlayer(byEntity.getDamager())) {
            return;
        }
        Ritual ritual = rituals.remove(id);
        ritual.task().cancel();
        LivingEntity summoner = mob.entity();
        summoner.getWorld().spawnParticle(Particle.POOF, summoner.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        summoner.getWorld().playSound(summoner.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
        nextSummon.put(id, System.currentTimeMillis() + INTERRUPTED_COOLDOWN_MILLIS);
    }

    private static boolean byPlayer(Entity damager) {
        return damager instanceof Player || damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    /** Ses sbires meurent avec lui. */
    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        List<LivingEntity> alive = minions.remove(mob.entity().getEntityId());
        if (alive == null) {
            return;
        }
        for (LivingEntity minion : alive) {
            if (minion.isValid()) {
                minion.getWorld().spawnParticle(Particle.POOF, minion.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
                minion.remove();
            }
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        int id = mob.entity().getEntityId();
        minions.remove(id);
        nextSummon.remove(id);
        Ritual ritual = rituals.remove(id);
        if (ritual != null) {
            ritual.task().cancel();
        }
    }

    /** Fléau : âmes qui tournent autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SOUL, 2, 1, 0.5);
    }
}
