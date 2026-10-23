package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.Rarity;

/** Le squelette de base, la référence des autres : seuls sa vie et ses flèches grandissent avec le ratio. */
public class ClassicSkeleton extends SkeletonVariant {

    public ClassicSkeleton() {
        super("skeleton", Rarity.COMMUN, "Ossis");
    }
}
