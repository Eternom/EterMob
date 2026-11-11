package fr.eternom.eterMob.module.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Le seul endroit qui touche l'API de BetterModel (chargé seulement s'il est installé). */
final class BetterModelBridge implements Models.Bridge {

    BetterModelBridge() {
        // Échoue tout de suite (LinkageError) si l'API n'est pas celle attendue
        BetterModel.class.getName();
    }

    @Override
    public boolean attach(LivingEntity entity, String model) {
        return BetterModel.model(model)
                .map(renderer -> renderer.getOrCreate(BukkitAdapter.adapt(entity)) != null)
                .orElse(false);
    }

    @Override
    public boolean play(Entity entity, String... animations) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUniqueId());
        if (registry == null) {
            return false;
        }
        for (EntityTracker tracker : registry.trackers()) {
            for (String animation : animations) {
                if (animation != null && tracker.animate(animation, AnimationModifier.DEFAULT_WITH_PLAY_ONCE)) {
                    return true;
                }
            }
        }
        return false;
    }
}
