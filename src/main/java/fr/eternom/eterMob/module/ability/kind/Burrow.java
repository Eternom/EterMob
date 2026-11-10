package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * burrow : plonge sous terre (invisible, intouchable) pendant seconds (2) ; un cercle suit sa cible sous terre, puis se
 * fige lock secondes (0,8) avant qu'il ne ressorte dessous : dégâts, projection et états à ceux dans le cercle. On quitte
 * le cercle quand il se fige. Pour rejoindre une cible au loin, ou perchée.
 */
public final class Burrow extends Ability {

    private final double seconds;
    private final double lock;

    public Burrow(ConfigurationSection config) {
        super("burrow", config, new Defaults(Trigger.ACTIVE, 16, 0.4, 4, 24, 1, 8, 2.5, 0.4, 1.0));
        this.seconds = Math.clamp(config.getDouble("seconds", 2), 0.8, 6);
        this.lock = Math.clamp(config.getDouble("lock", 0.8), 0.3, seconds);
    }

    @Override
    public boolean interruptible() {
        return false;
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        return clamp01(0.3 + Math.min(0.3, situation.distance() / 40) + (situation.unreachable() ? 0.5 : 0) + (situation.targetRanged() ? 0.2 : 0));
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        LivingEntity target = context.target();
        if (target == null) {
            return;
        }
        self.getWorld().playSound(self.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        dust(self.getLocation());
        self.setInvisible(true);
        self.setInvulnerable(true);
        if (self instanceof Mob mob) {
            mob.setAware(false);
        }
        int total = (int) Math.round(seconds * 20);
        int lockAt = total - (int) Math.round(lock * 20);
        new BukkitRunnable() {
            private int ticks;
            private Location spot = target.getLocation();

            @Override
            public void run() {
                if (!self.isValid()) {
                    cancel();
                    return;
                }
                if (ticks < lockAt && target.isValid() && target.getWorld().equals(self.getWorld())) {
                    spot = target.getLocation();
                }
                dangerRing(spot, radius, ticks < lockAt ? 0 : (double) (ticks - lockAt) / Math.max(1, total - lockAt));
                if (ticks % 4 == 0) {
                    dust(spot);
                }
                if (ticks++ >= total) {
                    cancel();
                    emerge(context, spot);
                }
            }
        }.runTaskTimer(Services.plugin(), 1, 1);
    }

    private void emerge(AbilityContext context, Location spot) {
        LivingEntity self = context.self();
        Location out = spot.clone();
        out.setYaw(self.getLocation().getYaw());
        Block feet = out.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            out = self.getLocation();
        }
        self.teleport(out);
        self.setInvisible(false);
        self.setInvulnerable(false);
        if (self instanceof Mob mob) {
            mob.setAware(true);
        }
        self.getWorld().playSound(out, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.3f);
        out.getWorld().spawnParticle(Particle.EXPLOSION, out, 3, 0.8, 0.2, 0.8, 0);
        dust(out);
        for (Player player : players(out, radius)) {
            hit(context, player, damage, out);
        }
    }

    private static void dust(Location at) {
        Block below = at.getBlock().getRelative(0, -1, 0);
        if (below.getType().isSolid()) {
            at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), 14, 0.6, 0.1, 0.6, 0, below.getBlockData());
        }
    }
}
