package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invocateur : quand il chasse un joueur proche, il appelle des zombies autour de lui toutes les 12 s (1 à la fois, 2 à
 * partir du ratio 3), jusqu'à 2 en vie (4 au ratio 4 et 5). Ses sbires ne lâchent rien, n'appellent pas de renforts et
 * meurent avec lui : il faut viser l'invocateur. Lui non plus n'appelle pas de renforts. Un peu plus fragile qu'un
 * zombie normal.
 */
public class SummonerZombie extends ZombieVariant {

    private static final int CHECK_TICKS = 20;
    private static final long COOLDOWN_MILLIS = 12_000;
    private static final double RANGE = 16;
    private static final double PER_WAVE = 1;
    private static final double MAX_ALIVE = 2;

    /** Sbires en vie de chaque invocateur chargé, et prochaine invocation possible (par identifiant d'entité). */
    private final Map<Integer, List<LivingEntity>> minions = new HashMap<>();
    private final Map<Integer, Long> nextSummon = new HashMap<>();

    public SummonerZombie() {
        super("zombie_summoner", Rarity.RARE, "Vocator");
    }

    /** Forêts et taïgas. */
    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.FORESTS);
    }

    @Override
    public double health() {
        return 0.9;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        Minions.noReinforcements(mob.entity());
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
        if (!(summoner.getTarget() instanceof Player target) || target.getWorld() != summoner.getWorld()
                || target.getLocation().distanceSquared(summoner.getLocation()) > RANGE * RANGE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextSummon.getOrDefault(id, 0L)) {
            return;
        }
        int room = mob.scaleInt(MAX_ALIVE) - alive.size();
        int wave = Math.min(room, mob.scaleInt(PER_WAVE));
        if (wave <= 0) {
            return;
        }
        nextSummon.put(id, now + COOLDOWN_MILLIS);
        puff(mob, Particle.SOUL, 15, 1, 0.6);
        for (int i = 0; i < wave; i++) {
            Location spot = spotNear(summoner.getLocation());
            spot.getWorld().spawnParticle(Particle.LARGE_SMOKE, spot, 10, 0.3, 0.5, 0.3, 0.01);
            alive.add(Minions.spawn(spot, Zombie.class, minion -> {
                minion.setAdult();
                minion.setTarget(target);
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
        minions.remove(mob.entity().getEntityId());
        nextSummon.remove(mob.entity().getEntityId());
    }

    /** Fléau : âmes qui tournent autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SOUL, 2, 1, 0.5);
    }
}
