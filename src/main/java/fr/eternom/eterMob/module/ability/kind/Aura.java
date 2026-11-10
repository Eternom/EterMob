package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * aura : périodique (toutes les cooldown secondes, sans télégraphe ni choix de l'IA) : dégâts et états aux joueurs dans
 * le rayon (froid, pourriture, peur...). particle : la particule du cercle (SNOWFLAKE...).
 */
public final class Aura extends Ability {

    private final Particle particle;

    public Aura(ConfigurationSection config) {
        super("aura", config, new Defaults(Trigger.PERIODIC, 3, 0, 0, 64, 1, 0, 4, 0, 0));
        Particle parsed;
        try {
            parsed = Particle.valueOf(config.getString("particle", "SMOKE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = Particle.SMOKE;
        }
        this.particle = parsed.getDataType() == Void.class ? parsed : Particle.SMOKE;
    }

    @Override
    protected fr.eternom.eterLib.module.combat.DamageKind defaultKind() {
        return fr.eternom.eterLib.module.combat.DamageKind.MAGIC;
    }

    @Override
    public void cast(AbilityContext context) {
        Location center = context.self().getLocation();
        ring(center, radius, particle);
        for (Player player : players(center, radius)) {
            hit(context, player, damage, center);
        }
    }
}
