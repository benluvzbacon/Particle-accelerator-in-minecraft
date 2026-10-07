package com.particlephysics.radiation;

import com.particlephysics.config.ModConfig;
import com.particlephysics.world.ModState;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * What radiation does to a player.
 *
 * <p>Dose is accumulated continuously from the real field computed by
 * {@link com.particlephysics.world.RadiationState} and turned into the effects a real exposure
 * would have: a short exposure makes you sick, a long one kills you. The thresholds are the ones
 * used in radiation protection (1 Sv = acute radiation syndrome, 5 Sv = lethal), scaled by the
 * server configuration.
 */
public final class RadiationEffects {
    /** Dose rate above which the player starts accumulating acute dose, in uSv/h. */
    public static final double SICK_THRESHOLD = 1_000.0;

    private RadiationEffects() {
    }

    /** Called every second for each player near a machine. */
    public static void tickPlayer(ServerPlayerEntity player) {
        ModConfig config = ModConfig.get();
        if (!config.radiationDamageEnabled) {
            return;
        }
        double microSvPerHour = ModState.radiation().doseRateMicroSvPerHour(player.getWorld(),
                player.getPos()) * config.radiationScale;
        // 1 hour of game time is 50 seconds of real time at 20 ticks/s; the dose below is what the
        // player receives during one second of play.
        double doseSvThisSecond = microSvPerHour * 1.0e-6 / 3600.0 * 50.0;
        if (doseSvThisSecond <= 0) {
            return;
        }
        var research = ModState.research(player);
        research.accumulatedDose += doseSvThisSecond;
        research.recentDose += doseSvThisSecond;
        applySymptoms(player, research.recentDose);
    }

    /** Immediate effects of a single large exposure (a hand in the beam, a hand in a source). */
    public static void applyAcute(ServerPlayerEntity player, double millisievert) {
        if (!ModConfig.get().radiationDamageEnabled) {
            return;
        }
        var research = ModState.research(player);
        double doseSv = millisievert / 1000.0;
        research.accumulatedDose += doseSv;
        research.recentDose += doseSv;
        applySymptoms(player, research.recentDose);
    }

    private static void applySymptoms(ServerPlayerEntity player, double recentDoseSv) {
        if (recentDoseSv > 0.2 && recentDoseSv <= 1.0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0));
        } else if (recentDoseSv > 1.0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 400, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 400, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 0));
            if (recentDoseSv > 4.0) {
                player.damage(player.getDamageSources().magic(), 4.0f);
            }
        }
        // the body recovers slowly: recent dose fades by 2% per call
        var research = ModState.research(player);
        research.recentDose *= 0.98;
    }

    /** Describes the accumulated dose for the journal. */
    public static String describe(double accumulatedSv) {
        if (accumulatedSv < 0.001) {
            return "no measurable exposure";
        }
        if (accumulatedSv < 0.1) {
            return String.format(java.util.Locale.ROOT, "%.1f mSv lifetime (background level)",
                    accumulatedSv * 1000.0);
        }
        if (accumulatedSv < 1.0) {
            return String.format(java.util.Locale.ROOT, "%.2f Sv lifetime (occupational limit 0.02 Sv)",
                    accumulatedSv);
        }
        return String.format(java.util.Locale.ROOT, "%.2f Sv lifetime (acute radiation syndrome)",
                accumulatedSv);
    }
}
