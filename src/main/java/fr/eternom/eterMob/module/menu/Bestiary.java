package fr.eternom.eterMob.module.menu;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterMob.module.loot.KillStats;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.MobVariant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * /bestiary : les familles de monstres, puis leurs variantes. Une variante déjà tuée montre sa description, ses kills,
 * les rangs croisés (Ordinaire → Fléau) et son butin possible ; les autres restent « ??? ». Kills : KillStats (base).
 */
public class Bestiary implements TabExecutor {

    /** Une famille : son identifiant (lang/ > bestiary.family.&lt;id&gt;), son icône, ses variantes dans l'ordre. */
    public record Family(String id, Material icon, List<String> variants) {
    }

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final MobService mobs;
    private final KillStats stats;
    private final Messages messages;
    private final List<Family> families;

    public Bestiary(JavaPlugin plugin, MobService mobs, KillStats stats, Messages messages, List<Family> families) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.stats = stats;
        this.messages = messages;
        this.families = List.copyOf(families);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }

    public void open(Player player) {
        Tasks.async(plugin, player, () -> stats.all(player.getUniqueId()),
                kills -> player.openInventory(new Families(player, kills).getInventory()),
                () -> messages.send(player, "error.generic"));
    }

    private long total(Map<Integer, Long> byRatio) {
        return byRatio == null ? 0 : byRatio.values().stream().mapToLong(Long::longValue).sum();
    }

    // ---------- Les familles ----------

    private final class Families implements Menu {

        private static final int[] SLOTS = {11, 13, 15, 20, 22, 24};
        private static final int BACK = 31;

        private final Inventory inventory;
        private final Map<Integer, Family> familyAt = new HashMap<>();
        private final Map<String, Map<Integer, Long>> kills;
        private final BackButton back = EterLib.get().backButton("");

        Families(Player viewer, Map<String, Map<Integer, Long>> kills) {
            this.kills = kills;
            inventory = Bukkit.createInventory(this, 36, messages.get(viewer, "bestiary.title"));
            Frame.draw(inventory, Material.RED_STAINED_GLASS_PANE);
            for (int i = 0; i < families.size() && i < SLOTS.length; i++) {
                Family family = families.get(i);
                long found = family.variants().stream().filter(id -> total(kills.get(id)) > 0).count();
                long killed = family.variants().stream().mapToLong(id -> total(kills.get(id))).sum();
                inventory.setItem(SLOTS[i], Items.item(family.icon(), messages.get(viewer, "bestiary.family." + family.id() + ".name"), List.of(
                        messages.get(viewer, "bestiary.family." + family.id() + ".lore"),
                        messages.get(viewer, "bestiary.found", "found", String.valueOf(found), "total", String.valueOf(family.variants().size())),
                        messages.get(viewer, "bestiary.kills", "count", String.valueOf(killed)),
                        Component.empty(),
                        messages.get(viewer, "bestiary.open")), found == family.variants().size()));
                familyAt.put(SLOTS[i], family);
            }
            inventory.setItem(BACK, back.item(viewer));
        }

        @Override
        public void onClick(Player player, int slot, ClickType click) {
            if (slot == BACK) {
                back.click(player);
                return;
            }
            Family family = familyAt.get(slot);
            if (family != null) {
                Sounds.click(player);
                player.openInventory(new Variants(player, family, kills).getInventory());
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------- Les variantes d'une famille ----------

    private final class Variants implements Menu {

        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        private static final int BACK = 49;

        private final Inventory inventory;
        private final Map<String, Map<Integer, Long>> kills;
        private final BackButton back = EterLib.get().backButton("bestiary");

        Variants(Player viewer, Family family, Map<String, Map<Integer, Long>> kills) {
            this.kills = kills;
            inventory = Bukkit.createInventory(this, 54, messages.get(viewer, "bestiary.family-title",
                    "family", messages.plain(viewer, "bestiary.family." + family.id() + ".name")));
            Frame.draw(inventory, Material.RED_STAINED_GLASS_PANE);
            int i = 0;
            for (String id : family.variants()) {
                MobVariant variant = mobs.variants().get(id);
                if (variant == null || i >= SLOTS.length) {
                    continue;
                }
                inventory.setItem(SLOTS[i++], item(viewer, variant, kills.get(id)));
            }
            inventory.setItem(BACK, back.item(viewer));
        }

        private ItemStack item(Player viewer, MobVariant variant, Map<Integer, Long> byRatio) {
            long killed = total(byRatio);
            if (killed == 0) {
                return Items.item(Material.GRAY_DYE, messages.get(viewer, "bestiary.unknown"), List.of(messages.get(viewer, "bestiary.unknown-lore")));
            }
            List<Component> lore = new ArrayList<>();
            variant.lore().forEach(line -> lore.add(MINI.deserialize(line)));
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(messages.get(viewer, "bestiary.kills", "count", String.valueOf(killed)));
            // Rangs croisés : chaque rang de l'échelle, coché s'il en a tué un
            List<String> ranks = new ArrayList<>();
            for (int ratio = 1; ratio <= mobs.ratios().max(); ratio++) {
                boolean met = byRatio.getOrDefault(ratio, 0L) > 0;
                ranks.add(met ? mobs.ratios().status(ratio) + " <green>✔" : "<dark_gray>" + MINI.stripTags(mobs.ratios().status(ratio)) + " ✘");
            }
            lore.add(messages.get(viewer, "bestiary.ranks", Placeholder.component("ranks", MINI.deserialize(String.join("<dark_gray>, ", ranks)))));
            LootTable loot = variant.loot();
            if (!loot.isEmpty()) {
                Set<Material> items = new LinkedHashSet<>();
                loot.entries().forEach(entry -> items.add(entry.item()));
                List<Component> names = new ArrayList<>();
                items.stream().limit(6).forEach(item -> names.add(Component.translatable(item.translationKey())));
                lore.add(messages.get(viewer, "bestiary.loot", Placeholder.component("loot", Component.join(
                        net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")), names))));
            }
            Material egg = Material.matchMaterial(variant.type().name() + "_SPAWN_EGG");
            return Items.item(egg == null ? Material.ZOMBIE_HEAD : egg, MINI.deserialize("<!italic>" + variant.name()), lore);
        }

        @Override
        public void onClick(Player player, int slot, ClickType click) {
            if (slot == BACK) {
                back.click(player);
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
