package fr.eternom.eterMob.module.ability;

import fr.eternom.eterLib.module.combat.DamageKind;
import fr.eternom.eterLib.module.state.States;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Une capacité : une brique réglée dans la config d'un variant, d'un meneur ou d'un boss (kind + réglages). Les
 * réglages communs sont lus ici :
 * <pre>
 *   kind      sorte de capacité (slam, leap, volley...)            id        nom unique dans le monstre (défaut : kind)
 *   trigger   active | on-hit | on-damaged | on-death | below-health | periodic
 *   cooldown  secondes entre deux lancements                       cast      télégraphe avant l'effet (secondes)
 *   range     [min, max] : distance à la cible pour la lancer      sight     ligne de vue obligatoire (true)
 *   weight    préférence de l'IA (1)                               chance    déclencheurs on-hit / on-damaged (1)
 *   below     below-health : sous cette part de vie (0,5)          damage    dégâts de base (× la puissance du ratio)
 *   radius    rayon d'effet                                        knockback / lift : poussée et soulèvement
 *   states    états EterLib posés : ["bleed 1 4", "slow 0.3 3"]
 *   damage-type  physical (armure), magic (résistance magique), true (brut) ; défaut : physique, magique pour les sorts
 * </pre>
 * Une capacité active est notée (score, 0 à 1 environ) par l'IA selon la situation ; la mieux notée est lancée. Une
 * capacité ne garde rien d'un lancement à l'autre : son état de lancement est dans AbilityContext.
 */
public abstract class Ability {

    public enum Trigger { ACTIVE, ON_HIT, ON_DAMAGED, ON_DEATH, BELOW_HEALTH, PERIODIC }

    protected final String id;
    protected final String kind;
    protected final Trigger trigger;
    protected final double cooldown;
    protected final double cast;
    protected final double minRange;
    protected final double maxRange;
    protected final boolean sight;
    protected final double weight;
    protected final double chance;
    protected final double below;
    protected final double damage;
    protected final double radius;
    protected final double knockback;
    protected final double lift;
    protected final List<StateSpec> states;
    protected final DamageKind damageKind;
    protected final boolean interruptible;

