package fr.eternom.eterMob.module.variant.book;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.Spec;
import fr.eternom.eterMob.module.ability.kind.Beam;
import fr.eternom.eterMob.module.ability.kind.Leap;
import fr.eternom.eterMob.module.boss.SoulLinks;
import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Les capacités propres à la famille du Livre (ce que les briques génériques ne font pas). */
final class BookAbilities {

    private BookAbilities() {
    }

    /** Un mort-vivant (zombies, squelettes, fantômes, vex : la famille du Livre). */
    static boolean undead(Entity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton || entity instanceof Phantom || entity instanceof Vex;
    }

    /** Les morts-vivants vivants à moins de radius blocs (sans lui). */
    static List<LivingEntity> undeadAround(LivingEntity self, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : self.getNearbyEntities(radius, radius / 2, radius)) {
            if (entity instanceof LivingEntity living && undead(entity) && living.isValid() && !living.isDead()) {
                found.add(living);
            }
        }
        return found;
    }

    static double healthPart(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 1 : entity.getHealth() / max.getValue();
    }

    static void healPart(LivingEntity entity, double part) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            entity.setHealth(Math.min(max.getValue(), entity.getHealth() + max.getValue() * part));
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, entity.getHeight() + 0.2, 0), 2, 0.3, 0.2, 0.3, 0);
        }
    }

    // ---------- Goule ----------

    /** Affamée : plus rapide (+20 %) tant que sa cible saigne. */
    static final class Hungry extends Ability {

        private static NamespacedKey key;

        Hungry(ConfigurationSection config) {
            super("hungry", config, new Defaults(Trigger.PERIODIC, 1, 0, 0, 64, 1, 0, 0, 0, 0));
        }

        @Override
        public void cast(AbilityContext context) {
            if (key == null) {
                key = new NamespacedKey(Services.plugin(), "affamee");
            }
            AttributeInstance speed = context.self().getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            boolean bleeding = context.target() != null && Services.states() != null && Services.states().has(context.target(), "bleed");
            boolean has = speed.getModifier(key) != null;
            if (bleeding && !has) {
                speed.addTransientModifier(new AttributeModifier(key, 0.2, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            } else if (!bleeding && has) {
                speed.removeModifier(key);
            }
        }
    }

    /** Dévorer : au coup, sur une cible à moins de 30 % de vie, elle se soigne de 10 %. */
    static final class Devour extends Ability {

        Devour(ConfigurationSection config) {
            super("devour", config, new Defaults(Trigger.ON_HIT, 6, 0, 0, 4, 1, 0, 0, 0, 0));
        }

        @Override
        public void cast(AbilityContext context) {
            if (context.target() != null && healthPart(context.target()) < 0.3) {
                healPart(context.self(), 0.1);
                context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.6f);
            }
        }
    }

    /** Frénésie : sous 50 % de sa vie, chaque coup pose 2 piles de saignement en plus. */
    static final class Frenzy extends Ability {

        Frenzy(ConfigurationSection config) {
            super("frenzy", config, new Defaults(Trigger.ON_HIT, 0, 0, 0, 4, 1, 0, 0, 0, 0));
        }

        @Override
        public void cast(AbilityContext context) {
            if (context.target() == null || healthPart(context.self()) >= 0.5 || Services.states() == null) {
                return;
            }
            for (int i = 0; i < 2; i++) {
                Services.states().apply(context.target(), "bleed", context.caster().abilityPower(), 4, context.self());
            }
            context.self().getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, context.target().getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0);
        }
    }

    // ---------- Page volante ----------

    /** Reliure : 3 Pages proches fusionnent en un Livre volant (plus grand, 3 fois plus de vie) qui lance des rayons. */
    static final class Binding extends Ability {

        private static NamespacedKey bound;
        private static NamespacedKey sizeKey;
        private static NamespacedKey healthKey;

        Binding(ConfigurationSection config) {
            super("binding", config, new Defaults(Trigger.PERIODIC, 5, 0, 0, 64, 1, 0, 0, 0, 0));
        }

        static boolean isBound(LivingEntity entity) {
            return bound != null && entity.getPersistentDataContainer().has(bound, PersistentDataType.BYTE);
        }

        @Override
        public void cast(AbilityContext context) {
            if (bound == null) {
                bound = new NamespacedKey(Services.plugin(), "bound");
                sizeKey = new NamespacedKey(Services.plugin(), "bound_size");
                healthKey = new NamespacedKey(Services.plugin(), "bound_health");
            }
            LivingEntity self = context.self();
            if (isBound(self) || !(context.caster() instanceof ActiveMob mine)) {
                return;
            }
            List<LivingEntity> others = new ArrayList<>();
            for (Entity entity : self.getNearbyEntities(6, 4, 6)) {
                ActiveMob other = Services.mobs().get(entity);
                if (other != null && other.variant() == mine.variant() && !isBound(other.entity())) {
                    others.add(other.entity());
                }
            }
            if (others.size() < 2) {
                return;
            }
            for (LivingEntity other : others.subList(0, 2)) {
                other.getWorld().spawnParticle(Particle.ENCHANT, other.getLocation(), 30, 0.3, 0.3, 0.3, 1);
                other.remove();
            }
            self.getPersistentDataContainer().set(bound, PersistentDataType.BYTE, (byte) 1);
            modifier(self, Attribute.SCALE, sizeKey, 0.8);
            modifier(self, Attribute.MAX_HEALTH, healthKey, 2);
            healPart(self, 1);
            self.customName(net.kyori.adventure.text.Component.text("Livre volant"));
            self.getWorld().playSound(self.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 2f, 0.5f);
            self.getWorld().spawnParticle(Particle.ENCHANT, self.getLocation(), 80, 0.6, 0.6, 0.6, 1.5);
        }

        private static void modifier(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null && instance.getModifier(key) == null) {
                instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    /** Rayon du Livre : seulement une fois relié (Reliure). */
    static final class BookRay extends Ability {

        private final Beam beam;

        BookRay(ConfigurationSection config) {
            super("book-ray", config, new Defaults(Trigger.ACTIVE, 10, 1, 3, 16, 1, 6, 0, 0, 0));
            this.beam = new Beam(config);
        }

        @Override
        public double score(AbilityContext context) {
            return Binding.isBound(context.self()) ? beam.score(context) : 0;
        }

        @Override
        public void begin(AbilityContext context) {
            beam.begin(context);
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            beam.telegraph(context, progress);
        }

        @Override
        public void cast(AbilityContext context) {
            beam.cast(context);
        }
    }

    // ---------- Âme enchaînée ----------

    /** Garde des morts : les morts-vivants autour d'elle prennent 15 % de dégâts en moins (état ward). */
    static final class UndeadWard extends Ability {

        private final double part;

        UndeadWard(ConfigurationSection config) {
            super("undead-ward", config, new Defaults(Trigger.PERIODIC, 2, 0, 0, 64, 1, 0, 6, 0, 0));
            this.part = config.getDouble("part", 0.15);
        }

        @Override
        public void cast(AbilityContext context) {
            if (Services.states() == null) {
                return;
            }
            for (LivingEntity ally : undeadAround(context.self(), radius)) {
                Services.states().apply(ally, "ward", part, 3, context.self());
            }
        }
    }

    /** Lien : 2 joueurs proches partagent leurs dégâts quelques secondes (comme la Gardienne). */
    static final class MinorLink extends Ability {

        MinorLink(ConfigurationSection config) {
            super("minor-link", config, new Defaults(Trigger.ACTIVE, 16, 0.8, 0, 12, 1, 0, 0, 0, 0));
        }

        @Override
        public double score(AbilityContext context) {
            return players(context.self().getLocation(), 10).size() >= 2 ? 0.5 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            List<Player> near = new ArrayList<>(players(context.self().getLocation(), 10));
            near.sort(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(context.self().getLocation())));
            SoulLinks.link(near.subList(0, Math.min(2, near.size())), 4, context.self());
        }
    }

    /** Délivrance : en mourant, elle soigne les morts-vivants autour de 20 %. */
    static final class Deliverance extends Ability {

        Deliverance(ConfigurationSection config) {
            super("deliverance", config, new Defaults(Trigger.ON_DEATH, 0, 0, 0, 64, 1, 0, 8, 0, 0));
        }

        @Override
        public void cast(AbilityContext context) {
            context.self().getWorld().spawnParticle(Particle.SOUL, context.self().getLocation(), 30, 1, 1, 1, 0.05);
            for (LivingEntity ally : undeadAround(context.self(), radius)) {
                healPart(ally, 0.2);
            }
        }
    }

    // ---------- Garde d'os ----------

    /**
     * Interposition : un joueur qui frappe ses alliés morts-vivants dans les 4 dernières secondes : il bondit sur lui,
     * l'affaiblit (20 %, 3 s) et le prend pour cible.
     */
    static final class Interpose extends Ability {

        private final Leap leap;

        Interpose(ConfigurationSection config) {
            super("interpose", config, new Defaults(Trigger.PERIODIC, 10, 0, 0, 64, 1, 4, 3, 0.4, 0.3));
            this.leap = new Leap(Spec.of("damage", config.getDouble("damage", 4), "radius", 3, "states", List.of("weakness 0.2 3")));
        }

        @Override
        public void cast(AbilityContext context) {
            LivingEntity self = context.self();
            long now = System.currentTimeMillis();
            for (LivingEntity ally : undeadAround(self, 12)) {
                ActiveMob mob = Services.mobs().get(ally);
                if (mob == null || mob.lastPlayerHit() == null || now - mob.lastPlayerHitAt() > 4000) {
                    continue;
                }
                Player attacker = Bukkit.getPlayer(mob.lastPlayerHit());
                if (attacker != null && Hits.fighting(attacker) && attacker.getWorld().equals(self.getWorld())
                        && attacker.getLocation().distanceSquared(self.getLocation()) < 16 * 16) {
                    context.caster().brain().threat().add(attacker.getUniqueId(), 20);
                    leap.cast(new AbilityContext(context.caster(), attacker, null));
                    return;
                }
            }
        }
    }

    // ---------- Acolyte du Livre ----------

    /** Prière noire : soigne de 15 % le mort-vivant le plus blessé autour de lui (télégraphe interruptible). */
    static final class BlackPrayer extends Ability {

        BlackPrayer(ConfigurationSection config) {
            super("black-prayer", config, new Defaults(Trigger.ACTIVE, 10, 1.5, 0, 64, 1, 0, 10, 0, 0));
        }

        @Override
        public boolean inRange(double distance, boolean hasSight) {
            return true;
        }

        private LivingEntity wounded(LivingEntity self) {
            return undeadAround(self, radius).stream().filter(ally -> healthPart(ally) < 0.8)
                    .min(Comparator.comparingDouble(BookAbilities::healthPart)).orElse(null);
        }

        @Override
        public double score(AbilityContext context) {
            LivingEntity ally = wounded(context.self());
            return ally == null ? 0 : clamp01(0.4 + (0.8 - healthPart(ally)));
        }

        @Override
        public void begin(AbilityContext context) {
            LivingEntity ally = wounded(context.self());
            context.data(ally == null ? null : ally.getUniqueId());
        }

        @Override
        public void telegraph(AbilityContext context, double progress) {
            super.telegraph(context, progress);
            UUID id = context.data();
            if (id != null && Bukkit.getEntity(id) instanceof LivingEntity ally) {
                line(context.self().getEyeLocation(), ally.getEyeLocation(), Particle.WITCH);
            }
        }

        @Override
        public void cast(AbilityContext context) {
            UUID id = context.data();
            if (id != null && Bukkit.getEntity(id) instanceof LivingEntity ally && ally.isValid()) {
                healPart(ally, 0.15);
                ally.getWorld().playSound(ally.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.6f);
            }
        }
    }

    /** Bénédiction du Livre : 5 s, ses alliés morts-vivants prennent 30 % de dégâts en moins. */
    static final class Blessing extends Ability {

        Blessing(ConfigurationSection config) {
            super("blessing", config, new Defaults(Trigger.ACTIVE, 20, 1, 0, 64, 1, 0, 10, 0, 0));
        }

        @Override
        public boolean inRange(double distance, boolean hasSight) {
            return true;
        }

        @Override
        public double score(AbilityContext context) {
            return undeadAround(context.self(), radius).size() >= 2 ? 0.6 : 0;
        }

        @Override
        public void cast(AbilityContext context) {
            if (Services.states() == null) {
                return;
            }
            context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.7f);
            ring(context.self().getLocation(), radius, Particle.ENCHANT);
            for (LivingEntity ally : undeadAround(context.self(), radius)) {
                Services.states().apply(ally, "ward", 0.3, 5, context.self());
            }
        }
    }
}
