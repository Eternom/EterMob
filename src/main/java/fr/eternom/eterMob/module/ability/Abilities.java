package fr.eternom.eterMob.module.ability;

import fr.eternom.eterMob.module.ability.kind.Aura;
import fr.eternom.eterMob.module.ability.kind.Beam;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Burrow;
import fr.eternom.eterMob.module.ability.kind.Burst;
import fr.eternom.eterMob.module.ability.kind.Call;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Curse;
import fr.eternom.eterMob.module.ability.kind.Evade;
import fr.eternom.eterMob.module.ability.kind.Heal;
import fr.eternom.eterMob.module.ability.kind.Leap;
import fr.eternom.eterMob.module.ability.kind.Pillar;
import fr.eternom.eterMob.module.ability.kind.Pull;
import fr.eternom.eterMob.module.ability.kind.Slam;
import fr.eternom.eterMob.module.ability.kind.Strike;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ability.kind.Tombs;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Vortex;
import fr.eternom.eterMob.module.ability.kind.Wave;
import fr.eternom.eterMob.module.ability.kind.Zone;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Les sortes de capacités (kind) et leur fabrique. Un autre plugin peut en ajouter (register) avant le chargement des
 * variants ; une capacité codée sur mesure s'écrit en étendant Ability.
 */
public final class Abilities {

    private static final Map<String, Function<ConfigurationSection, Ability>> KINDS = new LinkedHashMap<>();

    static {
        register("strike", Strike::new);
        register("slam", Slam::new);
        register("leap", Leap::new);
        register("charge", Charge::new);
        register("volley", Volley::new);
        register("pull", Pull::new);
        register("curse", Curse::new);
        register("blink", Blink::new);
        register("pillar", Pillar::new);
        register("aura", Aura::new);
        register("heal", Heal::new);
        register("call", Call::new);
        register("evade", Evade::new);
        register("burst", Burst::new);
        register("summon", Summon::new);
        register("cleave", Cleave::new);
        register("wave", Wave::new);
        register("tombs", Tombs::new);
        register("vortex", Vortex::new);
        register("beam", Beam::new);
        register("burrow", Burrow::new);
        register("zone", Zone::new);
    }

    private Abilities() {
    }

    public static void register(String kind, Function<ConfigurationSection, Ability> factory) {
        KINDS.put(kind.toLowerCase(Locale.ROOT), factory);
    }

    public static Set<String> kinds() {
        return Collections.unmodifiableSet(KINDS.keySet());
    }

    /** Les capacités d'une liste de la config (abilities: [ {kind: slam, ...}, ... ]) ; les erreurs vont dans la console. */
    public static List<Ability> load(ConfigurationSection parent, String path, String owner, Logger logger) {
        List<Ability> abilities = new ArrayList<>();
        if (parent == null) {
            return abilities;
        }
        List<Map<?, ?>> entries = parent.getMapList(path);
        for (int i = 0; i < entries.size(); i++) {
            MemoryConfiguration section = new MemoryConfiguration();
            entries.get(i).forEach((key, value) -> section.set(String.valueOf(key), value));
            String kind = section.getString("kind", "").toLowerCase(Locale.ROOT);
            Function<ConfigurationSection, Ability> factory = KINDS.get(kind);
            if (factory == null) {
                logger.warning(owner + " : capacité " + (i + 1) + " de sorte inconnue « " + kind + " » (sortes : " + String.join(", ", KINDS.keySet()) + ")");
                continue;
            }
            try {
                Ability ability = factory.apply(section);
                if (abilities.stream().anyMatch(other -> other.id().equals(ability.id()))) {
                    logger.warning(owner + " : deux capacités s'appellent « " + ability.id() + " » (donner un id à l'une)");
                    continue;
                }
                abilities.add(ability);
            } catch (RuntimeException e) {
                logger.warning(owner + " : capacité " + kind + " illisible (" + e.getMessage() + ")");
            }
        }
        return abilities;
    }
}
