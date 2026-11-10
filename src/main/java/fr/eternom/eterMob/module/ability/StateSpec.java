package fr.eternom.eterMob.module.ability;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un état d'EterLib posé par une capacité : « bleed 1 4 » (état, puissance, secondes) ou « stun 1 1.5 0.5 » (avec une
 * chance), ou en carte { state, power, seconds, chance }. La puissance suit le ratio du monstre (dégâts par seconde,
 * part de ralentissement...) ; la durée jamais (un contrôle plus long ne serait plus jouable).
 */
public record StateSpec(String state, double power, double seconds, double chance) {

    public static List<StateSpec> list(ConfigurationSection section, String path) {
        List<StateSpec> specs = new ArrayList<>();
        if (section == null) {
            return specs;
        }
        for (Object raw : section.getList(path, List.of())) {
            StateSpec spec = parse(raw);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    static StateSpec parse(Object raw) {
        if (raw instanceof String text) {
            String[] parts = text.trim().split("\\s+");
            if (parts.length < 3) {
                return null;
            }
            return new StateSpec(parts[0].toLowerCase(Locale.ROOT), number(parts[1], 1), number(parts[2], 1),
                    parts.length > 3 ? number(parts[3], 1) : 1);
        }
        if (raw instanceof Map<?, ?> map && map.get("state") != null) {
            return new StateSpec(String.valueOf(map.get("state")).toLowerCase(Locale.ROOT), number(map.get("power"), 1),
                    number(map.get("seconds"), 1), number(map.get("chance"), 1));
        }
        return null;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value).replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
