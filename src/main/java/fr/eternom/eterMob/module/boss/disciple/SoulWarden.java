package fr.eternom.eterMob.module.boss.disciple;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Aura;
import fr.eternom.eterMob.module.ability.kind.Blink;
import fr.eternom.eterMob.module.ability.kind.Cleave;
import fr.eternom.eterMob.module.ability.kind.Vortex;
import fr.eternom.eterMob.module.ability.kind.Zone;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.SoulLinks;
import fr.eternom.eterMob.module.boss.SubBoss;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Minions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La Gardienne des âmes, disciple de Mortarius : elle gardait les âmes que le Livre volait, et elle a fini par en faire
 * partie. Duelliste vampirique (armure et résistance magique moyennes), dégâts magiques et bruts.
 * <ul>
 *   <li>Passifs : Âmes errantes (aura) ; Soif (se soigne de 20 % des dégâts qu'elle inflige) ; Âmes enchaînées (3 âmes :
 *   chacune lui retire 15 % des dégâts reçus, il faut les tuer).</li>
 *   <li>Phase 1, la Gardienne : Faux (arc de 180°, faiblesse), Lien d'âmes (2 joueurs partagent leurs dégâts, casse à
 *   plus de 8 blocs), Moisson (canalisée : se soigne par joueur touché ; interruptible), Pas de l'ombre (dans le dos).</li>
 *   <li>Phase 2, la Récolte : Pacte (un joueur doit la frapper dans les 5 s, sinon elle se soigne), Pluie d'âmes, Lien
 *   d'âmes ×3, Récolte (aspiration, peur, coup circulaire en dégâts bruts).</li>
 * </ul>
 */
public final class SoulWarden extends SubBoss {

    public static final String ID = "gardienne";
    private static final double LIFESTEAL = 0.2;
    private static final double SOUL_SHIELD = 0.15;
    private static final long PACT_MILLIS = 5_000;

    private static final class State {
        final List<UUID> souls = new ArrayList<>();
        UUID pact;
        long pactUntil;
        long ticks;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private List<Ability> phase1 = List.of();
    private List<Ability> phase2 = List.of();

    public SoulWarden() {
        super(ID, "La Gardienne des âmes", "Disciple de Mortarius", 500, 120, 120, 0.2, Map.of("attack", 9.0, "scythe", 9.0,
                "harvest", 6.0, "rain", 7.0, "reaping", 9.0, "aura", 1.0));
    }

