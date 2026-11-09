package org.bitwisemadness.abilitytensura;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.bitwisemadness.abilitytensura.client.ClientConfigScreen;
import org.bitwisemadness.abilitytensura.config.TensuraClientConfig;
import org.bitwisemadness.abilitytensura.config.TensuraServerConfig;
import org.bitwisemadness.abilitytensura.resource.TensuraResources;
import org.slf4j.Logger;

/**
 * Ability: Tensura entry point.
 */
@Mod(AbilityTensura.MOD_ID)
public final class AbilityTensura {

    public static final String MOD_ID = "ability_tensura";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AbilityTensura(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("Ability: Tensura initialized");
        // The audit's config pair: SERVER = gate/activation policy (read at
        // use time - it loads at server start), CLIENT = menu preferences
        // (read at common setup and later; see each class's timing note)
        modContainer.registerConfig(ModConfig.Type.SERVER, TensuraServerConfig.SPEC,
                "ability_tensura-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, TensuraClientConfig.SPEC,
                "ability_tensura-client.toml");
        // Claim NeoForge's config screen before Configured can, so its
        // editor (which reverts unedited values on save) never handles our
        // file. Client-only: ConfigurationScreen must never load server-side.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientConfigScreen.register(modContainer);
        }
        // mod construction - before RegisterEvent, so registration-time cost
        // decisions are order-safe (core's declare timing contract)
        TensuraResources.declareMagicules();
        TensuraResources.declareAura();
        TensuraResources.declareEP();
    }
}
