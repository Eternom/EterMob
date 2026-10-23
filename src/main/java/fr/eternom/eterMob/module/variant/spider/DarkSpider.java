package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Ténèbres : ses coups donnent l'Obscurité (l'effet du Warden) au joueur touché, 3 s au ratio 1, environ 6,6 s au
 * ratio 5 : on voit mal autour de soi. Sur un joueur déjà dans l'Obscurité, ses coups font 1,5 fois plus mal : le
 * premier aveugle, les suivants font mal. Profondeurs (sous Y = 0), forêts sombres, deep dark, marais.
 */
public class DarkSpider extends SpiderVariant {

    private static final double DARKNESS_TICKS = 60;
    private static final double DARK_DAMAGE = 1.5;

    public DarkSpider() {
        super("spider_dark", Rarity.RARE, "Caligo");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.below(entity, 0) || Spawns.biome(entity, Spawns.DARK, Spawns.SWAMPS);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() == mob.entity() && victim instanceof Player) {
            if (victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                event.setDamage(event.getDamage() * DARK_DAMAGE);
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, mob.scaleInt(DARKNESS_TICKS), 0));
        }
    }

    /** Fléau : fumée noire. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SQUID_INK, 3, 0.6, 0.4);
    }
}
