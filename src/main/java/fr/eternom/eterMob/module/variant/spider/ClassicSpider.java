package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * L'araignée de base, la référence des autres.
 * <ul>
 *   <li>Ratio 1 et 2 : comme dans Minecraft (calme au jour, sauf si on la frappe).</li>
 *   <li>Ratio 3 et plus : elle reste agressive en plein jour (elle reprend pour cible le joueur le plus proche qu'elle
 *   voit, à moins de 16 blocs).</li>
 *   <li>Ratio 5 : en plus, 15 % plus rapide la nuit.</li>
 * </ul>
 */
public class ClassicSpider extends SpiderVariant {

    private static final int CHECK_TICKS = 10;
    private static final int DAY_HUNTER_RATIO = 3;
    private static final double HUNT_RANGE = 16;
    private static final double NIGHT_SPEED = 0.15;

    private NamespacedKey nightKey;

    public ClassicSpider() {
        super("spider", Rarity.COMMUN, "Aranea");
    }

    private NamespacedKey nightKey() {
        if (nightKey == null) {
            nightKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(ClassicSpider.class), "night");
        }
        return nightKey;
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        if (mob.ratio() < DAY_HUNTER_RATIO) {
            return;
        }
        Spider spider = (Spider) mob.entity();
        if (spider.getTarget() == null && spider.getWorld().isDayTime()) {
            huntInDaylight(spider);
        }
        if (mob.ratio() >= service().ratios().max()) {
            nightSpeed(spider, !spider.getWorld().isDayTime());
        }
    }

    /** Au jour, Minecraft la rend calme : elle reprend pour cible le joueur le plus proche qu'elle voit. */
    private static void huntInDaylight(Spider spider) {
        Player nearest = null;
        double best = HUNT_RANGE * HUNT_RANGE;
        for (Player player : spider.getLocation().getNearbyPlayers(HUNT_RANGE)) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(spider.getLocation());
            if (distance < best && spider.hasLineOfSight(player)) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            spider.setTarget(nearest);
        }
    }

    private void nightSpeed(Spider spider, boolean night) {
        AttributeInstance speed = spider.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null || (speed.getModifier(nightKey()) != null) == night) {
            return;
        }
        speed.removeModifier(nightKey());
        if (night) {
            speed.addModifier(new AttributeModifier(nightKey(), NIGHT_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
