package fr.eternom.eterMob.module.boss.golem;

import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterLib.module.combat.StatSheet;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Boulder;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Slam;
import fr.eternom.eterMob.module.ability.kind.Tombs;
import fr.eternom.eterMob.module.ability.kind.Wave;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Tier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orveth, le Gardien du monument (Hollow Dungeon). Un ancien gardien de pierre bâti pour protéger le monument ; les
 * mineurs lui ont arraché le cristal qui lui servait de cœur. Réveillé, couvert de mousse et de racines, il veut le
 * reprendre. Colosse lent : armure énorme (300) mais résistance magique faible (40) : le tank magique y est roi.
 * Pensé pour 4 joueurs au plus. Rien ne touche la map : rochers et ronces sont de faux blocs.
 * <ul>
 *   <li>Passifs : Racines du cœur (point faible : +30 % dans le dos, +50 % en phase 3) ; Enracinement (immobile 3 s :
 *   il régénère 0,5 %/s et prend 30 % de moins ; il faut le faire bouger ; plus en phase 3) ; pas sismiques ; rage.</li>
 *   <li>Phase 1, le Réveil : Poing de pierre (cône long), Jet de rocher, Frappe sismique (fragilité), Ronces.</li>
 *   <li>Phase 2, les Fragments : toutes les 20 s, 3 golems de mousse arrivent des bords de l'arène et marchent vers lui ;
 *   chacun qui l'atteint lui rend 5 % et une pile d'armure : il faut les intercepter. Le Rouleau (sonné 3 s dans un mur),
 *   Éboulement (rochers du ciel), Poing, Ronces.</li>
 *   <li>Phase 3, la Colère du sceau : Onde du sceau, Poigne (saisi, soulevé, projeté), Jet de rocher ×2, Poing ;
 *   Dernier éboulement à 10 % (8 s de rochers sur ceux qu'il voit : se mettre à couvert derrière un pilier).</li>
 * </ul>
 */
public final class Orveth extends BrainBoss {

    public static final String ID = "orveth";
    private static final double SCALE = 2.2;
    private static final long ROOT_MILLIS = 3000;
    private static final long FRAGMENT_MILLIS = 20_000;
    private static final int MAX_STACKS = 5;
    private static final double STACK_ARMOR = 30;
    private static final long LAST_STAND_MILLIS = 8000;

    private static final class State {
        Location lastSpot;
        long stillSince;
        boolean rooted;
        long nextFragments;
        final List<UUID> fragments = new ArrayList<>();
        int stacks;
        long lastStandUntil;
        boolean lastStandDone;
        long nextRain;
        long ticks;
    }

    // Réglages (config.yml > bosses.orveth)
    private double health = 1000;
    private double toughness = 1.6;
    private double armor = 300;
    private double magicResist = 40;
    private double piercing = 0.3;
    private double leash = 40;
    private double enrage = 15;
    private final Map<String, Double> damage = new HashMap<>(Map.of("attack", 15.0, "fist", 18.0, "boulder", 12.0,
            "slam", 10.0, "brambles", 6.0, "roll", 14.0, "rockfall", 10.0, "wave", 10.0, "grip", 8.0, "last-stand", 8.0));

    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();
    private List<Ability> phase3 = List.of();
    private Boulder lastStandRock;
    private final Map<UUID, State> states = new HashMap<>();

    public Orveth() {
        super(ID, "Orveth", "Gardien du monument");
    }

    @Override
    public void configure(ConfigurationSection section) {
        if (section != null) {
            health = Math.clamp(section.getDouble("health", health), 50, 1024);
            toughness = section.getDouble("toughness", toughness);
            armor = section.getDouble("armor", armor);
            magicResist = section.getDouble("magic-resist", magicResist);
            piercing = Math.clamp(section.getDouble("piercing", piercing), 0, 0.9);
            leash = Math.max(0, section.getDouble("leash-radius", leash));
            enrage = section.getDouble("enrage-minutes", enrage);
            for (String key : List.copyOf(damage.keySet())) {
                damage.put(key, section.getDouble("damage." + key, damage.get(key)));
            }
        }
        build();
    }

    private void build() {
        Ability fist = new Cleave(Spec.of("id", "poing-de-pierre", "angle", 60, "radius", 6, "damage", damage.get("fist"),
                "knockback", 1.2, "lift", 0.3, "cooldown", 7, "cast", 0.9));
        Ability rock = new Boulder(Spec.of("id", "jet-de-rocher", "mode", "throw", "damage", damage.get("boulder"), "radius", 3,
                "range", List.of(6.0, 30.0), "cooldown", 10, "cast", 0.6, "interruptible", false, "states", List.of("slow 0.3 3")));
        Ability slam = new Slam(Spec.of("id", "frappe-sismique", "radius", 5, "damage", damage.get("slam"), "knockback", 1, "lift", 0.6,
                "cooldown", 12, "cast", 1, "interruptible", false, "states", List.of("fragile 0.25 6")));
        Ability brambles = new Tombs(Spec.of("id", "ronces", "targets", 2, "block", "MANGROVE_ROOTS", "damage", damage.get("brambles"),
                "cooldown", 14, "cast", 1.1, "interruptible", false, "states", List.of("root 1 1.5", "bleed 1 4")));
        phase1 = List.of(fist, rock, slam, brambles);
        Ability roll = new Charge(Spec.of("id", "rouleau", "damage", damage.get("roll"), "range", List.of(8.0, 22.0), "speed", 1.0,
                "distance", 22, "wall-stun", 3, "cooldown", 16, "cast", 1.2, "interruptible", false, "knockback", 1.4));
        Ability rockfall = new Boulder(Spec.of("id", "eboulement", "mode", "fall", "count", 6, "damage", damage.get("rockfall"),
                "radius", 2.5, "range", List.of(0.0, 30.0), "cooldown", 18, "cast", 1, "interruptible", false));
        phase2 = List.of(fist, rock, roll, rockfall, brambles);
        Ability wave = new Wave(Spec.of("id", "onde-du-sceau", "rings", 3, "radius", 10, "damage", damage.get("wave"), "cooldown", 14,
                "cast", 1, "interruptible", false));
        Ability grip = new Grip(Spec.of("id", "poigne", "damage", damage.get("grip"), "range", List.of(0.0, 6.0), "cooldown", 16, "cast", 0.8));
        Ability rock2 = new Boulder(Spec.of("id", "jet-de-rocher", "mode", "throw", "count", 2, "damage", damage.get("boulder"), "radius", 3,
                "range", List.of(6.0, 30.0), "cooldown", 9, "cast", 0.6, "interruptible", false, "states", List.of("slow 0.3 3")));
        phase3 = List.of(fist, wave, grip, rock2);
        lastStandRock = new Boulder(Spec.of("id", "dernier-eboulement", "mode", "fall", "damage", damage.get("last-stand"), "radius", 2,
                "flight", 16));
    }

    @Override
    protected List<Ability> abilities(int phase) {
        return switch (phase) {
            case 1 -> phase1;
            case 2 -> phase2;
            default -> phase3;
        };
    }

    @Override
    protected double globalCooldown(int phase) {
        return switch (phase) {
            case 1 -> 6;
            case 2 -> 5;
            default -> 4;
        };
    }

    @Override
    protected AiProfile ai() {
        // Il garde sa cible (un colosse ne se retourne pas pour rien), et punit les tireurs à coups de rochers
        return new AiProfile(48, 1.3, 0.5, 0.5, 0.5, 0.6, 0, 1.4, 3500, 6, 0.25, true);
    }

    @Override
    public double piercing() {
        return piercing;
    }

    @Override
    public double armor() {
        return armor;
    }

    @Override
    public double magicResist() {
        return magicResist;
    }

    @Override
    public void contribute(BossFight fight, StatSheet sheet) {
        super.contribute(fight, sheet);
        State state = states.get(fight.id());
        if (state != null && state.stacks > 0) {
            sheet.add(Stat.ARMOR, STACK_ARMOR * state.stacks, "fragments de mousse");
        }
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.AMETHYST_SHARD, 1, 1, 1, 1, "eclat-du-coeur"),
                new LootTable.Entry(Material.MOSSY_COBBLESTONE, 8, 16, 1, 1, null),
                new LootTable.Entry(Material.IRON_INGOT, 4, 10, 0.8, 1, null),
                new LootTable.Entry(Material.EMERALD, 4, 10, 0.8, 1, null),
                new LootTable.Entry(Material.NETHERITE_SCRAP, 1, 2, 0.3, 1, null)));
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return Material.MOSSY_STONE_BRICKS;
    }

    @Override
    public List<String> lore() {
        return List.of("Gardien de pierre du monument (Hollow Dungeon).", "Armure énorme, faible à la magie.");
    }

    @Override
    public double baseHealth() {
        return health;
    }

    @Override
    public double toughness() {
        return toughness;
    }

    @Override
    public double height() {
        return 6;
    }

    @Override
    public double leashRadius() {
        return leash;
    }

    @Override
    public double enrageMinutes() {
        return enrage;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return IronGolem.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        IronGolem golem = (IronGolem) entity;
        golem.setPlayerCreated(false);
        base(golem, Attribute.SCALE, SCALE);
        base(golem, Attribute.KNOCKBACK_RESISTANCE, 1);
        base(golem, Attribute.FOLLOW_RANGE, 64);
        base(golem, Attribute.ATTACK_DAMAGE, damage.get("attack"));
        base(golem, Attribute.MOVEMENT_SPEED, 0.22);
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.66, 0.33};
    }

    @Override
    public String phaseName(int phase) {
        return switch (phase) {
            case 1 -> "Le Réveil";
            case 2 -> "Les Fragments";
            default -> "La Colère du sceau";
        };
    }

    @Override
    public BossBar.Color color(int phase) {
        return switch (phase) {
            case 1 -> BossBar.Color.GREEN;
            case 2 -> BossBar.Color.YELLOW;
            default -> BossBar.Color.RED;
        };
    }

    @Override
    public String phaseLine(int phase) {
        return switch (phase) {
            case 2 -> "La mousse... revient... à moi.";
            case 3 -> "MON. CŒUR.";
            default -> null;
        };
    }

    @Override
    public String spawnLine() {
        return "Qui... a pris... mon cœur ?";
    }

    @Override
    public String deathLine() {
        return "Le monument... sans gardien...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    // ---------- Combat ----------

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        super.onPhase(fight, phase, resumed);
        State state = state(fight);
        state.rooted = false;
        if (phase == 2) {
            state.nextFragments = System.currentTimeMillis() + 3000;
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
        long now = System.currentTimeMillis();
        LivingEntity golem = fight.entity();
        steps(golem, state);
        rooting(fight, state, now);
        if (fight.phase() == 2) {
            fragments(fight, state, now);
        }
        if (fight.phase() >= 3) {
            lastStand(fight, state, now);
        }
    }

    /** Pas sismiques : poussière et grondement. */
    private static void steps(LivingEntity golem, State state) {
        Vector velocity = golem.getVelocity();
        if (state.ticks % 10 == 0 && golem.isOnGround() && velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.002) {
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 1.6f, 0.4f);
            Block below = golem.getLocation().getBlock().getRelative(0, -1, 0);
            if (below.getType().isSolid()) {
                golem.getWorld().spawnParticle(Particle.BLOCK, golem.getLocation(), 12, 0.9, 0.05, 0.9, 0, below.getBlockData());
            }
        }
    }

    /** Immobile 3 s (hors phase 3) : il s'enracine, régénère 0,5 %/s et prend 30 % de dégâts en moins. */
    private void rooting(BossFight fight, State state, long now) {
        LivingEntity golem = fight.entity();
        Location here = golem.getLocation();
        boolean moved = state.lastSpot == null || !state.lastSpot.getWorld().equals(here.getWorld()) || state.lastSpot.distanceSquared(here) > 0.25;
        if (moved) {
            state.lastSpot = here;
            state.stillSince = now;
            if (state.rooted) {
                state.rooted = false;
                golem.getWorld().playSound(here, Sound.BLOCK_ROOTS_BREAK, 1.2f, 0.6f);
            }
            return;
        }
        if (fight.phase() >= 3 || now - state.stillSince < ROOT_MILLIS) {
            return;
        }
        if (!state.rooted) {
            state.rooted = true;
            golem.getWorld().playSound(here, Sound.BLOCK_ROOTS_PLACE, 1.4f, 0.5f);
            service().say(fight, "...racines...");
        }
        AttributeInstance max = golem.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            golem.setHealth(Math.min(service().healthCap(fight), Math.min(max.getValue(), golem.getHealth() + max.getValue() * 0.005 / 4)));
        }
        if (state.ticks % 10 == 0) {
            golem.getWorld().spawnParticle(Particle.BLOCK, here.clone().add(0, 0.2, 0), 10, 1.2, 0.1, 1.2, 0, Material.MANGROVE_ROOTS.createBlockData());
        }
    }

    /** Fragments de mousse : arrivent des bords, marchent vers lui ; chacun qui l'atteint le soigne et l'arme. */
    private void fragments(BossFight fight, State state, long now) {
        LivingEntity golem = fight.entity();
        state.fragments.removeIf(id -> !(org.bukkit.Bukkit.getEntity(id) instanceof IronGolem fragment) || !fragment.isValid() || fragment.isDead());
        if (now >= state.nextFragments) {
            state.nextFragments = now + FRAGMENT_MILLIS;
            Location home = fight.home();
            double edge = fight.leash() > 0 ? Math.min(fight.leash() * 0.8, 18) : 16;
            for (int i = 0; i < 3; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                Location spot = Blink.safeSpot(home.clone().add(Math.cos(angle) * edge, 0, Math.sin(angle) * edge));
                if (spot == null) {
                    continue;
                }
                IronGolem fragment = Minions.spawn(spot, IronGolem.class, small -> {
                    small.setPlayerCreated(false);
                    small.customName(Component.text("Fragment de mousse"));
                    small.setCustomNameVisible(true);
                    base(small, Attribute.SCALE, 0.45);
                    base(small, Attribute.MAX_HEALTH, 20);
                    small.setHealth(20);
                    base(small, Attribute.MOVEMENT_SPEED, 0.2);
                    base(small, Attribute.ATTACK_DAMAGE, 2);
                });
                service().own(fight, fragment);
                state.fragments.add(fragment.getUniqueId());
                spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0, Material.MOSS_BLOCK.createBlockData());
            }
            golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_MOSS_BREAK, 2f, 0.5f);
        }
        for (UUID id : List.copyOf(state.fragments)) {
            if (!(org.bukkit.Bukkit.getEntity(id) instanceof IronGolem fragment)) {
                continue;
            }
            fragment.setTarget(null);
            if (fragment.getLocation().distanceSquared(golem.getLocation()) < 9) {
                absorb(fight, state, fragment);
            } else if (state.ticks % 10 == 0) {
                fragment.getPathfinder().moveTo(golem.getLocation(), 1.0);
            }
        }
    }

    private void absorb(BossFight fight, State state, IronGolem fragment) {
        state.fragments.remove(fragment.getUniqueId());
        fragment.getWorld().spawnParticle(Particle.BLOCK, fragment.getLocation().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0, Material.MOSS_BLOCK.createBlockData());
        fragment.remove();
        LivingEntity golem = fight.entity();
        AttributeInstance max = golem.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            golem.setHealth(Math.min(service().healthCap(fight), Math.min(max.getValue(), golem.getHealth() + max.getValue() * 0.05)));
        }
        state.stacks = Math.min(MAX_STACKS, state.stacks + 1);
        golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_MOSS_PLACE, 2f, 0.6f);
        golem.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, golem.getLocation().add(0, 3, 0), 15, 1, 1, 1, 0);
    }

    /** Dernier éboulement (à 10 %) : 8 s de rochers sur ceux qu'il voit ; un pilier entre lui et soi protège. */
    private void lastStand(BossFight fight, State state, long now) {
        LivingEntity golem = fight.entity();
        AttributeInstance max = golem.getAttribute(Attribute.MAX_HEALTH);
        double part = max == null ? 1 : golem.getHealth() / max.getValue();
        if (!state.lastStandDone && part <= 0.1) {
            state.lastStandDone = true;
            state.lastStandUntil = now + LAST_STAND_MILLIS;
            service().say(fight, "TOUT... S'EFFONDRE !");
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.5f);
        }
        if (now >= state.lastStandUntil || now < state.nextRain) {
            return;
        }
        state.nextRain = now + 1000;
        List<Location> spots = new ArrayList<>();
        for (Player player : golem.getLocation().getNearbyPlayers(48)) {
            if (Hits.fighting(player) && golem.hasLineOfSight(player)) {
                spots.add(player.getLocation());
            } else if (Hits.fighting(player)) {
                // À couvert : un rocher tombe à côté, pour le décor
                spots.add(player.getLocation().add(ThreadLocalRandom.current().nextDouble(-6, 6), 0, ThreadLocalRandom.current().nextDouble(-6, 6)));
            }
        }
        lastStandRock.strike(new AbilityContext(caster(fight), brain(fight).target(), null), spots);
    }

    /** Point faible dans le dos ; enraciné : 30 % de moins. */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        State state = state(fight);
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            LivingEntity golem = fight.entity();
            Vector facing = golem.getLocation().getDirection().setY(0);
            Vector toAttacker = damager.getLocation().toVector().subtract(golem.getLocation().toVector()).setY(0);
            if (facing.lengthSquared() > 0 && toAttacker.lengthSquared() > 0 && facing.angle(toAttacker) > Math.toRadians(110)) {
                event.setDamage(event.getDamage() * (fight.phase() >= 3 ? 1.5 : 1.3));
                golem.getWorld().spawnParticle(Particle.GLOW, golem.getLocation().add(0, golem.getHeight() * 0.6, 0), 8, 0.4, 0.4, 0.4, 0);
            }
        }
        if (state.rooted) {
            event.setDamage(event.getDamage() * 0.7);
        }
        super.onDamaged(fight, event);
    }

    @Override
    public void onDeath(BossFight fight) {
        LivingEntity golem = fight.entity();
        golem.getWorld().spawnParticle(Particle.BLOCK, golem.getLocation().add(0, 2, 0), 120, 1.5, 2, 1.5, 0.1, Material.MOSSY_STONE_BRICKS.createBlockData());
        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 2f, 0.4f);
        super.onDeath(fight);
    }

    @Override
    public void onReset(BossFight fight) {
        super.onReset(fight);
        states.remove(fight.id());
    }

    @Override
    public void onEnd(BossFight fight) {
        super.onEnd(fight);
        states.remove(fight.id());
    }

    // ---------- Poigne ----------

    /** Poigne : il saisit sa cible, la soulève, puis la projette au loin (dégâts, puis la chute). */
    private static final class Grip extends Ability {

        Grip(ConfigurationSection config) {
            super("grip", config, new Defaults(Trigger.ACTIVE, 16, 0.8, 0, 6, 1, 8, 0, 0, 0));
        }

        @Override
        public boolean interruptible() {
            return false;
        }

        @Override
        public double score(AbilityContext context) {
            return context.situation().distance() <= 5 ? 0.55 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity target = context.target();
            LivingEntity self = context.self();
            if (!(target instanceof Player player) || !Hits.fighting(player) || player.getLocation().distance(self.getLocation()) > 6.5) {
                return;
            }
            self.getWorld().playSound(self.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.5f, 0.5f);
            player.setVelocity(new Vector(0, 1.3, 0));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isValid() || !self.isValid()) {
                        return;
                    }
                    Vector away = player.getLocation().toVector().subtract(self.getLocation().toVector()).setY(0);
                    away = away.lengthSquared() < 0.01 ? self.getLocation().getDirection().setY(0) : away;
                    player.setVelocity(away.normalize().multiply(1.8).setY(0.4));
                    hit(context, player, damage, self.getLocation());
                    self.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
                }
            }.runTaskLater(Services.plugin(), 14);
        }
    }

    /** Pour les tests : la phase d'une capacité par son id. */
    int phaseOf(String abilityId) {
        for (int phase = 1; phase <= 3; phase++) {
            for (Ability ability : abilities(phase)) {
                if (ability.id().equals(abilityId)) {
                    return phase;
                }
            }
        }
        return 0;
    }
}
