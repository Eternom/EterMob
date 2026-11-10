package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Materials;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * pillar : des colonnes qui sortent du sol (os, pierre...). Rien ne touche la map : ce sont des affichages de blocs
 * (BlockDisplay) qui imitent les vrais et disparaissent après seconds (6), et à l'arrêt du plugin. Le point est fixé au
 * début du télégraphe : on l'esquive en bougeant.
 * <pre>
 *   mode     spike : une colonne sous la cible (dégâts, projeté) ;
 *            cage  : un cercle de colonnes autour d'elle : qui s'approche du bord est repoussé dedans ;
 *            wall  : un mur en travers entre lui et la cible : on est repoussé en voulant passer, les projectiles s'y
 *                    arrêtent
 *   block    matière affichée (BONE_BLOCK)   height  hauteur (3)   seconds  durée (6)
 * </pre>
 */
public final class Pillar extends Ability {

    private enum Mode { SPIKE, CAGE, WALL }

    private static final double CAGE_RADIUS = 2.2;
    private static final double WALL_HALF = 2.5;
    /** Tous les affichages en place (retirés à l'arrêt du plugin). */
    private static final Set<BlockDisplay> ALIVE = new HashSet<>();

    private final Mode mode;
    private final Material block;
    private final int height;
    private final long ticks;

