package fr.eternom.eterMob.module.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /etermob boss &lt;id&gt; | list | remove [numéro|all] (etermob.admin). La suppression retire le boss sans le tuer (ni
 * butin, ni expérience), avec sa barre et tout ce qu'il a créé ; elle marche aussi sur un boss lointain ou non chargé.
 */
public class BossCommand {

    /** Distance de recherche du boss le plus proche pour « remove » sans numéro. */
    private static final double NEAREST_RANGE = 64;

    private final BossService bosses;

    public BossCommand(BossService bosses) {
        this.bosses = bosses;
    }

    /** args[0] = "boss". */
    public void handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> list(sender);
            case "remove" -> remove(sender, args.length >= 3 ? args[2] : null);
            default -> spawn(sender, args[1]);
        }
    }

    private void spawn(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("En jeu seulement.", NamedTextColor.RED));
            return;
        }
        Boss boss = bosses.boss(id.toLowerCase());
        if (boss == null) {
            usage(sender);
            return;
        }
        String refused = bosses.spawn(boss, player.getLocation());
        if (refused != null) {
            sender.sendMessage(Component.text(refused, NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(boss.name() + " est apparu.", NamedTextColor.GREEN));
    }

    private void list(CommandSender sender) {
        List<BossRegistry.Entry> entries = bosses.registry().entries();
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("Aucun boss en vie.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Boss en vie (" + entries.size() + ") :", NamedTextColor.GOLD));
        for (BossRegistry.Entry entry : entries) {
            Boss boss = bosses.boss(entry.boss());
            boolean loaded = Bukkit.getEntity(entry.uuid()) != null;
            sender.sendMessage(Component.text(" #" + entry.number() + " ", NamedTextColor.YELLOW)
                    .append(Component.text((boss == null ? entry.boss() : boss.name()) + " · " + entry.world() + " "
                            + (int) entry.x() + " " + (int) entry.y() + " " + (int) entry.z()
                            + (loaded ? " · chargé" : " · non chargé") + (entry.removing() ? " · suppression en attente" : ""),
                            NamedTextColor.GRAY)));
        }
    }

    private void remove(CommandSender sender, String which) {
        if (which == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Depuis la console : /etermob boss remove <numéro|all>.", NamedTextColor.RED));
                return;
            }
            BossFight nearest = bosses.nearest(player.getLocation(), NEAREST_RANGE);
            if (nearest == null) {
                sender.sendMessage(Component.text("Aucun boss à moins de " + (int) NEAREST_RANGE + " blocs (/etermob boss list).",
                        NamedTextColor.RED));
                return;
            }
            String name = nearest.boss().name();
            bosses.remove(nearest);
            sender.sendMessage(Component.text(name + " supprimé.", NamedTextColor.GREEN));
            return;
        }
        if (which.equalsIgnoreCase("all")) {
            List<BossRegistry.Entry> entries = bosses.registry().entries();
            entries.forEach(bosses::remove);
            sender.sendMessage(Component.text(entries.size() + " boss supprimé(s).", NamedTextColor.GREEN));
            return;
        }
        BossRegistry.Entry entry;
        try {
            entry = bosses.registry().find(Integer.parseInt(which.replace("#", "")));
        } catch (NumberFormatException e) {
            entry = null;
        }
        if (entry == null) {
            sender.sendMessage(Component.text("Numéro inconnu (/etermob boss list).", NamedTextColor.RED));
            return;
        }
        boolean loaded = Bukkit.getEntity(entry.uuid()) != null;
        bosses.remove(entry);
        sender.sendMessage(Component.text(loaded ? "Boss #" + entry.number() + " supprimé."
                : "Boss #" + entry.number() + " non chargé : son chunk est chargé un instant, il sera supprimé dès qu'il apparaît.",
                NamedTextColor.GREEN));
    }

    private void usage(CommandSender sender) {
        List<String> ids = new ArrayList<>();
        bosses.bosses().forEach(boss -> ids.add(boss.id()));
        sender.sendMessage(Component.text("/etermob boss <" + String.join("|", ids) + "> | list | remove [numéro|all]",
                NamedTextColor.RED));
    }

    /** args[0] = "boss". */
    public List<String> complete(String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            options.add("list");
            options.add("remove");
            bosses.bosses().forEach(boss -> options.add(boss.id()));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            options.add("all");
            bosses.registry().entries().forEach(entry -> options.add(String.valueOf(entry.number())));
        }
        return options;
    }
}
