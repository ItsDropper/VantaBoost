package dev.itsdropper.vantaboost.client;

import dev.itsdropper.vantaboost.VantaBoost;
import net.fabricmc.api.ClientModInitializer;

/** Client-only lifecycle wiring. */
public final class VantaBoostClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VantaBoost.LOGGER.info("VantaBoost client runtime initialized");
    }
}
