package fr.eternom.eterMob.module.mob;

import java.util.Locale;

/** Rareté d'un variant : sa chance d'être choisi et son budget d'équilibrage se règlent dans config.yml > rarities. */
public enum Rarity {
    COMMUN("commun"),
    PEU_COMMUN("peu-commun"),
    RARE("rare"),
    EPIQUE("epique"),
    LEGENDAIRE("legendaire");

    private final String id;

    Rarity(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Rarity parse(String text, Rarity fallback) {
        if (text == null) {
            return fallback;
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('_', '-');
        for (Rarity rarity : values()) {
            if (rarity.id.equals(lower)) {
                return rarity;
            }
        }
        return fallback;
    }
}
