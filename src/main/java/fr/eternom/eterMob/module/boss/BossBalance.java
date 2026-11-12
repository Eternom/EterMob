package fr.eternom.eterMob.module.boss;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Rééquilibrage 2.16.0 : les boss et sous-boss étaient trop résistants (armure, résistance magique, résistance de base),
 * même pour un joueur bien équipé. Un serveur qui garde son ancien config.yml reçoit les nouvelles valeurs, mais
 * seulement là où il avait encore l'ancienne valeur par défaut : un réglage changé à la main n'est jamais touché.
 */
public final class BossBalance {

    /** chemin -> {ancienne valeur par défaut, nouvelle}. */
    private static final Map<String, double[]> CHANGES = Map.ofEntries(
            Map.entry("bosses.mortarius.toughness", new double[]{1.5, 1.2}),
            Map.entry("bosses.mortarius.armor", new double[]{110, 60}),
            Map.entry("bosses.mortarius.magic-resist", new double[]{80, 60}),
            Map.entry("bosses.orveth.toughness", new double[]{1.6, 1.25}),
            Map.entry("bosses.orveth.armor", new double[]{300, 130}),
            Map.entry("bosses.scribe.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.scribe.armor", new double[]{60, 40}),
            Map.entry("bosses.scribe.magic-resist", new double[]{160, 100}),
            Map.entry("bosses.fossoyeur.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.fossoyeur.armor", new double[]{220, 100}),
            Map.entry("bosses.gardienne.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.gardienne.armor", new double[]{120, 70}),
            Map.entry("bosses.gardienne.magic-resist", new double[]{120, 80}),
            Map.entry("bosses.contremaitre.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.contremaitre.armor", new double[]{200, 90}),
            Map.entry("bosses.dryade.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.dryade.armor", new double[]{50, 30}),
            Map.entry("bosses.dryade.magic-resist", new double[]{170, 100}),
            Map.entry("bosses.voleur.toughness", new double[]{1.2, 1.0}),
            Map.entry("bosses.voleur.armor", new double[]{110, 60}),
            Map.entry("bosses.voleur.magic-resist", new double[]{90, 60}));

    private BossBalance() {
    }

    /** À l'activation, avant de lire les boss. Enregistre config.yml s'il a changé. */
    public static void migrate(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int changed = 0;
        for (var change : CHANGES.entrySet()) {
            String path = change.getKey();
            if (config.isSet(path) && Math.abs(config.getDouble(path) - change.getValue()[0]) < 1e-9) {
                double value = change.getValue()[1];
                config.set(path, value == Math.rint(value) ? (Object) (int) value : (Object) value);
                changed++;
            }
        }
        if (changed > 0) {
            plugin.saveConfig();
            plugin.getLogger().info("Boss rééquilibrés : " + changed + " valeur(s) par défaut mises à jour dans config.yml");
        }
    }
}
