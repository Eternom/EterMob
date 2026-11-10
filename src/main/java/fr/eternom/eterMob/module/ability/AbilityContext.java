package fr.eternom.eterMob.module.ability;

import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.ai.Situation;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Un lancement de capacité : qui, sur qui, dans quelle situation, et le point visé (fixé au début du télégraphe :
 * les joueurs ont le temps de s'en écarter). data : ce qu'une capacité garde entre begin et cast.
 */
public final class AbilityContext {

    private final Caster caster;
    private final LivingEntity target;
    private final Situation situation;
    private Location spot;
    private Object data;

    public AbilityContext(Caster caster, LivingEntity target, Situation situation) {
        this.caster = caster;
        this.target = target;
        this.situation = situation;
    }

    public Caster caster() {
        return caster;
    }

    public LivingEntity self() {
        return caster.entity();
    }

    /** La cible (peut être null pour une capacité sans cible : aura, mort). */
    public LivingEntity target() {
        return target;
    }

    public Situation situation() {
        return situation;
    }

    /** Point visé, fixé par begin (par défaut : la position de la cible au début, sinon celle du lanceur). */
    public Location spot() {
        if (spot == null) {
            spot = (target != null && target.isValid() ? target : caster.entity()).getLocation();
        }
        return spot.clone();
    }

    public void spot(Location spot) {
        this.spot = spot;
    }

    @SuppressWarnings("unchecked")
    public <T> T data() {
        return (T) data;
    }

    public void data(Object data) {
        this.data = data;
    }

    /** Multiplie une valeur de base par la puissance du lanceur (ratio, rage). */
    public double scale(double base) {
        return base * caster.abilityPower();
    }
}
