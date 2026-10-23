package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chaque joueur touché par son explosion se prend la foudre sur la tête, une fois au ratio 1 et 2, deux fois à partir du
 * ratio 3. La foudre ignore l'armure et les enchantements (l'armure réduirait un petit coup à presque rien) : elle
 * retire directement 2 à 4,4 points de vie selon le ratio (cœurs d'absorption d'abord), et met le joueur en feu.
 * L'éclair est seulement visuel : pas de feu sur les blocs, pas de creeper chargé ni de cochon transformé autour.
 */
public class LightningCreeper extends CreeperVariant {

    private static final double STRIKES = 1;
    /** Points de vie retirés par un éclair, sans armure ni enchantement. */
    private static final double STRIKE_DAMAGE = 2;
    /** Joueur en feu après un éclair (ticks). */
    private static final double FIRE_TICKS = 60;
    /** Premier éclair après l'explosion, puis un toutes les STRIKE_GAP (ticks). */
    private static final int FIRST_DELAY = 10;
    private static final int STRIKE_GAP = 15;

    public LightningCreeper() {
        super("creeper_lightning", Rarity.RARE, "Fulmen", 3, 15);
    }

    /** Fléau : étincelles électriques sur lui, plus fortes pendant la mèche. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ELECTRIC_SPARK, fusing(mob) ? 15 : 4, 1, 0.4);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || !(victim instanceof Player player)) {
            return;
        }
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(LightningCreeper.class);
        int strikes = mob.scaleInt(STRIKES);
        double damage = mob.scale(STRIKE_DAMAGE);
        int fire = mob.scaleInt(FIRE_TICKS);
        for (int i = 0; i < strikes; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> strike(player, damage, fire), FIRST_DELAY + (long) i * STRIKE_GAP);
        }
    }

    private static void strike(Player player, double damage, int fire) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.setFireTicks(Math.max(player.getFireTicks(), fire));
        trueDamage(player, damage, DamageSource.builder(DamageType.LIGHTNING_BOLT).build());
    }
}
