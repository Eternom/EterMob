package fr.eternom.eterMob.module.mob;

import fr.eternom.eterLib.module.combat.DamageKind;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Flèches magiques des variants qui les ont (magicShots, ex. l'archer d'os) : une traînée bleue derrière la flèche, des
 * dégâts magiques à l'impact (résistance magique au lieu de l'armure, par le combat d'EterLib), jamais ramassées.
 * Les capacités (salves...) gardent leur propre type de dégâts : seuls les tirs normaux sont convertis ici.
 */
public final class MagicShots implements Listener {

    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(0x6FD8FF), 0.9f);

    private final JavaPlugin plugin;
    private final MobService mobs;
    private final NamespacedKey key;

    public MagicShots(JavaPlugin plugin, MobService mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.key = new NamespacedKey(plugin, "magic_shot");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        ActiveMob mob = mobs.get(shooter);
        if (mob == null || !mob.variant().magicShots()) {
            return;
        }
        projectile.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        // Traînée bleue tant que la flèche vole (au plus 5 s)
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                boolean landed = projectile instanceof AbstractArrow arrow && arrow.isInBlock();
                if (!projectile.isValid() || landed || ++ticks > 100) {
                    cancel();
                    return;
                }
                projectile.getWorld().spawnParticle(Particle.DUST, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0, TRAIL);
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Après les salves (MobListener, HIGH) : un tir normal qui touche fait des dégâts magiques, puis la flèche s'efface. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity living ? living : null;
        if (event.getHitEntity() instanceof LivingEntity victim && victim != shooter) {
            event.setCancelled(true);
            if (!(victim instanceof Player player) || Hits.fighting(player)) {
                double speed = projectile.getVelocity().length();
                double base = projectile instanceof AbstractArrow arrow ? arrow.getDamage() : 2;
                double damage = Math.ceil(Math.min(speed * base, Integer.MAX_VALUE));
                Hits.abilityDamage(victim, damage, shooter, DamageKind.MAGIC);
            }
            burst(projectile);
            projectile.remove();
            return;
        }
        // Dans un bloc : un éclat bleu, et la flèche disparaît (rien à ramasser, rien qui traîne)
        burst(projectile);
        plugin.getServer().getScheduler().runTask(plugin, projectile::remove);
    }

    private static void burst(Projectile projectile) {
        projectile.getWorld().spawnParticle(Particle.DUST, projectile.getLocation(), 8, 0.15, 0.15, 0.15, 0, TRAIL);
    }
}
