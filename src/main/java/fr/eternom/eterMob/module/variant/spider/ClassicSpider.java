package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.Rarity;

/** L'araignée de base, la référence des autres : seuls sa vie et ses dégâts grandissent avec le ratio. */
public class ClassicSpider extends SpiderVariant {

    public ClassicSpider() {
        super("spider", Rarity.COMMUN, "Aranea");
    }
}
