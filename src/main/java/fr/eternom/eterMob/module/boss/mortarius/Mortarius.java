package fr.eternom.eterMob.module.boss.mortarius;

import fr.eternom.eterLib.module.combat.DamageKind;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Charge;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Curse;
import fr.eternom.eterMob.module.ability.kind.Leap;
import fr.eternom.eterMob.module.ability.kind.Pull;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ability.kind.Tombs;
import fr.eternom.eterMob.module.ability.kind.Vortex;
import fr.eternom.eterMob.module.ability.kind.Wave;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Tier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mortarius, Seigneur des Tombes. Un nécromancien mort depuis longtemps, tué par le livre qu'il avait écrit sur le
 * contrôle des âmes : c'est maintenant le Livre des morts qui le contrôle. Mage-guerrier tank, pensé pour un donjon de
 * 4 joueurs au plus (faisable seul, en longtemps). Ses coups de masse sont lourds, télégraphiés, et ne s'interrompent
 * pas ; ses sorts du Livre sont plus vicieux mais s'interrompent (étourdi, silence) : c'est le rôle des joueurs.
 * <ul>
 *   <li>Base : zombie 2,5 fois plus grand, 1 000 PV, résistance 1,5 (+35 % par joueur en plus, plafonnée), armure
 *   (≈ 200 avec son équipement), résistance magique 80, pénétration d'armure 35 %, ne brûle pas, un tiers des
 *   contrôles ; rayon de déplacement maximal autour de son point d'apparition (config, ou la salle du donjon).</li>
 *   <li>Passifs : pas lourds ; aura de pourriture (dégâts bruts, empêche les soins : état rot) ; seigneur de la horde
 *   (les morts-vivants proches prennent sa cible) ; Marque du Livre sur sa cible ; bond (gap closer) ; rage à 15 min.</li>
 *   <li>Phase 1, le Seigneur : coup de masse en arc, Levée des morts (sort), Lecture funeste (sort : silence).</li>
 *   <li>Phase 2, le Bouclier d'os : le Livre est planté au sol, 4 piliers à rôle (Garde : intouchable tant qu'il tient ;
 *   Vie : il se soigne ; Tourment : aura élargie ; Appel : les morts se lèvent plus souvent) ; tous tombés : vulnérable
 *   60 s, puis ils se relèvent ; charge (sonné 3 s dans un mur), Chaînes d'âmes (sort), masse.</li>
 *   <li>Phase 3, le Livre prend le contrôle : plus rapide ; triple onde sismique, tombes, aspiration (sort : étourdi
 *   1,5 s), masse ; dernier souffle à 10 % (4 morts, aura à 8 blocs).</li>
 *   <li>Mort : le Livre tombe et se referme ; butin personnel dont une Page du Livre des morts.</li>
 * </ul>
 */
public final class Mortarius extends BrainBoss {

    public static final String ID = "mortarius";
    private static final double SCALE = 2.5;
    private static final int PILLARS = 4;
    private static final double PILLAR_RADIUS = 5;
    private static final double PILLAR_SCALE = 1.7;
    private static final float PILLAR_HEIGHT = 3.4f;

    private enum Role {
        GUARD("Garde", Color.fromRGB(0xE0C060), "<gold>"),
        LIFE("Vie", Color.fromRGB(0x3FD15A), "<green>"),
        TORMENT("Tourment", Color.fromRGB(0x9B30FF), "<light_purple>"),
        CALL("Appel", Color.fromRGB(0x8A0303), "<dark_red>");

        final String label;
        final Color color;
        final String tag;

        Role(String label, Color color, String tag) {
            this.label = label;
            this.color = color;
            this.tag = tag;
        }
    }

    /** Un pilier d'os : le bloc affiché, son corps invisible (qui reçoit les coups), son étiquette, son rôle, ses PV. */
    private static final class Pillar {
        final BlockDisplay display;
        final LivingEntity body;
        final TextDisplay label;
        final Role role;
        double health;

        Pillar(BlockDisplay display, LivingEntity body, TextDisplay label, Role role, double health) {
            this.display = display;
            this.body = body;
            this.label = label;
            this.role = role;
            this.health = health;
        }
    }

    /** Ce que Mortarius garde pour un combat. */
    private static final class State {
        final Map<UUID, Pillar> pillars = new HashMap<>();
        ItemDisplay book;
        long shieldBackAt;
        long windowUntil;
        boolean lastBreath;
        long nextAura;
        long nextHorde;
        long nextCall;
        long ticks;
    }

