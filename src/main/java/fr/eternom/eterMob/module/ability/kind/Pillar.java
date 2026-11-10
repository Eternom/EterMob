package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.TempBlocks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * pillar : des blocs qui sortent du sol (os, pierre...), toujours temporaires et seulement dans l'air (jamais à la place
 * d'un bloc, jamais sur un terrain protégé : alors seulement l'effet, sans blocs). Le point est fixé au début du
 * télégraphe : on l'esquive en bougeant.
 * <pre>
 *   mode     spike (une colonne sous la cible : dégâts et projeté), cage (un cercle autour d'elle), wall (un mur
 *            entre lui et la cible, en travers)
 *   block    matière (BONE_BLOCK)   height  hauteur (3)   seconds  durée (6)
 * </pre>
 */
public final class Pillar extends Ability {

    private enum Mode { SPIKE, CAGE, WALL }

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
        this.block = material != null && fr.eternom.eterMob.module.mob.Materials.block(material) ? material : Material.BONE_BLOCK;
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
            case CAGE -> dangerRing(spot, 2.2, progress);
            case WALL -> line(spot.clone().add(side(context).multiply(-2.5)), spot.clone().add(side(context).multiply(2.5)), Particle.CRIT);
        }
        spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.1, 0), 6, 0.4, 0, 0.4, 0, block.createBlockData());
    }

    @Override
    public void cast(AbilityContext context) {
        Location spot = context.spot();
        spot.getWorld().playSound(spot, Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, 0.6f);
        switch (mode) {
            case SPIKE -> {
                for (Player player : players(spot, radius)) {
                    hit(context, player, damage, spot);
                }
                column(spot.getBlock());
            }
            case CAGE -> {
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12;
                    column(spot.clone().add(Math.cos(angle) * 2.2, 0, Math.sin(angle) * 2.2).getBlock());
                }
                for (Player player : players(spot, 2)) {
                    applyStates(context, player);
                }
            }
            case WALL -> {
                Vector side = side(context);
                for (double offset = -2.5; offset <= 2.5; offset += 1) {
                    column(spot.clone().add(side.clone().multiply(offset)).getBlock());
                }
            }
        }
    }

    /** Colonne de blocs temporaires à partir de ce bloc (dans l'air seulement). */
    private void column(Block from) {
        if (Services.guard() != null && !Services.guard().allowsBlocks(from.getLocation())) {
            return;
        }
        for (int y = 0; y < height; y++) {
            Block block = from.getRelative(0, y, 0);
            if (!TempBlocks.place(block, this.block, ticks)) {
                break;
            }
        }
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
