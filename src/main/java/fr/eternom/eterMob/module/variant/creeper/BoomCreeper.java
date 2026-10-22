package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.Rarity;

/**
 * Sa force est la zone : rayon de base 6 (un creeper chargé), environ 13 au ratio le plus fort, et les dégâts les plus
 * hauts des creepers, sans jamais traverser une armure en Protection IV à bout portant.
 */
public class BoomCreeper extends CreeperVariant {

    public BoomCreeper() {
        super("creeper_boom", Rarity.RARE, "Fragor", 6, 22);
    }
}
