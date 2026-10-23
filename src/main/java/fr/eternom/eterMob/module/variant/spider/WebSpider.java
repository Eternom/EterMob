package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import fr.eternom.eterMob.module.mob.TempBlocks;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tisserand : à chaque coup, une toile apparaît aux pieds du joueur touché (seulement dans l'air) et disparaît après
 * 2 s, 3 s au ratio 5. La toile ne lâche rien si on la casse. Forêts, jungles, grottes.
 */
public class WebSpider extends SpiderVariant {

    private static final long WEB_TICKS = 40;
    /** Ticks de toile en plus par ratio au-dessus de 1. */
    private static final long WEB_TICKS_PER_RATIO = 5;

    public WebSpider() {
        super("spider_web", Rarity.PEU_COMMUN, "Textor");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.FORESTS, Spawns.JUNGLES);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() == mob.entity() && victim instanceof Player) {
            TempBlocks.place(victim.getLocation().getBlock(), Material.COBWEB, WEB_TICKS + (mob.ratio() - 1) * WEB_TICKS_PER_RATIO);
        }
    }

    /** Fléau : fils de toile autour d'elle. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.WHITE_ASH, 8, 0.6, 0.6);
    }
}
