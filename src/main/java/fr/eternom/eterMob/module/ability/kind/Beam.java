package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * beam : un trait en ligne droite devant lui, de length blocs (14) et width de large (1,5), dans la direction de la
 * cible fixée au début du télégraphe (une ligne rouge au sol) : on s'en écarte. Dégâts et états à tous ceux sur la ligne.
 * Magique par défaut. color : la couleur du trait (RRGGBB, 1A1A2E : encre).
 */
public final class Beam extends Ability {

    private final double length;
    private final double width;
    private final Color color;

    public Beam(ConfigurationSection config) {
        super("beam", config, new Defaults(Trigger.ACTIVE, 12, 1, 3, 16, 1, 8, 0, 0.5, 0.2));
        this.length = Math.clamp(config.getDouble("length", 14), 3, 40);
        this.width = Math.clamp(config.getDouble("width", 1.5), 0.5, 6);
        Color parsed;
        try {
            parsed = Color.fromRGB(Integer.parseInt(config.getString("color", "1A1A2E"), 16));
        } catch (IllegalArgumentException e) {
            parsed = Color.fromRGB(0x1A1A2E);
        }
        this.color = parsed;
    }

    @Override
    protected fr.eternom.eterLib.module.combat.DamageKind defaultKind() {
        return fr.eternom.eterLib.module.combat.DamageKind.MAGIC;
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        return situation.sight() ? clamp01(0.4 + 0.15 * Math.max(0, situation.playersNearTarget() - 1) + (situation.targetRanged() ? 0.2 : 0)) : 0;
    }

    @Override
    public void begin(AbilityContext context) {
        Location from = context.self().getLocation();
        Vector direction = context.target() == null ? from.getDirection()
                : context.target().getLocation().toVector().subtract(from.toVector());
        from.setDirection(direction.setY(0).lengthSquared() < 0.01 ? new Vector(1, 0, 0) : direction.setY(0));
        context.spot(from);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        Location from = context.spot();
        Vector step = from.getDirection().setY(0).normalize();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(220, (int) (100 * (1 - progress)), 20), 1f);
        for (double d = 1; d <= length; d += 0.7) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(step.clone().multiply(d)).add(0, 0.15, 0), 1, 0, 0, 0, 0, dust);
        }
    }

    @Override
    public void cast(AbilityContext context) {
        Location from = context.spot();
        Vector step = from.getDirection().setY(0).normalize();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.8f);
        for (double d = 0.5; d <= length; d += 0.4) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(step.clone().multiply(d)).add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0, dust);
        }
        from.getWorld().playSound(from, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.6f);
        for (Player player : players(from, length + 1)) {
            Vector to = player.getLocation().toVector().subtract(from.toVector()).setY(0);
            double along = to.dot(step);
            double across = to.clone().subtract(step.clone().multiply(along)).length();
            if (along >= 0 && along <= length && across <= width) {
                hit(context, player, damage, from);
            }
        }
    }
}
