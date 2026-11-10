package fr.eternom.eterMob.module.mob;

import java.util.Locale;

/** Rang d'un monstre : variant (le commun), meneur (sous-boss, à la tête d'un groupe), boss. */
public enum Tier {
    VARIANT("variant"),
    LEADER("meneur"),
    BOSS("boss");

    private final String id;

    Tier(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Tier parse(String text, Tier fallback) {
        if (text == null) {
            return fallback;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Tier tier : values()) {
            if (tier.id.equals(lower) || tier.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return tier;
            }
        }
        return fallback;
    }
}
