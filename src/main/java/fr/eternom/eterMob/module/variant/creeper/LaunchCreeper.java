package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Doit venir tout près (2 blocs) et explose sur une petite zone, avec peu de dégâts, mais envoie en l'air tout ce qu'il
 * touche : la hauteur du saut, et donc la chute, grandit avec le ratio (environ 4 blocs au ratio 1, 17 au ratio 5).
 */
public class LaunchCreeper extends CreeperVariant {

    /** Vitesse verticale donnée à chaque cible touchée (blocs par tick). */
    private static final double LAUNCH = 0.9;

    public LaunchCreeper() {
        super("creeper_launch", Rarity.PEU_COMMUN, "Volatus", 2, 10, 2);
    }

    @Override
    public List<String> lore() {
        return List.of(
                "S'allume à 2 blocs, envoie en l'air (4 à 17 blocs).",
                "Résistant et rapide.",
                "Biomes : partout.");
    }

    /** Contact : il doit coller sa cible, donc il est résistant et plus rapide. */
    @Override
    public double health() {
        return 1.3;
    }

    @Override
    public double speed() {
        return 1.2;
    }

    /** Fléau : petites bourrasques à ses pieds, une rafale pendant la mèche. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, fusing(mob) ? Particle.GUST : Particle.SMALL_GUST, 1, 0.2, 0.3);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        double launch = mob.scale(LAUNCH);
        // Au tick suivant : le recul de l'explosion est appliqué après les dégâts et écraserait la vitesse donnée ici
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(LaunchCreeper.class), () -> {
            if (victim.isValid()) {
                Vector velocity = victim.getVelocity();
                victim.setVelocity(new Vector(velocity.getX(), Math.max(velocity.getY(), launch), velocity.getZ()));
            }
        });
    }
}