    public Pillar(ConfigurationSection config) {
        super("pillar", config, new Defaults(Trigger.ACTIVE, 14, 1.2, 2, 18, 1, 5, 1.2, 0, 0.9));
        Mode parsed;
        try {
            parsed = Mode.valueOf(config.getString("mode", "spike").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = Mode.SPIKE;
        }
        this.mode = parsed;
        Material material = Material.matchMaterial(config.getString("block", "BONE_BLOCK"));
        this.block = material != null && Materials.block(material) ? material : Material.BONE_BLOCK;
        this.height = Math.clamp(config.getInt("height", 3), 1, 6);
        this.ticks = Math.round(Math.clamp(config.getDouble("seconds", 6), 1, 30) * 20);
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        return switch (mode) {
            // Une cible qui ne bouge pas (tenue, ou qui tire sur place) se fait cueillir
            case SPIKE -> clamp01(0.35 + (situation.targetHeld() ? 0.4 : 0) + (situation.targetMoving() ? -0.15 : 0.15));
            // Enfermer un tireur, ou un joueur qui fuit
            case CAGE -> clamp01(0.25 + (situation.targetRanged() ? 0.35 : 0) + (situation.distance() > 8 ? 0.15 : 0));
            // Couper un tireur de sa ligne de tir
            case WALL -> situation.targetRanged() && situation.distance() > 6 ? 0.55 : 0.1;
        };
    }

    @Override
    public void begin(AbilityContext context) {
        Location at = context.target() == null ? context.self().getLocation() : context.target().getLocation();
        context.spot(ground(at));
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        Location spot = context.spot();
        switch (mode) {
            case SPIKE -> dangerRing(spot, radius, progress);
            case CAGE -> dangerRing(spot, CAGE_RADIUS, progress);
            case WALL -> line(spot.clone().add(side(context).multiply(-WALL_HALF)), spot.clone().add(side(context).multiply(WALL_HALF)), Particle.CRIT);
        }
        spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.1, 0), 6, 0.4, 0, 0.4, 0, block.createBlockData());
    }

    @Override
    public void cast(AbilityContext context) {
        Location spot = context.spot();
        spot.getWorld().playSound(spot, Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, 0.6f);
        List<BlockDisplay> shown = new ArrayList<>();
        switch (mode) {
            case SPIKE -> {
                for (Player player : players(spot, radius)) {
                    hit(context, player, damage, spot);
                }
                shown.add(column(spot));
            }
            case CAGE -> {
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12;
                    shown.add(column(spot.clone().add(Math.cos(angle) * CAGE_RADIUS, 0, Math.sin(angle) * CAGE_RADIUS)));
                }
                for (Player player : players(spot, CAGE_RADIUS)) {
                    applyStates(context, player);
                }
            }
            case WALL -> {
                Vector side = side(context);
                for (double offset = -WALL_HALF; offset <= WALL_HALF; offset += 1) {
                    shown.add(column(spot.clone().add(side.clone().multiply(offset))));
                }
            }
        }
        hold(context, spot, shown);
    }

    /** Une colonne affichée (height blocs) qui sort du sol. */
    private BlockDisplay column(Location base) {
        BlockData data = block.createBlockData();
        BlockDisplay display = base.getWorld().spawn(ground(base), BlockDisplay.class, shown -> {
            shown.setBlock(data);
            shown.setPersistent(false);
            shown.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1, 0.1f, 1), new AxisAngle4f()));
        });
        ALIVE.add(display);
        // Elle pousse en 4 ticks
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(4);
        display.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1, height, 1), new AxisAngle4f()));
        base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 0.5, 0), 12, 0.4, 0.4, 0.4, 0.05, data);
        return display;
    }

    /** Pendant la durée : la cage retient, le mur repousse et arrête les projectiles ; puis tout disparaît. */
    private void hold(AbilityContext context, Location spot, List<BlockDisplay> shown) {
        Vector side = mode == Mode.WALL ? side(context) : null;
        new BukkitRunnable() {
            private long elapsed;

            @Override
            public void run() {
                if (elapsed++ >= ticks || shown.stream().noneMatch(BlockDisplay::isValid)) {
                    cancel();
                    shown.forEach(display -> {
                        display.getWorld().spawnParticle(Particle.BLOCK, display.getLocation().add(0, 1, 0), 8, 0.3, 0.6, 0.3, 0.05, block.createBlockData());
                        display.remove();
                        ALIVE.remove(display);
                    });
                    return;
                }
                if (mode == Mode.CAGE) {
                    for (Player player : players(spot, CAGE_RADIUS + 1.5)) {
                        Vector out = player.getLocation().toVector().subtract(spot.toVector()).setY(0);
                        if (out.length() > CAGE_RADIUS - 0.6 && out.length() < CAGE_RADIUS + 1.2 && Hits.fighting(player)) {
                            // Dedans, il ne sort pas ; dehors, il n'entre pas : repoussé du côté d'où il vient
                            boolean inside = player.getLocation().clone().subtract(player.getVelocity()).toVector()
                                    .subtract(spot.toVector()).setY(0).length() < CAGE_RADIUS;
                            Vector push = out.normalize().multiply(inside ? -0.35 : 0.35);
                            player.setVelocity(push.setY(0.05));
                        }
                    }
                } else if (mode == Mode.WALL && side != null) {
                    Vector normal = new Vector(side.getZ(), 0, -side.getX());
                    for (Entity entity : spot.getWorld().getNearbyEntities(spot, WALL_HALF + 1, height + 1, WALL_HALF + 1)) {
                        Vector rel = entity.getLocation().toVector().subtract(spot.toVector());
                        double along = rel.dot(side);
                        double across = rel.dot(normal);
                        if (Math.abs(along) > WALL_HALF + 0.5 || Math.abs(across) > 0.8 || rel.getY() > height || rel.getY() < -1) {
                            continue;
                        }
                        if (entity instanceof Projectile projectile) {
                            projectile.getWorld().spawnParticle(Particle.BLOCK, projectile.getLocation(), 6, 0.1, 0.1, 0.1, 0, block.createBlockData());
                            projectile.remove();
                        } else if (entity instanceof Player player && Hits.fighting(player)) {
                            player.setVelocity(normal.clone().multiply(across >= 0 ? 0.4 : -0.4).setY(0.05));
                        }
                    }
                }
            }
        }.runTaskTimer(Services.plugin(), 1, 1);
    }

    /** Arrêt du plugin : tous les affichages retirés. */
    public static void clearAll() {
        ALIVE.forEach(Entity::remove);
        ALIVE.clear();
    }

    /** Le premier bloc d'air au-dessus du sol, sous at. */
    private static Location ground(Location at) {
        Block block = at.getBlock();
        for (int i = 0; i < 6 && !block.getRelative(0, -1, 0).getType().isSolid(); i++) {
            block = block.getRelative(0, -1, 0);
        }
        return block.getLocation().add(0.5, 0, 0.5);
    }

    /** La direction en travers de lui -> point visé. */
    private static Vector side(AbilityContext context) {
        Vector toward = context.spot().toVector().subtract(context.self().getLocation().toVector()).setY(0);
        if (toward.lengthSquared() < 0.01) {
            return new Vector(1, 0, 0);
        }
        return new Vector(-toward.getZ(), 0, toward.getX()).normalize();
    }
}
