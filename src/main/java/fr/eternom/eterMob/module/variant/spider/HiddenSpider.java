package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Cachée : invisible (on ne voit que ses yeux) tant qu'aucun joueur n'est à moins de 6 blocs, puis elle se révèle, et
 * son premier coup fait double dégâts. Elle se cache de nouveau quand plus personne n'est à moins de 16 blocs.
 * Forêts sombres, jardin pâle, forêts, neige, grottes.
 */
public class HiddenSpider extends SpiderVariant {

    private static final int CHECK_TICKS = 5;
    private static final double REVEAL = 6;
    private static final double HIDE = 16;
    private static final double AMBUSH_DAMAGE = 2;

    /** Cachées, et révélées dont le premier coup n'est pas encore porté. */
    private final Set<Integer> hidden = new HashSet<>();
    private final Set<Integer> armed = new HashSet<>();

    public HiddenSpider() {
        super("spider_hidden", Rarity.PEU_COMMUN, "Latens");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.DARK, Spawns.FORESTS, Spawns.COLD);
    }

    @Override
    public void onLoad(ActiveMob mob) {
        hide(mob);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        LivingEntity spider = mob.entity();
        int id = spider.getEntityId();
        if (hidden.contains(id)) {
            if (!spider.getLocation().getNearbyPlayers(REVEAL).isEmpty()) {
                hidden.remove(id);
                spider.setInvisible(false);
                spider.getWorld().spawnParticle(Particle.LARGE_SMOKE, spider.getLocation().add(0, 0.5, 0), 12, 0.5, 0.3, 0.5, 0.01);
            }
        } else if (spider.getLocation().getNearbyPlayers(HIDE).isEmpty()) {
            hide(mob);
        }
    }

    private void hide(ActiveMob mob) {
        int id = mob.entity().getEntityId();
        mob.entity().setInvisible(true);
        hidden.add(id);
        armed.add(id);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() == mob.entity() && armed.remove(mob.entity().getEntityId())) {
            event.setDamage(event.getDamage() * AMBUSH_DAMAGE);
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        hidden.remove(mob.entity().getEntityId());
        armed.remove(mob.entity().getEntityId());
    }
}
