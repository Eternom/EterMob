package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * slam : frappe le sol autour de lui après un télégraphe (cercle rouge) : dégâts, poussée et états à tous les joueurs
 * dans le rayon. L'IA la préfère quand plusieurs joueurs l'entourent, et sur une cible tenue (étourdie, enracinée).
 */
public final class Slam extends Ability {

    public Slam(ConfigurationSection config) {
        super("slam", config, new Defaults(Trigger.ACTIVE, 10, 1, 0, 4, 1, 6, 4, 0.8, 0.5));
    }

    @Override
    public double score(AbilityContext context) {
        int near = context.situation().playersNearSelf();
        if (near == 0) {
            return 0;
        }
        return clamp01(0.35 + 0.25 * (near - 1) + (context.situation().targetHeld() ? 0.3 : 0));
    }

    @Override
    public void begin(AbilityContext context) {
        context.spot(context.self().getLocation());
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        dangerRing(context.spot(), radius, progress);
    }

    @Override
    public void cast(AbilityContext context) {
        Location center = context.self().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, radius / 3, 0.2, radius / 3, 0);
        for (Player player : players(center, radius)) {
            hit(context, player, damage, center);
        }
    }
}
