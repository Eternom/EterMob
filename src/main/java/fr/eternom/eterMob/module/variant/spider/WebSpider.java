package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import fr.eternom.eterMob.module.mob.TempBlocks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Spider;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tisserand. Ses toiles ne se posent que dans l'air, ne lâchent rien si on les casse et disparaissent à l'arrêt du
 * serveur.
 * <ul>
 *   <li>Tous les ratios : à chaque morsure, une toile apparaît aux pieds du joueur, 2 s (3 s au ratio 5).</li>
 *   <li>Ratio 3 et plus : il crache sa toile. Entre 4 et 10 blocs, toutes les 6 s, il lance une boule de toile vers
 *   le joueur ; là où elle tombe, une toile apparaît 3 s.</li>
 *   <li>Ratio 5 : la boule fait une toile en croix (5 toiles).</li>
 * </ul>
 * Forêts, jungles, forêts sombres, marais, grottes.
 */
public class WebSpider extends SpiderVariant {

    private static final long WEB_TICKS = 40;
    /** Ticks de toile en plus par ratio au-dessus de 1. */
    private static final long WEB_TICKS_PER_RATIO = 5;

    private static final int CHECK_TICKS = 10;
    private static final int SPIT_RATIO = 3;
    private static final double MIN_DISTANCE = 4;
    private static final double MAX_DISTANCE = 10;
    private static final long SPIT_COOLDOWN_MILLIS = 6_000;
    private static final long SPIT_WEB_TICKS = 60;
    /** Vitesse de la boule (blocs par tick) et sa chute par tick² (boule de neige). */
    private static final double SPIT_SPEED = 1.2;
    private static final double GRAVITY = 0.03;
    private static final BlockFace[] CROSS = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final Map<Integer, Long> nextSpit = new HashMap<>();
    /** Boules de toile en vol, et celles qui font une croix (Fléau). */
    private final Set<UUID> spits = new HashSet<>();
    private final Set<UUID> nets = new HashSet<>();

    public WebSpider() {
        super("spider_web", Rarity.PEU_COMMUN, "Textor");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Toile aux pieds à chaque morsure.",
                "Crache des toiles dès le ratio 3, croix au Fléau.",
                "Biomes : grottes, forêts, jungles, marais.");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.FORESTS, Spawns.JUNGLES, Spawns.DARK, Spawns.SWAMPS);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() == mob.entity() && victim instanceof Player) {
            TempBlocks.place(victim.getLocation().getBlock(), Material.COBWEB, WEB_TICKS + (mob.ratio() - 1) * WEB_TICKS_PER_RATIO);
        }
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        if (mob.ratio() < SPIT_RATIO) {
            return;
        }
        Spider spider = (Spider) mob.entity();
        if (!(spider.getTarget() instanceof Player target) || target.getWorld() != spider.getWorld()) {
            return;
        }
        double distance = target.getLocation().distance(spider.getLocation());
        long now = System.currentTimeMillis();
        int id = spider.getEntityId();
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || now < nextSpit.getOrDefault(id, 0L) || !spider.hasLineOfSight(target)) {
            return;
        }
        nextSpit.put(id, now + SPIT_COOLDOWN_MILLIS);
        spit(mob, spider, target);
    }

    /** Boule de toile vers les pieds du joueur, en compensant la chute. */
    private void spit(ActiveMob mob, Spider spider, Player target) {
        Location from = spider.getEyeLocation();
        Vector delta = target.getLocation().add(0, 0.3, 0).toVector().subtract(from.toVector());
        double time = Math.hypot(delta.getX(), delta.getZ()) / SPIT_SPEED;
        delta.setY(delta.getY() + 0.5 * GRAVITY * time * time);
        boolean net = mob.ratio() >= service().ratios().max();
        Snowball ball = spider.launchProjectile(Snowball.class, delta.normalize().multiply(SPIT_SPEED),
                snowball -> snowball.setItem(new ItemStack(Material.COBWEB)));
        spits.add(ball.getUniqueId());
        if (net) {
            nets.add(ball.getUniqueId());
        }
        spider.getWorld().playSound(from, Sound.ENTITY_LLAMA_SPIT, 1f, 1.4f);
    }

    /** La boule tombe : une toile là où elle touche (une croix pour le Fléau). */
    @Override
    public void onProjectileHit(ActiveMob mob, ProjectileHitEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (!spits.remove(id)) {
            return;
        }
        boolean net = nets.remove(id);
        Block center;
        if (event.getHitEntity() != null) {
            center = event.getHitEntity().getLocation().getBlock();
        } else if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            center = event.getHitBlock().getRelative(event.getHitBlockFace());
        } else {
            center = event.getEntity().getLocation().getBlock();
        }
        center.getWorld().spawnParticle(Particle.WHITE_ASH, center.getLocation().add(0.5, 0.5, 0.5), 30, 0.6, 0.4, 0.6, 0.02);
        TempBlocks.place(center, Material.COBWEB, SPIT_WEB_TICKS);
        if (net) {
            for (BlockFace face : CROSS) {
                TempBlocks.place(center.getRelative(face), Material.COBWEB, SPIT_WEB_TICKS);
            }
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextSpit.remove(mob.entity().getEntityId());
    }

    /** Fléau : fils de toile autour d'elle. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.WHITE_ASH, 8, 0.6, 0.6);
    }
}
