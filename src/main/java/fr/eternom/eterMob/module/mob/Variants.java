package fr.eternom.eterMob.module.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Tous les variants et meneurs : lus dans plugins/EterMob/variants/*.yml (section variants:), sauf les fichiers qui
 * commencent par « _ » (exemples). Identifiant : [a-z0-9_-], jamais changé une fois en jeu (il est écrit sur le mob).
 */
public class Variants {

    private final Map<String, MobVariant> byId = new LinkedHashMap<>();
    private final Set<String> disabled;

    public Variants(Set<String> disabled) {
        this.disabled = disabled;
    }

    /** Relit le dossier ; renvoie le nombre de variants chargés. */
    public int load(File folder, Logger logger) {
        byId.clear();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") && !name.startsWith("_"));
        if (files == null) {
            return 0;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("variants");
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                String id = key.toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]+")) {
                    logger.warning(file.getName() + " : identifiant de variant invalide « " + key + " » ([a-z0-9_-])");
                    continue;
                }
                if (byId.containsKey(id)) {
                    logger.warning(file.getName() + " : le variant « " + id + " » existe déjà (ignoré)");
                    continue;
                }
                ConfigurationSection variant = section.getConfigurationSection(key);
                MobVariant loaded = variant == null ? null : MobVariant.load(id, variant, logger);
                if (loaded != null) {
                    byId.put(id, loaded);
                }
            }
        }
        return byId.size();
    }

    public MobVariant get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<MobVariant> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Désactivé (disabled-variants) : n'apparaît plus de lui-même (commande et invocation restent possibles). */
    public boolean disabled(String id) {
        return disabled.contains(id);
    }
}
