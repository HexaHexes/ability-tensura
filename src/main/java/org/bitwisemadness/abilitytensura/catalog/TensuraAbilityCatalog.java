package org.bitwisemadness.abilitytensura.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;

/**
 * The skill and magic catalog (ability-tensura#42, slice 1): registers every
 * native skill and magic ARR ships as a core ability definition - pure
 * metadata layering, the dynamic sibling of {@link BattlewillCatalog}.
 *
 * <p><b>Enumeration source: ARR's own category tag files</b>
 * ({@code data/tensura/tags/manascore_skill/skills/<tag>.json} read from
 * ARR's mod file via FML at {@code RegisterEvent}), <i>not</i> the runtime
 * skill registry: registry population order across mods is not guaranteed to
 * precede this event, while the shipped tags are static, complete (verified:
 * all five skill type tags + all three magic type tags, flat values arrays,
 * no nested references, disjoint, battlewill-separated - 213 skills + 119
 * magic) and exactly the categorization #42's requirements name. ULTIMATE
 * skills ride {@code unique_skills} and MISC magic rides
 * {@code aspectual_magic} (owner confirmation + class-package evidence).</p>
 *
 * <p>Design decisions, recorded with {@link BattlewillCatalog}'s (same
 * shape, same reasons): native ids ({@code tensura:<id>}), ARR's own names
 * ({@code tensura.skill.<id>}) and icons ({@code textures/skill/<type>/},
 * {@code textures/magic/<group>/} - all 332 verified present in the shipped
 * jar), {@link ActivationType#CHANNELLED} for the single native hold entry
 * ({@code startHoldSkill}) - a per-ART mapping stays #42's slice with #52's
 * evidence - empty costs (ARR charges its own currencies,
 * ADR-002 NATIVE). The category tag ({@code tensura:skill} /
 * {@code tensura:magic}) plus {@code type:<name>} tags give consumers both
 * the coarse group and the exact ARR category.</p>
 *
 * <p>Without Tensura installed the catalog registers nothing - the
 * {@code ModFile} lookup returns null and we return, the pairs' gate
 * pattern (AC4 of #42: provider absent, everything else still works).</p>
 */
public final class TensuraAbilityCatalog {

    /**
     * One ARR category tag: the tag file, the system it belongs to (also
     * the definition's category and a tag), its type name (the
     * {@code type:} tag) and the icon directory under
     * {@code assets/tensura/textures/}.
     */
    private record Category(String tag, String system, String type, String iconDir) {

        ResourceLocation category() {
            return ResourceLocation.fromNamespaceAndPath("tensura", system);
        }

        String typeTag() {
            return "type:" + type;
        }

        String iconPath(String artPath) {
            return "textures/" + iconDir + "/" + artPath + ".png";
        }
    }

    /** The eight category tags ARR ships (5 skill types + 3 magic groups). */
    private static final List<Category> CATEGORIES = List.of(
            new Category("common_skills", "skill", "common", "skill/common"),
            new Category("extra_skills", "skill", "extra", "skill/extra"),
            new Category("intrinsic_skills", "skill", "intrinsic", "skill/intrinsic"),
            new Category("resistance_skills", "skill", "resistance", "skill/resistance"),
            new Category("unique_skills", "skill", "unique", "skill/unique"),
            new Category("aspectual_magic", "magic", "aspectual", "magic/aspectual"),
            new Category("spiritual_magic", "magic", "spiritual", "magic/spiritual"),
            new Category("summoning_magic", "magic", "summoning", "magic/summoning"));

    /** The id namespace every category entry lives in. */
    private static final String ID_NAMESPACE = "tensura";
    /** The built-in Tensura Skill Wiki's mod id (owner's brief). */
    private static final String WIKI_MOD_ID = "foxablazeaqzl_wiki";
    /** Its description-key prefix: {@code <prefix><ns>.<path>.description}. */
    private static final String WIKI_KEY_PREFIX = "foxablazeaqzl_wiki.wiki.skill.";
    /** Lazy one-shot read of the wiki's description keys (absent = empty). */
    private static boolean wikiKeysRead;
    private static Set<String> wikiDescriptionKeys = Set.of();
    /** The shipped wiki.gg prose, keyed by full id (owner's middle layer). */
    private static final String SHIPPED_WIKI_RESOURCE =
            "/assets/ability_tensura/wiki_descriptions.json";
    private static Map<String, String> shippedWiki = Map.of();

    private TensuraAbilityCatalog() {
    }

