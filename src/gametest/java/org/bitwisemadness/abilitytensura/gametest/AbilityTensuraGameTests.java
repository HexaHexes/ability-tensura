package org.bitwisemadness.abilitytensura.gametest;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.IAbility;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
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
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeOwners;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilitytensura.AbilityTensura;
import org.bitwisemadness.abilitytensura.activation.TensuraActivation;
import org.bitwisemadness.abilitytensura.catalog.BattlewillCatalog;
import org.bitwisemadness.abilitytensura.catalog.TensuraAbilityCatalog;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;

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
     * Costs are display metadata while charging stays NATIVE
     * ({@code ResourceCost}'s own contract: the descriptor says what a
     * cast costs, the provider's chargingModel is the only charge site) -
     * and every cost the catalog declares is an honest, positive amount
     * of a currency ARR itself quotes (magicule or aura).
     */
    @GameTest(template = "empty")
    public static void catalogDeclaresDisplayCostsAndNativeCharging(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the display-cost test");
        var expected = new java.util.HashSet<>(TensuraAbilityCatalog.expectedIds());
        for (String path : BattlewillCatalog.paths()) {
            expected.add(BattlewillCatalog.idFor(path));
        }
        for (ResourceLocation id : expected) {
            var definition = AbilityDiscovery.get(id).orElseThrow();
            for (org.bitwisemadness.abilitycore.api.ResourceCost cost : definition.costs()) {
                check(Double.isFinite(cost.amount()) && cost.amount() >= 0,
                        "a declared cost must be a finite non-negative amount: " + id);
                check(cost.resourceId().equals("tensura:magicule")
                                || cost.resourceId().equals("tensura:aura"),
                        "declared costs must name ARR's own currencies: " + id);
            }
            check(AbilityCore.HANDLERS.get(id).isPresent(),
                    "the activation handler must exist for: " + id);
            check(AbilityCore.HANDLERS.modelOf(id) == ChargingModel.NATIVE,
                    "core must never charge a native art (NATIVE model): " + id);
        }
        helper.succeed();
    }

    /**
     * The registration-time timing probe (owner's bytecode brief): the
     * definitions were built while ARR's skill registry was POPULATED -
     * resolved shapes map real activation types and costs instead of
     * degrading to the INSTANT/empty fallback the absent case produces.
     */
    @GameTest(template = "empty")
    public static void registrationSeesTheSkillRegistry(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the registration-timing test");
        int resolved = TensuraActivation.skillsResolvedAtRegistration();
        check(resolved > 0,
                "definitions must resolve ARR skills at RegisterEvent (resolved="
                        + resolved + " of " + TensuraAbilityCatalog.expectedIds().size() + ")");
        helper.succeed();
    }

    /**
     * The owner's custom-preset directive, both environments: WITH
     * hotbar, a cast maps the active bar to its <b>own preset at the
     * tail</b> of ARR's presets and mirrors the bar's slots into it - the
     * player's native presets stay byte-for-byte untouched, so the
     * "ability bar is full" collision of first-empty-slot mirroring can
     * never happen, and each bar's skills/modes stay isolated. WITHOUT
     * hotbar (the repo's living linkage proof - it is a soft hook and
     * the gametest boots without it), the soft gates must take the
     * legacy path: ARR's own active preset decides, never remapped.
     */
    @GameTest(template = "empty")
    public static void customPresetsPerHotbarLeaveNativePresetsAlone(
            GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the custom-preset test");
        boolean hotbarStaged = ModList.get().isLoaded("ability_hotbar");
        ServerPlayer player = mockPlayer(helper);
        String lastStage = "no candidate attempted";

        for (ResourceLocation id : TensuraAbilityCatalog.expectedIds().stream().limit(8)
                .toList()) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (skill == null) {
                lastStage = "registry miss: " + id;
                continue;
            }
            SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
            if (SkillAPI.getSkillsFrom(player).getSkill(id).isEmpty()) {
                lastStage = "updateSkill did not register: " + id;
                continue;
            }
            IAbility ability = TensuraStorages.getAbilityFrom(player);
            if (ability == null) {
                lastStage = "ability storage missing";
                continue;
            }
            if (ability.isAbilityInActivePreset(skill)) {
                lastStage = "fixture leak: " + id + " already equipped";
                continue;
            }
            int presets = ability.getPresets().size();
            check(presets > 1, "the player must have several presets to map onto");
            var nativeBefore = java.util.List.copyOf(ability.getAbilitySlots(0));
            int activeBefore = ability.getActivePreset();

            if (hotbarStaged) {
                // our menu's built-in window bar, with the skill slotted to it
                player.setData(BarStateAttachments.BAR_STATE,
                        org.bitwisemadness.abilityhotbar.state.BarState.empty(9, 1)
                                .withAssignment(0, id));
            }
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            if (existence == null) {
                lastStage = "existence storage missing";
                continue;
            }
            existence.setMagicule(EnergyHelper.getMaxMagicule(player));
            existence.setAura(EnergyHelper.getMaxAura(player));

            var result = AbilityCore.PIPELINE.run(
                    ActivationRequest.of(id, ActivationSource.DIRECT_SLOT), player);
            if (result.outcome() != ActivationOutcome.ACTED) {
                lastStage = "pipeline refused " + id + ": " + result.outcome();
                continue;
            }
            check(ability.isAbilityInActivePreset(skill),
                    "the skill must be equipped after the cast");
            if (!hotbarStaged) {
                // the linkage proof: soft gates took the legacy path
                check(ability.getActivePreset() == activeBefore,
                        "without hotbar ARR's own active preset must decide "
                                + "(no remap), active=" + ability.getActivePreset());
                helper.succeed();
                return;
            }
            check(ability.getActivePreset() == presets - 1,
                    "bar 0 must map to the LAST preset (active="
                            + ability.getActivePreset() + " of " + presets + ")");
            var nativeAfter = ability.getAbilitySlots(0);
            check(nativeAfter.size() == nativeBefore.size(),
                    "the native preset keeps its shape");
            for (int i = 0; i < nativeAfter.size(); i++) {
                boolean same = sameSlot(nativeBefore.get(i), nativeAfter.get(i));
                check(same, "the native preset must stay untouched at slot " + i);
            }
            helper.succeed();
            return;
        }
        check(false, "no catalog skill cast through a custom preset; last stage: "
                + lastStage);
    }

    /** Slot-for-slot equality (preset immutability pin). */
    private static boolean sameSlot(
            io.github.manasmods.tensura.storage.ability.AbilitySlot before,
            io.github.manasmods.tensura.storage.ability.AbilitySlot after) {
        if (before.isEmpty() || after.isEmpty()) {
            return before.isEmpty() && after.isEmpty();
        }
        return before.getSkill() == after.getSkill()
                && before.getMode() == after.getMode();
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

    /**
     * A bare server-side player with a loopback connection: reads work, and
     * setup steps that message or send (learnSkill's notification) never
     * NPE on a null listener - outbound packets land in an embedded channel
     * that discards them.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var profile = new GameProfile(java.util.UUID.randomUUID(), "ModeIntentProbe");
        var player = new ServerPlayer(server, helper.getLevel(), profile,
                ClientInformation.createDefault());
        var embedded = new io.netty.channel.embedded.EmbeddedChannel();
        embedded.pipeline().addFirst(new io.netty.channel.ChannelInboundHandlerAdapter());
        var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        try {
            connection.channelActive(embedded.pipeline().firstContext());
        } catch (Exception ignored) {
            // best effort: sends degrade to drops, never crash the fixture
        }
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                server, connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
        return player;
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
     * The full cast chain, end to end (the #42 parity fixture): for a
     * candidate catalog skill, give the player the native-equivalent
     * setup - learned, slotted into the active preset at slot 0 mode 0,
     * existence topped up - then run the WHOLE core pipeline. ACTED
     * proves eligibility + cooldown handler + resource + activation +
     * ARR's startHoldSkill together; every other outcome names the stage
     * that broke (self-diagnosing: the failure message IS the locus).
     */
    @GameTest(template = "empty")
    public static void castsASetUpSkillThroughTheFullChain(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the cast-chain test");
        ServerPlayer player = mockPlayer(helper);
        String lastStage = "no candidate attempted";

        for (ResourceLocation id : TensuraAbilityCatalog.expectedIds().stream().limit(8)
                .toList()) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (skill == null) {
                lastStage = "registry miss: " + id;
                continue;
            }
            // Possession without the learning economy: ARR's UNLOCK_SKILL
            // listener cancels for an unqualified mock (skill points/race),
            // but the fixture only owes "the player HAS the skill".
            SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
            var instance = SkillAPI.getSkillsFrom(player).getSkill(id).orElse(null);
            if (instance == null) {
                lastStage = "updateSkill did not register: " + id;
                continue;
            }
            IAbility ability = TensuraStorages.getAbilityFrom(player);
            if (ability == null) {
                lastStage = "ability storage missing for a bare player";
                continue;
            }
            ability.setAbilitySlot(ability.getActivePreset(), 0, skill, 0);
            boolean held = false;
            for (var slot : ability.getAbilitySlots()) {
                if (slot.getSkill() == skill) {
                    held = true;
                    break;
                }
            }
            if (!held) {
                lastStage = "setAbilitySlot did not land (preset "
                        + ability.getActivePreset() + ", id=" + id + ")";
                continue;
            }
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            if (existence == null) {
                lastStage = "existence storage missing";
                continue;
            }
            existence.setMagicule(EnergyHelper.getMaxMagicule(player));
            existence.setAura(EnergyHelper.getMaxAura(player));

            var result = AbilityCore.PIPELINE.run(
                    ActivationRequest.of(id, ActivationSource.DIRECT_SLOT), player);
            if (result.outcome() == ActivationOutcome.ACTED) {
                helper.succeed();
                return;
            }
            lastStage = "pipeline refused " + id + ": " + result.outcome()
                    + " (eligibility=" + AbilityUnlocks.isLearned(player, id)
                    + ", mode=" + instance.getModes() + ")";
        }
        check(false, "no catalog skill cast after full native-equivalent setup; last stage: "
                + lastStage);
    }

    /**
     * The on-demand preset mirror (the owner's cast directive): a learned
     * skill that was NEVER equipped into ARR's active preset still casts
     * through the full pipeline - the service equips it into the first
     * free preset slot on the way in, so a hotbar slot casts without
     * ARR-side setup. The equipped assertion is the fixture's own proof
     * that the setup was absent when the pipeline ran.
     */
    @GameTest(template = "empty")
    public static void castsAnUnequippedSkillThroughTheMirror(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the mirror cast test");
        ServerPlayer player = mockPlayer(helper);
        String lastStage = "no candidate attempted";

        for (ResourceLocation id : TensuraAbilityCatalog.expectedIds().stream().limit(8)
                .toList()) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (skill == null) {
                lastStage = "registry miss: " + id;
                continue;
            }
            SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
            if (SkillAPI.getSkillsFrom(player).getSkill(id).isEmpty()) {
                lastStage = "updateSkill did not register: " + id;
                continue;
            }
            IAbility ability = TensuraStorages.getAbilityFrom(player);
            if (ability == null) {
                lastStage = "ability storage missing for a bare player";
                continue;
            }
            if (ability.isAbilityInActivePreset(skill)) {
                lastStage = "fixture leak: " + id + " equipped without setup";
                continue;
            }
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            if (existence == null) {
                lastStage = "existence storage missing";
                continue;
            }
            existence.setMagicule(EnergyHelper.getMaxMagicule(player));
            existence.setAura(EnergyHelper.getMaxAura(player));

            var result = AbilityCore.PIPELINE.run(
                    ActivationRequest.of(id, ActivationSource.DIRECT_SLOT), player);
            if (result.outcome() == ActivationOutcome.ACTED) {
                check(ability.isAbilityInActivePreset(skill),
                        "the mirror must have equipped " + id + " into the active preset");
                helper.succeed();
                return;
            }
            lastStage = "pipeline refused " + id + ": " + result.outcome();
        }
        check(false, "no catalog skill cast without preset setup; last stage: " + lastStage);
    }

    /**
     * The owner's resistance visibility directive (play-test round): a
     * resistance lists only once trained - ARR instance present with
     * mastery above zero. Known-from-start (no instance) and
     * zero-mastery instances hide, a trained instance lists, and a
     * non-resistance keeps the unchanged learned-only rule (one category
     * tightens, the general formula never regresses).
     */
    @GameTest(template = "empty")
    public static void resistanceVisibilityRequiresTraining(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the resistance visibility test");
        ServerPlayer player = mockPlayer(helper);

        ResourceLocation resistance = TensuraAbilityCatalog.expectedIds().stream()
                .filter(id -> SkillAPI.getSkillRegistry().get(id) != null)
                .filter(id -> AbilityDiscovery.get(id)
                        .map(definition -> definition.tags().contains("type:resistance"))
                        .orElse(false))
                .findFirst()
                .orElse(null);
        check(resistance != null,
                "the catalog must carry a resistance-tagged art (vacuous guard)");

        check(!UnlockGate.menuVisible(player, resistance),
                "a known-from-start resistance (no instance) must hide");

        ManasSkill skill = SkillAPI.getSkillRegistry().get(resistance);
        if (skill == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "the resistance must resolve in ARR's registry");
        }
        SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
        var instance = SkillAPI.getSkillsFrom(player).getSkill(resistance).orElse(null);
        if (instance == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "the resistance instance must register");
        }
        instance.setMastery(0);
        check(!UnlockGate.menuVisible(player, resistance),
                "a zero-mastery resistance must hide (owner's rule)");

        instance.setMastery(5);
        check(UnlockGate.menuVisible(player, resistance),
                "a trained resistance must list");

        ResourceLocation other = TensuraAbilityCatalog.expectedIds().stream()
                .filter(id -> SkillAPI.getSkillRegistry().get(id) != null)
                .filter(id -> !AbilityDiscovery.get(id)
                        .map(definition -> definition.tags().contains("type:resistance"))
                        .orElse(false))
                .findFirst()
                .orElse(null);
        check(other != null, "the catalog must carry a non-resistance art (vacuous guard)");
        ManasSkill otherSkill = SkillAPI.getSkillRegistry().get(other);
        if (otherSkill == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "the non-resistance must resolve in ARR's registry");
        }
        SkillAPI.getSkillsFrom(player).updateSkill(otherSkill.createDefaultInstance(), true);
        var otherInstance = SkillAPI.getSkillsFrom(player).getSkill(other).orElse(null);
        if (otherInstance == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "the non-resistance instance must register");
        }
        otherInstance.setMastery(0);
        check(UnlockGate.menuVisible(player, other),
                "a learned non-resistance lists at zero mastery (unchanged rule)");
        helper.succeed();
    }
}
