package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.Rarity;

/** Le creeper de base, la référence des autres : vie, vitesse, mèche et rayon de Minecraft, dégâts un peu en dessous. */
public class ClassicCreeper extends CreeperVariant {

    public ClassicCreeper() {
        super("creeper", Rarity.COMMUN, "Sibilus", 3, 20);
    }
}
