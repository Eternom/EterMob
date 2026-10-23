package fr.eternom.eterMob.module.menu;

import fr.eternom.eterMob.module.boss.Boss;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BossRegistry;
import fr.eternom.eterMob.module.boss.BossService;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.MobVariant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu du staff (/etermob menu, etermob.admin), indépendant des autres menus : les variants par famille (description,
 * choix du ratio, apparition devant soi) et les boss (apparition, boss en vie : téléportation, suppression confirmée).
 */
public class MobMenu implements Listener {

    private static final Map<EntityType, String> FAMILIES = new LinkedHashMap<>();
    /** Bouton de chaque ratio, du plus faible au plus fort. */
    private static final Material[] RATIO_ICONS = {Material.GRAY_DYE, Material.LIME_DYE, Material.YELLOW_DYE,
            Material.ORANGE_DYE, Material.RED_DYE, Material.PURPLE_DYE, Material.BLACK_DYE};
    /** Cases du contenu d'un menu de 54 (bordure laissée vide). */
    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    static {
        FAMILIES.put(EntityType.CREEPER, "Creepers");
        FAMILIES.put(EntityType.ZOMBIE, "Zombies");
        FAMILIES.put(EntityType.SKELETON, "Squelettes");
        FAMILIES.put(EntityType.SPIDER, "Araignées");
    }

    /** Un écran ouvert : son inventaire et l'action de chaque case. */
    private static final class Screen implements InventoryHolder {
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final MobService mobs;
    private final BossService bosses;

    public MobMenu(MobService mobs, BossService bosses) {
        this.mobs = mobs;
        this.bosses = bosses;
    }

    public void open(Player player) {
        home(player);
    }

    // ---- Écrans ----

    private void home(Player player) {
        Screen screen = screen(27, "<dark_gray>EterMob");
        int slot = 10;
        for (Map.Entry<EntityType, String> family : FAMILIES.entrySet()) {
            long count = mobs.variants().all().stream().filter(variant -> variant.type() == family.getKey()).count();
            set(screen, slot, item(egg(family.getKey()), "<gold>" + family.getValue(),
                    List.of("<gray>" + count + " variants", "", "<yellow>Clic : voir les variants")), p -> family(p, family.getKey()));
            slot += 2;
        }
        set(screen, 22, item(Material.WITHER_SKELETON_SKULL, "<red><b>Boss</b>",
                List.of("<gray>" + bosses.registry().entries().size() + " en vie", "", "<yellow>Clic : apparition et boss en vie")),
                this::bosses);
        player.openInventory(screen.inventory);
    }

    private void family(Player player, EntityType type) {
        Screen screen = screen(54, "<dark_gray>EterMob · " + FAMILIES.get(type));
        int index = 0;
        for (MobVariant variant : mobs.variants().all()) {
            if (variant.type() != type || index >= CONTENT.length) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + variant.id());
            lore.add("<gray>Rareté : <white>" + variant.rarity().id());
            if (variant.health() != 1 || variant.speed() != 1) {
                lore.add("<gray>Vie <white>×" + format(variant.health()) + "</white> · vitesse <white>×" + format(variant.speed()));
            }
            lore.add("");
            variant.lore().forEach(line -> lore.add("<gray>" + line));
            lore.add("");
            lore.add("<yellow>Clic : choisir le ratio");
            set(screen, CONTENT[index++], item(egg(type), "<gold>" + variant.name(), lore), p -> ratio(p, variant));
        }
        back(screen, 49, this::home);
        player.openInventory(screen.inventory);
    }

    private void ratio(Player player, MobVariant variant) {
        Screen screen = screen(27, "<dark_gray>" + variant.name() + " · ratio");
        int max = Math.min(mobs.ratios().max(), RATIO_ICONS.length);
        int first = 13 - max / 2;
        for (int ratio = 1; ratio <= max; ratio++) {
            int chosen = ratio;
            set(screen, first + ratio - 1, item(RATIO_ICONS[ratio - 1], mobs.ratios().status(ratio) + " <gray>(ratio " + ratio + ")",
                    List.of("<gray>Multiplicateur : <white>×" + format(mobs.ratios().multiplier(ratio)),
                            "<gray>Perce-armure : <white>" + Math.round(mobs.ratios().armorPiercing(ratio) * 100) + " %",
                            "", "<yellow>Clic : le faire apparaître devant toi")),
                    p -> {
                        mobs.spawn(variant, chosen, inFront(p));
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<green>" + variant.name() + " apparu, ratio " + chosen + "."));
                    });
        }
        back(screen, 22, p -> family(p, variant.type()));
        player.openInventory(screen.inventory);
    }

