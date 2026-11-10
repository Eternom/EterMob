package fr.eternom.eterMob.module.ai;

import fr.eternom.eterMob.module.mob.Tier;
import org.bukkit.entity.LivingEntity;

/**
 * Ce qui lance des capacités et réfléchit : un variant (ActiveMob) ou un boss. Ses capacités lisent sa puissance (ratio,
 * rage) et son perce-armure ; son IA (Brain) choisit sa cible et ses capacités.
 */
public interface Caster {

    LivingEntity entity();

    Tier tier();

    /** Multiplicateur des dégâts et de la puissance des états de ses capacités. */
    double abilityPower();

    /** Part de l'armure des joueurs que ses coups ignorent (0 à 1). */
    double piercing();

    Brain brain();

    /** Nom affiché (messages, inspection). */
    String displayName();

    /**
     * Mob de Minecraft avec ratio (natural-ratios) : il garde son caractère (neutre tant qu'on ne l'attaque pas, un
     * zombie qui chasse un villageois continue) ; l'IA ne fait que mieux choisir parmi les joueurs qui le provoquent.
     */
    default boolean respectsVanillaAggro() {
        return false;
    }
}
