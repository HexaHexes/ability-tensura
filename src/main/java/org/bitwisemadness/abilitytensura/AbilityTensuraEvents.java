package org.bitwisemadness.abilitytensura;

import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilitytensura.activation.TensuraActivation;
import org.bitwisemadness.abilitytensura.catalog.BattlewillCatalog;
import org.bitwisemadness.abilitytensura.catalog.TensuraAbilityCatalog;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;
import org.bitwisemadness.abilitytensura.mastery.TensuraMastery;

/**
 * The mod-bus listener half of Ability: Tensura. Routing is not spelled
 * out: {@code AutomaticEventSubscriber} places each listener from its event
 * type - {@code RegisterEvent} lands on the mod bus, the rest on the game
 * bus - so only the event type here carries meaning.
 *
 * <p>Registration keeps extension-point-1's target (core's ability
 * registry) but fires only at <b>ARR's skill registry event</b> with a
 * <b>raw {@code Registry.register}</b> write: the per-art shape reads
 * (activation mapping, declared costs) need ARR's skills populated - the
 * timing gametest proved core's own event runs against an empty skill
 * registry (resolved=0 of 332) - and NeoForge's {@code RegisterEvent}
 * source shows {@code register(key, ...)} is a <b>silent no-op on key
 * mismatch</b> with <b>eager</b> supplier evaluation, so core's event
 * can never build live shapes and this event can never write through
 * that method. All registries stay open until {@code freezeData()} runs
 * <i>after</i> every RegisterEvent, so the raw write here is byte-for-
 * byte what NeoForge's own method performs internally; dependency order
 * guarantees architectury's skill flush ran before this listener.</p>
 */
@EventBusSubscriber(modid = AbilityTensura.MOD_ID)
public final class AbilityTensuraEvents {

    private AbilityTensuraEvents() {
    }

    /**
     * Contributes the catalogs to core's registry at ARR's skill event -
     * the one moment both inputs exist: live skills for the shapes, an
     * open registry for the write (the battlewills, #58, and the dynamic
     * skill/magic enumeration, #42 slice 1).
     */
    @SubscribeEvent
    public static void onRegisterAbilities(RegisterEvent event) {
        if (!event.getRegistryKey().equals(SkillAPI.getSkillRegistryKey())) {
            return;
        }
        Registry<AbilityDefinition> registry = AbilityRegistries.registry();
        if (registry == null) {
            AbilityTensura.LOGGER.error(
                    "core's ability registry is missing - catalogs not registered");
            return;
        }
        BattlewillCatalog.register(registry);
        TensuraAbilityCatalog.register(registry);
        // The mastery declarations ride the same event (REQ-CORE-12): an
        // art that is a menu ability is a mastery participant, id sets
        // always aligned with the catalogs' own track() loop
        TensuraMastery.declareAll();
    }

    /**
     * Registers the unlock gate and the activation handlers after every
     * catalog id is tracked (the registry phase completes before common
     * setup): eligibility rule, availability declarations, the
     * REMOVE_SKILL listener, one shared NATIVE handler per ability, and
     * the config-live mastery gain handler.
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        UnlockGate.register();
        TensuraActivation.registerHandlers();
        TensuraMastery.registerGainHandler();
    }

    /**
     * Login sweep of the removal directive: unlearnt assignments leave the
     * player before their first snapshot reaches the client.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnlockGate.reconcile(player);
        }
    }
}
