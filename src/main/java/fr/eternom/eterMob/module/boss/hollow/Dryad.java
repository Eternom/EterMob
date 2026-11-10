package fr.eternom.eterMob.module.boss.hollow;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.FakeBlocks;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Pull;
import fr.eternom.eterMob.module.ability.kind.Tombs;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La Dryade des ronces, sous-boss d'Orveth : la mousse elle-même, éveillée par la colère du Gardien. Mage de soutien
 * (peu d'armure, beaucoup de résistance magique), dégâts magiques. Jungles, grottes luxuriantes, marais. Corps d'evoker
 * sans ses sorts de Minecraft (il garde ses distances), couronne de feuilles.
 * <ul>
 *   <li>Passifs : Photosynthèse (en pleine lumière, elle régénère) ; Pollen (ses alliés autour régénèrent).</li>
 *   <li>Phase 1, la Floraison : Spores, Ronces, Bourgeon (une fleur qui soigne ses alliés tant qu'on ne la casse pas),
 *   Fuite dans les feuilles.</li>
 *   <li>Phase 2, la Jungle : Liane (attire un tireur), Pluie de pollen, Bourgeons ×3, Étreinte végétale (enracine tout
 *   le groupe, s'interrompt).</li>
 * </ul>
 */
public final class Dryad extends SubBoss {

    public static final String ID = "dryade";
    private static final double BUD_HEALTH = 14;

    private static final class State {
        final List<FakeBlocks.FakeBlock> buds = new ArrayList<>();
        ItemDisplay crown;
        long ticks;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();

    public Dryad() {
        super(ID, "La Dryade des ronces", "Gardienne de la mousse", 450, 50, 170, 0, Map.of("spores", 4.0, "brambles", 6.0,
                "vine", 3.0, "pollen", 1.0));
    }

    @Override
    protected void build() {
        Ability spores = new Volley(Spec.of("id", "spores", "projectile", "snowball", "count", 3, "spread", 8, "damage", damage("spores"),
                "damage-type", "magic", "cooldown", 6, "cast", 0.5, "states", List.of("slow 0.2 2")));
        Ability brambles = new Tombs(Spec.of("id", "ronces", "targets", 2, "block", "MANGROVE_ROOTS", "damage", damage("brambles"),
                "damage-type", "magic", "cooldown", 13, "cast", 1.1, "states", List.of("root 1 1.5")));
        Ability bud = new Bud(Spec.of("id", "bourgeon", "count", 1, "cooldown", 14, "cast", 1));
        Ability flee = new Blink(Spec.of("id", "fuite-dans-les-feuilles", "mode", "escape", "escape-health", 1.0, "cooldown", 12, "cast", 0.3));
        phase1 = List.of(spores, brambles, bud, flee);
        Ability vine = new Pull(Spec.of("id", "liane", "damage", damage("vine"), "damage-type", "magic", "range", List.of(6.0, 22.0),
                "cooldown", 14, "cast", 0.8, "states", List.of("slow 0.3 2")));
        Ability pollen = new Zone(Spec.of("id", "pluie-de-pollen", "mode", "linger", "count", 3, "radius", 2.5, "seconds", 8,
                "damage", damage("pollen"), "color", "F5DE50", "cooldown", 18, "cast", 1, "states", List.of("weakness 0.2 3")));
        Ability buds = new Bud(Spec.of("id", "bourgeons", "count", 3, "cooldown", 20, "cast", 1.2));
        Ability embrace = new Embrace(Spec.of("id", "etreinte-vegetale", "cooldown", 26, "cast", 2));
        phase2 = List.of(spores, brambles, flee, vine, pollen, buds, embrace);
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
    protected fr.eternom.eterMob.module.ai.AiProfile ai() {
        return new fr.eternom.eterMob.module.ai.AiProfile(32, 1, 0.1, 0.5, 0.7, 1, -0.3, 1.25, 2500, 4, 0.25, true);
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.MOSS_BLOCK, 4, 8, 1, 1, null),
                new LootTable.Entry(Material.FLOWERING_AZALEA, 1, 2, 0.8, 1, null),
                new LootTable.Entry(Material.GLOW_BERRIES, 3, 8, 0.8, 1, null),
                new LootTable.Entry(Material.SPORE_BLOSSOM, 1, 1, 0.4, 1, "fleur-de-la-dryade"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    @Override
    public Material icon() {
        return Material.FLOWERING_AZALEA_LEAVES;
    }

    @Override
    public List<String> lore() {
        return List.of("Sous-boss d'Orveth, mage de soutien.", "Jungles, grottes luxuriantes, marais.");
    }

    @Override
    public double height() {
        return 2.5;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return Evoker.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        base(entity, Attribute.SCALE, 1.1);
        base(entity, Attribute.FOLLOW_RANGE, 48);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    @Override
    public String phaseName(int phase) {
        return phase <= 1 ? "La Floraison" : "La Jungle";
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.GREEN : BossBar.Color.YELLOW;
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Tout ce que vous avez coupé repousse." : null;
    }

    @Override
    public String spawnLine() {
        return "La mousse se souvient de chaque pas.";
    }

    @Override
    public String deathLine() {
        return "Je... refleurirai...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        // Pas de crocs ni de vex de Minecraft : seulement ses sorts
        if (fight.entity() instanceof Mob mob) {
            for (var goal : List.copyOf(Bukkit.getMobGoals().getAllGoals(mob))) {
                if (goal.getKey().getNamespacedKey().getKey().contains("spell")) {
                    Bukkit.getMobGoals().removeGoal(mob, goal);
                }
            }
        }
        State state = state(fight);
        if (state.crown == null || !state.crown.isValid()) {
            LivingEntity self = fight.entity();
            state.crown = self.getWorld().spawn(self.getLocation(), ItemDisplay.class, display -> {
                display.setItemStack(ItemStack.of(Material.FLOWERING_AZALEA_LEAVES));
                display.setPersistent(false);
                display.setTeleportDuration(5);
                display.setBillboard(Display.Billboard.FIXED);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.4f, 0.7f), new AxisAngle4f()));
            });
            service().own(fight, state.crown);
        }
    }

    @Override
    public void tick(BossFight fight) {
        super.tick(fight);
        if (fight.dying()) {
            return;
        }
        State state = state(fight);
        state.ticks += 5;
        LivingEntity self = fight.entity();
        if (state.crown != null && state.crown.isValid()) {
            state.crown.teleport(self.getLocation().add(0, self.getHeight() + 0.1, 0));
        }
        state.buds.removeIf(bud -> !bud.intact());
        if (state.ticks % 20 != 0) {
            return;
        }
        // Photosynthèse : en pleine lumière, 0,5 % par seconde
        if (self.getLocation().getBlock().getLightLevel() >= 12) {
            heal(fight, maxHealth(self) * 0.005);
            self.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, self.getLocation().add(0, 2, 0), 2, 0.3, 0.3, 0.3, 0);
        }
        // Pollen : ses alliés autour régénèrent un peu
        for (Entity entity : self.getNearbyEntities(8, 4, 8)) {
            if (entity instanceof Monster ally && ally.isValid() && !fight.owned().contains(ally)) {
                ally.setHealth(Math.min(maxHealth(ally), ally.getHealth() + 0.5));
            }
        }
        // Bourgeons : chacun soigne ses alliés (et elle) à 8 blocs
        for (FakeBlocks.FakeBlock bud : state.buds) {
            Location at = bud.location();
            at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 4, 0.4, 0.4, 0.4, 0);
            if (at.getWorld().equals(self.getWorld()) && at.distanceSquared(self.getLocation()) < 64) {
                heal(fight, maxHealth(self) * 0.01);
                line(at.clone().add(0, 1, 0), self.getLocation().add(0, 1.5, 0));
            }
        }
    }

    private static double maxHealth(LivingEntity entity) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    private static void line(Location from, Location to) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x7CFC00), 0.8f);
        var step = to.toVector().subtract(from.toVector());
        double length = step.length();
        step.normalize().multiply(0.6);
        Location point = from.clone();
        for (double done = 0; done < length; done += 0.6) {
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            point.add(step);
        }
    }

    @Override
    public void onEnd(BossFight fight) {
        State state = states.remove(fight.id());
        if (state != null) {
            state.buds.forEach(FakeBlocks::remove);
        }
        super.onEnd(fight);
    }

    @Override
    public void onReset(BossFight fight) {
        State state = states.remove(fight.id());
        if (state != null) {
            state.buds.forEach(FakeBlocks::remove);
        }
        super.onReset(fight);
    }

    private static BossFight fightOf(AbilityContext context) {
        return ((BrainBoss.BossCaster) context.caster()).fight();
    }

    // ---------- Capacités ----------

    /** Bourgeon : une fleur (faux bloc, avec de la vie) qui la soigne tant qu'on ne la casse pas (3 au plus). */
    private final class Bud extends Ability {

        private final int count;

        Bud(ConfigurationSection config) {
            super("bud", config, new Defaults(Trigger.ACTIVE, 14, 1, 0, 40, 1, 0, 0, 0, 0));
            this.count = Math.clamp(config.getInt("count", 1), 1, 3);
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).buds.size() >= 3 ? 0 : 0.55;
        }

        @Override
        public void cast(AbilityContext context) {
            State state = state(fightOf(context));
            LivingEntity self = context.self();
            for (int i = 0; i < count && state.buds.size() < 3; i++) {
                double angle = Math.random() * Math.PI * 2;
                Location spot = Blink.safeSpot(self.getLocation().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4));
                if (spot != null) {
                    state.buds.add(FakeBlocks.column(spot, Material.FLOWERING_AZALEA, 1, BUD_HEALTH, () ->
                            spot.getWorld().playSound(spot, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.2f, 0.8f)));
                }
            }
            self.getWorld().playSound(self.getLocation(), Sound.BLOCK_AZALEA_PLACE, 1.2f, 0.8f);
        }
    }

    /** Étreinte végétale : longue incantation (2 s, s'interrompt) ; si elle finit, tout le groupe est enraciné 1,5 s. */
    private final class Embrace extends Ability {

        Embrace(ConfigurationSection config) {
            super("embrace", config, new Defaults(Trigger.ACTIVE, 26, 2, 0, 40, 1, 0, 16, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return players(context.self().getLocation(), radius).size() >= 2 ? 0.6 : 0.2;
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            super.telegraph(context, progress);
            ring(context.self().getLocation(), radius * progress + 1, Particle.HAPPY_VILLAGER);
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity self = context.self();
            self.getWorld().playSound(self.getLocation(), Sound.BLOCK_ROOTS_PLACE, 2f, 0.5f);
            for (Player player : players(self.getLocation(), radius)) {
                if (Hits.fighting(player) && Services.states() != null) {
                    Services.states().apply(player, "root", 1, 1.5, self);
                    player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), 15, 0.4, 0.2, 0.4, 0,
                            Material.MANGROVE_ROOTS.createBlockData());
                }
            }
        }
    }
}
