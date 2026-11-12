package fr.eternom.eterMob.module.boss.disciple;

import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterLib.module.combat.StatSheet;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Beam;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Curse;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.ai.Brain;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Tier;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Vex;
import org.bukkit.inventory.EquipmentSlot;
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
 * Le Scribe, disciple de Mortarius : il a recopié le Livre, l'encre lui coule encore des doigts. Mage d'arrière-ligne
 * (peu d'armure, beaucoup de résistance magique : on le frappe au corps à corps), dégâts magiques.
 * <ul>
 *   <li>Passifs : Encre collante (ses projectiles ralentissent) ; Manuscrit : toutes les 20 s, une pile de Chapitre
 *   (+10 % de dégâts magiques, 3 au plus) ; tuer une Page volante lui en retire une.</li>
 *   <li>Phase 1, la Copie : Trait d'encre, Lecture interdite (silence), Page envolée (fuite quand on le colle),
 *   Chapitre de la servitude (2 Pages volantes qui marquent).</li>
 *   <li>Phase 2, l'Encre noire : Copie conforme (2 copies ; seul le vrai a la plume qui brille), Flaques d'encre
 *   (ralentissement, brisure), Rature (trait en ligne), Trait d'encre ×5, Dernière page (canalisée 4 s : silence et
 *   marque à tout le groupe si on ne l'interrompt pas).</li>
 * </ul>
 */
public final class Scribe extends SubBoss {

    public static final String ID = "scribe";
    private static final long CHAPTER_MILLIS = 20_000;
    private static final int MAX_CHAPTERS = 3;

    private static final class State {
        int chapters;
        long nextChapter;
        ItemDisplay quill;
        final List<UUID> pages = new ArrayList<>();
        final List<Stray> copies = new ArrayList<>();
        long ticks;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();
    private Volley copyVolley;

    public Scribe() {
        super(ID, "Le Scribe", "Disciple de Mortarius", 450, 60, 160, 0, Map.of("volley", 4.0, "curse", 3.0, "beam", 8.0,
                "ink", 1.0, "last-page", 0.0));
    }

    @Override
    protected void build() {
        Ability volley = new Volley(Spec.of("id", "trait-d-encre", "projectile", "snowball", "count", 3, "spread", 6, "damage", damage("volley"),
                "damage-type", "magic", "cooldown", 6, "cast", 0.5, "states", List.of("slow 0.2 2")));
        Ability curse = new Curse(Spec.of("id", "lecture-interdite", "damage", damage("curse"), "cast", 1, "cooldown", 14,
                "states", List.of("silence 1 2"), "weight", 1.2));
        Ability flee = new Blink(Spec.of("id", "page-envolee", "mode", "escape", "escape-health", 0.5, "cooldown", 20, "cast", 0.3));
        Ability servitude = new Servitude(Spec.of("id", "chapitre-de-la-servitude", "cooldown", 30, "cast", 1.5));
        phase1 = List.of(volley, curse, flee, servitude);
        Ability volley5 = new Volley(Spec.of("id", "trait-d-encre", "projectile", "snowball", "count", 5, "spread", 10, "damage", damage("volley"),
                "damage-type", "magic", "cooldown", 6, "cast", 0.5, "states", List.of("slow 0.2 2")));
        Ability copy = new Copy(Spec.of("id", "copie-conforme", "cooldown", 35, "cast", 1.5));
        Ability puddles = new Zone(Spec.of("id", "flaques-d-encre", "mode", "linger", "count", 3, "seconds", 10, "radius", 2.5,
                "damage", damage("ink"), "color", "1A1A2E", "cooldown", 20, "cast", 1, "states", List.of("slow 0.3 2", "shatter 0.3 3")));
        Ability erase = new Beam(Spec.of("id", "rature", "damage", damage("beam"), "length", 16, "width", 1.5, "cooldown", 12, "cast", 1,
                "states", List.of("shatter 0.3 4")));
        Ability lastPage = new LastPage(Spec.of("id", "derniere-page", "cooldown", 35, "cast", 4));
        phase2 = List.of(volley5, curse, flee, copy, puddles, erase, lastPage);
        copyVolley = new Volley(Spec.of("id", "trait-copie", "projectile", "snowball", "count", 2, "spread", 8, "damage", damage("volley") / 2,
                "damage-type", "magic", "states", List.of("slow 0.15 2")));
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
        // Il vise les tireurs et les soigneurs, et préfère ceux qui ne le collent pas
        return new fr.eternom.eterMob.module.ai.AiProfile(32, 1, 0.1, 0.6, 0.7, 1, -0.3, 1.25, 2500, 4, 0.25, true);
    }

    @Override
    protected LootTable loot() {
        return LootTable.of(List.of(
                new LootTable.Entry(Material.INK_SAC, 3, 8, 1, 1, null),
                new LootTable.Entry(Material.PAPER, 2, 5, 1, 1, null),
                new LootTable.Entry(Material.BOOK, 1, 1, 0.5, 1, "encre-du-scribe"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    @Override
    public void contribute(BossFight fight, StatSheet sheet) {
        super.contribute(fight, sheet);
        sheet.add(Stat.MAGIC_PEN, 0.3, "Scribe");
        State state = states.get(fight.id());
        if (state != null && state.chapters > 0) {
            sheet.add(Stat.MAGIC_BONUS, 0.1 * state.chapters, "Chapitre");
        }
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return Material.WRITABLE_BOOK;
    }

    @Override
    public List<String> lore() {
        return List.of("Disciple de Mortarius, mage d'arrière-ligne.", "Forêts sombres, la nuit.");
    }

    @Override
    public double height() {
        return 2.5;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return Stray.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        base(entity, Attribute.SCALE, 1.15);
        base(entity, Attribute.FOLLOW_RANGE, 48);
        base(entity, Attribute.KNOCKBACK_RESISTANCE, 0.4);
        // Un livre en main : pas d'arc, il se bat avec l'encre
        wear(entity, EquipmentSlot.HAND, Material.WRITABLE_BOOK);
        wear(entity, EquipmentSlot.HEAD, Material.LEATHER_HELMET);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    @Override
    public String phaseName(int phase) {
        return phase <= 1 ? "La Copie" : "L'Encre noire";
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.BLUE : BossBar.Color.PURPLE;
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Chaque page que vous lisez vous appartient un peu moins." : null;
    }

    @Override
    public String spawnLine() {
        return "Encore un chapitre... encore un...";
    }

    @Override
    public String deathLine() {
        return "L'encre... sèche...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        State state = state(fight);
        if (state.quill == null || !state.quill.isValid()) {
            LivingEntity self = fight.entity();
            state.quill = self.getWorld().spawn(self.getLocation(), ItemDisplay.class, display -> {
                display.setItemStack(ItemStack.of(Material.FEATHER));
                display.setPersistent(false);
                display.setTeleportDuration(5);
                display.setGlowing(true);
                display.setGlowColorOverride(Color.fromRGB(0x6A5ACD));
                display.setBillboard(Display.Billboard.FIXED);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            });
            service().own(fight, state.quill);
        }
        state.nextChapter = System.currentTimeMillis() + CHAPTER_MILLIS;
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
        long now = System.currentTimeMillis();
        if (state.quill != null && state.quill.isValid()) {
            state.quill.teleport(self.getLocation().add(0, self.getHeight() * 0.9 + Math.sin(state.ticks / 8.0) * 0.1, 0));
        }
        if (now >= state.nextChapter) {
            state.nextChapter = now + CHAPTER_MILLIS;
            if (state.chapters < MAX_CHAPTERS) {
                state.chapters++;
                self.getWorld().playSound(self.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.5f, 0.8f);
                self.getWorld().spawnParticle(Particle.ENCHANT, self.getLocation().add(0, 2, 0), 40, 0.6, 0.6, 0.6, 1);
                service().say(fight, "Chapitre " + state.chapters + ".");
            }
        }
        // Les copies tirent leur encre toutes les 3 s
        state.copies.removeIf(copy -> !copy.isValid() || copy.isDead());
        if (state.ticks % 60 == 0) {
            LivingEntity target = brain(fight).target();
            for (Stray copy : state.copies) {
                if (target != null && copy.hasLineOfSight(target)) {
                    copyVolley.cast(new AbilityContext(new CopyCaster(copy), target, null));
                }
            }
        }
    }

    @Override
    public void onOwnedDeath(BossFight fight, Entity owned) {
        State state = state(fight);
        if (state.pages.remove(owned.getUniqueId()) && state.chapters > 0) {
            state.chapters--;
            fight.entity().getWorld().playSound(fight.entity().getLocation(), Sound.ITEM_BOOK_PUT, 1f, 0.6f);
        }
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

    private static BossFight fightOf(AbilityContext context) {
        return ((BrainBoss.BossCaster) context.caster()).fight();
    }

    // ---------- Capacités signatures ----------

    /** Chapitre de la servitude : 2 Pages volantes (vex) dont les coups posent Marque. */
    private final class Servitude extends Ability {

        Servitude(ConfigurationSection config) {
            super("servitude", config, new Defaults(Trigger.ACTIVE, 30, 1.5, 0, 40, 1, 0, 0, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).pages.size() >= 2 ? 0 : 0.6;
        }

        @Override
        public void cast(AbilityContext context) {
            BossFight fight = fightOf(context);
            State state = state(fight);
            LivingEntity self = context.self();
            for (int i = state.pages.size(); i < 2; i++) {
                Vex page = Minions.spawn(self.getLocation().add(0, 2, 0), Vex.class, vex -> {
                    vex.customName(net.kyori.adventure.text.Component.text("Page volante"));
                    base(vex, Attribute.MAX_HEALTH, 10);
                    vex.setHealth(10);
                    Minions.hitStates(vex, "mark 0.15 4");
                    if (context.target() != null) {
                        vex.setTarget(context.target());
                    }
                });
                service().own(fight, page);
                fr.eternom.eterMob.module.model.Models.attachLater(page, "page-volante");
                state.pages.add(page.getUniqueId());
            }
            self.getWorld().playSound(self.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.5f, 1.4f);
        }
    }

    /** Copie conforme : 2 copies (peu de PV, sans butin) qui tirent aussi ; seul le vrai a la plume qui brille. */
    private final class Copy extends Ability {

        Copy(ConfigurationSection config) {
            super("copy", config, new Defaults(Trigger.ACTIVE, 35, 1.5, 0, 40, 1, 0, 0, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).copies.isEmpty() ? 0.7 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            BossFight fight = fightOf(context);
            State state = state(fight);
            LivingEntity self = context.self();
            self.getWorld().spawnParticle(Particle.SQUID_INK, self.getLocation().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.05);
            self.getWorld().playSound(self.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 0.8f);
            for (int i = 0; i < 2; i++) {
                double angle = Math.PI * i + Math.random();
                Location spot = Blink.safeSpot(self.getLocation().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4));
                if (spot == null) {
                    continue;
                }
                Stray copy = Minions.spawn(spot, Stray.class, stray -> {
                    base(stray, Attribute.SCALE, 1.15);
                    base(stray, Attribute.MAX_HEALTH, 20);
                    stray.setHealth(20);
                    wear(stray, EquipmentSlot.HAND, Material.WRITABLE_BOOK);
                    wear(stray, EquipmentSlot.HEAD, Material.LEATHER_HELMET);
                    if (context.target() != null) {
                        stray.setTarget(context.target());
                    }
                });
                service().own(fight, copy);
                // La copie a le même modèle que lui (seul le vrai garde la plume qui brille)
                fr.eternom.eterMob.module.model.Models.attachLater(copy, "scribe");
                state.copies.add(copy);
            }
            // Le vrai change de place avec une de ses copies : il faut regarder la plume
            if (!state.copies.isEmpty()) {
                Stray swap = state.copies.get((int) (Math.random() * state.copies.size()));
                Location mine = self.getLocation();
                self.teleport(swap.getLocation());
                swap.teleport(mine);
            }
        }
    }

    /** Dernière page : canalisée 4 s (interruptible) ; si elle finit, Silence 3 s et Marque à tous ceux à 24 blocs. */
    private final class LastPage extends Ability {

        LastPage(ConfigurationSection config) {
            super("last-page", config, new Defaults(Trigger.ACTIVE, 35, 4, 0, 40, 1, 0, 0, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return 0.6;
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            super.telegraph(context, progress);
            LivingEntity self = context.self();
            self.getWorld().spawnParticle(Particle.ENCHANT, self.getLocation().add(0, 2, 0), 30, 1, 1, 1, 1.5);
            if (progress == 0) {
                service().say(fightOf(context), "La dernière page... écoutez-la.");
            }
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity self = context.self();
            self.getWorld().playSound(self.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.2f);
            for (Player player : self.getLocation().getNearbyPlayers(24)) {
                if (Hits.fighting(player) && Services.states() != null) {
                    Services.states().apply(player, "silence", 1, 3, self);
                    Services.states().apply(player, "mark", 0.2, 6, self);
                }
            }
        }
    }

    /** Une copie, pour lancer le Trait d'encre en son nom (moitié moins forte, pas d'IA). */
    private record CopyCaster(LivingEntity entity) implements Caster {

        @Override
        public Tier tier() {
            return Tier.BOSS;
        }

        @Override
        public double abilityPower() {
            return 1;
        }

        @Override
        public double piercing() {
            return 0;
        }

        @Override
        public Brain brain() {
            return null;
        }

        @Override
        public String displayName() {
            return "Copie du Scribe";
        }
    }
}
