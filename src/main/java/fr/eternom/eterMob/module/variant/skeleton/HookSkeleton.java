package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Harpon : ses flèches attirent le joueur touché vers lui, plus fort selon le ratio (environ 4 blocs au ratio 1, 9 au
 * ratio 5). Il tire au milieu des arbres et des autres mobs. Forêts, taïgas, jungles, marais,
 * rivières, neige.
 */
public class HookSkeleton extends SkeletonVariant {

    /** Vitesse donnée vers le squelette (blocs par tick), et petite poussée vers le haut pour décoller du sol. */
    private static final double PULL = 0.9;
    private static final double LIFT = 0.35;

    public HookSkeleton() {
        super("skeleton_hook", Rarity.PEU_COMMUN, "Uncus");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.FORESTS, Spawns.JUNGLES, Spawns.SWAMPS, Spawns.RIVERS, Spawns.COLD);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (!(event.getDamager() instanceof AbstractArrow) || !(victim instanceof Player)) {
            return;
        }
        LivingEntity skeleton = mob.entity();
        double pull = mob.scale(PULL);
        // Au tick suivant : le recul de la flèche est appliqué après les dégâts et écraserait la vitesse donnée ici
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(HookSkeleton.class), () -> {
            if (!victim.isValid() || !skeleton.isValid() || victim.getWorld() != skeleton.getWorld()) {
                return;
            }
            Vector toward = skeleton.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (toward.lengthSquared() < 1) {
                return;
            }
            victim.setVelocity(toward.normalize().multiply(pull).setY(LIFT));
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
        });
    }

    /** Fléau : éclats bleus. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ENCHANTED_HIT, 3, 1.2, 0.35);
    }
}