    // Réglages (config.yml > bosses.mortarius)
    private double health = 1000;
    private double toughness = 1.5;
    private double armor = 110;
    private double magicResist = 80;
    private double piercing = 0.35;
    private double leash = 40;
    private double enrage = 15;
    private double attack = 12;
    private double pillarHealth = 80;
    private long windowMillis = 60_000;
    private double[] aura = {1, 2, 2};
    private final Map<String, Double> damage = new HashMap<>(Map.of("cleave", 16.0, "leap", 10.0, "charge", 10.0,
            "wave", 10.0, "tombs", 12.0, "vortex", 4.0, "curse", 3.0, "chains", 3.0));

    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();
    private List<Ability> phase3 = List.of();
    private Summon raise;
    private Summon lastStand;
    private final Map<UUID, State> states = new HashMap<>();
    private NamespacedKey rageKey;

    public Mortarius() {
        super(ID, "Mortarius", "Seigneur des Tombes");
    }

    // ---------- Réglages et capacités ----------

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
            attack = section.getDouble("damage.attack", attack);
            pillarHealth = section.getDouble("pillar-health", pillarHealth);
            windowMillis = Math.round(section.getDouble("window-seconds", 60) * 1000);
            List<Double> auraList = section.getDoubleList("damage.aura");
            if (auraList.size() == 3) {
                aura = new double[]{auraList.get(0), auraList.get(1), auraList.get(2)};
            }
            for (String key : List.copyOf(damage.keySet())) {
                damage.put(key, section.getDouble("damage." + key, damage.get(key)));
            }
        }
        build();
    }

    private void build() {
        Ability cleave = new Cleave(Spec.of("id", "masse", "damage", damage.get("cleave"), "cooldown", 7, "cast", 0.8,
                "states", List.of("fragile 0.2 5")));
        Ability leap = new Leap(Spec.of("id", "bond", "damage", damage.get("leap"), "range", List.of(9.0, 26.0), "cooldown", 12,
                "cast", 0.6, "interruptible", false, "weight", 0.9));
        Ability curse = new Curse(Spec.of("id", "lecture-funeste", "damage", damage.get("curse"), "cast", 1, "cooldown", 14,
                "range", List.of(4.0, 26.0), "weight", 1.1, "states", List.of("silence 1 2", "slow 0.3 3")));
        raise = new Summon(Spec.of("id", "levee-des-morts", "mobs", List.of("ZOMBIE"), "count", List.of(2, 3), "max-alive", 6,
                "around", "players", "cast", 1.5, "cooldown", 25));
        Ability raiseRare = new Summon(Spec.of("id", "levee-des-morts", "mobs", List.of("ZOMBIE"), "count", List.of(2, 3),
                "max-alive", 6, "around", "players", "cast", 1.5, "cooldown", 40));
        Ability charge = new Charge(Spec.of("id", "charge", "damage", damage.get("charge"), "range", List.of(8.0, 20.0),
                "cooldown", 14, "cast", 1, "wall-stun", 3, "interruptible", false));
        Ability chains = new Pull(Spec.of("id", "chaines-d-ames", "damage", damage.get("chains"), "damage-type", "magic",
                "root-first", 1.5, "range", List.of(6.0, 22.0), "cooldown", 18, "cast", 0.8));
        Ability wave = new Wave(Spec.of("id", "onde-sismique", "damage", damage.get("wave"), "rings", 3, "radius", 9,
                "cooldown", 14, "cast", 1, "interruptible", false));
        Ability tombs = new Tombs(Spec.of("id", "tombes", "damage", damage.get("tombs"), "cooldown", 16, "cast", 1.2,
                "states", List.of("root 1 1"), "interruptible", false));
        Ability vortex = new Vortex(Spec.of("id", "aspiration", "damage", damage.get("vortex"), "radius", 14, "cooldown", 24,
                "cast", 1.5, "states", List.of("stun 1 1.5")));
        lastStand = new Summon(Spec.of("id", "dernier-souffle", "mobs", List.of("ZOMBIE"), "count", List.of(4, 4), "max-alive", 10,
                "around", "players", "cooldown", 0, "cast", 0));
        phase1 = List.of(cleave, leap, curse, raise);
        phase2 = List.of(cleave, leap, charge, chains, raiseRare);
        Ability fastCleave = new Cleave(Spec.of("id", "masse", "damage", damage.get("cleave"), "cooldown", 6, "cast", 0.7,
                "states", List.of("fragile 0.2 5")));
        phase3 = List.of(fastCleave, leap, wave, tombs, vortex);
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
            case 1 -> 7;
            case 2 -> 6;
            default -> 5;
        };
    }

    @Override
    protected AiProfile ai() {
        AiProfile base = AiProfile.defaults(Tier.BOSS);
        // Il veut achever et punir les tireurs, sans lâcher sa cible pour un rien
        return new AiProfile(48, 1.2, 0.4, 0.7, 0.6, 0.8, 0, 1.3, 3000, 7, base.threshold(), true);
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
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.PAPER, 1, 1, 1, 1, "page-livre-des-morts"),
                new LootTable.Entry(Material.NETHERITE_SCRAP, 1, 2, 0.35, 1, null),
                new LootTable.Entry(Material.BONE_BLOCK, 4, 8, 1, 1, null),
                new LootTable.Entry(Material.EMERALD, 4, 10, 0.8, 1, null)));
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return Material.ZOMBIE_HEAD;
    }

    @Override
    public List<String> lore() {
        return List.of("Nécromancien tenu par le Livre des morts.", "Mage-guerrier tank, donjon de 4 joueurs.");
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
        return 5;
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
        return Zombie.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        Zombie zombie = (Zombie) entity;
        zombie.setAdult();
        zombie.setCanBreakDoors(false);
        zombie.setShouldBurnInDay(false);
        Minions.noReinforcements(zombie);
        base(zombie, Attribute.SCALE, SCALE);
        base(zombie, Attribute.KNOCKBACK_RESISTANCE, 1);
        base(zombie, Attribute.FOLLOW_RANGE, 64);
        base(zombie, Attribute.ATTACK_DAMAGE, attack);
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            wear(equipment, EquipmentSlot.HEAD, Material.NETHERITE_HELMET);
            wear(equipment, EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE);
            wear(equipment, EquipmentSlot.HAND, Material.MACE);
        }
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void wear(EntityEquipment equipment, EquipmentSlot slot, Material material) {
        equipment.setItem(slot, ItemStack.of(material));
        equipment.setDropChance(slot, 0);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.66, 0.33};
    }

    @Override
    public String phaseName(int phase) {
        return switch (phase) {
            case 1 -> "Le Seigneur";
            case 2 -> "Le Bouclier d'os";
            default -> "Le Livre prend le contrôle";
        };
    }

    @Override
    public BossBar.Color color(int phase) {
        return switch (phase) {
            case 1 -> BossBar.Color.RED;
            case 2 -> BossBar.Color.WHITE;
            default -> BossBar.Color.PURPLE;
        };
    }

    @Override
    public String phaseLine(int phase) {
        return switch (phase) {
            case 2 -> "Mes os seront votre mur. Brisez-les si vous le pouvez.";
            case 3 -> "Ce n'est plus moi qui parle... c'est LUI.";
            default -> null;
        };
    }

    @Override
    public String spawnLine() {
        return "Encore des âmes pour le Livre...";
    }

    @Override
    public String deathLine() {
        return "Enfin... il se tait...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    private static JavaPlugin plugin() {
        return Services.plugin();
    }

    // ---------- Combat ----------

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        rageKey = new NamespacedKey(plugin(), "mortarius_rage");
        State state = state(fight);
        if (state.book == null || !state.book.isValid()) {
            spawnBook(fight, state);
        }
    }

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        super.onPhase(fight, phase, resumed);
        State state = state(fight);
        if (phase >= 2 && (state.book == null || !state.book.isValid())) {
            spawnBook(fight, state);
        }
        if (phase == 2) {
            state.shieldBackAt = 0;
            state.windowUntil = 0;
            raiseShield(fight, state);
        }
        if (phase >= 3) {
            breakShield(fight, state, false);
            rage(fight.entity(), true);
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
        LivingEntity boss = fight.entity();
        moveBook(fight, state);
        steps(boss, state);
        if (now >= state.nextAura) {
            state.nextAura = now + 2000;
            rotAura(fight, state);
            horde(fight);
            markTarget(fight);
        }
        if (fight.phase() == 2) {
            pillarPhase(fight, state, now);
        }
        if (fight.phase() >= 3 && !state.lastBreath && healthPart(boss) <= 0.1) {
            state.lastBreath = true;
            service().say(fight, "Le Livre... ne me laissera pas tomber...");
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);
            lastStand.cast(new AbilityContext(caster(fight), brain(fight).target(), null));
        }
    }

    private static double healthPart(LivingEntity boss) {
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 1 : boss.getHealth() / max.getValue();
    }

    /** Pas lourds : on l'entend venir. */
    private static void steps(LivingEntity boss, State state) {
        Vector velocity = boss.getVelocity();
        if (state.ticks % 10 == 0 && boss.isOnGround() && velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.002) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 1.4f, 0.5f);
            Block below = boss.getLocation().getBlock().getRelative(0, -1, 0);
            if (below.getType().isSolid()) {
                boss.getWorld().spawnParticle(Particle.BLOCK, boss.getLocation(), 8, 0.6, 0.05, 0.6, 0, below.getBlockData());
            }
        }
    }

    /** Aura de pourriture : dégâts bruts, et plus aucun soin tant qu'on y reste (état rot) ; Faiblesse dès la phase 2. */
    private void rotAura(BossFight fight, State state) {
        LivingEntity boss = fight.entity();
        double radius = auraRadius(fight, state);
        double amount = aura[Math.clamp(fight.phase(), 1, 3) - 1];
        Particle.DustOptions rot = new Particle.DustOptions(Color.fromRGB(0x5E8C31), 1.4f);
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0, rot);
        }
        for (Player player : boss.getLocation().getNearbyPlayers(radius)) {
            if (!Hits.fighting(player)) {
                continue;
            }
            Hits.abilityDamage(player, amount, boss, DamageKind.TRUE);
            if (Services.states() != null) {
                Services.states().apply(player, "rot", 1, 2.5, boss);
                if (fight.phase() >= 2) {
                    Services.states().apply(player, "weakness", 0.15, 3, boss);
                }
            }
        }
    }

    private double auraRadius(BossFight fight, State state) {
        if (state.lastBreath) {
            return 8;
        }
        if (fight.phase() >= 3) {
            return 7;
        }
        return fight.phase() == 2 && has(state, Role.TORMENT) ? 9 : 6;
    }

    /** Seigneur de la horde : les morts-vivants proches sans cible prennent la sienne. */
    private void horde(BossFight fight) {
        LivingEntity target = brain(fight).target();
        if (target == null) {
            return;
        }
        for (Entity entity : fight.entity().getNearbyEntities(24, 12, 24)) {
            if ((entity instanceof Zombie || entity instanceof AbstractSkeleton) && entity instanceof Mob mob
                    && mob.getTarget() == null && !(fight.owned().contains(entity))) {
                mob.setTarget(target);
            }
        }
    }

    /** Marque du Livre : sa cible prend 15 % de dégâts en plus de tout le monde. */
    private void markTarget(BossFight fight) {
        LivingEntity target = brain(fight).target();
        if (target instanceof Player && Services.states() != null) {
            Services.states().apply(target, "mark", 0.15, 3, fight.entity());
        }
    }

    private void rage(LivingEntity boss, boolean on) {
        AttributeInstance speed = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null || rageKey == null) {
            return;
        }
        speed.removeModifier(rageKey);
        if (on) {
            speed.addModifier(new AttributeModifier(rageKey, 0.1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    // ---------- Le Livre ----------

    private void spawnBook(BossFight fight, State state) {
        LivingEntity boss = fight.entity();
        state.book = boss.getWorld().spawn(boss.getLocation().add(0, 3, 0), ItemDisplay.class, display -> {
            display.setItemStack(ItemStack.of(Material.ENCHANTED_BOOK));
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTeleportDuration(5);
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(0x5A0F8C));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
        });
        service().own(fight, state.book);
    }

    /** Phase 1 : il flotte à son épaule ; phase 2 : planté au sol, au centre ; phase 3 : collé à sa poitrine. */
    private void moveBook(BossFight fight, State state) {
        if (state.book == null || !state.book.isValid()) {
            return;
        }
        LivingEntity boss = fight.entity();
        Location at;
        double bob = Math.sin(state.ticks / 10.0) * 0.15;
        if (fight.phase() == 2) {
            at = fight.home().add(0, 1.2 + bob, 0);
        } else {
            Vector forward = boss.getLocation().getDirection().setY(0);
            forward = forward.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : forward.normalize();
            Vector side = new Vector(-forward.getZ(), 0, forward.getX());
            at = fight.phase() >= 3
                    ? boss.getLocation().add(forward.multiply(0.9)).add(0, boss.getHeight() * 0.6, 0)
                    : boss.getLocation().add(side.multiply(1.6)).add(0, boss.getHeight() * 0.85 + bob, 0);
        }
        at.setYaw(boss.getLocation().getYaw());
        state.book.teleport(at);
        if (state.ticks % 20 == 0) {
            state.book.getWorld().spawnParticle(Particle.ENCHANT, at, 6, 0.3, 0.3, 0.3, 0.5);
        }
    }

    // ---------- Phase 2 : les piliers ----------

    private static boolean has(State state, Role role) {
        return state.pillars.values().stream().anyMatch(pillar -> pillar.role == role);
    }

    private void pillarPhase(BossFight fight, State state, long now) {
        if (state.pillars.isEmpty() && state.shieldBackAt > 0 && now >= state.shieldBackAt) {
            state.shieldBackAt = 0;
            state.windowUntil = 0;
            raiseShield(fight, state);
        }
        LivingEntity boss = fight.entity();
        if (has(state, Role.LIFE)) {
            // 0,5 PV par seconde, jamais au-dessus du plafond de la phase
            double cap = service().healthCap(fight);
            boss.setHealth(Math.min(cap, Math.min(boss.getHealth() + 0.125, boss.getAttribute(Attribute.MAX_HEALTH).getValue())));
        }
        if (has(state, Role.CALL) && now >= state.nextCall) {
            state.nextCall = now + 20_000;
            raise.cast(new AbilityContext(caster(fight), brain(fight).target(), null));
        }
        if (state.ticks % 10 == 0) {
            for (Pillar pillar : state.pillars.values()) {
                tether(pillar.body.getLocation().add(0, PILLAR_HEIGHT - 0.3, 0), boss.getLocation().add(0, boss.getHeight() * 0.6, 0),
                        pillar.role.color);
            }
        }
    }

    private void raiseShield(BossFight fight, State state) {
        if (!state.pillars.isEmpty()) {
            return;
        }
        Location center = fight.home();
        BlockData bone = Material.BONE_BLOCK.createBlockData();
        Role[] roles = Role.values();
        service().say(fight, "Garde, Vie, Tourment, Appel. Choisissez lequel tomber en premier.");
        for (int i = 0; i < PILLARS; i++) {
            Role role = roles[i % roles.length];
            double angle = Math.PI / 4 + i * Math.PI / 2;
            Location spot = ground(center.clone().add(Math.cos(angle) * PILLAR_RADIUS, 0, Math.sin(angle) * PILLAR_RADIUS));
            if (spot == null) {
                continue;
            }
            BlockDisplay display = spot.getWorld().spawn(spot, BlockDisplay.class, block -> {
                block.setBlock(bone);
                block.setPersistent(false);
                block.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(),
                        new Vector3f(1, PILLAR_HEIGHT, 1), new AxisAngle4f()));
                block.setGlowing(true);
                block.setGlowColorOverride(role.color);
            });
            Skeleton body = spot.getWorld().spawn(spot, Skeleton.class, support -> {
                support.setInvisible(true);
                support.setSilent(true);
                support.setAI(false);
                support.setGravity(false);
                support.setCollidable(false);
                support.setPersistent(false);
                support.setShouldBurnInDay(false);
                support.setCanPickupItems(false);
                if (support.getEquipment() != null) {
                    support.getEquipment().clear();
                }
                base(support, Attribute.SCALE, PILLAR_SCALE);
            });
            TextDisplay label = spot.getWorld().spawn(spot.clone().add(0, PILLAR_HEIGHT + 0.5, 0), TextDisplay.class, text -> {
                text.setPersistent(false);
                text.setBillboard(Display.Billboard.CENTER);
                text.text(MiniMessage.miniMessage().deserialize(role.tag + role.label + " <white>" + Math.round(pillarHealth)));
            });
            service().own(fight, display);
            service().own(fight, body);
            service().own(fight, label);
            state.pillars.put(body.getUniqueId(), new Pillar(display, body, label, role, pillarHealth));
            spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.1, bone);
            spot.getWorld().playSound(spot, Sound.BLOCK_BONE_BLOCK_PLACE, 1.5f, 0.6f);
        }
    }

    private void breakShield(BossFight fight, State state, boolean window) {
        for (Pillar pillar : new ArrayList<>(state.pillars.values())) {
            destroy(pillar);
        }
        state.pillars.clear();
        if (window) {
            long now = System.currentTimeMillis();
            state.windowUntil = now + windowMillis;
            state.shieldBackAt = now + windowMillis;
            LivingEntity boss = fight.entity();
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);
            service().say(fight, "Mon rempart... ! Profitez-en, il reviendra.");
        }
    }

    private static void destroy(Pillar pillar) {
        Location at = pillar.body.getLocation().add(0, 1.5, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 50, 0.4, 1, 0.4, 0.1, Material.BONE_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1.5f, 0.6f);
        pillar.display.remove();
        pillar.body.remove();
        pillar.label.remove();
    }

    /** Garde debout : intouchable (le coup sonne sur l'os). */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        State state = state(fight);
        if (fight.phase() == 2 && has(state, Role.GUARD)) {
            event.setCancelled(true);
            LivingEntity boss = fight.entity();
            boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, boss.getLocation().add(0, boss.getHeight() * 0.6, 0), 10, 0.6, 0.8, 0.6, 0.1);
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BONE_BLOCK_HIT, 1f, 0.6f);
            return;
        }
        super.onDamaged(fight, event);
    }

    /** Coup sur un pilier : il perd des PV ; il titube quand un pilier tombe ; le dernier ouvre la fenêtre de 60 s. */
    @Override
    public void onOwnedDamaged(BossFight fight, Entity owned, EntityDamageEvent event) {
        State state = state(fight);
        Pillar pillar = state.pillars.get(owned.getUniqueId());
        if (pillar == null) {
            if (owned instanceof Display) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        Player breaker = breaker(event);
        if (breaker == null) {
            return;
        }
        pillar.health -= event.getDamage() / Math.max(1, fight.toughness());
        Location at = owned.getLocation().add(0, 1.2, 0);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.3, 0.5, 0.3, 0.05, Material.BONE_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_HIT, 1f, 0.8f);
        pillar.label.text(MiniMessage.miniMessage().deserialize(pillar.role.tag + pillar.role.label + " <white>"
                + Math.max(0, Math.round(pillar.health))));
        if (pillar.health > 0) {
            return;
        }
        state.pillars.remove(owned.getUniqueId());
        destroy(pillar);
        fight.count(breaker, "piliers");
        service().say(fight, "Mon pilier de " + pillar.role.label + "... !");
        brain(fight).daze(1);
        if (state.pillars.isEmpty()) {
            breakShield(fight, state, true);
        }
    }

    private static Player breaker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        return byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    private static void tether(Location from, Location to, Color color) {
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9f);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        step.normalize().multiply(0.6);
        Location point = from.clone();
        for (double done = 0; done < length; done += 0.6) {
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            point.add(step);
        }
    }

    private static Location ground(Location point) {
        Block block = point.getBlock();
        for (int i = 0; i < 8; i++) {
            if (block.isPassable() && block.getRelative(0, -1, 0).getType().isSolid()) {
                return block.getLocation().add(0.5, 0, 0.5);
            }
            block = block.getRelative(0, block.isPassable() ? -1 : 1, 0);
        }
        return null;
    }

    // ---------- Fin ----------

    @Override
    public void onDeath(BossFight fight) {
        State state = state(fight);
        if (state.book != null && state.book.isValid()) {
            Location at = state.book.getLocation();
            at.getWorld().spawnParticle(Particle.SOUL, at, 40, 0.5, 0.5, 0.5, 0.05);
            at.getWorld().playSound(at, Sound.ITEM_BOOK_PUT, 1.5f, 0.5f);
            at.getWorld().playSound(at, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.4f);
        }
        super.onDeath(fight);
    }

    @Override
    public void onReset(BossFight fight) {
        super.onReset(fight);
        rage(fight.entity(), false);
        states.remove(fight.id());
        spawnBook(fight, state(fight));
    }

    @Override
    public void onEnd(BossFight fight) {
        super.onEnd(fight);
        states.remove(fight.id());
    }

    /** Pour /etermob inspect et le bilan : les piliers debout. */
    public Map<String, Double> pillars(BossFight fight) {
        Map<String, Double> list = new HashMap<>();
        State state = states.get(fight.id());
        if (state != null) {
            state.pillars.values().forEach(pillar -> list.put(pillar.role.label, pillar.health));
        }
        return list;
    }

}
