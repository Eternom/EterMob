package fr.eternom.eterMob.module.mob;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Coups communs aux variants et aux boss : dégâts qui ignorent l'armure, onde de choc. Jamais en créatif ni en spectateur. */
public final class Hits {

    private Hits() {
    }

    public static boolean fighting(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    /**
     * Dégâts qui ignorent l'armure et les enchantements : retirés directement de la vie (cœurs d'absorption d'abord).
     * Un coup fatal passe par de vrais dégâts magiques venant de cause (null : sans auteur), pour le message de mort et
     * le totem d'immortalité. Magiques : ils ignorent l'armure et ne l'usent pas (des dégâts « infinis » qui passent par
     * l'armure la détruisent entièrement, au lieu qu'elle tombe à la mort).
     */
    public static void trueDamage(Player player, double damage, Entity cause) {
        if (!fighting(player)) {
            return;
        }
        double left = damage;
        double absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            double taken = Math.min(absorption, left);
            player.setAbsorptionAmount(absorption - taken);
            left -= taken;
        }
        if (left <= 0) {
            player.playHurtAnimation(0);
        } else if (left < player.getHealth()) {
            player.setHealth(player.getHealth() - left);
            player.playHurtAnimation(0);
        } else {
            DamageSource.Builder fatal = DamageSource.builder(DamageType.MAGIC);
            if (cause != null) {
                fatal.withCausingEntity(cause).withDirectEntity(cause);
            }
            player.damage(Float.MAX_VALUE, fatal.build());
        }
    }

    /**
     * Onde de choc : les joueurs à moins de radius blocs du centre prennent damage (venant de source, donc à travers leur
     * armure) et sont poussés vers l'extérieur (push) et vers le haut (lift), en blocs par tick.
     */
    public static void shockwave(LivingEntity source, Location center, double radius, double damage, double push, double lift) {
        for (Player player : center.getNearbyPlayers(radius)) {
            if (!fighting(player)) {
                continue;
            }
            player.damage(damage, source);
            knock(player, center, push, lift);
        }
    }

    /** Pousse le joueur loin du centre (push) et vers le haut (lift). */
    public static void knock(Player player, Location center, double push, double lift) {
        Vector away = player.getLocation().toVector().subtract(center.toVector()).setY(0);
        away = away.lengthSquared() < 0.01 ? new Vector() : away.normalize().multiply(push);
        player.setVelocity(away.setY(lift));
    }
}
