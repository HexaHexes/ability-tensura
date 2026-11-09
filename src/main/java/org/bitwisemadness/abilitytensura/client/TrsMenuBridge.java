package org.bitwisemadness.abilitytensura.client;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.bitwisemadness.abilityhotbar.client.AbilityMenuScreen;
import org.bitwisemadness.abilitytensura.AbilityTensura;
import org.bitwisemadness.abilitytensura.config.TensuraClientConfig;

/**
 * The Tensura RPG System menu replacement (#70, phase 2): with TRS
 * installed, its equip-skills surface opens <b>this pack's ability menu</b>
 * instead - one menu, no extra hotkey. Phase-1 evidence on #70: TRS ships
 * no API package and is built entirely on public NeoForge events, so this
 * mirrors that exact seam - {@code ScreenEvent.Opening} at
 * {@link EventPriority#LOWEST}, after TRS's own NORMAL-priority swap has
 * run (they register from their mod constructor), so both ARR's original
 * entry (their config off) and their landing screen are visible here.
 *
 * <p>The decision matches <b>class names</b>, not types: no compile
 * dependency on TRS, no vendored jar, and the whole rule is a pure
 * function the fixture pins. Without TRS the predicate is false and
 * nothing changes - the byte-identical baseline (#70's requirement 2).
 * Their shop, learning, tree and hub screens are never touched
 * (non-goals).</p>
 */
@EventBusSubscriber(modid = AbilityTensura.MOD_ID, value = Dist.CLIENT)
public final class TrsMenuBridge {

    /** The equip-skills surface (#70 phase 1): ARR's entry + their chain. */
    private static final String[] EQUIP_ENTRIES = {
        "io.github.manasmods.tensura.client.screen.AbilitySelectionScreen",
        "com.japaeditor.tensuraevolution.client.CategoryActionScreen",
        "com.japaeditor.tensuraevolution.client.DirectSkillLoadoutScreen",
        "com.japaeditor.tensuraevolution.client.EvolutionSkillLoadoutScreen",
        // the status screen's "Equipar skills" button lands here too (its
        // PT-BR strings hid it from the first pass) - the second entry to
        // their equip surface, opened from StatusScreen/EvolutionMainScreen
        "com.japaeditor.tensuraevolution.client.SkillCategoryPickerScreen",
    };

    private TrsMenuBridge() {
    }

    /**
     * The routing decision over an opening screen (null = nothing
     * opening): replace only when TRS is present and the screen is the
     * equip-skills surface.
     */
    public static boolean shouldReplace(boolean trsPresent, Screen screen) {
        return screen != null && replaces(trsPresent, screen.getClass().getName());
    }

    /**
     * The name-level core of the decision - pure, the fixture's seam.
     *
     * @param trsPresent whether Tensura RPG System is installed
     * @param className  the opening screen's runtime class name (nullable)
     * @return true only for the equip surface, only with TRS present
     */
    public static boolean replaces(boolean trsPresent, String className) {
        if (!trsPresent || className == null) {
            return false;
        }
        for (String entry : EQUIP_ENTRIES) {
            if (entry.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The screen-open listener: gates on TRS <b>and</b> the hotbar (both
     * ModList checks), then swaps the equip entry for {@link
     * AbilityMenuScreen}. No keybind is registered anywhere - the
     * directive's core ask: TRS users keep their entry, they just land
     * in our menu.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ModList.get().isLoaded("tensuraevolution")
                || !ModList.get().isLoaded("ability_hotbar")) {
            return;
        }
        if (!TensuraClientConfig.REPLACE_TRS_EQUIP_SCREEN.get()) {
            return; // pack keeps Tensura Evolution's own equip screens
        }
        if (shouldReplace(true, event.getNewScreen())) {
            event.setNewScreen(new AbilityMenuScreen());
        }
    }
}
