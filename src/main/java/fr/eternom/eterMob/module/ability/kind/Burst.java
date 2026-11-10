package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * burst : une déflagration autour de lui (sans casser de bloc) : dégâts, poussée, états. À sa mort (on-death, défaut),
 * sous un seuil de vie (below-health), ou active avec télégraphe ; suicide: true le tue après (un creeper à capacité).
 * particle : la particule (EXPLOSION).
 */
public final class Burst extends Ability {

    private final boolean suicide;
    private final Particle particle;

    public Burst(ConfigurationSection config) {
        super("burst", config, new Defaults(Trigger.ON_DEATH, 10, 1.5, 0, 3, 1, 6, 3.5, 0.9, 0.5));
        this.suicide = config.getBoolean("suicide", false);
        Particle parsed;
        try {
            parsed = Particle.valueOf(config.getString("particle", "EXPLOSION").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = Particle.EXPLOSION;
        }
        this.particle = parsed.getDataType() == Void.class ? parsed : Particle.EXPLOSION;
    }

    @Override
    public double score(AbilityContext context) {
        int near = context.situation().playersNearSelf();
        return near == 0 ? 0 : clamp01(0.3 + 0.25 * near);
    }

    @Override
    public void begin(AbilityContext context) {
        context.spot(context.self().getLocation());
        context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 0.8f);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        dangerRing(context.self().getLocation(), radius, progress);
    }

    @Override
    public void cast(AbilityContext context) {
        Location center = context.self().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        center.getWorld().spawnParticle(particle, center.clone().add(0, 0.5, 0), 6, radius / 3, 0.3, radius / 3, 0);
        for (Player player : players(center, radius)) {
            hit(context, player, damage, center);
        }
        if (suicide && trigger == Trigger.ACTIVE) {
            context.self().setHealth(0);
        }
    }
}
