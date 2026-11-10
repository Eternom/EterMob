package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

/**
 * strike : un coup chargé. Par défaut au coup (on-hit, chance 0,3) : dégâts en plus et états (saignement, fragilité...)
 * sur la victime. En active (trigger: active), c'est un coup au contact choisi par l'IA (range 0 à 3,5).
 */
public final class Strike extends Ability {

    public Strike(ConfigurationSection config) {
        super("strike", config, new Defaults(Trigger.ON_HIT, 4, 0, 0, 3.5, 0.3, 2, 0, 0.4, 0.1));
    }

    @Override
    public double score(AbilityContext context) {
        // Meilleur sur une cible qui n'a pas encore ses états, et quand elle est tenue (étourdie)
        double fresh = states.isEmpty() || !context.situation().targetStates().contains(states.getFirst().state()) ? 0.3 : 0;
        return 0.4 + fresh + (context.situation().targetHeld() ? 0.3 : 0);
    }

    @Override
    public void cast(AbilityContext context) {
        if (context.target() == null || !context.target().isValid()) {
            return;
        }
        context.target().getWorld().spawnParticle(Particle.SWEEP_ATTACK, context.target().getLocation().add(0, 1, 0), 1);
        hit(context, context.target(), damage, context.self().getLocation());
    }
}
