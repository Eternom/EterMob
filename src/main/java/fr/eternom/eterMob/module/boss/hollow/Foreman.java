package fr.eternom.eterMob.module.boss.hollow;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Boulder;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Strike;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Le Contremaître, sous-boss d'Orveth : le chef des mineurs qui ont creusé sous le monument ; mort avec eux, il creuse
 * encore. Brute qui commande (beaucoup d'armure, peu de résistance magique), dégâts physiques. Grottes et montagnes.
 * <ul>
 *   <li>Passifs : Lanterne (les joueurs à 6 blocs sont marqués) ; Chef d'équipe (ses mineurs tapent 15 % plus fort près
 *   de lui : état fury).</li>
 *   <li>Phase 1, le Chantier : Coup de pioche, Appel des mineurs, Dynamite (mèche 2 s, explosion en faux blocs),
 *   Wagonnet.</li>
 *   <li>Phase 2, le Coup de grisou : Grisou (nuages de gaz : une explosion dedans les fait détoner), Dynamite ×3,
 *   Éboulement de galerie.</li>
 * </ul>
 */
public final class Foreman extends SubBoss {

    public static final String ID = "contremaitre";

    /** Un nuage de grisou au sol : une explosion à moins de radius + 2 blocs le fait détoner. */
    private record Gas(Location at, double radius, long until) {
    }

    private static final List<Gas> GAS = new ArrayList<>();

    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();

    public Foreman() {
        super(ID, "Le Contremaître", "Gardien des galeries", 550, 200, 40, 0.25, Map.of("attack", 10.0, "dynamite", 9.0,
                "cart", 8.0, "gas", 12.0, "rockfall", 8.0));
    }

    @Override
    protected void build() {
        Ability pick = new Strike(Spec.of("id", "coup-de-pioche", "chance", 0.5, "damage", 0, "cooldown", 2, "states", List.of("fragile 0.15 4")));
        Ability call = new Summon(Spec.of("id", "appel-des-mineurs", "mobs", List.of("zombie-mineur"), "count", List.of(3, 3), "max-alive", 5,
                "ratio", 3, "cast", 1.5, "cooldown", 25));
        Ability dynamite = new Dynamite(Spec.of("id", "dynamite", "count", 1, "damage", damage("dynamite"), "radius", 4, "cooldown", 10, "cast", 0.6));
        Ability cart = new Charge(Spec.of("id", "wagonnet", "damage", damage("cart"), "range", List.of(6.0, 18.0), "speed", 1.1, "wall-stun", 2,
                "cooldown", 14, "cast", 1));
        phase1 = List.of(pick, call, dynamite, cart);
        Ability gas = new Firedamp(Spec.of("id", "grisou", "count", 3, "radius", 3, "seconds", 15, "cooldown", 20, "cast", 1));
        Ability dynamite3 = new Dynamite(Spec.of("id", "dynamite", "count", 3, "damage", damage("dynamite"), "radius", 4, "cooldown", 12, "cast", 0.8));
        Ability rockfall = new Boulder(Spec.of("id", "eboulement-de-galerie", "mode", "fall", "count", 4, "damage", damage("rockfall"),
                "radius", 2.5, "block", "COBBLESTONE", "range", List.of(0.0, 30.0), "cooldown", 16, "cast", 1, "interruptible", false));
        phase2 = List.of(pick, call, dynamite3, cart, gas, rockfall);
    }

    @Override
    protected List<Ability> abilities(int phase) {
        return phase <= 1 ? phase1 : phase2;
    }

