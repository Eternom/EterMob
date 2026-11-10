package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ai.Caster;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * call : appel à l'aide (cri) : les monstres sans cible à moins de radius blocs (16) prennent sa cible, et ceux d'EterMob
 * la comptent comme une menace. Surtout pour les meneurs, avec leurs suiveurs.
 */
public final class Call extends Ability {

    public Call(ConfigurationSection config) {
        super("call", config, new Defaults(Trigger.ACTIVE, 20, 0.6, 0, 40, 1, 0, 16, 0, 0));
    }

    @Override
    public double score(AbilityContext context) {
        int idle = context.situation().idleAllies();
        return idle == 0 ? 0 : clamp01(0.35 + 0.12 * idle);
    }

    @Override
    public void cast(AbilityContext context) {
        LivingEntity self = context.self();
        LivingEntity target = context.target();
        self.getWorld().playSound(self.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
        if (!(target instanceof Player player)) {
            return;
        }
        for (Entity nearby : self.getNearbyEntities(radius, radius / 2, radius)) {
            if (nearby instanceof Mob mob && nearby instanceof Monster && mob.getTarget() == null && mob.isValid()) {
                mob.setTarget(player);
                Caster ally = Services.mobs() == null ? null : Services.mobs().caster(mob);
                if (ally != null) {
                    ally.brain().threat().add(player.getUniqueId(), 5);
                }
            }
        }
    }
}
