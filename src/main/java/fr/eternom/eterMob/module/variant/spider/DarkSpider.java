package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Particle;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Ténèbres : ses coups donnent l'Obscurité (l'effet du Warden) au joueur touché, 3 s au ratio 1, environ 6,6 s au
 * ratio 5 : on voit mal autour de soi. Profondeurs (sous Y = 0), deep dark, marais.
 */
public class DarkSpider extends SpiderVariant {

    private static final double DARKNESS_TICKS = 60;
    private static final Set<Biome> DEEP_DARK = Set.of(Biome.DEEP_DARK);

    public DarkSpider() {
        super("spider_dark", Rarity.RARE, "Caligo");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.below(entity, 0) || Spawns.biome(entity, DEEP_DARK, Spawns.SWAMPS);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() == mob.entity() && victim instanceof Player) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, mob.scaleInt(DARKNESS_TICKS), 0));
        }
    }

    /** Fléau : fumée noire. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SQUID_INK, 3, 0.6, 0.4);
    }
}
