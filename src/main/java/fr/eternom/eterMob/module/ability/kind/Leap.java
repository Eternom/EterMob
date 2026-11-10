package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * leap : bondit sur le point où était la cible au début du télégraphe (cercle rouge à l'arrivée) ; à l'atterrissage,
 * dégâts, poussée et états autour de lui. Pour rejoindre une cible au loin ou hors d'atteinte, ou un tireur.
 */
public final class Leap extends Ability {

    public Leap(ConfigurationSection config) {
        super("leap", config, new Defaults(Trigger.ACTIVE, 12, 0.8, 5, 16, 1, 5, 3, 0.6, 0.4));
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        double far = Math.min(1, (situation.distance() - minRange) / Math.max(1, maxRange - minRange));
        return clamp01(0.3 + 0.3 * far + (situation.unreachable() ? 0.5 : 0) + (situation.targetRanged() ? 0.25 : 0));
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        dangerRing(context.spot(), radius, progress);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        Location to = context.spot();
        Vector delta = to.toVector().subtract(self.getLocation().toVector());
        double horizontal = Math.hypot(delta.getX(), delta.getZ());
        // Arc : environ 1 tick par demi-bloc, assez haut pour passer un muret
        Vector velocity = new Vector(delta.getX(), 0, delta.getZ()).normalize().multiply(Math.min(2.2, horizontal * 0.11 + 0.25));
        velocity.setY(Math.clamp(0.55 + delta.getY() * 0.08 + horizontal * 0.02, 0.45, 1.3));
        self.setVelocity(velocity);
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 0.8f, 1.1f);
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (!self.isValid() || self.isDead()) {
                    cancel();
                    return;
                }
                if ((ticks > 4 && self.isOnGround()) || ticks > 40) {
                    cancel();
                    land(context);
                }
            }
        }.runTaskTimer(Services.plugin(), 2, 1);
    }

    private void land(AbilityContext context) {
        Location center = context.self().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 2, 0.6, 0.1, 0.6, 0);
        for (Player player : players(center, radius)) {
            hit(context, player, damage, center);
        }
    }
}
