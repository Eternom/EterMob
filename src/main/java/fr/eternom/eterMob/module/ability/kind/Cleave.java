package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * cleave : un coup en arc devant lui (cône de angle degrés, 120, sur radius blocs, 4,5), après une fissure au sol qui
 * montre la zone (télégraphe). La direction est fixée au début : on l'esquive en passant derrière ou sur le côté. Un
 * bouclier levé face à lui bloque le coup (dégâts et états). blind : secondes de Cécité (pelletée de terre). Ne s'interrompt pas par défaut (un coup de masse lancé part).
 */
public final class Cleave extends Ability {

    private final double angle;
    private final double blind;

    public Cleave(ConfigurationSection config) {
        super("cleave", config, new Defaults(Trigger.ACTIVE, 7, 0.8, 0, 4.5, 1, 14, 4.5, 0.9, 0.3));
        this.angle = Math.toRadians(Math.clamp(config.getDouble("angle", 120), 20, 360) / 2);
        this.blind = Math.max(0, config.getDouble("blind", 0));
    }

    @Override
    public boolean interruptible() {
        return false;
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        return clamp01(0.5 + 0.2 * Math.max(0, situation.playersNearSelf() - 1) + (situation.targetHeld() ? 0.3 : 0));
    }

    @Override
    public void begin(AbilityContext context) {
        LivingEntity self = context.self();
        Location facing = self.getLocation();
        if (context.target() != null) {
            facing.setDirection(context.target().getLocation().toVector().subtract(self.getLocation().toVector()).setY(0));
        }
        context.spot(facing);
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 0.8f, 0.6f);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        Location origin = context.spot();
        Vector forward = origin.getDirection().setY(0).normalize();
        // La fissure : des points de terre sur le cône, de plus en plus loin
        for (double r = 1; r <= radius * Math.max(0.3, progress); r += 0.8) {
            for (double a = -angle; a <= angle; a += 0.25) {
                Vector direction = forward.clone().rotateAroundY(a).multiply(r);
                Location point = origin.clone().add(direction).add(0, 0.1, 0);
                point.getWorld().spawnParticle(Particle.BLOCK, point, 1, 0.1, 0, 0.1, 0, Material.COARSE_DIRT.createBlockData());
            }
        }
    }

    @Override
    public void cast(AbilityContext context) {
        Location origin = context.spot();
        Vector forward = origin.getDirection().setY(0).normalize();
        origin.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
        origin.getWorld().spawnParticle(Particle.SWEEP_ATTACK, origin.clone().add(forward.clone().multiply(2)).add(0, 1, 0), 3, 1, 0.3, 1, 0);
        for (Player player : players(origin, radius)) {
            Vector to = player.getLocation().toVector().subtract(origin.toVector()).setY(0);
            if (to.lengthSquared() > 0.01 && forward.angle(to.normalize()) > angle) {
                continue;
            }
            if (blocks(player, origin)) {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
                continue;
            }
            hit(context, player, damage, origin);
            if (blind > 0) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS,
                        (int) Math.round(blind * 20), 0, true, false, true));
            }
        }
    }

    /** Bouclier levé, tourné vers le coup. */
    private static boolean blocks(Player player, Location from) {
        if (!player.isBlocking()) {
            return false;
        }
        Vector look = player.getLocation().getDirection().setY(0);
        Vector toSource = from.toVector().subtract(player.getLocation().toVector()).setY(0);
        return look.lengthSquared() > 0 && toSource.lengthSquared() > 0 && look.angle(toSource) < Math.toRadians(80);
    }
}
