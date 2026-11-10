package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.MobVariant;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * summon : fait sortir des sbires autour de lui. mobs : des mobs de Minecraft (ZOMBIE, SKELETON...) ou des variants
 * (par leur identifiant) ; count [min, max] (2 à 3) ; max-alive : ses sbires vivants au plus (4). Les sbires ne lâchent
 * rien, ne sont jamais sauvegardés, et prennent sa cible. L'IA invoque quand il est seul ou que sa vie baisse.
 */
public final class Summon extends Ability {

    /** Sbires vivants de chaque lanceur. */
    private static final Map<UUID, Set<UUID>> ALIVE = new HashMap<>();

    private final List<String> mobs;
    private final int min;
    private final int max;
    private final int maxAlive;
    private final int ratio;
    private final boolean aroundPlayers;

    public Summon(ConfigurationSection config) {
        super("summon", config, new Defaults(Trigger.ACTIVE, 25, 1.5, 0, 32, 1, 0, 0, 0, 0));
        List<String> list = new ArrayList<>(config.getStringList("mobs"));
        if (list.isEmpty()) {
            list.add("ZOMBIE");
        }
        this.mobs = list;
        List<Integer> count = config.getIntegerList("count");
        this.min = count.size() == 2 ? Math.max(1, count.get(0)) : 2;
        this.max = count.size() == 2 ? Math.max(min, count.get(1)) : 3;
        this.maxAlive = Math.clamp(config.getInt("max-alive", 4), 1, 12);
        this.ratio = config.getInt("ratio", 0);
        this.aroundPlayers = config.getString("around", "self").equalsIgnoreCase("players");
    }

    @Override
    public boolean inRange(double distance, boolean hasSight) {
        return distance <= maxRange;
    }

    @Override
    public double score(AbilityContext context) {
        int alive = alive(context.self().getUniqueId());
        if (alive >= maxAlive) {
            return 0;
        }
        var situation = context.situation();
        return clamp01(0.3 + 0.3 * (1 - Math.min(1, situation.allies() / 5.0)) + (situation.selfHealth() < 0.5 ? 0.2 : 0)
                + 0.1 * situation.playersNearTarget());
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        Location at = context.self().getLocation();
        at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.2, 0), 6, 1.5, 0.1, 1.5, 0.01);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        UUID owner = self.getUniqueId();
        int room = maxAlive - alive(owner);
        int count = Math.min(room, ThreadLocalRandom.current().nextInt(min, max + 1));
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.9f);
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location center = self.getLocation();
            if (aroundPlayers) {
                List<org.bukkit.entity.Player> near = players(self.getLocation(), 24);
                if (!near.isEmpty()) {
                    center = near.get(ThreadLocalRandom.current().nextInt(near.size())).getLocation();
                }
            }
            Location spot = Blink.safe(center.add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5));
            if (spot == null) {
                continue;
            }
            LivingEntity minion = spawn(mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())), spot, context);
            if (minion == null) {
                continue;
            }
            ALIVE.computeIfAbsent(owner, id -> new HashSet<>()).add(minion.getUniqueId());
            if (minion instanceof Mob mob && context.target() != null) {
                mob.setTarget(context.target());
            }
            spot.getWorld().spawnParticle(Particle.LARGE_SMOKE, spot.clone().add(0, 0.5, 0), 8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    private LivingEntity spawn(String what, Location spot, AbilityContext context) {
        MobVariant variant = Services.mobs() == null ? null : Services.mobs().variants().get(what.toLowerCase(Locale.ROOT));
        if (variant != null) {
            return Services.mobs().spawnMinion(variant, ratio > 0 ? ratio : Services.mobs().casterRatio(context.caster()), spot);
        }
        EntityType type;
        try {
            type = EntityType.valueOf(what.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            return null;
        }
        @SuppressWarnings("unchecked") Class<? extends LivingEntity> living = (Class<? extends LivingEntity>) entityClass;
        return Minions.spawn(spot, living, mob -> { });
    }

    private static int alive(UUID owner) {
        Set<UUID> minions = ALIVE.get(owner);
        if (minions == null) {
            return 0;
        }
        minions.removeIf(id -> {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            return entity == null || !entity.isValid() || entity.isDead();
        });
        if (minions.isEmpty()) {
            ALIVE.remove(owner);
            return 0;
        }
        return minions.size();
    }

    /** Ses sbires (boss retiré, fin de combat) : à faire disparaître. */
    public static void removeMinionsOf(UUID owner) {
        Set<UUID> minions = ALIVE.remove(owner);
        if (minions != null) {
            minions.forEach(id -> {
                Entity entity = org.bukkit.Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            });
        }
    }
}
