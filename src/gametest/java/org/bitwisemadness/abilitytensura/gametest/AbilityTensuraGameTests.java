package org.bitwisemadness.abilitytensura.gametest;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeOwners;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitytensura.AbilityTensura;
import org.bitwisemadness.abilitytensura.activation.TensuraActivation;
import org.bitwisemadness.abilitytensura.catalog.BattlewillCatalog;
import org.bitwisemadness.abilitytensura.catalog.TensuraAbilityCatalog;
import org.bitwisemadness.abilitytensura.gate.TensuraMenuTabs;

/**
 * Boot and catalog coverage for the integration layer (#58): the mod loads,
 * and every native battlewill ARR registers is exposed to core - the
 * mission's registry-size == integrated-count pattern, style-free (the
 * style layer rides the deferred Better Spellcasting track, #10).
 *
 * <p>Keep the {@code expectedTests} count in {@code build.gradle} in sync
 * with the number of {@code @GameTest} methods here, or the harness fails
 * the run.</p>
 */
@GameTestHolder(AbilityTensura.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityTensuraGameTests {

    private AbilityTensuraGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * Coverage (#58 + #42 slice 1): the staging is real (Tensura must be on
     * the gametest runtime), ARR's category tags yield their full inventory
     * (213 skills + 119 magic, pinned), the 23 battlewills ride their own
     * catalog, and every id is registered and resolvable through core's
     * discovery with the registry holding exactly that set - a lost
     * registration or a stray entry fails the run.
     */
    @GameTest(template = "empty")
    public static void catalogRegistersAllNativeArts(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the catalog coverage test (#42)");

        var skillAndMagic = TensuraAbilityCatalog.expectedIds();
        check(skillAndMagic.size() == 332,
                "the native inventory is 213 skills + 119 magic (ARR category tags), got "
                        + skillAndMagic.size());

        var expected = new java.util.HashSet<>(skillAndMagic);
        for (String path : BattlewillCatalog.paths()) {
            expected.add(BattlewillCatalog.idFor(path));
        }
        for (ResourceLocation id : expected) {
            check(AbilityDiscovery.get(id).isPresent(),
                    "catalog art must be registered and resolvable: " + id);
        }
        check(AbilityDiscovery.all().size() == expected.size(),
                "registry must hold exactly the catalogs, got "
                        + AbilityDiscovery.all().size() + " expected " + expected.size());

        // The unlock gate's display half is wired for every tracked id...
        for (ResourceLocation id : expected) {
            check(AvailabilityProviders.has(id),
                    "the unlock gate must declare availability for: " + id);
        }
        // ...its read seam is declared for every tracked id (core#117)...
        for (ResourceLocation id : expected) {
            check(AbilityUnlocks.has(id),
                    "the unlock provider must be declared for: " + id);
        }
        // ...and every tracked id defers its cooldown to ARR through
        // core's handler contract (alpha.45).
        for (ResourceLocation id : expected) {
            check(CooldownOwners.ownerOf(id) == CooldownOwners.Owner.HANDLER,
                    "the cooldown handler must be declared for: " + id);
        }
        // ...carries core's mode-change adapter for the hotbar's intent
        // (#53: the domain half runs ARR's native body)...
        for (ResourceLocation id : expected) {
            check(ModeChangeHandlers.has(id),
                    "the mode-change handler must be registered for: " + id);
        }
        // ...and tensura's own menu visibility rule is wired for every
        // tracked id (core#124: unlocked-gated listing).
        for (ResourceLocation id : expected) {
            check(VisibilityHandlers.has(id),
                    "the visibility handler must be declared for: " + id);
        }
        // ...its activation half sits on core's canonical chain...
        check(AbilityCore.ELIGIBILITY.size() > 0,
                "the unlock gate must register an eligibility rule");
        // ...and every tracked ability carries the shared NATIVE handler
        // (#42 slice 2: one service, never per-feature calls).
        for (ResourceLocation id : expected) {
            check(AbilityCore.HANDLERS.get(id).isPresent(),
                    "the activation service must register a handler for: " + id);
        }
        helper.succeed();
    }

    /**
     * The cost-deferral gate (the native-economy rule): every catalog art
     * carries <b>empty costs</b> and a <b>NATIVE</b> charging model -
     * Tensura: Reincarnated prices, charges and regenerates aura and
     * magicule itself, so core must never price, affordability-gate, or
     * charge these ids. A cost or an EXTERNAL model creeping into the
     * catalog fails the run (the #12 "no pre-charge" code gate, now
     * covering all 355 arts).
     */
    @GameTest(template = "empty")
    public static void catalogDefersCostsToTensura(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the cost-deferral test");
        var expected = new java.util.HashSet<>(TensuraAbilityCatalog.expectedIds());
        for (String path : BattlewillCatalog.paths()) {
            expected.add(BattlewillCatalog.idFor(path));
        }
        for (ResourceLocation id : expected) {
            var definition = AbilityDiscovery.get(id).orElseThrow();
            check(definition.costs().isEmpty(),
                    "costs must defer to Tensura: Reincarnated for: " + id);
            check(AbilityCore.HANDLERS.get(id).isPresent(),
                    "the activation handler must exist for: " + id);
            check(AbilityCore.HANDLERS.modelOf(id) == ChargingModel.NATIVE,
                    "core must never charge a native art (NATIVE model): " + id);
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(message);
        }
    }

    /**
     * The mode-change seam (#53): an intent against an unlearned ability
     * refuses RESTRICTED through core's vocabulary before ARR's native
     * body can run - no packet constructed, no state written, zero
     * residue by construction. The cycle parity and the not-slotted
     * refusal are ARR-domain checks carried by the issue's documented
     * in-game line (they need a real preset; the hotbar intent transport
     * arrives with hotbar#29).
     */
    @GameTest(template = "empty")
    public static void unlearnedModeChangeRefusesRestricted(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the mode-change test (#53)");
        ServerPlayer player = mockPlayer(helper);
        ResourceLocation id = TensuraAbilityCatalog.expectedIds().iterator().next();
        var result = ModeChangeHandlers.run(new ModeChangeRequest(id, 0), player);
        check(result.outcome() == ActivationOutcome.REFUSED_RESTRICTED,
                "an unlearned ability must refuse RESTRICTED, got " + result.outcome());
        helper.succeed();
    }

    /** A bare server-side player - enough for reads; never logged in. */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var profile = new GameProfile(java.util.UUID.randomUUID(), "ModeIntentProbe");
        return new ServerPlayer(server, helper.getLevel(), profile,
                ClientInformation.createDefault());
    }

    /**
     * Tensura's visibility rule in action (core#124): an unlearned art
     * hides for a live player, while an unknown context (null player)
     * keeps it listed - unknown never hides (hotbar#153's never-guess).
     */
    @GameTest(template = "empty")
    public static void menuVisibilityHidesUnlearnedArts(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the visibility test (core#124)");
        ServerPlayer player = mockPlayer(helper);
        ResourceLocation id = TensuraAbilityCatalog.expectedIds().iterator().next();

        check(!VisibilityHandlers.visible(player, id),
                "an unlearned art must hide for a live player: " + id);
        check(VisibilityHandlers.visible(null, id),
                "an unknown context must never hide the art: " + id);
        helper.succeed();
    }

    /**
     * The mode vocabulary's coverage (core#113): every catalog art whose
     * upstream skill offers an expressible vocabulary declares it - and
     * only those - computed through {@link TensuraActivation#vocabularyFor}
     * so the fixture and production share ONE rule and can never drift.
     * The registry lookup and a non-vacuous cycleable count are asserted so
     * an extraction that silently resolved nothing can never pass.
     */
    @GameTest(template = "empty")
    public static void declaresModeVocabularyForMultiModeArts(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the mode vocabulary test");
        var skillAndMagic = TensuraAbilityCatalog.expectedIds();
        check(SkillAPI.getSkillRegistry().get(skillAndMagic.iterator().next()) != null,
                "the skill registry must resolve catalog ids");
        var expected = new java.util.HashSet<>(skillAndMagic);
        for (String path : BattlewillCatalog.paths()) {
            expected.add(BattlewillCatalog.idFor(path));
        }
        int cycleable = 0;
        for (ResourceLocation id : expected) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            boolean declares = !TensuraActivation.vocabularyFor(id, skill).isEmpty();
            if (declares) {
                cycleable++;
            }
            check(AbilityModes.has(id) == declares,
                    "mode vocabulary must match the upstream rule for: " + id);
            check((ModeOwners.projectionFor(id) != null) == declares,
                    "the live mode projection must pair with the vocabulary for: " + id);
        }
        check(cycleable > 0,
                "upstream must offer at least one cycleable art (no vacuous pass)");
        helper.succeed();
    }

    /**
     * The menu-tabs soft hook (hotbar#120's linkage lesson, honored by the
     * brief's tab declaration): this environment deliberately stages NO
     * hotbar, so an explicit declare() must complete by gating <b>before</b>
     * MenuTabs ever links - a broken gate order would throw
     * NoClassDefFoundError here. The three tabs and their icons are the
     * pack's in-game check (they need hotbar + Skill Study Books present).
     */
    @GameTest(template = "empty")
    public static void menuTabsGateOnTheAbsentHotbar(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the menu-tabs test");
        check(!ModList.get().isLoaded("ability_hotbar"),
                "this environment must keep proving the soft hook: no hotbar staged");

        Throwable linkageFailure = null;
        try {
            TensuraMenuTabs.declare();
        } catch (Throwable failure) { // NoClassDefFoundError would land here
            linkageFailure = failure;
        }
        check(linkageFailure == null,
                "the gate must return before the hotbar's MenuTabs links: " + linkageFailure);
        helper.succeed();
    }
}
