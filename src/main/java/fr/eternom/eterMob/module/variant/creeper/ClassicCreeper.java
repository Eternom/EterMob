package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.Rarity;

/** Le creeper de base : rayon de 3 (comme Minecraft), dégâts modérés ; au ratio le plus fort, comme un creeper normal. */
public class ClassicCreeper extends CreeperVariant {

    public ClassicCreeper() {
        super("creeper", Rarity.COMMUN, "Sibilus", 3, 18);
    }
}
