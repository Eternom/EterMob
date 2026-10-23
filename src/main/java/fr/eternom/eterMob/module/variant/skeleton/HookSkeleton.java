package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Harpon : ses flèches attirent le joueur touché, plus fort selon le ratio (environ 4 blocs au ratio 1, 9 au ratio 5).
 * Si un autre mob hostile est à moins de 8 blocs du joueur, il l'attire vers ce mob : il le jette dans la meute. Sinon
 * il l'attire vers lui, et quand le joueur arrive à moins de 3 blocs (dans les 3 s), il sort une épée et lui donne un
 * coup de crochet qui le renvoie loin en arrière, puis reprend son arc. À partir du ratio 3, le coup de crochet a une
 * petite chance de faire tomber l'objet en main du joueur (8 %, 12 % au ratio 5) : il tombe devant lui, ramassable
 * après 1 s. Forêts, taïgas, jungles, marais, rivières, neige.
 */
public class HookSkeleton extends SkeletonVariant {

    /** Vitesse donnée vers la cible de l'attraction (blocs par tick), et petite poussée vers le haut pour décoller. */
    private static final double PULL = 0.9;
    private static final double LIFT = 0.35;
    /** Distance (blocs) à laquelle un autre mob hostile sert de destination. */
    private static final double PACK_RANGE = 8;

    private static final int CHECK_TICKS = 5;
    /** Coup de crochet : fenêtre après l'attraction, distance, dégâts de base (multipliés par le ratio) et recul. */
    private static final long HOOK_WINDOW_MILLIS = 3_000;
    private static final double STRIKE_RANGE = 3;
    private static final double STRIKE_DAMAGE = 4;
    private static final double STRIKE_KNOCKBACK = 1.6;
    private static final double STRIKE_LIFT = 0.5;
    /** Durée de l'épée en main (ticks), avant de reprendre l'arc. */
    private static final int SWORD_TICKS = 10;
    /** Désarmement : à partir de ce ratio, chance (ratio 3 et 4, puis Fléau), délai avant de pouvoir ramasser (ticks). */
    private static final int DISARM_RATIO = 3;
    private static final double DISARM_CHANCE = 0.08;
    private static final double DISARM_CHANCE_SCOURGE = 0.12;
    private static final int DISARM_PICKUP_TICKS = 20;

    /** Joueur attiré vers le squelette, et jusqu'à quand le coup de crochet est possible (par squelette). */
    private final Map<Integer, UUID> hooked = new HashMap<>();
    private final Map<Integer, Long> hookUntil = new HashMap<>();

    public HookSkeleton() {
        super("skeleton_hook", Rarity.PEU_COMMUN, "Uncus");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.FORESTS, Spawns.JUNGLES, Spawns.SWAMPS, Spawns.RIVERS, Spawns.COLD);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (!(event.getDamager() instanceof AbstractArrow) || !(victim instanceof Player player)) {
            return;
        }
        LivingEntity skeleton = mob.entity();
        LivingEntity pack = nearestEnemy(player, skeleton);
        if (pack == null) {
            hooked.put(skeleton.getEntityId(), player.getUniqueId());
            hookUntil.put(skeleton.getEntityId(), System.currentTimeMillis() + HOOK_WINDOW_MILLIS);
        }
        LivingEntity toward = pack != null ? pack : skeleton;
        double pull = mob.scale(PULL);
        // Au tick suivant : le recul de la flèche est appliqué après les dégâts et écraserait la vitesse donnée ici
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(HookSkeleton.class), () -> {
            if (!player.isValid() || !toward.isValid() || player.getWorld() != toward.getWorld()) {
                return;
            }
            Vector direction = toward.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (direction.lengthSquared() < 1) {
                return;
            }
            player.setVelocity(direction.normalize().multiply(pull).setY(LIFT));
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
        });
    }

    /** Autre mob hostile le plus proche du joueur (pas ce squelette), ou null. */
    private static LivingEntity nearestEnemy(Player player, LivingEntity self) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(PACK_RANGE, PACK_RANGE, PACK_RANGE)) {
            if (entity instanceof Enemy enemy && entity != self && !enemy.isDead()) {
                double distance = entity.getLocation().distanceSquared(player.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = enemy;
                }
            }
        }
        return nearest;
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        LivingEntity skeleton = mob.entity();
        int id = skeleton.getEntityId();
        UUID playerId = hooked.get(id);
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || System.currentTimeMillis() > hookUntil.getOrDefault(id, 0L) || player.getWorld() != skeleton.getWorld()) {
            forget(id);
            return;
        }
        if (player.getLocation().distanceSquared(skeleton.getLocation()) > STRIKE_RANGE * STRIKE_RANGE
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        forget(id);
        strike(mob, player);
    }

    /** Coup de crochet : épée en main un instant, coup fort, et le joueur part loin en arrière. */
    private void strike(ActiveMob mob, Player player) {
        LivingEntity skeleton = mob.entity();
        EntityEquipment equipment = skeleton.getEquipment();
        if (equipment != null) {
            ItemStack bow = equipment.getItemInMainHand();
            float dropChance = equipment.getItemInMainHandDropChance();
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            equipment.setItemInMainHandDropChance(0);
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(HookSkeleton.class), () -> {
                if (skeleton.isValid()) {
                    equipment.setItemInMainHand(bow);
                    equipment.setItemInMainHandDropChance(dropChance);
                }
            }, SWORD_TICKS);
        }
        skeleton.swingMainHand();
        player.damage(mob.scale(STRIKE_DAMAGE), skeleton);
        Vector away = player.getLocation().toVector().subtract(skeleton.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 0.01) {
            player.setVelocity(away.normalize().multiply(STRIKE_KNOCKBACK).setY(STRIKE_LIFT));
        }
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        disarm(mob, player);
    }

    /** Petite chance de faire tomber l'objet en main du joueur, devant lui. */
    private void disarm(ActiveMob mob, Player player) {
        if (mob.ratio() < DISARM_RATIO) {
            return;
        }
        double chance = mob.ratio() >= service().ratios().max() ? DISARM_CHANCE_SCOURGE : DISARM_CHANCE;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        player.getInventory().setItemInMainHand(null);
        Item dropped = player.getWorld().dropItem(player.getEyeLocation(), held, item -> {
            item.setPickupDelay(DISARM_PICKUP_TICKS);
            item.setVelocity(player.getLocation().getDirection().setY(0.2).multiply(0.3));
        });
        dropped.getWorld().playSound(dropped.getLocation(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 0.8f);
    }

    private void forget(int id) {
        hooked.remove(id);
        hookUntil.remove(id);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        forget(mob.entity().getEntityId());
    }

    /** Fléau : éclats bleus. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ENCHANTED_HIT, 3, 1.2, 0.35);
    }
}
