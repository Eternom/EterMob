package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meute : apparaît entourée d'araignées venimeuses (2 au ratio 1, 4 au ratio 5) qui ne lâchent rien, ne sont jamais
 * sauvegardées et meurent avec elle. Jungles et marais.
 */
public class PackSpider extends SpiderVariant {

    private static final double PACK = 2;

    private final Map<Integer, List<LivingEntity>> packs = new HashMap<>();

    public PackSpider() {
        super("spider_pack", Rarity.RARE, "Grex");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.JUNGLES, Spawns.SWAMPS);
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        // Au tick suivant : la meneuse n'est pas encore dans le monde
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(PackSpider.class), () -> {
            LivingEntity leader = mob.entity();
            if (!leader.isValid()) {
                return;
            }
            List<LivingEntity> pack = packs.computeIfAbsent(leader.getEntityId(), id -> new ArrayList<>());
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < mob.scaleInt(PACK); i++) {
                Location spot = leader.getLocation().add(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
                pack.add(Minions.spawn(spot, CaveSpider.class, spider -> { }));
            }
        });
    }

    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        List<LivingEntity> pack = packs.remove(mob.entity().getEntityId());
        if (pack == null) {
            return;
        }
        for (LivingEntity spider : pack) {
            if (spider.isValid()) {
                spider.getWorld().spawnParticle(Particle.POOF, spider.getLocation(), 8, 0.3, 0.2, 0.3, 0.02);
                spider.remove();
            }
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        packs.remove(mob.entity().getEntityId());
    }

    /** Fléau : fumée légère. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SMOKE, 3, 0.6, 0.4);
    }
}
