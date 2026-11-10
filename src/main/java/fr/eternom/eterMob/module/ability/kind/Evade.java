package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * evade : en étant frappé (on-damaged, chance 0,25), recule d'un bond loin de celui qui frappe ; ses états vont à
 * l'attaquant (aveugler, ralentir...). Pour les tireurs qui ne veulent pas du corps à corps.
 */
public final class Evade extends Ability {

    private final double strength;

    public Evade(ConfigurationSection config) {
        super("evade", config, new Defaults(Trigger.ON_DAMAGED, 6, 0, 0, 5, 0.25, 0, 0, 0, 0));
        this.strength = Math.clamp(config.getDouble("strength", 0.9), 0.2, 2);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        LivingEntity attacker = context.target();
        Vector away = attacker == null ? self.getLocation().getDirection().multiply(-1)
                : self.getLocation().toVector().subtract(attacker.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        self.setVelocity(away.normalize().multiply(strength).setY(0.42));
        self.getWorld().spawnParticle(Particle.CLOUD, self.getLocation(), 6, 0.3, 0.1, 0.3, 0.02);
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 0.7f, 1.3f);
        if (attacker != null) {
            applyStates(context, attacker);
        }
    }
}
