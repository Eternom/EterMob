package fr.eternom.eterMob.module.mob;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.boss.ConfiguredBoss;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les modèles livrés (variants/_exemple.yml, bosses/_exemple.yml) se lisent sans un seul avertissement. */
class ExamplesTest {

    private static YamlConfiguration resource(String path) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ExamplesTest.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8));
    }

    private static Logger capture(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    @Test
    void exampleVariantsLoad() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection variants = resource("variants/_exemple.yml").getConfigurationSection("variants");
        assertNotNull(variants);
        for (String id : variants.getKeys(false)) {
            MobVariant variant = MobVariant.load(id, variants.getConfigurationSection(id), capture(warnings));
            assertNotNull(variant, id);
            assertTrue(variant.value() > 0);
        }
        assertEquals(List.of(), warnings);
        MobVariant captain = MobVariant.load("capitaine-os", variants.getConfigurationSection("capitaine-os"), capture(warnings));
        assertEquals(Tier.LEADER, captain.tier());
        assertEquals(4, captain.abilities().size());
        assertEquals(2, captain.followersMin());
    }

    @Test
    void exampleBossLoads() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection boss = resource("bosses/_exemple.yml").getConfigurationSection("bosses.roi-des-os");
        ConfiguredBoss loaded = ConfiguredBoss.load("roi-des-os", boss, capture(warnings));
        assertNotNull(loaded);
        assertEquals(List.of(), warnings);
        assertEquals(2, loaded.phaseThresholds().length);
        assertEquals(0.6, loaded.phaseThresholds()[0]);
    }

    @Test
    void abilityTriggersAndDefaults() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection variants = resource("variants/_exemple.yml").getConfigurationSection("variants");
        MobVariant guard = MobVariant.load("squelette-garde", variants.getConfigurationSection("squelette-garde"), capture(warnings));
        Ability strike = guard.abilities().getFirst();
        assertEquals(Ability.Trigger.ON_HIT, strike.trigger());
        assertEquals(0.25, strike.chance());
        assertTrue(guard.abilities().get(1).inRange(8, true));
        assertTrue(!guard.abilities().get(1).inRange(2, true));
    }
}