    protected Ability(String kind, ConfigurationSection config, Defaults defaults) {
        this.kind = kind;
        this.id = config.getString("id", kind).toLowerCase(Locale.ROOT);
        this.trigger = trigger(config.getString("trigger"), defaults.trigger());
        this.cooldown = Math.max(0, config.getDouble("cooldown", defaults.cooldown()));
        this.cast = Math.max(0, config.getDouble("cast", defaults.cast()));
        List<Double> range = config.getDoubleList("range");
        this.minRange = range.size() == 2 ? range.get(0) : defaults.minRange();
        this.maxRange = range.size() == 2 ? range.get(1) : defaults.maxRange();
        this.sight = config.getBoolean("sight", true);
        this.weight = Math.max(0, config.getDouble("weight", 1));
        this.chance = Math.clamp(config.getDouble("chance", defaults.chance()), 0, 1);
        this.below = Math.clamp(config.getDouble("below", 0.5), 0, 1);
        this.damage = Math.max(0, config.getDouble("damage", defaults.damage()));
        this.radius = Math.max(0, config.getDouble("radius", defaults.radius()));
        this.knockback = config.getDouble("knockback", defaults.knockback());
        this.lift = config.getDouble("lift", defaults.lift());
        this.states = StateSpec.list(config, "states");
        DamageKind kindOf;
        try {
            kindOf = DamageKind.valueOf(config.getString("damage-type", defaultKind().name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kindOf = defaultKind();
        }
        this.damageKind = kindOf;
        this.interruptible = config.getBoolean("interruptible", true);
    }

    /** Valeurs par défaut propres à chaque sorte de capacité. */
    public record Defaults(Trigger trigger, double cooldown, double cast, double minRange, double maxRange, double chance,
                           double damage, double radius, double knockback, double lift) {
    }

    private static Trigger trigger(String text, Trigger fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Trigger.valueOf(text.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Type de dégâts par défaut de cette sorte (physique ; magique pour les sorts). Réglage : damage-type. */
    protected DamageKind defaultKind() {
        return DamageKind.PHYSICAL;
    }

    /** Un étourdissement ou un silence pendant le télégraphe l'annule (false : un coup de masse lancé part quand même). */
    public boolean interruptible() {
        return interruptible;
    }

    public DamageKind damageKind() {
        return damageKind;
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public Trigger trigger() {
        return trigger;
    }

    public double cooldown() {
        return cooldown;
    }

    public double castTime() {
        return cast;
    }

    public double weight() {
        return weight;
    }

    public double chance() {
        return chance;
    }

    public double below() {
        return below;
    }

    /** La distance et la vue permettent-elles de la lancer sur cette cible ? */
    public boolean inRange(double distance, boolean hasSight) {
        return distance >= minRange && distance <= maxRange && (!sight || hasSight);
    }

    /** Intérêt de la lancer maintenant (0 : aucun). Seulement pour les capacités actives. */
    public double score(AbilityContext context) {
        return 0.5;
    }

    /** Début du télégraphe : fixer le point visé (par défaut, la cible là où elle est). */
    public void begin(AbilityContext context) {
        context.spot();
    }

    /** Pendant le télégraphe (progress de 0 à 1), toutes les 5 ticks : de quoi le voir venir. */
    public void telegraph(AbilityContext context, double progress) {
        Location at = context.self().getLocation().add(0, context.self().getHeight() + 0.4, 0);
        at.getWorld().spawnParticle(Particle.DUST, at, 6, 0.3, 0.2, 0.3, 0, new Particle.DustOptions(Color.fromRGB(200, 30, 30), 1.2f));
        if (progress == 0) {
            at.getWorld().playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 1.2f);
        }
    }

    /** L'effet. */
    public abstract void cast(AbilityContext context);

    /**
     * Valeur d'équilibrage d'un lancement : dégâts × cibles probables + valeur des états (EterLib). Divisée par le délai,
     * elle donne une valeur par seconde, comparable entre capacités, monstres et classes.
     */
    public double value() {
        double total = damage * Math.max(1, radius > 0 ? 1.5 : 1);
        States lib = Services.states();
        for (StateSpec spec : states) {
            total += spec.chance() * (lib == null ? spec.seconds() : lib.value(spec.state(), spec.power(), spec.seconds(), 1));
        }
        return total;
    }

    /** Délai effectif (au moins 1 s pour le calcul de la valeur par seconde). */
    public double valuePerSecond() {
        return value() / Math.max(1, cooldown + cast);
    }

    // ---------- Outils communs ----------

    /** Frappe : dégâts (× puissance) venant du lanceur (l'armure compte, moins le perce-armure), états, poussée. */
    protected void hit(AbilityContext context, LivingEntity victim, double baseDamage, Location from) {
        if (victim instanceof Player player && !Hits.fighting(player)) {
            return;
        }
        if (baseDamage > 0) {
            Hits.abilityDamage(victim, context.scale(baseDamage), context.self(), damageKind);
        }
        applyStates(context, victim);
        if ((knockback != 0 || lift != 0) && victim instanceof Player player) {
            Hits.knock(player, from, knockback, lift);
        }
    }

    protected void applyStates(AbilityContext context, LivingEntity victim) {
        applyStates(context.caster(), victim, states);
    }

    public static void applyStates(Caster caster, LivingEntity victim, List<StateSpec> specs) {
        States lib = Services.states();
        if (lib == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (StateSpec spec : specs) {
            if (spec.chance() >= 1 || random.nextDouble() < spec.chance()) {
                lib.apply(victim, spec.state(), spec.power() * caster.abilityPower(), spec.seconds(), caster.entity());
            }
        }
    }

    /** Joueurs en combat (ni créatif, ni spectateur, ni invisibles du staff) à moins de range blocs. */
    protected static List<Player> players(Location center, double range) {
        List<Player> found = new ArrayList<>();
        for (Player player : center.getNearbyPlayers(range)) {
            if (Hits.fighting(player)) {
                found.add(player);
            }
        }
        return found;
    }

    /** Cercle de particules au sol (télégraphe d'une zone). */
    protected static void ring(Location center, double radius, Particle particle) {
        int points = Math.max(12, (int) (radius * 10));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    /** Cercle rouge (zone dangereuse). */
    protected static void dangerRing(Location center, double radius, double progress) {
        int points = Math.max(12, (int) (radius * 10));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(220, (int) (120 * (1 - progress)), 20), 1.1f);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    /** Ligne de particules entre deux points. */
    protected static void line(Location from, Location to, Particle particle) {
        double length = from.distance(to);
        if (length < 0.1 || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        var step = to.toVector().subtract(from.toVector()).normalize().multiply(0.4);
        Location point = from.clone();
        for (double done = 0; done < length; done += 0.4) {
            point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    protected static double clamp01(double value) {
        return Math.clamp(value, 0, 1.5);
    }
}
