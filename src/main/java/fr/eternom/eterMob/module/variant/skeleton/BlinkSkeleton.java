package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ombre. Quand un joueur s'approche à moins de 4 blocs, il se téléporte à 8-12 blocs de lui et recommence à tirer ;
 * toutes les 8 s au ratio 1, environ 3,5 s au ratio 5. Il faut le coincer ou le tuer de loin.
 * <ul>
 *   <li>Le leurre (tous les ratios) : à sa place reste un double d'ombre, immobile et muet, qui porte son nom et son
 *   arc ; il part en fumée au premier coup ou après 4 s. On ne sait plus lequel est le vrai.</li>
 *   <li>La flèche d'ombre (à partir du ratio 3) : la première flèche après une téléportation est noire et aveugle
 *   1,5 s le joueur touché.</li>
 * </ul>
 * Grottes, forêts sombres, jungles.
 */
public class BlinkSkeleton extends SkeletonVariant {

    private static final int CHECK_TICKS = 10;
    private static final double TRIGGER = 4;
    private static final double MIN_JUMP = 8;
    private static final double MAX_JUMP = 12;
    private static final double COOLDOWN_MILLIS = 8_000;
    /** Durée du leurre (ticks), vérifié toutes les 5 ticks. */
    private static final int DECOY_TICKS = 80;
    private static final int DECOY_CHECK = 5;
    /** Flèche d'ombre : à partir de ce ratio, et durée de l'aveuglement (ticks). */
    private static final int SHADOW_ARROW_RATIO = 3;
    private static final int BLINDNESS_TICKS = 30;

    private final Map<Integer, Long> nextBlink = new HashMap<>();
    /** Squelettes dont la prochaine flèche est une flèche d'ombre, et flèches d'ombre en vol. */
    private final Set<Integer> shadowReady = new HashSet<>();
    private final Set<UUID> shadowArrows = new HashSet<>();

    public BlinkSkeleton() {
        super("skeleton_blink", Rarity.PEU_COMMUN, "Umbra");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.cave(entity) || Spawns.biome(entity, Spawns.DARK, Spawns.JUNGLES);
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Skeleton skeleton = (Skeleton) mob.entity();
        if (!(skeleton.getTarget() instanceof Player target) || target.getWorld() != skeleton.getWorld()
                || target.getLocation().distanceSquared(skeleton.getLocation()) > TRIGGER * TRIGGER) {
            return;
        }
        long now = System.currentTimeMillis();
        int id = skeleton.getEntityId();
        if (now < nextBlink.getOrDefault(id, 0L)) {
            return;
        }
        Location spot = spotAway(target.getLocation());
        if (spot == null) {
            return;
        }
        nextBlink.put(id, now + (long) (COOLDOWN_MILLIS / mob.power()));
        Location from = skeleton.getLocation();
        portal(from);
        skeleton.teleport(spot.setDirection(target.getLocation().toVector().subtract(spot.toVector())));
        portal(spot);
        skeleton.setTarget(target);
        decoy(skeleton, from);
        if (mob.ratio() >= SHADOW_ARROW_RATIO) {
            shadowReady.add(id);
        }
    }

    /** Double d'ombre à l'ancienne place : immobile, muet, 1 PV, avec son nom et son arc ; fumée au premier coup ou après 4 s. */
    private static void decoy(Skeleton original, Location at) {
        Skeleton decoy = Minions.spawn(at, Skeleton.class, copy -> {
            copy.setAI(false);
            copy.setSilent(true);
            copy.customName(original.customName());
            copy.setCustomNameVisible(original.isCustomNameVisible());
            AttributeInstance max = copy.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(1);
                copy.setHealth(1);
            }
            EntityEquipment from = original.getEquipment();
            EntityEquipment to = copy.getEquipment();
            if (from != null && to != null) {
                to.setItemInMainHand(from.getItemInMainHand());
                to.setItemInMainHandDropChance(0);
            }
        });
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(BlinkSkeleton.class), () -> {
            elapsed[0] += DECOY_CHECK;
            if (decoy.isValid() && !decoy.isDead() && elapsed[0] < DECOY_TICKS) {
                return;
            }
            task[0].cancel();
            Location last = decoy.getLocation().add(0, 1, 0);
            last.getWorld().spawnParticle(Particle.LARGE_SMOKE, last, 20, 0.3, 0.6, 0.3, 0.02);
            last.getWorld().spawnParticle(Particle.SQUID_INK, last, 10, 0.3, 0.6, 0.3, 0.02);
            decoy.remove();
        }, DECOY_CHECK, DECOY_CHECK);
    }

    /** Flèche d'ombre : la première après une téléportation, à partir du ratio 3. */
    @Override
    public void onShoot(ActiveMob mob, EntityShootBowEvent event) {
        if (shadowReady.remove(mob.entity().getEntityId())) {
            shadowArrows.add(event.getProjectile().getUniqueId());
            event.getProjectile().getWorld().spawnParticle(Particle.SQUID_INK, event.getProjectile().getLocation(), 8, 0.1, 0.1, 0.1, 0.02);
        }
    }

    @Override
    public void onProjectileHit(ActiveMob mob, ProjectileHitEvent event) {
        // Ratée : on l'oublie ; touchée : onAttack la retire
        if (!(event.getHitEntity() instanceof Player)) {
            shadowArrows.remove(event.getEntity().getUniqueId());
        }
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (event.getDamager() instanceof AbstractArrow arrow && shadowArrows.remove(arrow.getUniqueId()) && victim instanceof Player) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0));
            victim.getWorld().spawnParticle(Particle.SQUID_INK, victim.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Un endroit sûr à 8-12 blocs du joueur, en cherchant un peu plus haut et plus bas ; null si rien trouvé. */
    private static Location spotAway(Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = random.nextDouble(MIN_JUMP, MAX_JUMP);
            Location base = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            for (int dy = 0; dy <= 6; dy++) {
                int offset = dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2;
                Block feet = base.clone().add(0, offset, 0).getBlock();
                if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                        && feet.getRelative(0, -1, 0).isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private static void portal(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextBlink.remove(mob.entity().getEntityId());
        shadowReady.remove(mob.entity().getEntityId());
    }

    /** Fléau : particules de portail autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.PORTAL, 6, 1, 0.4);
    }
}
