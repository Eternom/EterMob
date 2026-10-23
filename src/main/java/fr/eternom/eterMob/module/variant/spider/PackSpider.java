package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meute, rare. Elle est entourée d'araignées venimeuses (2 au ratio 1, 3 aux ratios 2 et 3, 4 aux ratios 4 et 5) qui ne
 * lâchent rien, ne sont jamais sauvegardées et meurent avec elle ; la meute se reforme quand elle est rechargée.
 * <ul>
 *   <li>Ratio 3 et plus : la ponte. Toutes les 10 s, elle remplace une araignée morte de sa meute : un cocon apparaît à
 *   côté d'elle et éclot 2 s plus tard.</li>
 *   <li>Ratio 5 : la reine protégée. Tant qu'il lui reste au moins une araignée, elle prend 40 % de dégâts en moins.</li>
 * </ul>
 * Jungles, marais, forêts.
 */
public class PackSpider extends SpiderVariant {

    private static final double PACK = 2;
    private static final int CHECK_TICKS = 20;
    private static final int BROOD_RATIO = 3;
    private static final long BROOD_COOLDOWN_MILLIS = 10_000;
    private static final int HATCH_TICKS = 40;
    private static final double QUEEN_DAMAGE = 0.6;

    private final Map<Integer, List<LivingEntity>> packs = new HashMap<>();
    private final Map<Integer, Long> nextBrood = new HashMap<>();

    public PackSpider() {
        super("spider_pack", Rarity.RARE, "Grex");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Meute d'araignées venimeuses.",
                "Ponte dès le ratio 3, reine protégée au Fléau.",
                "Biomes : jungles, marais, forêts.");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.JUNGLES, Spawns.SWAMPS, Spawns.FORESTS);
    }

    /** À l'apparition comme au rechargement (la meute n'est jamais sauvegardée) : au tick suivant, hors chargement. */
    @Override
    public void onLoad(ActiveMob mob) {
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(PackSpider.class), () -> {
            LivingEntity leader = mob.entity();
            if (!leader.isValid() || packs.containsKey(leader.getEntityId())) {
                return;
            }
            List<LivingEntity> pack = new ArrayList<>();
            packs.put(leader.getEntityId(), pack);
            for (int i = 0; i < mob.scaleInt(PACK); i++) {
                pack.add(spawnSpider(around(leader.getLocation())));
            }
        });
    }

    private static LivingEntity spawnSpider(Location at) {
        return Minions.spawn(at, CaveSpider.class, spider -> { });
    }

    private static Location around(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return center.clone().add(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    /** La ponte : remplace une araignée morte, toutes les 10 s au plus. */
    @Override
    public void tick(ActiveMob mob) {
        Spider leader = (Spider) mob.entity();
        int id = leader.getEntityId();
        List<LivingEntity> pack = packs.get(id);
        if (pack == null) {
            return;
        }
        pack.removeIf(spider -> !spider.isValid() || spider.isDead());
        long now = System.currentTimeMillis();
        if (mob.ratio() < BROOD_RATIO || pack.size() >= mob.scaleInt(PACK) || now < nextBrood.getOrDefault(id, 0L)) {
            return;
        }
        nextBrood.put(id, now + BROOD_COOLDOWN_MILLIS);
        Location spot = around(leader.getLocation());
        ItemDisplay cocoon = spot.getWorld().spawn(spot.clone().add(0, 0.3, 0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.COBWEB));
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
        });
        spot.getWorld().playSound(spot, Sound.BLOCK_SLIME_BLOCK_PLACE, 0.8f, 1.4f);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(PackSpider.class), () -> {
            cocoon.remove();
            List<LivingEntity> current = packs.get(id);
            if (!leader.isValid() || current == null) {
                return;
            }
            spot.getWorld().spawnParticle(Particle.WHITE_ASH, spot.clone().add(0, 0.3, 0), 25, 0.3, 0.3, 0.3, 0.02);
            spot.getWorld().playSound(spot, Sound.ENTITY_SPIDER_AMBIENT, 1f, 1.5f);
            current.add(spawnSpider(spot));
        }, HATCH_TICKS);
    }

    /** La reine protégée (Fléau) : 40 % de dégâts en moins tant qu'il lui reste une araignée. */
    @Override
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
        if (mob.ratio() < service().ratios().max()) {
            return;
        }
        List<LivingEntity> pack = packs.get(mob.entity().getEntityId());
        if (pack != null && pack.stream().anyMatch(spider -> spider.isValid() && !spider.isDead())) {
            event.setDamage(event.getDamage() * QUEEN_DAMAGE);
            mob.entity().getWorld().spawnParticle(Particle.ENCHANTED_HIT, mob.entity().getLocation().add(0, 0.5, 0), 6,
                    0.4, 0.2, 0.4, 0.05);
        }
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
        nextBrood.remove(mob.entity().getEntityId());
    }

    /** Fléau : fumée légère. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SMOKE, 3, 0.6, 0.4);
    }
}
