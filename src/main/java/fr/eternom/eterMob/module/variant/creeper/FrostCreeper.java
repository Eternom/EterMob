package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Petite explosion aux faibles dégâts, mais gèle tout ce qui l'entoure : givre à l'écran et lenteur, rayon et durée selon le ratio.
 * N'apparaît que dans le froid : là où il neige au lieu de pleuvoir (biomes de neige et de glace, haut des montagnes).
 */
public class FrostCreeper extends CreeperVariant {

    private static final double FREEZE_RADIUS = 5;
    /** Durée de la lenteur (ticks). */
    private static final double FREEZE_TICKS = 60;
    /** Sous cette température (biome et altitude), Minecraft fait tomber de la neige. */
    private static final double SNOW_TEMPERATURE = 0.15;

    public FrostCreeper() {
        super("creeper_frost", Rarity.PEU_COMMUN, "Gelidus", 2, 13);
    }

    /** Contrôle : un peu plus solide et plus lent, il compte sur le gel pour que sa cible ne s'échappe pas. */
    @Override
    public double health() {
        return 1.15;
    }

    @Override
    public double speed() {
        return 0.9;
    }

    /** Fléau : flocons autour de lui, une bourrasque de neige pendant la mèche. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SNOWFLAKE, fusing(mob) ? 20 : 4, 1, fusing(mob) ? 1 : 0.4);
    }

    @Override
    public boolean canSpawn(LivingEntity entity) {
        return entity.getLocation().getBlock().getTemperature() < SNOW_TEMPERATURE;
    }

    @Override
    public void onExplode(ActiveMob mob, EntityExplodeEvent event) {
        int duration = mob.scaleInt(FREEZE_TICKS);
        for (LivingEntity target : event.getLocation().getNearbyLivingEntities(mob.scale(FREEZE_RADIUS))) {
            if (target.equals(mob.entity())) {
                continue;
            }
            target.setFreezeTicks(target.getMaxFreezeTicks());
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 2));
        }
    }
}
