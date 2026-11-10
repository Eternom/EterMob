package fr.eternom.eterMob.module.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Le butin d'un variant, d'un meneur ou d'un boss, en plus de celui de Minecraft :
 * <pre>
 *   loot:
 *     - { item: BONE, amount: [1, 3], chance: 0.5 }
 *     - { item: DIAMOND, chance: 0.02, min-ratio: 4 }                  # seulement à partir du ratio 4
 *     - { item: ECHO_SHARD, chance: 0.1, tag: relique-os }             # objet marqué (etermob:item) pour plus tard
 * </pre>
 * La chance est multipliée par le butin du ratio (config.yml), jamais au-dessus de 1.
 */
public final class LootTable {

    public record Entry(Material item, int min, int max, double chance, int minRatio, String tag) {
    }

    public static final LootTable EMPTY = new LootTable(List.of());

    private final List<Entry> entries;

    private LootTable(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static LootTable load(ConfigurationSection parent, String path, String owner, Logger logger) {
        List<Entry> entries = new ArrayList<>();
        if (parent == null) {
            return EMPTY;
        }
        for (Map<?, ?> map : parent.getMapList(path)) {
            Material item = Material.matchMaterial(String.valueOf(map.get("item")));
            if (item == null || !fr.eternom.eterMob.module.mob.Materials.item(item)) {
                logger.warning(owner + " : objet de butin inconnu « " + map.get("item") + " »");
                continue;
            }
            int min = 1;
            int max = 1;
            if (map.get("amount") instanceof List<?> amount && amount.size() == 2) {
                min = Math.max(1, number(amount.get(0), 1));
                max = Math.max(min, number(amount.get(1), min));
            } else if (map.get("amount") instanceof Number amount) {
                min = Math.max(1, amount.intValue());
                max = min;
            }
            double chance = map.get("chance") instanceof Number number ? number.doubleValue() : 1;
            Object tag = map.get("tag");
            entries.add(new Entry(item, min, max, Math.clamp(chance, 0, 1), number(map.get("min-ratio"), 1),
                    tag == null ? null : String.valueOf(tag).toLowerCase(Locale.ROOT)));
        }
        return entries.isEmpty() ? EMPTY : new LootTable(entries);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Tire le butin pour ce ratio, chances × multiplier. */
    public List<ItemStack> roll(int ratio, double multiplier) {
        List<ItemStack> drops = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Entry entry : entries) {
            if (ratio < entry.minRatio() || random.nextDouble() >= Math.min(1, entry.chance() * multiplier)) {
                continue;
            }
            ItemStack stack = ItemStack.of(entry.item(), random.nextInt(entry.min(), entry.max() + 1));
            if (entry.tag() != null) {
                stack.editMeta(meta -> meta.getPersistentDataContainer().set(
                        new NamespacedKey(JavaPlugin.getProvidingPlugin(LootTable.class), "item"), PersistentDataType.STRING, entry.tag()));
            }
            drops.add(stack);
        }
        return drops;
    }
}
