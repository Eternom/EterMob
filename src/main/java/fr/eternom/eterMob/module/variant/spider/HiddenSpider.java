package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cachée : invisible (on ne voit que ses yeux) tant qu'aucun joueur n'est à moins de 6 blocs, puis elle se révèle, et sa
 * morsure d'embuscade fait double dégâts. Elle se cache de nouveau quand plus personne n'est à moins de 16 blocs.
 * <ul>
 *   <li>Ratio 3 et plus : frappe et disparaît. Après une morsure d'embuscade, elle recule et redevient invisible 3 s
 *   (toutes les 10 s au plus) ; sa morsure suivante refait double dégâts.</li>
 *   <li>Ratio 5 : la morsure d'embuscade donne aussi Lenteur III 2 s (venin paralysant).</li>
 * </ul>
 * Forêts sombres, jardin pâle, forêts, neige, grottes.
 */
public class HiddenSpider extends SpiderVariant {

    private static final int CHECK_TICKS = 5;
    private static final double REVEAL = 6;
    private static final double HIDE = 16;
    private static final double AMBUSH_DAMAGE = 2;
    /** Frappe et disparaît : à partir de ce ratio, durée de l'invisibilité, délai minimum entre deux, recul. */
    private static final int VANISH_RATIO = 3;
    private static final long VANISH_MILLIS = 3_000;
    private static final long VANISH_COOLDOWN_MILLIS = 10_000;
    private static final double RETREAT = 0.7;
    /** Venin paralysant (Fléau) : Lenteur III, durée (ticks). */
    private static final int VENOM_TICKS = 40;

    /** Cachées, et révélées dont le premier coup n'est pas encore porté. */
    private final Set<Integer> hidden = new HashSet<>();
    private final Set<Integer> armed = new HashSet<>();
    /** Disparues après une morsure : jusqu'à quand, et prochaine disparition possible. */
    private final Map<Integer, Long> vanishedUntil = new HashMap<>();
    private final Map<Integer, Long> nextVanish = new HashMap<>();

    public HiddenSpider() {
        super("spider_hidden", Rarity.PEU_COMMUN, "Latens");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Invisible jusqu'à 6 blocs, embuscade à double dégâts.",
                "Frappe et disparaît dès le ratio 3.",
                "Biomes : grottes, forêts, forêts sombres, neige.");
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
        Long until = vanishedUntil.get(id);
        if (until != null) {
            if (System.currentTimeMillis() >= until) {
                vanishedUntil.remove(id);
                reveal(spider);
            }
            return;
        }
        if (hidden.contains(id)) {
            if (!spider.getLocation().getNearbyPlayers(REVEAL).isEmpty()) {
                hidden.remove(id);
                reveal(spider);
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

    private static void reveal(LivingEntity spider) {
        spider.setInvisible(false);
        spider.getWorld().spawnParticle(Particle.LARGE_SMOKE, spider.getLocation().add(0, 0.5, 0), 12, 0.5, 0.3, 0.5, 0.01);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        LivingEntity spider = mob.entity();
        int id = spider.getEntityId();
        if (event.getDamager() != spider || !armed.remove(id)) {
            return;
        }
        event.setDamage(event.getDamage() * AMBUSH_DAMAGE);
        if (!(victim instanceof Player)) {
            return;
        }
        if (mob.ratio() >= service().ratios().max()) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, VENOM_TICKS, 2));
        }
        long now = System.currentTimeMillis();
        if (mob.ratio() >= VANISH_RATIO && now >= nextVanish.getOrDefault(id, 0L)) {
            nextVanish.put(id, now + VANISH_COOLDOWN_MILLIS);
            vanish(spider, victim, id, now);
        }
    }

    /** Recule et redevient invisible 3 s ; sa prochaine morsure refait double dégâts. */
    private void vanish(LivingEntity spider, LivingEntity victim, int id, long now) {
        vanishedUntil.put(id, now + VANISH_MILLIS);
        armed.add(id);
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(HiddenSpider.class), () -> {
            if (!spider.isValid()) {
                return;
            }
            spider.getWorld().spawnParticle(Particle.LARGE_SMOKE, spider.getLocation().add(0, 0.5, 0), 12, 0.5, 0.3, 0.5, 0.01);
            spider.setInvisible(true);
            Vector back = spider.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (back.lengthSquared() > 0.01) {
                spider.setVelocity(back.normalize().multiply(RETREAT).setY(0.3));
            }
        });
    }

    @Override
    public void onRemove(ActiveMob mob) {
        int id = mob.entity().getEntityId();
        hidden.remove(id);
        armed.remove(id);
        vanishedUntil.remove(id);
        nextVanish.remove(id);
    }
}
