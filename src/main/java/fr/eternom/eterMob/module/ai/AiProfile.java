package fr.eternom.eterMob.module.ai;

import fr.eternom.eterMob.module.mob.Tier;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Comment un monstre choisit sa cible et ses capacités (section ai d'un variant, d'un meneur ou d'un boss ; valeurs par
 * défaut selon le rang). Note d'un joueur = somme des poids × ses critères (0 à 1) :
 * <pre>
 *   threat     menace qu'il a générée (coups, tirs, soins des siens)      proximity  proche du monstre
 *   finish     vie basse (l'achever)                                      ranged     il tire (arc, arbalète, trident)
 *   healer     rôle soigneur (classes)                                    tank       rôle tank (négatif : l'éviter)
 * </pre>
 * sticky : il ne change de cible que pour une note sticky fois meilleure, et pas avant switch-seconds ;
 * range : distance où il cherche ses cibles ; global-cooldown : secondes entre deux capacités ; threshold : note
 * minimale d'une capacité pour la lancer ; anti-cheese : se téléporte près d'une cible hors d'atteinte (boss).
 */
public record AiProfile(double range, double threat, double proximity, double finish, double ranged, double healer, double tank,
                        double sticky, long switchMillis, double globalCooldown, double threshold, boolean antiCheese) {

    public static AiProfile defaults(Tier tier) {
        return switch (tier) {
            case VARIANT -> new AiProfile(20, 1, 0.8, 0.5, 0.2, 0.3, 0, 1.3, 1500, 3, 0.3, false);
            case LEADER -> new AiProfile(28, 1, 0.5, 0.6, 0.4, 0.6, -0.2, 1.25, 2000, 2.5, 0.3, false);
            case BOSS -> new AiProfile(48, 1.2, 0.3, 0.5, 0.5, 0.8, 0, 1.2, 3000, 2, 0.25, true);
        };
    }

    public static AiProfile load(ConfigurationSection section, Tier tier) {
        AiProfile base = defaults(tier);
        if (section == null) {
            return base;
        }
        ConfigurationSection focus = section.getConfigurationSection("focus");
        return new AiProfile(
                Math.clamp(section.getDouble("range", base.range), 4, 96),
                weight(focus, "threat", base.threat),
                weight(focus, "proximity", base.proximity),
                weight(focus, "finish", base.finish),
                weight(focus, "ranged", base.ranged),
                weight(focus, "healer", base.healer),
                weight(focus, "tank", base.tank),
                Math.clamp(section.getDouble("sticky", base.sticky), 1, 3),
                Math.round(Math.clamp(section.getDouble("switch-seconds", base.switchMillis / 1000.0), 0, 30) * 1000),
                Math.clamp(section.getDouble("global-cooldown", base.globalCooldown), 0, 30),
                Math.clamp(section.getDouble("threshold", base.threshold), 0, 2),
                section.getBoolean("anti-cheese", base.antiCheese));
    }

    private static double weight(ConfigurationSection focus, String key, double fallback) {
        return focus == null ? fallback : Math.clamp(focus.getDouble(key, fallback), -3, 3);
    }
}
