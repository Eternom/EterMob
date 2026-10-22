package fr.eternom.eterMob.module.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforme un mob en variant et suit les variants chargés. Le variant et le ratio sont écrits sur le mob (données
 * persistantes) : ils survivent aux redémarrages sans base de données. Les variants chargés sont gardés en mémoire par
 * identifiant d'entité, pour que les événements les retrouvent sans relire le mob. Thread principal.
 *
 * Tag : un texte flottant (TextDisplay) assis sur le mob, pas son nom : un nom visible se voit à travers les blocs, le
 * texte flottant non. Il n'est jamais sauvegardé : créé quand le mob entre dans le monde, retiré quand il en sort.
 */
public class MobService {

    /** Période de la tâche des capacités périodiques (ticks). */
    public static final int TICK_PERIOD = 5;
    /** Une capacité périodique ne tourne que si un joueur est à moins de cette distance (blocs). */
    public static final double TICK_RANGE = 32;

    private final JavaPlugin plugin;
    private final Variants variants;
    private final Ratios ratios;
    private final String tagFormat;
    /** Distance de vue du tag, en fois 64 blocs (unité de Minecraft pour les TextDisplay). */
    private final float tagViewRange;
    private final NamespacedKey variantKey;
    private final NamespacedKey ratioKey;
    /** Clé des modificateurs de vie et de dégâts posés par EterMob. */
    private final NamespacedKey modifierKey;

    private final Map<Integer, ActiveMob> active = new HashMap<>();
    /** Sous-ensemble de active : les variants qui ont une capacité périodique. */
    private final Map<Integer, ActiveMob> ticking = new HashMap<>();
    /** Tag de chaque variant chargé. */
    private final Map<Integer, TextDisplay> tags = new HashMap<>();
    private long ticks;

    public MobService(JavaPlugin plugin, Variants variants, Ratios ratios, String tagFormat, double tagDistance) {
        this.plugin = plugin;
        this.variants = variants;
        this.ratios = ratios;
        this.tagFormat = tagFormat;
        this.tagViewRange = (float) Math.max(0.05, tagDistance / 64);
        this.variantKey = new NamespacedKey(plugin, "variant");
        this.ratioKey = new NamespacedKey(plugin, "ratio");
        this.modifierKey = new NamespacedKey(plugin, "ratio");
    }

    public Variants variants() {
        return variants;
    }

    public Ratios ratios() {
        return ratios;
    }

    /** Suit les mobs déjà chargés (serveur démarré avant le plugin), puis lance la tâche des capacités périodiques. */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                track(entity);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Arrêt du plugin : retire les tags (ils seraient de toute façon perdus, jamais sauvegardés). */
    public void stop() {
        tags.values().forEach(Entity::remove);
        tags.clear();
    }

    /**
     * Fait de ce mob un variant de ce ratio : données, vie et dégâts, puis MobVariant#onSpawn. Appelé avant que le mob
     * entre dans le monde : il sera suivi (et recevra son tag) par track (EntityAddToWorldEvent).
     */
    public void apply(LivingEntity entity, MobVariant variant, int ratio) {
        ratio = ratios.clamp(ratio);
        double power = ratios.multiplier(ratio);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(variantKey, PersistentDataType.STRING, variant.id());
        data.set(ratioKey, PersistentDataType.INTEGER, ratio);

        scale(entity, Attribute.MAX_HEALTH, variant.health() * power);
        scale(entity, Attribute.ATTACK_DAMAGE, variant.damage() * power);
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            entity.setHealth(health.getValue());
        }

        variant.onSpawn(new ActiveMob(entity, variant, ratio, power));
    }

    /** Multiplie l'attribut par factor (rien si le mob ne l'a pas : un creeper n'a pas de dégâts d'attaque). */
    private void scale(LivingEntity entity, Attribute attribute, double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierKey);
        if (factor != 1) {
            instance.addModifier(new AttributeModifier(modifierKey, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Mob entré dans le monde (apparu ou chargé) : suivi s'il est un variant connu. */
    public void track(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        PersistentDataContainer data = living.getPersistentDataContainer();
        String id = data.get(variantKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        MobVariant variant = variants.get(id);
        if (variant == null) {
            return;
        }
        int ratio = ratios.clamp(data.getOrDefault(ratioKey, PersistentDataType.INTEGER, 1));
        ActiveMob mob = new ActiveMob(living, variant, ratio, ratios.multiplier(ratio));
        active.put(living.getEntityId(), mob);
        if (variant.tickInterval() > 0) {
            ticking.put(living.getEntityId(), mob);
        }
        // Au tick suivant : pas de nouvelle entité pendant qu'une autre entre dans le monde (chargement de chunk)
        Bukkit.getScheduler().runTask(plugin, () -> addTag(mob));
    }

    /** Mob sorti du monde (mort, déchargé) : plus suivi, son tag est retiré. */
    public void untrack(Entity entity) {
        active.remove(entity.getEntityId());
        ticking.remove(entity.getEntityId());
        removeTag(entity);
    }

    /** Retire le tag sans attendre (mort : le corps reste encore un instant). */
    public void removeTag(Entity entity) {
        TextDisplay tag = tags.remove(entity.getEntityId());
        if (tag != null) {
            tag.remove();
        }
    }

    private void addTag(ActiveMob mob) {
        LivingEntity entity = mob.entity();
        if (!entity.isValid() || entity.isDead() || active.get(entity.getEntityId()) != mob || tags.containsKey(entity.getEntityId())) {
            return;
        }
        Component text = MiniMessage.miniMessage().deserialize(tagFormat,
                Placeholder.parsed("status", ratios.status(mob.ratio())), Placeholder.unparsed("name", mob.variant().name()));
        TextDisplay tag = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, display -> {
            display.text(text);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setViewRange(tagViewRange);
            // Un peu au-dessus de la tête (le passager est posé au sommet du mob)
            display.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(),
                    new Vector3f(1, 1, 1), new AxisAngle4f()));
        });
        entity.addPassenger(tag);
        tags.put(entity.getEntityId(), tag);
    }

    /** Variant chargé de cette entité, ou null. */
    public ActiveMob get(Entity entity) {
        return entity == null ? null : active.get(entity.getEntityId());
    }

    private void tick() {
        ticks += TICK_PERIOD;
        if (ticking.isEmpty()) {
            return;
        }
        // Copie : une capacité peut faire apparaître ou tuer des mobs pendant le tour
        for (ActiveMob mob : new ArrayList<>(ticking.values())) {
            int interval = mob.variant().tickInterval();
            if (ticks % interval != 0 || !mob.entity().isValid()) {
                continue;
            }
            if (!mob.entity().getLocation().getNearbyPlayers(TICK_RANGE).isEmpty()) {
                mob.variant().tick(mob);
            }
        }
    }
}
