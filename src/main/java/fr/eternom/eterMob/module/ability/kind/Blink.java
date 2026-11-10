package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * blink : se téléporte. mode behind (défaut) : dans le dos de la cible (rejoindre un tireur, contourner) ; mode escape :
 * loin des joueurs quand sa vie est basse et qu'on l'entoure. Jamais dans un bloc, ni sur un terrain de clan.
 */
public final class Blink extends Ability {

    private final boolean escape;

    public Blink(ConfigurationSection config) {
        super("blink", config, new Defaults(Trigger.ACTIVE, 14, 0.5, 0, 24, 1, 0, 0, 0, 0));
        this.escape = config.getString("mode", "behind").toLowerCase(Locale.ROOT).equals("escape");
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        if (escape) {
            return situation.selfHealth() < 0.35 && situation.playersNearSelf() > 0 ? 0.8 : 0;
        }
        if (situation.distance() < 4) {
            return 0;
        }
        return clamp01(0.2 + (situation.unreachable() ? 0.6 : 0) + (situation.targetRanged() ? 0.3 : 0));
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        context.self().getWorld().spawnParticle(Particle.PORTAL, context.self().getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.3);
        if (progress == 0) {
            context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.7f, 1.3f);
        }
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        Location to = escape ? escapeSpot(self) : behind(context);
        if (to == null) {
            return;
        }
        self.getWorld().spawnParticle(Particle.REVERSE_PORTAL, self.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
        self.teleport(to);
        self.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
        self.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
        if (!escape && context.target() != null) {
            applyStates(context, context.target());
        }
    }

    private Location behind(AbilityContext context) {
        LivingEntity target = context.target();
        if (target == null || !target.isValid()) {
            return null;
        }
        Vector back = target.getLocation().getDirection().setY(0);
        back = back.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : back.normalize();
        for (double distance : new double[]{2, 1.5, -2, 3}) {
            Location spot = safe(target.getLocation().subtract(back.clone().multiply(distance)));
            if (spot != null) {
                spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
                return spot;
            }
        }
        return null;
    }

    private static Location escapeSpot(LivingEntity self) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            Location spot = safe(self.getLocation().add(Math.cos(angle) * 9, 0, Math.sin(angle) * 9));
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Un endroit où tenir debout près de at (3 blocs plus haut ou plus bas au plus), hors terrain protégé. */
    static Location safe(Location at) {
        Block base = at.getBlock();
        for (int dy = 0; dy <= 3; dy++) {
            for (int sign : new int[]{1, -1}) {
                Block feet = base.getRelative(0, dy * sign, 0);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()
                        && !feet.isLiquid() && (Services.guard() == null || Services.guard().allowsBlocks(feet.getLocation()))) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }
}
