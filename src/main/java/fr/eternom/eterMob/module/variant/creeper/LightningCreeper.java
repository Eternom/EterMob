package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chaque joueur touché par son explosion se prend la foudre sur la tête : dégâts de foudre et feu, plusieurs éclairs
 * aux ratios forts (1 au ratio 1, 2 à partir du ratio 3). L'éclair est seulement visuel : pas de feu sur les blocs,
 * pas de creeper chargé ni de cochon transformé autour ; les dégâts et le feu sont donnés au joueur directement.
 */
public class LightningCreeper extends CreeperVariant {

    private static final double STRIKES = 1;
    /** Dégâts d'un éclair (5 = foudre de Minecraft), avant armure. */
    private static final double STRIKE_DAMAGE = 5;
    /** Joueur en feu après un éclair (ticks). */
    private static final double FIRE_TICKS = 60;
    /** Premier éclair après l'explosion, puis un toutes les STRIKE_GAP (ticks) : plus que l'invulnérabilité après un coup. */
    private static final int FIRST_DELAY = 10;
    private static final int STRIKE_GAP = 15;

    public LightningCreeper() {
        super("creeper_lightning", Rarity.RARE, "Fulmen", 3, 15);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || !(victim instanceof Player)) {
            return;
        }
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(LightningCreeper.class);
        int strikes = mob.scaleInt(STRIKES);
        double damage = mob.scale(STRIKE_DAMAGE);
        int fire = mob.scaleInt(FIRE_TICKS);
        for (int i = 0; i < strikes; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!victim.isValid() || victim.isDead()) {
                    return;
                }
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                victim.damage(damage, DamageSource.builder(DamageType.LIGHTNING_BOLT).build());
                victim.setFireTicks(Math.max(victim.getFireTicks(), fire));
            }, FIRST_DELAY + (long) i * STRIKE_GAP);
        }
    }
}
