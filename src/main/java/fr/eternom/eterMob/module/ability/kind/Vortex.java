package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * vortex : aspiration : tous les joueurs à moins de radius blocs (14) sont attirés vers lui (sans dégâts), puis
 * projetés, et prennent ses dégâts et ses états (étourdi 1,5 s...). Long télégraphe (1,5 s), interruptible : la bonne
 * réponse est de l'étourdir ou de le réduire au silence.
 */
public final class Vortex extends Ability {

    public Vortex(ConfigurationSection config) {
        super("vortex", config, new Defaults(Trigger.ACTIVE, 22, 1.5, 0, 14, 1, 4, 14, 1.1, 0.6));
    }

    @Override
    protected fr.eternom.eterLib.module.combat.DamageKind defaultKind() {
        return fr.eternom.eterLib.module.combat.DamageKind.MAGIC;
    }

    @Override
    public double score(AbilityContext context) {
        int count = players(context.self().getLocation(), radius).size();
        return count < 2 ? 0.15 : clamp01(0.3 + 0.2 * count);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        Location center = context.self().getLocation();
        ring(center, radius * (1 - progress) + 1, Particle.REVERSE_PORTAL);
        if (progress == 0) {
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.6f);
        }
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        Location center = self.getLocation();
        List<Player> caught = players(center, radius);
        for (Player player : caught) {
            Vector toward = center.toVector().subtract(player.getLocation().toVector());
            double distance = toward.length();
            if (distance > 0.5) {
                player.setVelocity(toward.normalize().multiply(Math.min(2, 0.3 + distance * 0.12)).setY(0.35));
            }
        }
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.8f);
        // Arrivés près de lui : projetés, frappés, étourdis
        org.bukkit.Bukkit.getScheduler().runTaskLater(Services.plugin(), () -> {
            if (!self.isValid()) {
                return;
            }
            Location now = self.getLocation();
            now.getWorld().spawnParticle(Particle.SONIC_BOOM, now.clone().add(0, 1, 0), 1);
            now.getWorld().playSound(now, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1f);
            for (Player player : caught) {
                if (player.isValid() && player.getWorld().equals(now.getWorld()) && player.getLocation().distanceSquared(now) < 64) {
                    hit(context, player, damage, now);
                }
            }
        }, 12);
    }
}
