package fr.eternom.eterMob.module.ai;

/**
 * La note d'une cible et la règle de changement de cible, sans Bukkit (testées).
 */
public final class Focus {

    /**
     * Critères d'un joueur, chacun de 0 à 1 (sauf exception) :
     * threat (menace normalisée), proximity (1 au contact, 0 à la portée), finish (vie manquante), ranged, healer, tank
     * (0 ou 1), sight (1 : vu, sinon un facteur < 1), penalty (1 : rien ; hors d'atteinte : < 1).
     */
    public record Criteria(double threat, double proximity, double finish, boolean ranged, boolean healer, boolean tank,
                           double sight, double penalty) {
    }

    private Focus() {
    }

    public static double score(AiProfile profile, Criteria criteria) {
        double score = profile.threat() * criteria.threat()
                + profile.proximity() * criteria.proximity()
                + profile.finish() * criteria.finish()
                + (criteria.ranged() ? profile.ranged() : 0)
                + (criteria.healer() ? profile.healer() : 0)
                + (criteria.tank() ? profile.tank() : 0);
        // Une petite base : un joueur seul et loin reste une cible possible
        return Math.max(0, 0.05 + score) * criteria.sight() * criteria.penalty();
    }

    /** Proximité : 1 au contact (2 blocs), 0 à la portée. */
    public static double proximity(double distance, double range) {
        return Math.clamp(1 - (distance - 2) / Math.max(1, range - 2), 0, 1);
    }

    /** Vie manquante, accentuée (un joueur à 20 % compte beaucoup plus qu'un joueur à 60 %). */
    public static double finish(double healthRatio) {
        double missing = Math.clamp(1 - healthRatio, 0, 1);
        return missing * missing;
    }

    /**
     * Garder la cible actuelle ? Oui tant que la meilleure n'est pas sticky fois mieux notée (+ une marge), ou que le
     * dernier changement est trop récent. Une cible actuelle qui n'est plus valable (note négative) est toujours lâchée.
     */
    public static boolean keep(double currentScore, double bestScore, double sticky, long sinceSwitch, long switchMillis) {
        if (currentScore < 0) {
            return false;
        }
        if (sinceSwitch < switchMillis) {
            return true;
        }
        return bestScore < currentScore * sticky + 0.05;
    }

    /** Usages récents d'une capacité : chaque usage dans la fenêtre baisse son intérêt (variété). */
    public static double variety(int recentUses) {
        return Math.pow(0.7, Math.max(0, recentUses));
    }
}
