package fr.eternom.eterMob.module.boss.disciple;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Burrow;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Pillar;
import fr.eternom.eterMob.module.ability.kind.Slam;
import fr.eternom.eterMob.module.ability.kind.Strike;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le Fossoyeur, disciple de Mortarius : il creusait les tombes des expériences ratées de son maître. Brute qui contrôle
 * le terrain (beaucoup d'armure, peu de résistance magique : les mages le font fondre), dégâts physiques.
 * <ul>
 *   <li>Passifs : Lourd (aucun recul, peu de contrôle) ; Pelle tranchante (ses coups font saigner).</li>
 *   <li>Phase 1, le Creuseur : Coup de pelle (cône, 2 piles de saignement), Pic d'os, Cage d'os, Enterrement (sous
 *   terre, ressort sous sa cible).</li>
 *   <li>Phase 2, la Fosse commune : Tombes ouvertes (3 tombes lâchent une goule toutes les 15 s ; rester 2 s dessus pour
 *   la refermer, elle se rouvre 40 s plus tard), Pelletée (ralenti, aveuglé), Fosse commune (enracine autour de lui,
 *   2 goules).</li>
 *   <li>Rage à 25 % : plus rapide, Enterrement plus fréquent.</li>
 * </ul>
 */
public final class Gravedigger extends SubBoss {

    public static final String ID = "fossoyeur";
    private static final long GHOUL_MILLIS = 15_000;
    private static final long REOPEN_MILLIS = 40_000;
    private static final long SEAL_MILLIS = 2_000;

    /** Une tombe ouverte de la phase 2. */
    private static final class Grave {
        final Location at;
        final BlockDisplay stone;
        final TextDisplay label;
        boolean open = true;
        long nextGhoul;
        long reopenAt;
        long sealing;

        Grave(Location at, BlockDisplay stone, TextDisplay label) {
            this.at = at;
            this.stone = stone;
            this.label = label;
        }
    }

    private static final class State {
        final List<Grave> graves = new ArrayList<>();
    }

    private final Map<UUID, State> states = new HashMap<>();
    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();
    private List<Ability> phase3 = List.of();
    private NamespacedKey rageKey;

    public Gravedigger() {
        super(ID, "Le Fossoyeur", "Disciple de Mortarius", 600, 220, 30, 0.25, Map.of("attack", 10.0, "shovel", 10.0, "spike", 8.0,
                "burrow", 8.0, "shovelful", 4.0, "pit", 7.0));
    }

    @Override
    protected void build() {
        Ability bleedHit = new Strike(Spec.of("id", "pelle-tranchante", "chance", 0.5, "damage", 0, "cooldown", 1, "states", List.of("bleed 1 4")));
        Ability shovel = new Cleave(Spec.of("id", "coup-de-pelle", "angle", 90, "radius", 3.5, "damage", damage("shovel"), "cooldown", 6,
                "cast", 0.7, "states", List.of("bleed 1 4", "bleed 1 4")));
        Ability spike = new Pillar(Spec.of("id", "pic-d-os", "mode", "spike", "damage", damage("spike"), "cooldown", 12, "cast", 1.2,
                "states", List.of("root 1 1")));
        Ability cage = new Pillar(Spec.of("id", "cage-d-os", "mode", "cage", "seconds", 5, "cooldown", 18, "cast", 1));
        Ability burrow = new Burrow(Spec.of("id", "enterrement", "damage", damage("burrow"), "radius", 2.5, "cooldown", 16));
        phase1 = List.of(bleedHit, shovel, spike, cage, burrow);
        Ability shovelful = new Cleave(Spec.of("id", "pelletee", "angle", 70, "radius", 6, "damage", damage("shovelful"), "cooldown", 12,
                "cast", 0.6, "blind", 1, "states", List.of("slow 0.4 2")));
        Ability pit = new Pit(Spec.of("id", "fosse-commune", "radius", 5, "damage", damage("pit"), "cooldown", 22, "cast", 1.4,
                "states", List.of("root 1 1.5")));
        phase2 = List.of(bleedHit, shovel, spike, burrow, shovelful, pit);
        Ability fastBurrow = new Burrow(Spec.of("id", "enterrement", "damage", damage("burrow"), "radius", 2.5, "cooldown", 8));
        phase3 = List.of(bleedHit, shovel, spike, fastBurrow, shovelful, pit);
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
        return phase <= 1 ? 5 : phase == 2 ? 4 : 3.5;
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.BONE, 6, 12, 1, 1, null),
                new LootTable.Entry(Material.IRON_INGOT, 3, 7, 0.8, 1, null),
                new LootTable.Entry(Material.ROOTED_DIRT, 1, 1, 0.5, 1, "terre-des-tombes"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return Material.IRON_SHOVEL;
    }

    @Override
    public List<String> lore() {
        return List.of("Disciple de Mortarius, brute qui contrôle le terrain.", "Marais et plaines, la nuit.");
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
        zombie.setCanBreakDoors(false);
        Minions.noReinforcements(zombie);
        base(zombie, Attribute.SCALE, 1.4);
        base(zombie, Attribute.KNOCKBACK_RESISTANCE, 1);
        base(zombie, Attribute.FOLLOW_RANGE, 48);
        base(zombie, Attribute.ATTACK_DAMAGE, damage("attack"));
        wear(zombie, EquipmentSlot.HAND, Material.IRON_SHOVEL);
        wear(zombie, EquipmentSlot.CHEST, Material.LEATHER_CHESTPLATE);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5, 0.25};
    }

    @Override
    public String phaseName(int phase) {
        return switch (phase) {
            case 1 -> "Le Creuseur";
            case 2 -> "La Fosse commune";
            default -> "La Rage";
        };
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.YELLOW : phase == 2 ? BossBar.Color.GREEN : BossBar.Color.RED;
    }

    @Override
    public String phaseLine(int phase) {
        return switch (phase) {
            case 2 -> "J'ai creusé des tombes pour tout le monde.";
            case 3 -> "PLUS PROFOND !";
            default -> null;
        };
    }

    @Override
    public String spawnLine() {
        return "Encore un trou à remplir.";
    }

    @Override
    public String deathLine() {
        return "Enterrez-moi... bien...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    private static JavaPlugin plugin() {
        return JavaPlugin.getProvidingPlugin(Gravedigger.class);
    }

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        rageKey = new NamespacedKey(plugin(), "fossoyeur_rage");
    }

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        super.onPhase(fight, phase, resumed);
        if (phase >= 2 && state(fight).graves.isEmpty()) {
            openGraves(fight);
        }
        AttributeInstance speed = fight.entity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && rageKey != null) {
            speed.removeModifier(rageKey);
            if (phase >= 3) {
                speed.addModifier(new AttributeModifier(rageKey, 0.15, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    @Override
    public void tick(BossFight fight) {
        super.tick(fight);
        if (fight.dying() || fight.phase() < 2) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Grave grave : state(fight).graves) {
            if (!grave.open) {
                if (now >= grave.reopenAt) {
                    grave.open = true;
                    grave.nextGhoul = now + GHOUL_MILLIS;
                    grave.stone.setGlowing(true);
                    grave.label.text(MiniMessage.miniMessage().deserialize("<dark_red>Tombe ouverte"));
                    grave.at.getWorld().playSound(grave.at, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.5f);
                }
                continue;
            }
            grave.at.getWorld().spawnParticle(Particle.SMOKE, grave.at.clone().add(0, 0.3, 0), 3, 0.4, 0.1, 0.4, 0.01);
            boolean someone = false;
            for (Player player : grave.at.getNearbyPlayers(1.6)) {
                if (Hits.fighting(player)) {
                    someone = true;
                    break;
                }
            }
            if (someone) {
                if (grave.sealing == 0) {
                    grave.sealing = now;
                }
                grave.at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, grave.at.clone().add(0, 0.5, 0), 4, 0.4, 0.3, 0.4, 0);
                if (now - grave.sealing >= SEAL_MILLIS) {
                    seal(grave, now);
                }
            } else {
                grave.sealing = 0;
                if (now >= grave.nextGhoul) {
                    grave.nextGhoul = now + GHOUL_MILLIS;
                    ghoul(fight, grave.at);
                }
            }
        }
    }

    private void openGraves(BossFight fight) {
        State state = state(fight);
        BlockData stone = Material.MOSSY_COBBLESTONE.createBlockData();
        Location center = fight.entity().getLocation();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 * i / 3 + 0.4;
            Location spot = Blink.safeSpot(center.clone().add(Math.cos(angle) * 7, 0, Math.sin(angle) * 7));
            if (spot == null) {
                continue;
            }
            BlockDisplay display = spot.getWorld().spawn(spot, BlockDisplay.class, block -> {
                block.setBlock(stone);
                block.setPersistent(false);
                block.setGlowing(true);
                block.setTransformation(new Transformation(new Vector3f(-0.5f, -0.4f, -0.8f), new AxisAngle4f(),
                        new Vector3f(1, 0.5f, 1.6f), new AxisAngle4f()));
            });
            TextDisplay label = spot.getWorld().spawn(spot.clone().add(0, 1.3, 0), TextDisplay.class, text -> {
                text.setPersistent(false);
                text.setBillboard(Display.Billboard.CENTER);
                text.text(MiniMessage.miniMessage().deserialize("<dark_red>Tombe ouverte"));
            });
            service().own(fight, display);
            service().own(fight, label);
            Grave grave = new Grave(spot, display, label);
            grave.nextGhoul = now + 5_000;
            state.graves.add(grave);
            spot.getWorld().playSound(spot, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        }
        service().say(fight, "Trois tombes. Trois bouches. Fermez-les si vous l'osez.");
    }

    private static void seal(Grave grave, long now) {
        grave.open = false;
        grave.sealing = 0;
        grave.reopenAt = now + REOPEN_MILLIS;
        grave.stone.setGlowing(false);
        grave.label.text(MiniMessage.miniMessage().deserialize("<gray>Tombe refermée"));
        grave.at.getWorld().playSound(grave.at, Sound.BLOCK_STONE_PLACE, 1.4f, 0.6f);
        grave.at.getWorld().spawnParticle(Particle.BLOCK, grave.at.clone().add(0, 0.3, 0), 20, 0.4, 0.2, 0.4, 0,
                Material.MOSSY_COBBLESTONE.createBlockData());
    }

    /** Une goule : une husk qui sort du sol et fait saigner, sans butin. */
    private void ghoul(BossFight fight, Location at) {
        Husk ghoul = Minions.spawn(at, Husk.class, husk -> {
            husk.setAdult();
            husk.customName(net.kyori.adventure.text.Component.text("Goule"));
            Minions.hitStates(husk, "bleed 1 3");
            LivingEntity target = brain(fight).target();
            if (target != null) {
                husk.setTarget(target);
            }
        });
        service().own(fight, ghoul);
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 20, 0.4, 0.2, 0.4, 0,
                at.getBlock().getRelative(0, -1, 0).getBlockData());
    }

    @Override
    public void onEnd(BossFight fight) {
        super.onEnd(fight);
        states.remove(fight.id());
    }

    @Override
    public void onReset(BossFight fight) {
        super.onReset(fight);
        states.remove(fight.id());
    }

    /** Fosse commune : frappe du sol (enracine tout le monde autour de lui), puis 2 goules en sortent. */
    private final class Pit extends Ability {

        private final Slam slam;

        Pit(ConfigurationSection config) {
            super("pit", config, new Defaults(Trigger.ACTIVE, 22, 1.4, 0, 5, 1, 7, 5, 0.3, 0.2));
            this.slam = new Slam(config);
        }

        @Override
        public boolean interruptible() {
            return false;
        }

        @Override
        public double score(AbilityContext context) {
            return slam.score(context) + 0.1;
        }

        @Override
        public void begin(AbilityContext context) {
            slam.begin(context);
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            slam.telegraph(context, progress);
        }

        @Override
        public void cast(AbilityContext context) {
            slam.cast(context);
            BossFight fight = ((BrainBoss.BossCaster) context.caster()).fight();
            for (int i = 0; i < 2; i++) {
                Location spot = Blink.safeSpot(context.self().getLocation().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2));
                if (spot != null) {
                    ghoul(fight, spot);
                }
            }
        }
    }
}
