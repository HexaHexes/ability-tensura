package org.bitwisemadness.abilitytensura.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers NeoForge's own configuration screen for this mod (the fleet's
 * hotbar pattern).
 *
 * <p>Without an {@link IConfigScreenFactory}, config UI mods (Configured,
 * etc.) claim our config files and edit them with their own editor -
 * Configured's editor reverts every unedited non-default value to its
 * default when it saves. Registering a factory during mod construction
 * puts ours in place before those mods generate their factories during
 * {@code FMLCommonSetupEvent}, and they defer to any factory already
 * present.</p>
 *
 * <p>Client-only: this class references {@link ConfigurationScreen}, so a
 * dedicated server must never load it - the caller registers it only
 * behind a {@code Dist.CLIENT} check, which means the call (and with it
 * the class) is never reached server-side.</p>
 */
public final class ClientConfigScreen {

    private ClientConfigScreen() {
    }

    /**
     * Claims this mod's config screens for NeoForge's {@link ConfigurationScreen}.
     *
     * @param modContainer this mod's container, as passed to the mod constructor
     */
    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
