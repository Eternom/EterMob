package fr.eternom.eterMob.module.boss.hollow;

import fr.eternom.eterLib.module.combat.DamageKind;
import fr.eternom.eterLib.module.state.StateInstance;
import fr.eternom.eterLib.module.state.StateType;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.FakeBlocks;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Le Voleur du cœur, sous-boss d'Orveth : le mineur qui a arraché le cristal du Gardien ; le cristal s'est brisé dans sa
 * poitrine, ses éclats le maintiennent en vie et le rongent. Assassin agile (défense moyenne), dégâts physiques et un peu
 * de brut. Badlands, mesas, forêts sombres.
 * <ul>
 *   <li>Passifs : Éclats (un coup reçu peut faire jaillir un éclat qui blesse autour de lui, dégâts bruts) ; Ombre
 *   (invisible tant qu'il n'a pas frappé ; il y retourne s'il ne frappe pas pendant 6 s).</li>
 *   <li>Phase 1, le Larcin : Coup dans le dos (+50 % par derrière), Pas de l'ombre, Larcin (vole un état positif et de
 *   la vie), Bombe fumigène (cécité, il disparaît).</li>
 *   <li>Phase 2, le Cœur brisé : Éclats d'améthyste (éventail), Pièges (cristaux au sol qui enracinent et font saigner),
 *   Frénésie sous 25 %, Résonance (les éclats plantés explosent ensemble, s'interrompt).</li>
 * </ul>
 */
public final class HeartThief extends SubBoss {

    public static final String ID = "voleur";
    private static final long SHADOW_MILLIS = 6000;

    private static final class State {
        long lastStrike;
        boolean hidden;
        final List<FakeBlocks.FakeBlock> traps = new ArrayList<>();
        boolean frenzy;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();
    private NamespacedKey frenzyKey;

    public HeartThief() {
        super(ID, "Le Voleur du cœur", "Rongé par le cristal", 480, 110, 90, 0.3, Map.of("attack", 9.0, "shards", 4.0, "trap", 5.0,
                "resonance", 9.0, "splinter", 2.0));
    }

    @Override
    protected void build() {
        Ability shadowStep = new Blink(Spec.of("id", "pas-de-l-ombre", "mode", "behind", "cooldown", 10, "cast", 0.3));
        Ability theft = new Theft(Spec.of("id", "larcin", "cooldown", 8));
        Ability smoke = new Smoke(Spec.of("id", "bombe-fumigene", "cooldown", 18, "cast", 0.4));
        phase1 = List.of(shadowStep, theft, smoke);
        Ability shards = new Volley(Spec.of("id", "eclats-d-amethyste", "projectile", "snowball", "count", 5, "spread", 25, "damage", damage("shards"),
                "damage-type", "true", "cooldown", 9, "cast", 0.6, "states", List.of("bleed 1 3")));
        Ability traps = new Traps(Spec.of("id", "pieges", "count", 4, "seconds", 20, "damage", damage("trap"), "cooldown", 16, "cast", 0.8,
                "states", List.of("root 1 1.5", "bleed 1 4")));
        Ability resonance = new Resonance(Spec.of("id", "resonance", "damage", damage("resonance"), "radius", 2.5, "cooldown", 22, "cast", 2));
        phase2 = List.of(shadowStep, theft, smoke, shards, traps, resonance);
    }

    @Override
    protected List<Ability> abilities(int phase) {
        return phase <= 1 ? phase1 : phase2;
    }

    @Override
    protected double globalCooldown(int phase) {
        return phase <= 1 ? 3.5 : 3;
    }

    @Override
    protected fr.eternom.eterMob.module.ai.AiProfile ai() {
        // Il chasse les isolés, les soigneurs et les blessés
        return new fr.eternom.eterMob.module.ai.AiProfile(32, 0.9, 0.4, 1.0, 0.4, 0.9, -0.4, 1.2, 2000, 3.5, 0.25, true);
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.AMETHYST_SHARD, 4, 10, 1, 1, null),
                new LootTable.Entry(Material.GOLD_INGOT, 2, 5, 0.7, 1, null),
                new LootTable.Entry(Material.AMETHYST_CLUSTER, 1, 1, 0.4, 1, "eclat-vole"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    @Override
    public Material icon() {
        return Material.AMETHYST_CLUSTER;
    }

    @Override
    public List<String> lore() {
        return List.of("Sous-boss d'Orveth, assassin agile.", "Badlands, mesas, forêts sombres.");
    }

    @Override
    public double height() {
        return 2.5;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return Vindicator.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        base(entity, Attribute.FOLLOW_RANGE, 48);
        base(entity, Attribute.ATTACK_DAMAGE, damage("attack"));
        base(entity, Attribute.MOVEMENT_SPEED, 0.38);
        wear(entity, EquipmentSlot.HAND, Material.IRON_AXE);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    @Override
    public String phaseName(int phase) {
        return phase <= 1 ? "Le Larcin" : "Le Cœur brisé";
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.PURPLE : BossBar.Color.PINK;
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Il me brûle... il veut rentrer chez lui... JAMAIS !" : null;
    }

    @Override
    public String spawnLine() {
        return "Vous ne l'aurez pas. Il est à moi.";
    }

    @Override
    public String deathLine() {
        return "Rends-le-lui... il saura...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        frenzyKey = new NamespacedKey(Services.plugin(), "voleur_frenesie");
        hide(fight, state(fight), true);
    }

    private static void hide(BossFight fight, State state, boolean hidden) {
        if (state.hidden == hidden) {
            return;
        }
        state.hidden = hidden;
        LivingEntity self = fight.entity();
        self.setInvisible(hidden);
        self.getWorld().spawnParticle(Particle.LARGE_SMOKE, self.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        if (self.getEquipment() != null) {
            self.getEquipment().setItemInMainHand(hidden ? null : org.bukkit.inventory.ItemStack.of(Material.IRON_AXE));
            self.getEquipment().setItemInMainHandDropChance(0);
        }
    }

    @Override
    public void tick(BossFight fight) {
        super.tick(fight);
        if (fight.dying()) {
            return;
        }
        State state = state(fight);
        LivingEntity self = fight.entity();
        long now = System.currentTimeMillis();
        if (!state.hidden && now - state.lastStrike > SHADOW_MILLIS) {
            hide(fight, state, true);
        }
        if (state.hidden && ThreadLocalRandom.current().nextInt(6) == 0) {
            self.getWorld().spawnParticle(Particle.WAX_OFF, self.getLocation().add(0, 1.2, 0), 1, 0.2, 0.4, 0.2, 0);
        }
        // Pièges : un joueur qui marche dessus est pris
        state.traps.removeIf(trap -> !trap.intact());
        for (FakeBlocks.FakeBlock trap : List.copyOf(state.traps)) {
            for (Player player : trap.location().getNearbyPlayers(0.9)) {
                if (Hits.fighting(player)) {
                    trapped(fight, player);
                    state.traps.remove(trap);
                    FakeBlocks.remove(trap);
                    break;
                }
            }
        }
        // Frénésie sous 25 %
        if (!state.frenzy && healthPart(self) < 0.25 && fight.phase() >= 2) {
            state.frenzy = true;
            AttributeInstance speed = self.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null && frenzyKey != null) {
                speed.addModifier(new AttributeModifier(frenzyKey, 0.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
            service().say(fight, "Il me brûle... encore... ENCORE !");
        }
    }

    private void trapped(BossFight fight, Player player) {
        Location at = player.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.7f);
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 20, 0.3, 0.2, 0.3, 0, Material.AMETHYST_BLOCK.createBlockData());
        Hits.abilityDamage(player, damage("trap"), fight.entity());
        if (Services.states() != null) {
            Services.states().apply(player, "root", 1, 1.5, fight.entity());
            Services.states().apply(player, "bleed", 1, 4, fight.entity());
        }
    }

    /** Il frappe : il sort de l'ombre. */
    @Override
    public void onDealt(BossFight fight, LivingEntity victim, double damage) {
        State state = state(fight);
        state.lastStrike = System.currentTimeMillis();
        hide(fight, state, false);
    }

    /** Coup dans le dos : +50 % quand il frappe par derrière (et en sortant de l'ombre). */
    @Override
    public double attackFactor(BossFight fight, LivingEntity victim) {
        LivingEntity self = fight.entity();
        Vector look = victim.getLocation().getDirection().setY(0);
        Vector toThief = self.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        boolean behind = look.lengthSquared() > 0 && toThief.lengthSquared() > 0 && look.angle(toThief) > Math.toRadians(110);
        return behind || state(fight).hidden ? 1.5 : 1;
    }

    /** Éclats : 25 % de chance qu'un coup reçu fasse jaillir un éclat (dégâts bruts autour de lui). */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            LivingEntity self = fight.entity();
            self.getWorld().spawnParticle(Particle.BLOCK, self.getLocation().add(0, 1.2, 0), 12, 0.4, 0.4, 0.4, 0.1,
                    Material.AMETHYST_BLOCK.createBlockData());
            self.getWorld().playSound(self.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.4f);
            for (Player player : self.getLocation().getNearbyPlayers(3)) {
                if (Hits.fighting(player)) {
                    Hits.abilityDamage(player, damage("splinter"), self, DamageKind.TRUE);
                }
            }
        }
        super.onDamaged(fight, event);
    }

    @Override
    public void onEnd(BossFight fight) {
        State state = states.remove(fight.id());
        if (state != null) {
            state.traps.forEach(FakeBlocks::remove);
        }
        super.onEnd(fight);
    }

    @Override
    public void onReset(BossFight fight) {
        State state = states.remove(fight.id());
        if (state != null) {
            state.traps.forEach(FakeBlocks::remove);
        }
        super.onReset(fight);
    }

    private BossFight fightOf(AbilityContext context) {
        return ((BrainBoss.BossCaster) context.caster()).fight();
    }

    // ---------- Capacités ----------

    /** Larcin : au coup, il vole un état positif du joueur et 3 % de sa vie max ; le joueur est affaibli 3 s. */
    private final class Theft extends Ability {

        Theft(ConfigurationSection config) {
            super("theft", config, new Defaults(Trigger.ON_HIT, 8, 0, 0, 4, 1, 0, 0, 0, 0));
        }

        @Override
        public void cast(AbilityContext context) {
            if (!(context.target() instanceof Player player) || Services.states() == null) {
                return;
            }
            for (StateInstance state : Services.states().active(player)) {
                if (state.type().category() == StateType.Category.BUFF) {
                    Services.states().remove(player, state.type().id());
                    break;
                }
            }
            Services.states().apply(player, "weakness", 0.15, 3, context.self());
            heal(fightOf(context), maxHealth(context.self()) * 0.03);
            context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        }
    }

    private static double maxHealth(LivingEntity entity) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** Bombe fumigène : cécité autour de lui, et il disparaît dans la fumée. */
    private final class Smoke extends Ability {

        Smoke(ConfigurationSection config) {
            super("smoke", config, new Defaults(Trigger.ACTIVE, 18, 0.4, 0, 6, 1, 0, 4, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return context.situation().playersNearSelf() >= 2 ? 0.6 : 0.2;
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity self = context.self();
            self.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, self.getLocation().add(0, 1, 0), 40, radius / 2, 1, radius / 2, 0.02);
            self.getWorld().playSound(self.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.2f, 0.6f);
            for (Player player : players(self.getLocation(), radius)) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, true, false, true));
            }
            BossFight fight = fightOf(context);
            State state = state(fight);
            state.lastStrike = 0;
            hide(fight, state, true);
        }
    }

    /** Pièges : des cristaux (faux blocs) autour des joueurs ; qui marche dessus est enraciné et saigne. */
    private final class Traps extends Ability {

        private final int count;

        Traps(ConfigurationSection config) {
            super("traps", config, new Defaults(Trigger.ACTIVE, 16, 0.8, 0, 30, 1, 5, 0, 0, 0));
            this.count = Math.clamp(config.getInt("count", 4), 1, 8);
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).traps.size() >= 6 ? 0 : 0.5;
        }

        @Override
        public void cast(AbilityContext context) {
            State state = state(fightOf(context));
            List<Player> near = players(context.self().getLocation(), 24);
            for (int i = 0; i < count; i++) {
                Location base = near.isEmpty() ? context.self().getLocation() : near.get(i % near.size()).getLocation();
                Location spot = Blink.safeSpot(base.add(ThreadLocalRandom.current().nextDouble(-4, 4), 0, ThreadLocalRandom.current().nextDouble(-4, 4)));
                if (spot != null && spot.distanceSquared(base) > 1) {
                    state.traps.add(FakeBlocks.column(spot, Material.AMETHYST_CLUSTER, 0.6, 0, null));
                }
            }
            context.self().getWorld().playSound(context.self().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.6f);
        }
    }

    /** Résonance : 2 s d'incantation (s'interrompt) ; ses cristaux plantés explosent tous (dégâts bruts autour). */
    private final class Resonance extends Ability {

        Resonance(ConfigurationSection config) {
            super("resonance", config, new Defaults(Trigger.ACTIVE, 22, 2, 0, 40, 1, 9, 2.5, 0.6, 0.4));
        }

        @Override
        protected DamageKind defaultKind() {
            return DamageKind.TRUE;
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).traps.size() >= 2 ? 0.65 : 0;
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            super.telegraph(context, progress);
            for (FakeBlocks.FakeBlock trap : state(fightOf(context)).traps) {
                dangerRing(trap.location(), radius, progress);
            }
        }

        @Override
        public void cast(AbilityContext context) {
            State state = state(fightOf(context));
            for (FakeBlocks.FakeBlock trap : List.copyOf(state.traps)) {
                Location at = trap.location();
                at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
                at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6f, 0.5f);
                for (Player player : players(at, radius)) {
                    hit(context, player, damage, at);
                }
                FakeBlocks.remove(trap);
            }
            state.traps.clear();
        }
    }
}
