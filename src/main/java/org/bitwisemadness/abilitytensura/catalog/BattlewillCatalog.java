package org.bitwisemadness.abilitytensura.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitytensura.activation.TensuraActivation;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;

/**
 * The battlewill catalog (ability-tensura#58): registers all 23 native
 * battlewills as core ability definitions - pure metadata layering over
 * Tensura: Reincarnated's own content (SPIKE-bw-1, #2, inventory section 1).
 *
 * <p>Design decisions, recorded here because they are load-bearing:</p>
 * <ul>
 *   <li><b>Native ids</b> ({@code tensura:<art>}) - one id space, so
 *       activation can hand the id straight to ARR's {@code startHoldSkill}
 *       pipeline (interop by id, #2 section 1). Foreign namespaces are legal
 *       in core's registry (the datapack-overlay doc's own example uses one).</li>
 *   <li><b>Names and icons come from ARR's own assets</b> - display name
 *       {@code tensura.skill.<art>} (verified present for all 23 in
 *       {@code assets/tensura/lang/en_us.json}), icon
 *       {@code textures/battlewill/<art>.png} (verified present for all 23).
 *       No duplicated strings or textures: metadata layering, nothing more.</li>
 *   <li><b>{@link ActivationType#CHANNELLED}</b> - every art enters through
 *       ARR's native hold ({@code startHoldSkill}), so held/channeled is the
 *       declared shape; a per-ART mapping (press vs toggle) stays #42's
 *       slice with #52's evidence.</li>
 *   <li><b>Empty costs</b> - battlewills charge native aura inside ARR's own
 *       callbacks (SPIKE-bw-1 section 2; ADR-002: NATIVE arts keep charging
 *       in their pipeline). Core must not price them.</li>
 *   <li><b>No styles here</b> - the style layer is content-shaped and rides
 *       the deferred Better Spellcasting track (#10); this catalog carries
 *       only the {@code battlewill} category tag.</li>
 * </ul>
 */
public final class BattlewillCatalog {

    /**
     * The 23 registered native arts (SPIKE-bw-1 #2 section 1), as registry
     * paths under the {@code tensura} namespace. Order is the tag-file
     * inventory order; the coverage gametest pins completeness against this
     * list. Package-private: the field never leaves this package - external
     * readers (the gametest) go through {@link #paths()}.
     */
    static final List<String> ART_PATHS = List.of(
        "aura_slash", "aura_sword", "earthshatter_kick", "heavy_slash",
        "ogre_sword_guillotine", "roaring_lion_punch", "five_petals_thrust",
        "eight_petals_flash", "dark_eight_palms", "death_march_dance",
        "elephant_stampede", "magic_bullet", "maximum_magic_bullet",
        "ogre_flame", "ogre_sword_cannon", "air_flight", "aura_shield",
        "battlewill", "diamond_path", "formhide", "haze", "instant_move",
        "violent_break");

    /** ARR's translation-key prefix for skill display names. */
    public static final String NAME_PREFIX = "tensura.skill.";

    /** The catalog's category (native grouping, #2 section 1). */
    public static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "battlewill");

    /** The tag every catalog entry carries (styles arrive via datapack). */
    public static final String CATEGORY_TAG = "battlewill";

    private BattlewillCatalog() {
    }

    /** The full id for one catalog art path. */
    public static ResourceLocation idFor(String path) {
        return ResourceLocation.fromNamespaceAndPath("tensura", path);
    }

    /**
     * The catalog's paths as an immutable view - the enumeration seam for
     * the coverage gametest (the field itself stays package-private).
     */
    public static List<String> paths() {
        return ART_PATHS;
    }

    /**
     * Registers the catalog on core's abilities registry - called from
     * ARR's skill-registry event (the timing and raw-write rationale
     * lives on {@code AbilityTensuraEvents}); idempotency and duplicate
     * rules are core's, a duplicate id fails loudly.
     */
    public static void register(Registry<AbilityDefinition> registry) {
        for (String path : ART_PATHS) {
            ResourceLocation id = idFor(path);
            UnlockGate.track(id);
            // eager build: the shapes read the LIVE skill registry now
            Registry.register(registry, id, definitionFor(path));
        }
    }

    /**
     * One catalog entry: ARR's name and icon, native category, and the
     * per-art shape from the shared mapper - activation mapped from ARR's
     * bytecode, usage costs declared as display metadata (charging stays
     * NATIVE).
     */
    static AbilityDefinition definitionFor(String path) {
        String name = NAME_PREFIX + path;
        TensuraActivation.ArtShape shape = TensuraActivation.shapeOf(idFor(path));
        return new AbilityDefinition(
                name,
                ResourceLocation.fromNamespaceAndPath("tensura",
                        "textures/battlewill/" + path + ".png"),
                CATEGORY,
                shape.activation(),
                shape.costs(),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.of(CATEGORY_TAG),
                // Owner's precedence: foxablaze mod key, shipped wiki.gg
                // prose, ARR's key - keys render translated, prose literally
                Map.of("description",
                        TensuraAbilityCatalog.descriptionValue(idFor(path))));
    }
}
