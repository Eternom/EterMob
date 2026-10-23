package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chasseuse de l'ombre : la lumière est l'enjeu du combat.
 * <ul>
 *   <li>Ses coups éteignent les lumières (torches, lanternes, glowstone...) à moins de 8 blocs du joueur touché, pour lui
 *   seul : de faux blocs envoyés à son jeu, qui recalcule son éclairage. Le monde ne change pas, les autres joueurs voient
 *   la lumière. 6 s au ratio 1, environ 13 s au ratio 5 ; les vrais blocs reviennent à la fin. Une lumière posée après
 *   coup est un vrai bloc : elle éclaire.</li>
 *   <li>Dans l'ombre (lumière 4 ou moins là où elle est, ou près d'un joueur dont elle a éteint les lumières) : invisible
 *   (on ne voit que ses yeux), +20 % de vitesse, +50 % de dégâts. Dans la lumière : visible et ralentie.</li>
 *   <li>Ses coups donnent aussi l'Obscurité (l'effet du Warden), 3 s au ratio 1, environ 6,6 s au ratio 5.</li>
 * </ul>
 * Profondeurs (sous Y = 0), forêts sombres, deep dark, marais.
 */
public class DarkSpider extends SpiderVariant {

    private static final double DARKNESS_TICKS = 60;
    /** Lumières éteintes : rayon autour du joueur (blocs) et durée de base (ticks), multipliée par le ratio. */
    private static final int BLACKOUT_RADIUS = 8;
    private static final double BLACKOUT_TICKS = 120;
    /** Ombre : lumière maximale, bonus de dégâts, vitesse dans l'ombre et dans la lumière. */
    private static final int SHADOW_LIGHT = 4;
    private static final double SHADOW_DAMAGE = 1.5;
    private static final double SHADOW_SPEED = 0.2;
    private static final double LIGHT_SPEED = -0.15;
    private static final int CHECK_TICKS = 10;

    /** Lumière éteinte pour un joueur : les blocs cachés à son jeu et la tâche qui les lui rendra. */
    private record Blackout(World world, Set<Location> blocks, BukkitTask restore) {
    }

    private final Map<UUID, Blackout> blackouts = new HashMap<>();
    /** Araignées dans l'ombre (par identifiant d'entité). */
    private final Set<Integer> shadowed = new HashSet<>();
    private NamespacedKey speedKey;

    public DarkSpider() {
        super("spider_dark", Rarity.RARE, "Caligo");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.below(entity, 0) || Spawns.biome(entity, Spawns.DARK, Spawns.SWAMPS);
    }

    private NamespacedKey speedKey() {
        if (speedKey == null) {
            speedKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(DarkSpider.class), "shadow");
        }
        return speedKey;
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() != mob.entity() || !(victim instanceof Player player)) {
            return;
        }
        if (shadowed.contains(mob.entity().getEntityId())) {
            event.setDamage(event.getDamage() * SHADOW_DAMAGE);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, mob.scaleInt(DARKNESS_TICKS), 0));
        blackout(player, mob.scaleInt(BLACKOUT_TICKS));
    }

    /** Éteint les lumières autour du joueur, pour lui seul ; un nouveau coup prolonge (les blocs sont recherchés à neuf). */
    private void blackout(Player player, int ticks) {
        restore(player.getUniqueId());
        World world = player.getWorld();
        Block center = player.getLocation().getBlock();
        Map<Location, BlockData> fake = new HashMap<>();
        BlockData stone = Material.BLACKSTONE.createBlockData();
        BlockData air = Material.AIR.createBlockData();
        for (int x = -BLACKOUT_RADIUS; x <= BLACKOUT_RADIUS; x++) {
            for (int y = -BLACKOUT_RADIUS; y <= BLACKOUT_RADIUS; y++) {
                for (int z = -BLACKOUT_RADIUS; z <= BLACKOUT_RADIUS; z++) {
                    Block block = center.getRelative(x, y, z);
                    Material type = block.getType();
                    if (type.isAir() || block.isLiquid() || type == Material.FIRE || type == Material.SOUL_FIRE) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data.getLightEmission() > 0) {
                        // Bloc plein (glowstone...) : une pierre noire, pour ne pas ouvrir de trou ; sinon (torche...) : rien
                        fake.put(block.getLocation(), data.isOccluding() ? stone : air);
                    }
                }
            }
        }
        if (fake.isEmpty()) {
            return;
        }
        player.sendMultiBlockChange(fake);
        player.playSound(player.getLocation(), Sound.BLOCK_CANDLE_EXTINGUISH, 1f, 0.6f);
        UUID id = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(DarkSpider.class), () -> restore(id), ticks);
        blackouts.put(id, new Blackout(world, fake.keySet(), task));
    }

    /** Rend au joueur les vrais blocs (ceux d'aujourd'hui : posés ou cassés entre-temps compris). */
    private void restore(UUID id) {
        Blackout blackout = blackouts.remove(id);
        if (blackout == null) {
            return;
        }
        blackout.restore().cancel();
        Player player = Bukkit.getPlayer(id);
        if (player == null || player.getWorld() != blackout.world()) {
            return;
        }
        Map<Location, BlockData> real = new HashMap<>();
        for (Location location : blackout.blocks()) {
            real.put(location, location.getBlock().getBlockData());
        }
        player.sendMultiBlockChange(real);
    }

    @Override
    public void onLoad(ActiveMob mob) {
        setShadow(mob, false);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        LivingEntity spider = mob.entity();
        boolean dark = spider.getLocation().getBlock().getLightLevel() <= SHADOW_LIGHT || nearBlackout(spider);
        if (dark != shadowed.contains(spider.getEntityId())) {
            setShadow(mob, dark);
        }
    }

    /** Près d'un joueur dont elle a éteint les lumières : le serveur voit encore la lumière, mais lui non. */
    private boolean nearBlackout(LivingEntity spider) {
        for (Player player : spider.getLocation().getNearbyPlayers(BLACKOUT_RADIUS)) {
            if (blackouts.containsKey(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void setShadow(ActiveMob mob, boolean dark) {
        LivingEntity spider = mob.entity();
        if (dark) {
            shadowed.add(spider.getEntityId());
        } else {
            shadowed.remove(spider.getEntityId());
        }
        spider.setInvisible(dark);
        AttributeInstance speed = spider.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(speedKey());
            speed.addModifier(new AttributeModifier(speedKey(), dark ? SHADOW_SPEED : LIGHT_SPEED,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        if (dark) {
            spider.getWorld().spawnParticle(Particle.SQUID_INK, spider.getLocation().add(0, 0.5, 0), 10, 0.4, 0.2, 0.4, 0.01);
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        shadowed.remove(mob.entity().getEntityId());
    }

    /** Fléau : fumée noire (visible seulement dans la lumière, où elle n'est pas invisible). */
    @Override
    public void ambient(ActiveMob mob) {
        if (!shadowed.contains(mob.entity().getEntityId())) {
            puff(mob, Particle.SQUID_INK, 3, 0.6, 0.4);
        }
    }
}
