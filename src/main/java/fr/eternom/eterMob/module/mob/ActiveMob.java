package fr.eternom.eterMob.module.mob;

import org.bukkit.entity.LivingEntity;

/** Un variant chargé dans un monde : le mob, son variant, son ratio (1 = le plus faible) et le multiplicateur du ratio. */
public record ActiveMob(LivingEntity entity, MobVariant variant, int ratio, double power) {

    /** Valeur de base du variant multipliée par le ratio. */
    public double scale(double base) {
        return base * power;
    }

    /** Pareil, arrondi, jamais sous 1 (rayons, durées en ticks...). */
    public int scaleInt(double base) {
        return Math.max(1, (int) Math.round(base * power));
    }
}
