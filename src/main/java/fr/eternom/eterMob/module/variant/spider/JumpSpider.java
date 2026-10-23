package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Sauteuse : quand le joueur qu'elle chasse est entre 4 et 10 blocs, elle bondit sur lui. Toutes les 6 s au ratio 1,
 * environ 2,7 s au ratio 5. Plaines, savanes, déserts, badlands.
 */
public class JumpSpider extends SpiderVariant {

    private static final int CHECK_TICKS = 10;
    private static final double MIN_DISTANCE = 4;
    private static final double MAX_DISTANCE = 10;
    private static final double COOLDOWN_MILLIS = 6_000;
    /** Élan horizontal par bloc de distance, et hauteur du saut (blocs par tick). */
    private static final double LEAP_PER_BLOCK = 0.17;
    private static final double LEAP_HEIGHT = 0.5;

    private final Map<Integer, Long> nextLeap = new HashMap<>();

    public JumpSpider() {
        super("spider_jump", Rarity.PEU_COMMUN, "Saltatrix");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.PLAINS, Spawns.DRY);
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
