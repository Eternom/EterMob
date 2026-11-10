package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.StateSpec;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/**
 * curse : un sort à distance (rayon de particules) qui pose ses états sur la cible (et ses dégâts, 0 par défaut). L'IA
 * la lance sur une cible qui n'a pas encore ces états, et plutôt sur les tireurs et les soigneurs (leur menace).
 */
public final class Curse extends Ability {

    public Curse(ConfigurationSection config) {
        super("curse", config, new Defaults(Trigger.ACTIVE, 12, 0.8, 3, 24, 1, 0, 0, 0, 0));
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        if (!situation.sight() || states.isEmpty()) {
            return 0;
        }
        boolean already = false;
        for (StateSpec spec : states) {
            already |= situation.targetStates().contains(spec.state());
        }
        return clamp01((already ? 0.1 : 0.5) + (situation.targetRanged() ? 0.2 : 0));
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        context.self().getWorld().spawnParticle(Particle.WITCH, context.self().getEyeLocation(), 4, 0.3, 0.3, 0.3, 0);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity target = context.target();
        if (target == null || !target.isValid() || (sight && !context.self().hasLineOfSight(target))) {
            return;
        }
        line(context.self().getEyeLocation(), target.getEyeLocation(), Particle.WITCH);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 0.9f);
        hit(context, target, damage, context.self().getLocation());
    }
}