    @Override
    protected double globalCooldown(int phase) {
        return phase <= 1 ? 4 : 3;
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.RAW_IRON, 4, 9, 1, 1, null),
                new LootTable.Entry(Material.RAW_GOLD, 2, 5, 0.7, 1, null),
                new LootTable.Entry(Material.LANTERN, 1, 1, 0.5, 1, "lanterne-du-contremaitre"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    @Override
    public Material icon() {
        return Material.IRON_PICKAXE;
    }

    @Override
    public List<String> lore() {
        return List.of("Sous-boss d'Orveth, brute qui commande.", "Grottes et montagnes.");
    }

    @Override
    public double height() {
        return 3;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return Zombie.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        Zombie zombie = (Zombie) entity;
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        Minions.noReinforcements(zombie);
        base(zombie, Attribute.SCALE, 1.3);
        base(zombie, Attribute.KNOCKBACK_RESISTANCE, 0.8);
        base(zombie, Attribute.FOLLOW_RANGE, 48);
        base(zombie, Attribute.ATTACK_DAMAGE, damage("attack"));
        wear(zombie, EquipmentSlot.HAND, Material.IRON_PICKAXE);
        wear(zombie, EquipmentSlot.OFF_HAND, Material.LANTERN);
        wear(zombie, EquipmentSlot.HEAD, Material.CHAINMAIL_HELMET);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    @Override
    public String phaseName(int phase) {
        return phase <= 1 ? "Le Chantier" : "Le Coup de grisou";
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.YELLOW : BossBar.Color.RED;
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Ça sent le grisou... parfait." : null;
    }

    @Override
    public String spawnLine() {
        return "Au travail, les gars ! Encore un filon !";
    }

    @Override
    public String deathLine() {
        return "Fin... de poste...";
    }

    @Override
    public void tick(BossFight fight) {
        super.tick(fight);
        if (fight.dying() || Services.states() == null) {
            return;
        }
        LivingEntity self = fight.entity();
        long now = System.currentTimeMillis();
        GAS.removeIf(gas -> gas.until() < now);
        if (now % 2000 < 260) {
            // Lanterne : les joueurs proches sont marqués ; Chef d'équipe : ses zombies tapent plus fort
            for (Player player : self.getLocation().getNearbyPlayers(6)) {
                if (Hits.fighting(player)) {
                    Services.states().apply(player, "mark", 0.1, 2.5, self);
                }
            }
            for (Entity entity : self.getNearbyEntities(10, 5, 10)) {
                if (entity instanceof Zombie ally && ally.isValid()) {
                    Services.states().apply(ally, "fury", 0.15, 2.5, self);
                }
            }
        }
        for (Gas gas : GAS) {
            for (int i = 0; i < 4; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double r = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * gas.radius();
                gas.at().getWorld().spawnParticle(Particle.DUST, gas.at().clone().add(Math.cos(angle) * r, 0.4, Math.sin(angle) * r), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(0x9ACD32), 1.6f));
            }
        }
    }

    @Override
    public void onEnd(BossFight fight) {
        super.onEnd(fight);
        GAS.clear();
    }

    /** Une explosion (dynamite) à cet endroit : les nuages de grisou proches détonent à leur tour. */
    private void detonateGas(AbilityContext context, Location at) {
        for (Iterator<Gas> it = GAS.iterator(); it.hasNext(); ) {
            Gas gas = it.next();
            if (!gas.at().getWorld().equals(at.getWorld()) || gas.at().distance(at) > gas.radius() + 2) {
                continue;
            }
            it.remove();
            Location center = gas.at();
            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            for (Player player : center.getNearbyPlayers(gas.radius() + 1)) {
                if (Hits.fighting(player)) {
                    Hits.abilityDamage(player, context.scale(damage("gas")), context.self());
                    Hits.knock(player, center, 1, 0.6);
                }
            }
        }
    }

    // ---------- Capacités ----------

    /** Dynamite : un bâton lancé en cloche (faux bloc), mèche visible 2 s, puis explosion (sans casser de bloc). */
    private final class Dynamite extends Ability {

        private final int count;

        Dynamite(ConfigurationSection config) {
            super("dynamite", config, new Defaults(Trigger.ACTIVE, 10, 0.6, 4, 26, 1, 9, 4, 1, 0.5));
            this.count = Math.clamp(config.getInt("count", 1), 1, 6);
        }

        @Override
        public boolean interruptible() {
            return false;
        }

        @Override
        public double score(AbilityContext context) {
            return clamp01(0.35 + 0.15 * Math.max(0, context.situation().playersNearTarget() - 1) + (context.situation().targetRanged() ? 0.2 : 0));
        }

        @Override
        public void cast(AbilityContext context) {
            List<Location> spots = new ArrayList<>();
            if (context.target() != null) {
                spots.add(context.target().getLocation());
            }
            for (Player player : players(context.self().getLocation(), maxRange)) {
                if (spots.size() >= count) {
                    break;
                }
                if (!player.equals(context.target())) {
                    spots.add(player.getLocation());
                }
            }
            for (Location spot : spots) {
                throwStick(context, spot.add(ThreadLocalRandom.current().nextDouble(-1, 1), 0, ThreadLocalRandom.current().nextDouble(-1, 1)));
            }
        }

        private void throwStick(AbilityContext context, Location target) {
            Location from = context.self().getEyeLocation();
            BlockDisplay stick = from.getWorld().spawn(from, BlockDisplay.class, display -> {
                display.setBlock(Material.TNT.createBlockData());
                display.setPersistent(false);
                display.setTeleportDuration(2);
                display.setTransformation(new Transformation(new Vector3f(-0.25f, 0, -0.25f), new AxisAngle4f(),
                        new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            });
            from.getWorld().playSound(from, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
            new BukkitRunnable() {
                private int ticks;

                @Override
                public void run() {
                    if (!stick.isValid()) {
                        cancel();
                        return;
                    }
                    ticks += 2;
                    if (ticks <= 20) {
                        double t = ticks / 20.0;
                        Vector line = target.toVector().subtract(from.toVector()).multiply(t);
                        stick.teleport(from.clone().add(line).add(0, 4 * 3 * t * (1 - t), 0));
                        return;
                    }
                    // Mèche : 2 s au sol, cercle rouge
                    dangerRing(target, radius, (ticks - 20) / 40.0);
                    target.getWorld().spawnParticle(Particle.SMOKE, target.clone().add(0, 0.6, 0), 2, 0.05, 0.05, 0.05, 0.01);
                    if (ticks % 10 == 0) {
                        target.getWorld().playSound(target, Sound.ENTITY_TNT_PRIMED, 0.6f, 1.2f);
                    }
                    if (ticks >= 60) {
                        cancel();
                        stick.remove();
                        target.getWorld().spawnParticle(Particle.EXPLOSION, target.clone().add(0, 0.5, 0), 4, radius / 3, 0.3, radius / 3, 0);
                        target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1f);
                        for (Player player : players(target, radius)) {
                            hit(context, player, damage, target);
                        }
                        detonateGas(context, target);
                    }
                }
            }.runTaskTimer(Services.plugin(), 2, 2);
        }
    }

    /** Grisou : des nuages de gaz au sol (15 s) ; une explosion dedans les fait détoner. */
    private final class Firedamp extends Ability {

        private final int count;
        private final double seconds;

        Firedamp(ConfigurationSection config) {
            super("firedamp", config, new Defaults(Trigger.ACTIVE, 20, 1, 0, 30, 1, 0, 3, 0, 0));
            this.count = Math.clamp(config.getInt("count", 3), 1, 6);
            this.seconds = config.getDouble("seconds", 15);
        }

        @Override
        public double score(AbilityContext context) {
            return GAS.size() < 3 ? 0.55 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            List<Player> near = players(context.self().getLocation(), 24);
            long until = System.currentTimeMillis() + Math.round(seconds * 1000);
            for (int i = 0; i < count; i++) {
                Location base = near.isEmpty() ? context.self().getLocation() : near.get(i % near.size()).getLocation();
                Location at = base.add(ThreadLocalRandom.current().nextDouble(-3, 3), 0, ThreadLocalRandom.current().nextDouble(-3, 3));
                GAS.add(new Gas(at, radius, until));
            }
            context.self().getWorld().playSound(context.self().getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
        }
    }
}