    @Override
    protected void build() {
        Ability aura = new Aura(Spec.of("id", "ames-errantes", "radius", 4, "damage", damage("aura"), "cooldown", 3, "particle", "SOUL"));
        Ability scythe = new Cleave(Spec.of("id", "faux", "angle", 180, "radius", 4, "damage", damage("scythe"), "cooldown", 6, "cast", 0.7,
                "states", List.of("weakness 0.15 3")));
        Ability link2 = new SoulLink(Spec.of("id", "lien-d-ames", "count", 2, "seconds", 6, "cooldown", 18, "cast", 1));
        Ability harvest = new Harvest(Spec.of("id", "moisson", "radius", 4.5, "damage", damage("harvest"), "cooldown", 16, "cast", 2));
        Ability shadow = new Blink(Spec.of("id", "pas-de-l-ombre", "mode", "behind", "cooldown", 14, "cast", 0.4,
                "states", List.of("weakness 0.1 2")));
        phase1 = List.of(aura, scythe, link2, harvest, shadow);
        Ability pact = new Pact(Spec.of("id", "pacte", "cooldown", 20, "cast", 0.8));
        Ability rain = new Zone(Spec.of("id", "pluie-d-ames", "mode", "impact", "count", 6, "radius", 2, "damage", damage("rain"),
                "color", "66CCFF", "cooldown", 16, "cast", 1.4, "states", List.of("fear 1 1")));
        Ability link3 = new SoulLink(Spec.of("id", "lien-d-ames", "count", 3, "seconds", 6, "cooldown", 18, "cast", 1));
        Ability reaping = new Vortex(Spec.of("id", "recolte", "radius", 10, "damage", damage("reaping"), "damage-type", "true",
                "cooldown", 26, "cast", 1.6, "states", List.of("fear 1 1")));
        phase2 = List.of(aura, scythe, harvest, pact, rain, link3, reaping);
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
                new LootTable.Entry(Material.SOUL_SAND, 4, 10, 1, 1, null),
                new LootTable.Entry(Material.ECHO_SHARD, 1, 1, 0.25, 1, null),
                new LootTable.Entry(Material.GHAST_TEAR, 1, 2, 0.4, 1, "ame-gardee"),
                new LootTable.Entry(Material.EMERALD, 3, 7, 0.8, 1, null)));
    }

    // ---------- Boss ----------

    @Override
    public Material icon() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public List<String> lore() {
        return List.of("Disciple de Mortarius, duelliste vampirique.", "Neiges, montagnes et vallée des âmes.");
    }

    @Override
    public double height() {
        return 3;
    }

    @Override
    public Class<? extends LivingEntity> type() {
        return WitherSkeleton.class;
    }

    @Override
    public void setup(LivingEntity entity) {
        base(entity, Attribute.SCALE, 0.95);
        base(entity, Attribute.FOLLOW_RANGE, 48);
        base(entity, Attribute.KNOCKBACK_RESISTANCE, 0.6);
        base(entity, Attribute.ATTACK_DAMAGE, damage("attack"));
        base(entity, Attribute.MOVEMENT_SPEED, 0.3);
        wear(entity, EquipmentSlot.HAND, Material.NETHERITE_HOE);
    }

    @Override
    public double[] phaseThresholds() {
        return new double[]{0.5};
    }

    @Override
    public String phaseName(int phase) {
        return phase <= 1 ? "La Gardienne" : "La Récolte";
    }

    @Override
    public BossBar.Color color(int phase) {
        return phase <= 1 ? BossBar.Color.BLUE : BossBar.Color.WHITE;
    }

    @Override
    public String phaseLine(int phase) {
        return phase == 2 ? "Vos âmes sont mûres." : null;
    }

    @Override
    public String spawnLine() {
        return "Je garde... je garde tout...";
    }

    @Override
    public String deathLine() {
        return "Libres... enfin...";
    }

    private State state(BossFight fight) {
        return states.computeIfAbsent(fight.id(), id -> new State());
    }

    private static BossFight fightOf(AbilityContext context) {
        return ((BrainBoss.BossCaster) context.caster()).fight();
    }

    @Override
    public void onLoad(BossFight fight) {
        super.onLoad(fight);
        State state = state(fight);
        state.souls.removeIf(id -> org.bukkit.Bukkit.getEntity(id) == null);
        if (state.souls.isEmpty() && fight.phase() <= 1) {
            chainSouls(fight, state);
        }
    }

    /** Âmes enchaînées : 3 vex lents qui tournent autour d'elle ; chacune lui retire 15 % des dégâts reçus. */
    private void chainSouls(BossFight fight, State state) {
        LivingEntity self = fight.entity();
        for (int i = 0; i < 3; i++) {
            Vex soul = Minions.spawn(self.getLocation().add(Math.cos(i * 2.1) * 2, 2, Math.sin(i * 2.1) * 2), Vex.class, vex -> {
                vex.customName(Component.text("Âme enchaînée"));
                base(vex, Attribute.MAX_HEALTH, 16);
                vex.setHealth(16);
                base(vex, Attribute.FLYING_SPEED, 0.2);
                base(vex, Attribute.ATTACK_DAMAGE, 1);
                vex.setGlowing(true);
            });
            service().own(fight, soul);
            fr.eternom.eterMob.module.model.Models.attachLater(soul, "ame-enchainee");
            state.souls.add(soul.getUniqueId());
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
        SoulLinks.tick();
        LivingEntity self = fight.entity();
        if (state.ticks % 10 == 0) {
            double angle = state.ticks / 10.0;
            self.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, self.getLocation().add(Math.cos(angle) * 1.2, 1.5, Math.sin(angle) * 1.2),
                    2, 0.05, 0.05, 0.05, 0);
            // Les âmes la suivent
            for (UUID id : state.souls) {
                if (org.bukkit.Bukkit.getEntity(id) instanceof Vex soul && soul.getLocation().distanceSquared(self.getLocation()) > 36) {
                    soul.teleport(self.getLocation().add(0, 2, 0));
                }
            }
        }
        if (state.pact != null && System.currentTimeMillis() > state.pactUntil) {
            Player broken = org.bukkit.Bukkit.getPlayer(state.pact);
            state.pact = null;
            AttributeHolder.heal(this, fight, 0.10);
            service().say(fight, (broken == null ? "Le pacte" : broken.getName() + ", ton pacte") + " est rompu. Je me nourris.");
            self.getWorld().spawnParticle(Particle.HEART, self.getLocation().add(0, 2.5, 0), 6, 0.5, 0.3, 0.5, 0);
        }
    }

    /** Âmes enchaînées en vie : moins de dégâts ; le joueur du pacte qui la frappe le tient. */
    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        State state = state(fight);
        state.souls.removeIf(id -> !(org.bukkit.Bukkit.getEntity(id) instanceof Vex vex) || vex.isDead());
        if (!state.souls.isEmpty()) {
            event.setDamage(event.getDamage() * (1 - SOUL_SHIELD * state.souls.size()));
        }
        if (state.pact != null && event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (damager.getUniqueId().equals(state.pact)) {
                state.pact = null;
                Services.messages().send(damager, "soul-warden.pact-held");
            }
        }
        super.onDamaged(fight, event);
    }

    @Override
    public void onOwnedDeath(BossFight fight, Entity owned) {
        if (state(fight).souls.remove(owned.getUniqueId())) {
            fight.entity().getWorld().playSound(fight.entity().getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.8f);
        }
    }

    /** Soif : 20 % des dégâts qu'elle inflige lui reviennent. */
    @Override
    public void onDealt(BossFight fight, LivingEntity victim, double damage) {
        if (victim instanceof Player && damage > 0) {
            heal(fight, damage * LIFESTEAL);
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

    /** Pour les classes internes : soin en part de sa vie max. */
    private static final class AttributeHolder {
        static void heal(SoulWarden warden, BossFight fight, double part) {
            var max = fight.entity().getAttribute(Attribute.MAX_HEALTH);
            warden.heal(fight, (max == null ? 20 : max.getValue()) * part);
        }
    }

    // ---------- Capacités signatures ----------

    /** Lien d'âmes : lie count joueurs proches (les plus proches de sa cible) pendant seconds. */
    private final class SoulLink extends Ability {

        private final int count;
        private final double seconds;

        SoulLink(ConfigurationSection config) {
            super("soul-link", config, new Defaults(Trigger.ACTIVE, 18, 1, 0, 24, 1, 0, 0, 0, 0));
            this.count = Math.clamp(config.getInt("count", 2), 2, 6);
            this.seconds = config.getDouble("seconds", 6);
        }

        @Override
        public double score(AbilityContext context) {
            return players(context.self().getLocation(), 16).size() >= 2 ? 0.6 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity anchor = context.target() != null ? context.target() : context.self();
            List<Player> near = new ArrayList<>(players(context.self().getLocation(), 16));
            near.sort(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(anchor.getLocation())));
            List<Player> linked = near.subList(0, Math.min(count, near.size()));
            SoulLinks.link(linked, seconds, context.self());
            context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_ALLAY_ITEM_THROWN, 1.2f, 0.5f);
            for (Player player : linked) {
                Services.messages().send(player, "soul-warden.linked");
            }
        }
    }

    /** Moisson : canalisée (interruptible), puis un coup autour d'elle ; elle se soigne de 5 % par joueur touché. */
    private final class Harvest extends Ability {

        Harvest(ConfigurationSection config) {
            super("harvest", config, new Defaults(Trigger.ACTIVE, 16, 2, 0, 5, 1, 6, 4.5, 0.5, 0.2));
        }

        @Override
        protected fr.eternom.eterLib.module.combat.DamageKind defaultKind() {
            return fr.eternom.eterLib.module.combat.DamageKind.MAGIC;
        }

        @Override
        public double score(AbilityContext context) {
            int near = context.situation().playersNearSelf();
            return near == 0 ? 0 : clamp01(0.3 + 0.2 * near + (context.situation().selfHealth() < 0.6 ? 0.2 : 0));
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            super.telegraph(context, progress);
            dangerRing(context.self().getLocation(), radius, progress);
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity self = context.self();
            int touched = 0;
            for (Player player : players(self.getLocation(), radius)) {
                hit(context, player, damage, self.getLocation());
                touched++;
            }
            self.getWorld().spawnParticle(Particle.SWEEP_ATTACK, self.getLocation().add(0, 1, 0), 8, 1.5, 0.3, 1.5, 0);
            if (touched > 0) {
                AttributeHolder.heal(SoulWarden.this, fightOf(context), 0.05 * touched);
                self.getWorld().spawnParticle(Particle.HEART, self.getLocation().add(0, 2.5, 0), touched * 2, 0.5, 0.3, 0.5, 0);
            }
        }
    }

    /** Pacte : un joueur doit la frapper dans les 5 s, sinon elle se soigne de 10 %. */
    private final class Pact extends Ability {

        Pact(ConfigurationSection config) {
            super("pact", config, new Defaults(Trigger.ACTIVE, 20, 0.8, 0, 32, 1, 0, 0, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return state(fightOf(context)).pact == null ? 0.55 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            List<Player> near = players(context.self().getLocation(), 32);
            if (near.isEmpty()) {
                return;
            }
            // Le plus loin d'elle : celui qui doit faire l'effort de venir
            Player chosen = near.stream().max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(context.self().getLocation()))).orElse(near.getFirst());
            State state = state(fightOf(context));
            state.pact = chosen.getUniqueId();
            state.pactUntil = System.currentTimeMillis() + PACT_MILLIS;
            chosen.showTitle(net.kyori.adventure.title.Title.title(Services.messages().get(chosen, "soul-warden.pact-title"),
                    Services.messages().get(chosen, "soul-warden.pact-subtitle")));
            chosen.playSound(chosen, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
            if (Services.states() != null) {
                Services.states().apply(chosen, "pact", 1, PACT_MILLIS / 1000.0, context.self());
            }
            for (Player player : near) {
                if (player != chosen && Hits.fighting(player)) {
                    Services.messages().send(player, "soul-warden.pact-on", "player", chosen.getName());
                }
            }
        }
    }
}
