package org.bitwisemadness.abilitytensura.gametest;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.network.c2s.RequestSkillActivationPacket;
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
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeOwners;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;
import org.bitwisemadness.abilitycore.discovery.AbilityDiscovery;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilitytensura.AbilityTensura;
import org.bitwisemadness.abilitytensura.activation.TensuraActivation;
import org.bitwisemadness.abilitytensura.catalog.BattlewillCatalog;
import org.bitwisemadness.abilitytensura.catalog.TensuraAbilityCatalog;
import org.bitwisemadness.abilitytensura.config.TensuraServerConfig;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;
import org.bitwisemadness.abilitytensura.mastery.TensuraMastery;

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

    /**
     * Battlewills' real subcategories (owner: they were both a category
     * and a subcategory): every art carries ONE {@code type:} tag from
     * ARR's own structure - never the bare category name - and the three
     * groups partition the inventory exactly (non-vacuous split).
     */
    @GameTest(template = "empty")
    public static void battlewillsSubdivideIntoRealCategories(GameTestHelper helper) {
        long melee = 0;
        long projectile = 0;
        for (String path : BattlewillCatalog.paths()) {
            String tag = BattlewillCatalog.subcategoryTag(path);
            var definition = AbilityDiscovery.get(BattlewillCatalog.idFor(path))
                    .orElse(null);
            check(definition != null,
                    "battlewill definition must register: " + path);
            check(definition.tags().contains(tag),
                    "the art carries its group tag " + tag + ": " + path);
            check(definition.tags().stream().noneMatch(t -> t.equals("battlewill")),
                    "no bare category duplication: " + path);
            if (tag.equals("type:melee")) {
                melee++;
            } else if (tag.equals("type:projectile")) {
                projectile++;
            }
        }
        check(melee == 8 && projectile == 7,
                "ARR's split must hold (melee=" + melee
                        + ", projectile=" + projectile + ")");
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

    /**
     * The mastery declarations ride the catalog (REQ-CORE-12): every
     * tracked art declares mastery with the config-live gain handler, the
     * ACTIVATION trigger, no static base, and exactly one share - to the
     * art's own definition category. The COST axis ships with all of them
     * and CAST_TIME with the Magic ids only (issue #111 steps 1-3): the
     * mixin'd funnels are the caster-aware seams those axes read through,
     * and nothing else applies.
     */
    @GameTest(template = "empty")
    public static void skillMasteryDeclarationsFollowTheCatalog(GameTestHelper helper) {
        var tracked = UnlockGate.trackedIds();
        check(!tracked.isEmpty(), "the catalogs must track their arts");
        var registry = org.bitwisemadness.abilitycore.registry.AbilityRegistries.registry();
        if (registry == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "core's ability registry must be live");
        }
        check(MasteryGainHandlers.has(TensuraMastery.GAIN_HANDLER),
                "the config-live gain handler must be registered at setup");
        double expectedGain = TensuraServerConfig.SKILL_XP_GAIN.get();
        for (ResourceLocation id : tracked) {
            check(MasteryDeclarations.has(id), "every tracked art declares mastery: " + id);
            var spec = MasteryDeclarations.get(id).orElseThrow();
            check(TensuraMastery.GAIN_HANDLER.equals(spec.gainHandler()),
                    "the gain comes from the config-live handler: " + id);
            check(spec.baseGain() == 0.0,
                    "the static base is the handler's fail-closed fallback: " + id);
            check(spec.triggers().equals(java.util.Set.of(MasteryTrigger.ACTIVATION)),
                    "activations grant; nothing else is declared: " + id);
            boolean isMagic = SkillAPI.getSkillRegistry().get(id)
                    instanceof io.github.manasmods.tensura.ability.magic.Magic;
            check(spec.effects().containsKey(MasteryAxis.COST),
                    "every art carries the COST axis: " + id);
            check(spec.effects().containsKey(MasteryAxis.CAST_TIME) == isMagic,
                    "CAST_TIME only where a cast time exists: " + id);
            check(spec.effects().size() == (isMagic ? 2 : 1),
                    "nothing beyond COST (+ CAST_TIME for magic) applies: " + id);
            check(spec.shares().size() == 1, "exactly one share per art: " + id);
            var share = spec.shares().iterator().next();
            var definition = registry.get(id);
            if (definition == null) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "every tracked art has its definition: " + id);
            }
            check(share.target().key().equals(
                            MasteryTarget.category(definition.category()).key()),
                    "the share routes to the definition's category: " + id);
            check(Math.abs(share.share() - TensuraMastery.CATEGORY_SHARE) < 1e-9,
                    "the category share is the declared constant: " + id);
        }
        // the phase-3 gate reads ARR mastery live: a null player is
        // never mastered, so the handler answers 0 (core skips it)
        double unmastered = MasteryGainHandlers.get(TensuraMastery.GAIN_HANDLER).orElseThrow()
                .gain(null, tracked.iterator().next(), MasteryTrigger.ACTIVATION);
        check(unmastered == 0.0,
                "an unmastered (null) player must earn nothing, got " + unmastered);
        // ... while a mastered mock player gets the live config value
        ServerPlayer masteredPlayer = mockPlayer(helper);
        ResourceLocation sample = null;
        for (ResourceLocation candidate : tracked) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(candidate);
            if (skill == null) {
                continue;
            }
            SkillAPI.getSkillsFrom(masteredPlayer)
                    .updateSkill(skill.createDefaultInstance(), true);
            var held = SkillAPI.getSkillsFrom(masteredPlayer).getSkill(candidate).orElse(null);
            if (held != null && held.getMaxMastery() > 0) {
                held.setMastery(held.getMaxMastery());
                sample = candidate;
                break;
            }
        }
        check(sample != null,
                "no cataloged art exposes a usable ARR maximum to master against");
        double masteredGain = MasteryGainHandlers.get(TensuraMastery.GAIN_HANDLER).orElseThrow()
                .gain(masteredPlayer, sample, MasteryTrigger.ACTIVATION);
        check(Math.abs(masteredGain - expectedGain) < 1e-9,
                "a mastered art must answer the live config value, got " + masteredGain);
        helper.succeed();
    }

    /**
     * The native keybind's award end-to-end (REQ-CORE-12): manascore's own
     * activation packet drives the real startHoldSkill through the mixin's
     * redirect - the one entry core's pipeline tail never sees - and the
     * configured gain lands split between the art's own target and its
     * definition category. The lambda the mixin targets is pinned first,
     * so an upstream rename fails here instead of silently dropping awards.
     */
    @GameTest(template = "empty")
    public static void nativeKeybindAwardsThroughThePacket(GameTestHelper helper) {
        check(ModList.get().isLoaded("tensura"),
                "Tensura must be staged for the native-keybind test");
        check(nativeActivationTargetPresent(),
                "manascore's activation lambda moved - retarget the mixin");
        ServerPlayer player = mockPlayer(helper);
        var data = MasteryAttachments.data(player);
        check(data.entries().isEmpty(), "a fresh player starts with no mastery XP");
        var registry = org.bitwisemadness.abilitycore.registry.AbilityRegistries.registry();
        if (registry == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "core's ability registry must be live");
        }
        String lastStage = "no candidate attempted";

        for (ResourceLocation id : TensuraAbilityCatalog.expectedIds().stream().limit(8)
                .toList()) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (skill == null) {
                lastStage = "registry miss: " + id;
                continue;
            }
            // the cast-chain recipe: possession, preset slot 0 mode 0, energy
            SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
            var instance = SkillAPI.getSkillsFrom(player).getSkill(id).orElse(null);
            if (instance == null) {
                lastStage = "updateSkill did not register: " + id;
                continue;
            }
            if (instance.getMaxMastery() <= 0) {
                lastStage = "no usable ARR maximum to master for " + id;
                continue;
            }
            // the phase-3 gate: core XP starts only after ARR's own
            // system has fully mastered the art
            instance.setMastery(instance.getMaxMastery());
            IAbility ability = TensuraStorages.getAbilityFrom(player);
            if (ability == null) {
                lastStage = "ability storage missing for a bare player";
                continue;
            }
            ability.setAbilitySlot(ability.getActivePreset(), 0, skill, 0);
            IExistence existence = TensuraStorages.getExistenceFrom(player);
            if (existence == null) {
                lastStage = "existence storage missing";
                continue;
            }
            existence.setMagicule(EnergyHelper.getMaxMagicule(player));
            existence.setAura(EnergyHelper.getMaxAura(player));

            new RequestSkillActivationPacket(0, id, 0).handle(nativeContext(helper, player));
            double own = data.xp(MasteryTarget.ability(id).key());
            if (own <= 0.0) {
                lastStage = "native activation granted nothing for " + id
                        + " (learned=" + AbilityUnlocks.isLearned(player, id)
                        + ", modes=" + instance.getModes() + ")";
                continue;
            }
            // the configured gain split by the definition-category share
            double gain = TensuraServerConfig.SKILL_XP_GAIN.get();
            double expectedOwn = gain * (1.0 - TensuraMastery.CATEGORY_SHARE);
            var definition = registry.get(id);
            if (definition == null) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "the granted art must resolve to a definition: " + id);
            }
            double routed = data.xp(MasteryTarget.category(definition.category()).key());
            check(Math.abs(own - expectedOwn) < 1e-9,
                    "the mixin awards the configured gain (self part), got " + own);
            check(Math.abs(routed - gain * TensuraMastery.CATEGORY_SHARE) < 1e-9,
                    "the category receives its share, got " + routed);
            helper.succeed();
            return;
        }
        check(false, "no native keybind activation granted mastery; last stage: "
                + lastStage);
    }

    /** The mixin's target: manascore's queued packet body (a rename must fail here). */
    private static boolean nativeActivationTargetPresent() {
        try {
            Class.forName("io.github.manasmods.manascore.skill.impl.network.c2s"
                            + ".RequestSkillActivationPacket")
                    .getDeclaredMethod("lambda$handle$0",
                            dev.architectury.networking.NetworkManager.PacketContext.class);
            return true;
        } catch (ReflectiveOperationException absent) {
            return false;
        }
    }

    /** The native packet's server context: same thread, the queue runs inline. */
    private static dev.architectury.networking.NetworkManager.PacketContext nativeContext(
            GameTestHelper helper, ServerPlayer player) {
        return new dev.architectury.networking.NetworkManager.PacketContext() {
            @Override
            public net.minecraft.world.entity.player.Player getPlayer() {
                return player;
            }

            @Override
            public void queue(Runnable task) {
                task.run();
            }

            @Override
            public dev.architectury.utils.Env getEnvironment() {
                return dev.architectury.utils.Env.SERVER;
            }

            @Override
            public net.minecraft.core.RegistryAccess registryAccess() {
                return helper.getLevel().registryAccess();
            }
        };
    }

    /**
     * The phase-3 mastery gate (the owner's rule): a Tensura art earns
     * core XP only after ARR's own system has fully mastered it. The
     * config-live gain handler answers 0 while the art is unmastered (a
     * grant core skips), the live config value once mastery reaches
     * ARR's maximum, and 0 again for an art the player does not hold.
     */
    @GameTest(template = "empty")
    public static void tensuraMasteryGateOpensOnlyWhenMastered(GameTestHelper helper) {
        var ids = TensuraAbilityCatalog.expectedIds();
        check(!ids.isEmpty(), "the catalog must stage its arts");
        ServerPlayer player = mockPlayer(helper);
        var handler = MasteryGainHandlers.get(TensuraMastery.GAIN_HANDLER).orElseThrow();
        double expectedGain = TensuraServerConfig.SKILL_XP_GAIN.get();

        // hold an art ARR gives a usable maximum to (the walk's rule)
        ManasSkillInstance masterable = null;
        ResourceLocation masterableId = null;
        for (ResourceLocation candidate : ids) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(candidate);
            if (skill == null) {
                continue;
            }
            SkillAPI.getSkillsFrom(player).updateSkill(skill.createDefaultInstance(), true);
            var held = SkillAPI.getSkillsFrom(player).getSkill(candidate).orElse(null);
            if (held != null && held.getMaxMastery() > 0) {
                masterable = held;
                masterableId = candidate;
                break;
            }
        }
        if (masterable == null || masterableId == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "no cataloged art exposes a usable ARR maximum to master against");
        }

        // the fresh default instance starts unmastered: no XP
        double unmastered = handler.gain(player, masterableId, MasteryTrigger.ACTIVATION);
        check(unmastered == 0.0, "an unmastered art must earn nothing, got " + unmastered);

        // ARR mastery at the maximum opens the gate: the live config value
        masterable.setMastery(masterable.getMaxMastery());
        double mastered = handler.gain(player, masterableId, MasteryTrigger.ACTIVATION);
        check(Math.abs(mastered - expectedGain) < 1e-9,
                "a mastered art must earn the live config value, got " + mastered);

        // an art the player does not hold earns nothing either
        ResourceLocation unheld = ids.stream()
                .filter(candidate -> SkillAPI.getSkillsFrom(player)
                        .getSkill(candidate).isEmpty())
                .findFirst().orElse(null);
        if (unheld != null) {
            double heldNothing = handler.gain(player, unheld, MasteryTrigger.ACTIVATION);
            check(heldNothing == 0.0, "an unheld art must earn nothing, got " + heldNothing);
        }
        helper.succeed();
    }

    /**
     * The effect mixins' upstream shape (issue #111, steps 2-3): all three
     * funnel methods must keep their exact signatures or an injection
     * silently stops scaling, and every staged spec must carry the COST
     * axis the cost mixin reads (undeclared = core answers 1.0 = inert).
     */
    @GameTest(template = "empty")
    public static void masteryCostAxisPinsItsUpstream(GameTestHelper helper) {
        check(effectFunnelsPresent(),
                "ARR renamed a funnel an effect MasteryCost/MasteryCastTime mixin injects");
        TensuraMastery.declareAll();
        var ids = TensuraAbilityCatalog.expectedIds();
        check(!ids.isEmpty(), "the catalog must stage its arts");
        var spec = MasteryDeclarations.get(ids.iterator().next()).orElse(null);
        check(spec != null && spec.effects()
                        .containsKey(org.bitwisemadness.abilitycore.api.MasteryAxis.COST),
                "declareAll must ship the COST axis, got " + spec);
        helper.succeed();
    }

    /** The mixins' entry points (a rename fails here). */
    private static boolean effectFunnelsPresent() {
        try {
            io.github.manasmods.tensura.ability.TensuraSkillInstance.class
                    .getDeclaredMethod("getMagiculeCost",
                            net.minecraft.world.entity.LivingEntity.class, int.class);
            io.github.manasmods.tensura.ability.TensuraSkillInstance.class
                    .getDeclaredMethod("getAuraCost",
                            net.minecraft.world.entity.LivingEntity.class, int.class);
            io.github.manasmods.tensura.ability.TensuraSkill.class
                    .getDeclaredMethod("isOutOfEnergy",
                            net.minecraft.world.entity.LivingEntity.class,
                            ManasSkillInstance.class, int.class, float.class);
            io.github.manasmods.tensura.ability.TensuraSkill.class
                    .getDeclaredMethod("createSource",
                            ManasSkillInstance.class,
                            net.minecraft.world.entity.LivingEntity.class,
                            net.minecraft.resources.ResourceKey.class, int.class);
            io.github.manasmods.tensura.ability.magic.Magic.class
                    .getDeclaredMethod("getCastingTime",
                            ManasSkillInstance.class,
                            net.minecraft.world.entity.LivingEntity.class,
                            int.class, boolean.class);
            return true;
        } catch (ReflectiveOperationException absent) {
            return false;
        }
    }
}
