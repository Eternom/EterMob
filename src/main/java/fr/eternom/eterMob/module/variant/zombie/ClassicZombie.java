package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.Rarity;

/** Le zombie de base, la référence des autres : seuls sa vie et ses dégâts grandissent avec le ratio. */
public class ClassicZombie extends ZombieVariant {

    public ClassicZombie() {
        super("zombie", Rarity.COMMUN, "Putridus");
    }
}
