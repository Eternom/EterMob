package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Petite explosion qui laisse un nuage de poison (comme une potion persistante) : empoisonne ceux qui restent dedans,
 * pas ceux que l'explosion touche. Le nuage rétrécit jusqu'à moitié avant de disparaître. Rayon, durée du nuage et du
 * poison selon le ratio ; Poison II à partir du ratio 4. Le poison de Minecraft ne tue pas (il laisse un demi-cœur).
 */
public class PoisonCreeper extends CreeperVariant {

    /** Rayon du nuage (blocs) : 3 au ratio 1, environ 6,6 au ratio 5. */
    private static final double CLOUD_RADIUS = 3;
    /** Durée du nuage (ticks) : 6 s au ratio 1, environ 13 s au ratio 5. */
    private static final double CLOUD_TICKS = 120;
    /** Durée du poison donné à chaque passage dans le nuage (ticks). */
    private static final double POISON_TICKS = 60;
    /** Poison II à partir de ce ratio. */
    private static final int STRONG_RATIO = 4;
    private static final Color GREEN = Color.fromRGB(0x4E9331);

    public PoisonCreeper() {
        super("creeper_poison", Rarity.PEU_COMMUN, "Venenum", 2, 10);
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Laisse un nuage de poison (Poison II dès le ratio 4).",
                "Biomes : partout.");
    }

    /** Usure : son nuage agit dans la durée, lui est solide et un peu lent. */
    @Override
    public double health() {
        return 1.2;
    }

    @Override
    public double speed() {
        return 0.95;
    }

    /** Fléau : gouttes vertes qui tombent de lui, une brume verte pendant la mèche. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ITEM_SLIME, 2, 1.2, 0.3);
        if (fusing(mob)) {
            puff(mob, Particle.DUST, 15, 0.8, 0.6, new Particle.DustOptions(GREEN, 1.3f));
        }
    }

    @Override
    public void onExplode(ActiveMob mob, EntityExplodeEvent event) {
        float radius = (float) mob.scale(CLOUD_RADIUS);
        int duration = mob.scaleInt(CLOUD_TICKS);
        PotionEffect poison = new PotionEffect(PotionEffectType.POISON, mob.scaleInt(POISON_TICKS),
                mob.ratio() >= STRONG_RATIO ? 1 : 0);
        event.getLocation().getWorld().spawn(event.getLocation(), AreaEffectCloud.class, cloud -> {
            cloud.setRadius(radius);
            cloud.setDuration(duration);
            cloud.setRadiusPerTick(-radius / 2 / duration);
            // Ne rétrécit pas quand quelqu'un est touché : c'est une zone, pas une dose
            cloud.setRadiusOnUse(0);
            cloud.setDurationOnUse(0);
            cloud.setWaitTime(10);
            cloud.setReapplicationDelay(20);
            cloud.setColor(GREEN);
            cloud.addCustomEffect(poison, true);
        });
    }
}
