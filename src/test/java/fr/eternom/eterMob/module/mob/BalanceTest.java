package fr.eternom.eterMob.module.mob;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceTest {

    private static Ratios ratios() {
        return new Ratios(List.of(
                new Ratios.Level(1, 1, 1, 40, "", 0, 0, 0, 1, 1),
                new Ratios.Level(1.3, 1.15, 1.15, 30, "", 0.1, 0.05, 0.1, 1.25, 1.3),
                new Ratios.Level(1.6, 1.3, 1.3, 18, "", 0.2, 0.1, 0.2, 1.5, 1.7),
                new Ratios.Level(2, 1.5, 1.5, 9, "", 0.35, 0.2, 0.35, 2, 2.2),
                new Ratios.Level(2.6, 1.75, 1.75, 3, "", 0.5, 0.3, 0.5, 3, 3)), 0.01);
    }

    @Test
    void rollFollowsTheWeights() {
        Ratios ratios = ratios();
        assertEquals(1, ratios.roll(0, 0, 0, 5, 0.0));
        assertEquals(1, ratios.roll(0, 0, 0, 5, 0.39));
        assertEquals(2, ratios.roll(0, 0, 0, 5, 0.41));
        assertEquals(5, ratios.roll(0, 0, 0, 5, 0.999));
    }

    @Test
    void depthAndBonusFavourStrongRatios() {
        Ratios ratios = ratios();
        // Même tirage : plus profond ou avec bonus, on tombe plus haut
        assertTrue(ratios.roll(120, 0, 0, 5, 0.5) > ratios.roll(0, 0, 0, 5, 0.5));
        assertTrue(ratios.roll(0, 1, 0, 5, 0.5) > ratios.roll(0, 0, 0, 5, 0.5));
    }

    @Test
    void shiftAndCap() {
        Ratios ratios = ratios();
        assertEquals(2, ratios.roll(0, 0, 1, 5, 0.0));
        assertEquals(3, ratios.roll(0, 0, 0, 3, 0.999));
        assertEquals(5, ratios.roll(0, 0, 3, 5, 0.999));
    }

    @Test
    void armorPiercingMakesNetheriteLessAbsolute() {
        // Une frappe de 6 : la netherite tient bien plus longtemps sans perce-armure qu'avec 50 %
        int without = MobCommand.hits(6, 20, 12, 0);
        int with = MobCommand.hits(6, 20, 12, 0.5);
        assertTrue(with < without);
        assertEquals(4, MobCommand.hits(6, 0, 0, 0));
    }
}
