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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * charge : fonce en ligne droite vers le point visé (fixé au début : on l'esquive en se décalant), touche chaque joueur
 * sur son passage une fois (dégâts, poussée, états) ; s'arrête contre un mur. Réglages : speed (blocs par tick, 0,9),
 * distance (au plus, 14).
 */
public final class Charge extends Ability {

    private final double speed;
    private final double distance;

    public Charge(ConfigurationSection config) {
        super("charge", config, new Defaults(Trigger.ACTIVE, 12, 1, 5, 14, 1, 6, 1.6, 1.2, 0.35));
        this.speed = Math.clamp(config.getDouble("speed", 0.9), 0.3, 2);
        this.distance = Math.max(4, config.getDouble("distance", 14));
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        return situation.sight() ? clamp01(0.4 + (situation.targetRanged() ? 0.3 : 0) + (situation.playersNearTarget() > 1 ? 0.2 : 0)) : 0;
    }

    @Override
    public void begin(AbilityContext context) {
        Location target = context.target() == null ? context.self().getLocation() : context.target().getLocation();
        context.spot(target);
        context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        line(context.self().getLocation().add(0, 0.2, 0), context.spot().add(0, 0.2, 0), Particle.CRIT);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        Vector direction = context.spot().toVector().subtract(self.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        Vector step = direction.normalize().multiply(speed);
        int maxTicks = (int) Math.ceil(distance / speed);
        Set<UUID> touched = new HashSet<>();
        new BukkitRunnable() {
            private int ticks;
            private Location last = self.getLocation();

            @Override
            public void run() {
                if (!self.isValid() || self.isDead() || ticks++ > maxTicks) {
                    cancel();
                    return;
                }
                self.setVelocity(step.clone().setY(Math.min(0, self.getVelocity().getY())));
                Location now = self.getLocation();
                self.getWorld().spawnParticle(Particle.CLOUD, now, 2, 0.2, 0.1, 0.2, 0);
                for (Player player : players(now, radius)) {
                    if (touched.add(player.getUniqueId())) {
                        hit(context, player, damage, now);
                    }
                }
                // Contre un mur : il n'avance plus
                if (ticks > 3 && now.distanceSquared(last) < speed * speed * 0.1) {
                    self.getWorld().playSound(now, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 0.8f);
                    cancel();
                }
                last = now;
            }
        }.runTaskTimer(Services.plugin(), 0, 1);
    }
}