    private void bosses(Player player) {
        Screen screen = screen(54, "<dark_gray>EterMob · Boss");
        int slot = 10;
        for (Boss boss : bosses.bosses()) {
            if (slot > 16) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + boss.id());
            boss.lore().forEach(line -> lore.add("<gray>" + line));
            lore.add("");
            lore.add("<yellow>Clic : le faire apparaître ici");
            set(screen, slot++, item(boss.icon(), "<red><b>" + boss.name() + "</b></red> <gray>" + boss.title(), lore), p -> {
                p.closeInventory();
                String refused = bosses.spawn(boss, p.getLocation());
                p.sendMessage(MiniMessage.miniMessage().deserialize(refused == null ? "<green>" + boss.name() + " est apparu." : "<red>" + refused));
            });
        }
        set(screen, 22, item(Material.BOOK, "<gold>Boss en vie", List.of("<gray>En dessous : clic pour se téléporter ou supprimer.")), p -> { });
        int index = 14;
        for (BossRegistry.Entry entry : bosses.registry().entries()) {
            if (index >= CONTENT.length) {
                break;
            }
            Boss boss = bosses.boss(entry.boss());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + entry.world() + " <white>" + (int) entry.x() + " " + (int) entry.y() + " " + (int) entry.z());
            BossFight fight = bosses.fight(Bukkit.getEntity(entry.uuid()));
            if (fight != null) {
                AttributeInstance max = fight.entity().getAttribute(Attribute.MAX_HEALTH);
                lore.add("<gray>Phase <white>" + fight.phase() + "</white> · <white>" + (int) fight.entity().getHealth()
                        + "</white>/" + (max == null ? "?" : (int) max.getValue()) + " PV");
            } else {
                lore.add("<gray>Non chargé" + (entry.removing() ? " · suppression en attente" : ""));
            }
            lore.add("");
            lore.add("<yellow>Clic : actions");
            set(screen, CONTENT[index++], item(boss == null ? Material.SKELETON_SKULL : boss.icon(),
                    "<red>#" + entry.number() + " " + (boss == null ? entry.boss() : boss.name()), lore), p -> actions(p, entry, false));
        }
        back(screen, 49, this::home);
        player.openInventory(screen.inventory);
    }

    /** Actions sur un boss en vie ; la suppression demande un second clic. */
    private void actions(Player player, BossRegistry.Entry entry, boolean confirm) {
        Boss boss = bosses.boss(entry.boss());
        String name = "#" + entry.number() + " " + (boss == null ? entry.boss() : boss.name());
        Screen screen = screen(27, "<dark_gray>" + name);
        if (confirm) {
            set(screen, 11, item(Material.RED_CONCRETE, "<red><b>Confirmer la suppression</b>",
                    List.of("<gray>Il disparaît sans mourir (ni butin ni expérience),", "<gray>avec sa barre et tout ce qu'il a créé.")), p -> {
                        bosses.remove(entry);
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<green>" + name + " supprimé."));
                        bosses(p);
                    });
        } else {
            set(screen, 11, item(Material.RED_CONCRETE, "<red>Supprimer", List.of("<gray>Clic : demande une confirmation.")),
                    p -> actions(p, entry, true));
        }
        set(screen, 13, item(Material.ENDER_PEARL, "<aqua>Se téléporter", List.of("<gray>À côté du boss.")), p -> {
            p.closeInventory();
            Entity loaded = Bukkit.getEntity(entry.uuid());
            World world = Bukkit.getWorld(entry.world());
            if (loaded != null) {
                p.teleport(loaded.getLocation().add(3, 0, 0));
            } else if (world != null) {
                p.teleport(new Location(world, entry.x(), entry.y(), entry.z()));
            }
        });
        back(screen, 15, this::bosses);
        player.openInventory(screen.inventory);
    }

    // ---- Outils ----

    private static Screen screen(int size, String title) {
        Screen screen = new Screen();
        screen.inventory = Bukkit.createInventory(screen, size, MiniMessage.miniMessage().deserialize(title));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < size; slot++) {
            screen.inventory.setItem(slot, filler);
        }
        return screen;
    }

    private static void set(Screen screen, int slot, ItemStack item, Consumer<Player> action) {
        screen.inventory.setItem(slot, item);
        screen.actions.put(slot, action);
    }

    private static void back(Screen screen, int slot, Consumer<Player> action) {
        set(screen, slot, item(Material.ARROW, "<gray>Retour", List.of()), action);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(text(name));
            List<Component> lines = new ArrayList<>();
            lore.forEach(line -> lines.add(text(line)));
            meta.lore(lines);
        });
        return item;
    }

    private static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static Material egg(EntityType type) {
        Material egg = Material.matchMaterial(type.getKey().getKey() + "_spawn_egg");
        return egg == null ? Material.EGG : egg;
    }

    private static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value).replace('.', ',');
    }

    /** 2 blocs devant le joueur, à hauteur de ses pieds. */
    private static Location inFront(Player player) {
        Location location = player.getLocation();
        return location.add(location.getDirection().setY(0).normalize().multiply(2));
    }

    // ---- Clics ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen screen)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Consumer<Player> action = screen.actions.get(event.getSlot());
        if (action != null) {
            action.accept(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen) {
            event.setCancelled(true);
        }
    }
}
