package fr.eternom.eterMob.module.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** /etermob list | spawn <variant> [ratio] : pour tester les variants un par un (etermob.admin). */
public class MobCommand implements TabExecutor {

    private final MobService mobs;

    public MobCommand(MobService mobs) {
        this.mobs = mobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Variants (" + mobs.variants().all().size() + ") :", NamedTextColor.GOLD));
            for (MobVariant variant : mobs.variants().all()) {
                sender.sendMessage(Component.text(" " + variant.id(), NamedTextColor.YELLOW)
                        .append(Component.text(" · " + variant.name() + " · " + variant.type().getKey().getKey() + " · "
                                + variant.rarity().id(), NamedTextColor.GRAY)));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("En jeu seulement.", NamedTextColor.RED));
                return true;
            }
            MobVariant variant = mobs.variants().get(args[1]);
            if (variant == null) {
                sender.sendMessage(Component.text("Variant inconnu : " + args[1] + " (/etermob list).", NamedTextColor.RED));
                return true;
            }
            int ratio;
            try {
                ratio = args.length >= 3 ? Integer.parseInt(args[2]) : mobs.ratios().roll(player.getLocation());
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Ratio : un nombre de 1 à " + mobs.ratios().max() + ".", NamedTextColor.RED));
                return true;
            }
            int finalRatio = mobs.ratios().clamp(ratio);
            player.getWorld().spawnEntity(player.getLocation(), variant.type(), CreatureSpawnEvent.SpawnReason.COMMAND,
                    entity -> {
                        if (entity instanceof LivingEntity living) {
                            mobs.apply(living, variant, finalRatio);
                        }
                    });
            sender.sendMessage(Component.text(variant.name() + " apparu, ratio " + finalRatio + ".", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("/etermob list | /etermob spawn <variant> [ratio]", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "spawn"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            mobs.variants().all().forEach(variant -> options.add(variant.id()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            IntStream.rangeClosed(1, mobs.ratios().max()).forEach(ratio -> options.add(String.valueOf(ratio)));
        }
        String start = args[args.length - 1].toLowerCase();
        options.removeIf(option -> !option.toLowerCase().startsWith(start));
        return options;
    }
}
