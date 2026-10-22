package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.Rarity;

/**
 * Sa force est la zone : il s'allume dès 5 blocs de sa cible (3 pour un creeper normal), rayon de base 6 (un creeper
 * chargé), environ 13 au ratio le plus fort, et les dégâts les plus
 * hauts des creepers. En difficulté Difficile, à bout portant en netherite Protection IV, on encaisse jusqu'au ratio 4 ;
 * le ratio 5 peut tuer d'un coup (Protection contre les explosions IV le tient).
 */
public class BoomCreeper extends CreeperVariant {

    public BoomCreeper() {
        super("creeper_boom", Rarity.RARE, "Fragor", 6, 24, 5);
    }
}
