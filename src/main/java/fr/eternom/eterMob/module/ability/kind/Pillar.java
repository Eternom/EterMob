package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.FakeBlocks;
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
 *   health   > 0 : chaque colonne se casse (un coup compté par joueur toutes les 500 ms) ; une cage cassée s'ouvre
 * </pre>
 */
public final class Pillar extends Ability {

    private enum Mode { SPIKE, CAGE, WALL }

    private static final double CAGE_RADIUS = 2.2;
    private static final double WALL_HALF = 2.5;

    private final Mode mode;
    private final Material block;
    private final int height;
    private final long ticks;
    private final double health;

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
        this.health = Math.max(0, config.getDouble("health", 0));
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
        List<FakeBlocks.FakeBlock> shown = new ArrayList<>();
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

    /** Une colonne affichée (height blocs) qui sort du sol ; cassable si health > 0. */
    private FakeBlocks.FakeBlock column(Location base) {
        return FakeBlocks.column(ground(base), block, height, health, null);
    }

    /** Pendant la durée : la cage retient, le mur repousse et arrête les projectiles ; puis tout disparaît. */
    private void hold(AbilityContext context, Location spot, List<FakeBlocks.FakeBlock> shown) {
        Vector side = mode == Mode.WALL ? side(context) : null;
        new BukkitRunnable() {
            private long elapsed;

            @Override
            public void run() {
                if (elapsed++ >= ticks || shown.stream().noneMatch(FakeBlocks.FakeBlock::intact)) {
                    cancel();
                    shown.forEach(FakeBlocks::remove);
                    return;
                }
                // Une colonne de la cage cassée : la cage est ouverte
                if (mode == Mode.CAGE && shown.stream().allMatch(FakeBlocks.FakeBlock::intact)) {
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

    /** Arrêt du plugin : toutes les colonnes retirées (FakeBlocks). */
    public static void clearAll() {
        FakeBlocks.clearAll();
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
