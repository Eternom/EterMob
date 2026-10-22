package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Petite explosion, mais gèle tout ce qui l'entoure : givre à l'écran et lenteur, rayon et durée selon le ratio. */
public class FrostCreeper extends MobVariant {

    private static final double RADIUS = 2;
    private static final double FREEZE_RADIUS = 5;
    /** Durée de la lenteur (ticks). */
    private static final double FREEZE_TICKS = 60;

    public FrostCreeper() {
        super("creeper_frost", EntityType.CREEPER, Rarity.PEU_COMMUN, "Creeper givrant");
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        ((Creeper) mob.entity()).setExplosionRadius(mob.scaleInt(RADIUS));
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
