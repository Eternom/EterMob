package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Particle;
import org.bukkit.entity.Creeper;

import java.util.List;

/**
 * Canon de verre : il s'allume dès 5 blocs de sa cible (3 pour un creeper normal), explose sur une grande zone (6 blocs,
 * environ 13 au ratio le plus fort) et fait les plus gros dégâts des creepers. En contrepartie il est fragile, lent, et
 * sa mèche est longue : le temps de le tuer une fois allumé. En Difficile, à bout portant en netherite Protection IV,
 * seul le ratio 5 peut tuer d'un coup (Protection contre les explosions IV le tient).
 */
public class BoomCreeper extends CreeperVariant {

    /** Mèche (ticks) : 2 s au lieu de 1,5 s. */
    private static final int FUSE = 40;

    public BoomCreeper() {
        super("creeper_boom", Rarity.RARE, "Fragor", 6, 24, 5);
    }

    @Override
    public List<String> lore() {
        return List.of(
                "S'allume à 5 blocs, grande zone (6 à 13 blocs).",
                "Fragile, lent, mèche de 2 s.",
                "Biomes : partout.");
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        super.onSpawn(mob);
        ((Creeper) mob.entity()).setMaxFuseTicks(FUSE);
    }

    /** Fléau : fumée sur sa tête, flammes et fumée épaisse pendant la mèche. */
    @Override
    public void ambient(ActiveMob mob) {
        if (fusing(mob)) {
            puff(mob, Particle.LARGE_SMOKE, 6, 1.5, 0.3);
            puff(mob, Particle.FLAME, 4, 1, 0.4);
        } else {
            puff(mob, Particle.SMOKE, 3, 1.7, 0.15);
        }
    }

    @Override
    public double health() {
        return 0.6;
    }

    @Override
    public double speed() {
        return 0.85;
    }
}