    /**
     * Registers the catalog on core's abilities registry - called from the
     * mod-bus {@code RegisterEvent} beside {@link BattlewillCatalog}. Fails
     * loudly on a malformed ARR tag (an unexpected namespace or a nested
     * reference is a broken input, not something to skip silently).
     */
    public static void register(RegisterEvent event) {
        ModFile tensura = tensuraModFile();
        if (tensura == null) {
            return; // Tensura absent: nothing to catalog (the pairs' gate pattern)
        }
        for (Category category : CATEGORIES) {
            for (String artPath : readTag(tensura, category)) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ID_NAMESPACE, artPath);
                UnlockGate.track(id);
                event.register(AbilityRegistries.ABILITY_REGISTRY_KEY, id,
                        () -> definitionFor(artPath, category));
            }
        }
    }

    /**
     * Every id this catalog registers, freshly read from ARR's tags - the
     * coverage seam the gametest asserts the live registry against.
     * Returns an empty set when Tensura is absent.
     */
    public static Set<ResourceLocation> expectedIds() {
        ModFile tensura = tensuraModFile();
        if (tensura == null) {
            return Set.of();
        }
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Category category : CATEGORIES) {
            for (String artPath : readTag(tensura, category)) {
                out.add(ResourceLocation.fromNamespaceAndPath(ID_NAMESPACE, artPath));
            }
        }
        return out;
    }

    /** ARR's mod file, or null when Tensura is not installed. */
    private static ModFile tensuraModFile() {
        var info = ModList.get().getModFileById(ID_NAMESPACE);
        if (info == null) {
            return null;
        }
        return (ModFile) info.getFile();
    }

    /** The wiki mod's file, or null when it is not installed. */
    private static ModFile wikiModFile() {
        var info = ModList.get().getModFileById(WIKI_MOD_ID);
        return info == null ? null : (ModFile) info.getFile();
    }

    /**
     * The description slot's value for one id, in the owner's precedence:
     * <b>foxablaze wiki mod</b> key when it covers the id, else our
     * <b>shipped wiki.gg prose</b> (curated from tensura.wiki.gg for the
     * ids the mod does not carry - magic and battlewills), else ARR's
     * {@code tensura.skill.<path>.description}. The menu resolves keys
     * through language files and draws prose literally - both forms ride
     * the same slot. Reads are one-shot and soft: a missing or malformed
     * layer degrades to the next (enrichment can never fail a catalog).
     */
    static String descriptionValue(ResourceLocation id) {
        if (!wikiKeysRead) {
            wikiKeysRead = true;
            wikiDescriptionKeys = readWikiDescriptionKeys();
            shippedWiki = readShippedWiki();
        }
        String modKey = WIKI_KEY_PREFIX + id.getNamespace() + "."
                + id.getPath() + ".description";
        if (wikiDescriptionKeys.contains(modKey)) {
            return modKey;
        }
        String prose = shippedWiki.get(id.toString());
        if (prose != null && !prose.isBlank()) {
            return prose;
        }
        return "tensura.skill." + id.getPath() + ".description";
    }

    /** The shipped wiki.gg prose, or empty when the resource is absent. */
    private static Map<String, String> readShippedWiki() {
        try (InputStream in = TensuraAbilityCatalog.class
                .getResourceAsStream(SHIPPED_WIKI_RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> out = new java.util.HashMap<>();
            for (String key : root.keySet()) {
                out.put(key, root.get(key).getAsString());
            }
            return out;
        } catch (IOException | RuntimeException malformed) {
            return Map.of(); // optional enrichment: never fails registration
        }
    }

    /** The wiki's {@code .description} keys, or empty when absent/unreadable. */
    private static Set<String> readWikiDescriptionKeys() {
        try {
            ModFile wiki = wikiModFile();
            if (wiki == null) {
                return Set.of();
            }
            Path lang = wiki.findResource("assets", WIKI_MOD_ID, "lang", "en_us.json");
            if (lang == null || !Files.exists(lang)) {
                return Set.of();
            }
            try (InputStream in = Files.newInputStream(lang)) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Set<String> keys = new LinkedHashSet<>();
                for (String key : root.keySet()) {
                    if (key.startsWith(WIKI_KEY_PREFIX) && key.endsWith(".description")) {
                        keys.add(key);
                    }
                }
                return keys;
            }
        } catch (IOException | RuntimeException malformed) {
            return Set.of(); // optional enrichment: never fails registration
        }
    }

    /** Parses one flat ARR category tag (values array of direct ids). */
    private static List<String> readTag(ModFile tensura, Category category) {
        Path path = tensura.findResource("data", "tensura", "tags",
                "manascore_skill", "skills", category.tag() + ".json");
        if (path == null || !Files.exists(path)) {
            throw new IllegalStateException(
                    "Tensura category tag missing from ARR's jar: " + category.tag());
        }
        try (InputStream in = Files.newInputStream(path)) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");
            if (values == null) {
                throw new IllegalStateException(
                        "Tensura category tag has no values array: " + category.tag());
            }
            List<String> out = new ArrayList<>();
            for (var element : values) {
                String raw = element.getAsString();
                if (!raw.startsWith(ID_NAMESPACE + ":")) {
                    throw new IllegalStateException("unexpected id in " + category.tag()
                            + " (nested references are not handled): " + raw);
                }
                out.add(raw.substring(ID_NAMESPACE.length() + 1));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "cannot read Tensura category tag " + category.tag(), e);
        }
    }

    /** One catalog entry: ARR's name and icon, the system's category. */
    static AbilityDefinition definitionFor(String artPath, Category category) {
        String name = BattlewillCatalog.NAME_PREFIX + artPath;
        return new AbilityDefinition(
                name,
                ResourceLocation.fromNamespaceAndPath(ID_NAMESPACE,
                        category.iconPath(artPath)),
                category.category(),
                // ARR's one pipeline entry is the native hold - channelled
                // (a per-ART mapping stays #42's slice, with #52's evidence)
                ActivationType.CHANNELLED,
                List.of(),
                ConsumptionStrategies.ALL,
                AmountResolvers.STATIC,
                Set.of(category.system(), category.typeTag()),
                // Owner's precedence: foxablaze mod key, shipped wiki.gg
                // prose, ARR's key - the menu renders keys translated and
                // prose literally, so both forms ride this one slot
                Map.of("description", descriptionValue(
                        ResourceLocation.fromNamespaceAndPath(ID_NAMESPACE, artPath))));
    }
}
