package fr.eternom.eterMob.module.ability;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Faux blocs : des colonnes affichées (BlockDisplay) qui imitent les vrais blocs sans jamais toucher la map, retirées
 * à la fin et à l'arrêt du plugin. Avec de la vie (health > 0), un corps invisible reçoit les coups : chaque joueur ne
 * compte qu'un coup toutes les HIT_COOLDOWN ms (pas de spam), et la colonne casse à 0 (onBreak). Pour les piliers, cages,
 * murs, rochers des capacités et des boss.
 */
public final class FakeBlocks {

    /** Un joueur ne compte qu'un coup par colonne toutes les 500 ms. */
    public static final long HIT_COOLDOWN = 500;

    /** Une colonne : son affichage, son corps (null : sans vie), sa vie, et quand chaque joueur l'a frappée. */
    public static final class FakeBlock {
        private final BlockDisplay display;
        private final ArmorStand body;
        private final BlockData data;
        private double health;
        private final Runnable onBreak;
        private final Map<UUID, Long> lastHit = new HashMap<>();
        private boolean broken;

        private FakeBlock(BlockDisplay display, ArmorStand body, BlockData data, double health, Runnable onBreak) {
            this.display = display;
            this.body = body;
            this.data = data;
            this.health = health;
            this.onBreak = onBreak;
        }

        public boolean intact() {
            return !broken && display.isValid();
        }

        public double health() {
            return health;
        }

        public Location location() {
            return display.getLocation();
        }
    }

    private static final Map<UUID, FakeBlock> BODIES = new HashMap<>();
    private static final Set<FakeBlock> ALL = new HashSet<>();

    private FakeBlocks() {
    }

    /** Une colonne de height blocs sur base ; health > 0 : on peut la casser (onBreak, peut être null). */
    public static FakeBlock column(Location base, Material material, double height, double health, Runnable onBreak) {
        BlockData data = material.createBlockData();
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, shown -> {
            shown.setBlock(data);
            shown.setPersistent(false);
            shown.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1, 0.1f, 1), new AxisAngle4f()));
        });
        // Elle pousse en 4 ticks
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(4);
        display.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1, (float) height, 1), new AxisAngle4f()));
        ArmorStand body = null;
        if (health > 0) {
            body = base.getWorld().spawn(base, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setGravity(false);
                stand.setBasePlate(false);
                stand.setPersistent(false);
                stand.setCanPickupItems(false);
                stand.setSilent(true);
                AttributeInstance scale = stand.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(Math.clamp(height / 2.0, 0.5, 3));
                }
            });
        }
        FakeBlock block = new FakeBlock(display, body, data, health, onBreak);
        ALL.add(block);
        if (body != null) {
            BODIES.put(body.getUniqueId(), block);
        }
        base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 0.5, 0), 12, 0.4, 0.4, 0.4, 0.05, data);
        return block;
    }

    /** Retire la colonne (fin de capacité), avec un nuage de débris. */
    public static void remove(FakeBlock block) {
        if (!ALL.remove(block)) {
            return;
        }
        if (block.display.isValid()) {
            block.display.getWorld().spawnParticle(Particle.BLOCK, block.display.getLocation().add(0, 1, 0), 8, 0.3, 0.6, 0.3, 0.05, block.data);
            block.display.remove();
        }
        if (block.body != null) {
            BODIES.remove(block.body.getUniqueId());
            block.body.remove();
        }
    }

    public static boolean is(Entity entity) {
        return BODIES.containsKey(entity.getUniqueId());
    }

    /**
     * Un coup sur un corps de faux bloc (MobListener) : annulé ; un coup par joueur toutes les 500 ms retire ses dégâts
     * de base ; à 0, la colonne casse. Renvoie true si c'était un faux bloc.
     */
    public static boolean onDamage(EntityDamageEvent event) {
        FakeBlock block = BODIES.get(event.getEntity().getUniqueId());
        if (block == null) {
            return false;
        }
        event.setCancelled(true);
        Player player = attacker(event);
        if (player == null || block.broken) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - block.lastHit.getOrDefault(player.getUniqueId(), 0L) < HIT_COOLDOWN) {
            return true;
        }
        block.lastHit.put(player.getUniqueId(), now);
        block.health -= event.getDamage();
        Location at = block.display.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 10, 0.3, 0.5, 0.3, 0.05, block.data);
        at.getWorld().playSound(at, block.data.getSoundGroup().getHitSound(), 1f, 0.8f);
        if (block.health <= 0) {
            block.broken = true;
            at.getWorld().playSound(at, block.data.getSoundGroup().getBreakSound(), 1.2f, 0.8f);
            remove(block);
            if (block.onBreak != null) {
                block.onBreak.run();
            }
        }
        return true;
    }

    private static Player attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        return byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    /** Arrêt du plugin : toutes les colonnes retirées. */
    public static void clearAll() {
        for (FakeBlock block : Set.copyOf(ALL)) {
            remove(block);
        }
    }
}
