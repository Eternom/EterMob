package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * pull : un crochet (ligne de particules) qui ramène la cible vers lui, avec ses états (ralentissement...). Contre les
 * tireurs et les joueurs perchés hors d'atteinte. Réglage : strength (force de traction, 1,2) ; root-first : enracine d'abord
 * tant de secondes, puis tire (chaînes).
 */
public final class Pull extends Ability {

    private final double strength;
    private final double rootFirst;

    public Pull(ConfigurationSection config) {
        super("pull", config, new Defaults(Trigger.ACTIVE, 14, 0.7, 6, 22, 1, 1, 0, 0, 0));
        this.strength = Math.clamp(config.getDouble("strength", 1.2), 0.3, 3);
        this.rootFirst = Math.max(0, config.getDouble("root-first", 0));
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        if (!situation.sight()) {
            return 0;
        }
        return clamp01(0.2 + (situation.targetRanged() ? 0.45 : 0) + (situation.unreachable() ? 0.5 : 0)
                + Math.min(0.2, situation.distance() / 60));
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        if (context.target() != null) {
            line(context.self().getEyeLocation(), context.target().getEyeLocation(), Particle.ENCHANTED_HIT);
        }
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity target = context.target();
        LivingEntity self = context.self();
        if (target == null || !target.isValid() || (target instanceof Player player && !Hits.fighting(player))
                || (sight && !self.hasLineOfSight(target))) {
            return;
        }
        line(self.getEyeLocation(), target.getEyeLocation(), Particle.CRIT);
        if (rootFirst > 0) {
            // Chaînes : enraciné d'abord, puis tiré quand les chaînes se tendent
            if (Services.states() != null) {
                Services.states().apply(target, "root", 1, rootFirst, self);
            }
            org.bukkit.Bukkit.getScheduler().runTaskLater(Services.plugin(), () -> {
                if (target.isValid() && self.isValid() && target.getWorld().equals(self.getWorld())) {
                    drag(context, self, target);
                }
            }, Math.round(rootFirst * 20));
            return;
        }
        drag(context, self, target);
    }

    private void drag(AbilityContext context, LivingEntity self, LivingEntity target) {
        Vector toward = self.getLocation().toVector().subtract(target.getLocation().toVector());
        double distance = toward.length();
        if (distance < 0.5) {
            return;
        }
        Vector velocity = toward.normalize().multiply(Math.min(strength * 1.6, 0.2 + distance * 0.09 * strength));
        velocity.setY(Math.clamp(0.35 + toward.getY() * 0.04, 0.25, 0.9));
        target.setVelocity(velocity);
        self.getWorld().playSound(self.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
        hit(context, target, damage, self.getLocation());
    }
}
