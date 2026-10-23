package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * Tireur d'élite : ses flèches partent 1,6 fois plus vite, donc plus droit et plus loin (presque pas de courbe), et
 * font un peu plus mal (la vitesse d'une flèche fait ses dégâts). Plus fragile. Montagnes et badlands, là où l'on voit
 * loin.
 */
public class SniperSkeleton extends SkeletonVariant {

    private static final double ARROW_SPEED = 1.6;

    public SniperSkeleton() {
        super("skeleton_sniper", Rarity.PEU_COMMUN, "Sagitta");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.MOUNTAINS, Spawns.DRY);
    }

    @Override
    public double health() {
        return 0.8;
    }

    /** La vitesse multiplie déjà les dégâts par 1,6 : ramenés à environ 1,1. */
    @Override
    protected double arrowDamage() {
        return 0.7;
    }

    @Override
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
        event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(ARROW_SPEED));
    }

    /** Fléau : éclats de visée. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.CRIT, 3, 1.4, 0.3);
    }
}
