package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.StateSpec;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * volley : une salve de projectiles vers la cible (visée là où elle va : sa vitesse est anticipée). Réglages :
 * projectile (arrow, snowball, fireball, skull), count (3), spread (degrés, 8), speed (1,8). Les dégâts et les états
 * s'appliquent au joueur touché. L'IA la préfère à distance, et contre une cible qu'elle ne peut pas rejoindre.
 */
public final class Volley extends Ability {

    /** Projectiles en vol : qui les a tirés, et ce qu'ils font en touchant. Retirés à l'impact ou au bout de 10 s. */
    private record Shot(Caster caster, double damage, List<StateSpec> states, long until) {
    }

    private static final Map<UUID, Shot> SHOTS = new HashMap<>();

    private final String projectile;
    private final int count;
    private final double spread;
    private final double speed;

    public Volley(ConfigurationSection config) {
        super("volley", config, new Defaults(Trigger.ACTIVE, 8, 0.6, 5, 28, 1, 4, 0, 0, 0));
        this.projectile = config.getString("projectile", "arrow").toLowerCase(Locale.ROOT);
        this.count = Math.clamp(config.getInt("count", 3), 1, 12);
        this.spread = Math.clamp(config.getDouble("spread", 8), 0, 45);
        this.speed = Math.clamp(config.getDouble("speed", 1.8), 0.4, 4);
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        if (!situation.sight()) {
            return 0;
        }
        double far = Math.min(1, situation.distance() / 16);
        return clamp01(0.3 + 0.4 * far + (situation.unreachable() ? 0.4 : 0) - (situation.playersNearSelf() > 0 ? 0.3 : 0));
    }

    @Override
    public double value() {
        return super.value() * Math.max(1, count * 0.6);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        LivingEntity target = context.target();
        if (target == null || !target.isValid()) {
            return;
        }
        Location eye = self.getEyeLocation();
        // Visée anticipée : là où sera la cible quand le projectile arrivera
        double travel = eye.distance(target.getEyeLocation()) / (speed * 20);
        Vector aim = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector()
                .add(target.getVelocity().clone().setY(0).multiply(travel * 20))
                .subtract(eye.toVector()).normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        self.getWorld().playSound(eye, Sound.ENTITY_SKELETON_SHOOT, 1f, 0.9f);
        long now = System.currentTimeMillis();
        SHOTS.values().removeIf(shot -> shot.until() < now);
        for (int i = 0; i < count; i++) {
            Vector direction = aim.clone();
            if (spread > 0 && count > 1) {
                direction.rotateAroundY(Math.toRadians(random.nextDouble(-spread, spread)))
                        .add(new Vector(0, Math.toRadians(random.nextDouble(-spread, spread)) * 0.5, 0)).normalize();
            }
            Projectile shot = launch(self, direction);
            if (shot != null) {
                SHOTS.put(shot.getUniqueId(), new Shot(context.caster(), context.scale(damage), states, now + 10_000));
            }
        }
    }

    private Projectile launch(LivingEntity self, Vector direction) {
        Vector velocity = direction.multiply(speed);
        Projectile shot = switch (projectile) {
            case "snowball" -> self.launchProjectile(Snowball.class, velocity);
            case "fireball" -> self.launchProjectile(SmallFireball.class, velocity);
            case "skull" -> self.launchProjectile(WitherSkull.class, velocity);
            default -> self.launchProjectile(Arrow.class, velocity);
        };
        if (shot instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(0.01);
        }
        if (shot instanceof SmallFireball fireball) {
            fireball.setIsIncendiary(false);
        }
        return shot;
    }

    /** Un projectile d'une salve touche (MobListener) : dégâts et états au joueur, et rien d'autre (pas de feu, pas de bloc). */
    public static boolean onHit(ProjectileHitEvent event) {
        Shot shot = SHOTS.remove(event.getEntity().getUniqueId());
        if (shot == null) {
            return false;
        }
        if (event.getHitEntity() instanceof LivingEntity victim && victim != shot.caster().entity()
                && (!(victim instanceof Player player) || Hits.fighting(player))) {
            Hits.abilityDamage(victim, shot.damage(), shot.caster().entity());
            applyStates(shot.caster(), victim, shot.states());
        }
        event.setCancelled(true);
        event.getEntity().remove();
        return true;
    }
}
