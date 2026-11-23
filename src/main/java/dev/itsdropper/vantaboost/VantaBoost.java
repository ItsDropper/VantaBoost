package dev.itsdropper.vantaboost;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint. Keep initialization independent from client integrations. */
public final class VantaBoost implements ModInitializer {
    public static final String MOD_ID = "vantaboost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("VantaBoost initialized");
    }
}
