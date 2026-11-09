package org.bitwisemadness.abilitytensura.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/**
 * Guards the config screen's localisation (the fleet's
 * {@code ConfigLocalisationTest} pattern): NeoForge's ConfigurationScreen
 * derives each label as {@code modId + ".configuration." + entryKey} with
 * the <b>leaf</b> key (its fallback after the value/level translation
 * keys), plus {@code .tooltip} - and renders the raw key when the entry
 * is missing. Sections recurse exactly like the screen does. Enums must
 * wire their translated name or the button shows the raw constant.
 */
class ConfigLocalisationTest {

    private static final String CONFIG_PREFIX = "ability_tensura.configuration.";

    private static JsonObject loadLang() {
        try (InputStream in = ConfigLocalisationTest.class
                .getResourceAsStream("/assets/ability_tensura/lang/en_us.json")) {
            if (in == null) {
                throw new IllegalStateException("en_us.json must be on the test classpath");
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every translation key the screen will look up for one spec. */
    private static List<String> screenKeys(ModConfigSpec spec) {
        List<String> keys = new ArrayList<>();
        collectSection(spec.getValues(), keys);
        return keys;
    }

    private static void collectSection(UnmodifiableConfig section, List<String> keys) {
        for (Entry entry : section.entrySet()) {
            // getRawValue() is generic: bind to Object before instanceof (char[] trap)
            Object raw = entry.getRawValue();
            keys.add(CONFIG_PREFIX + entry.getKey());
            keys.add(CONFIG_PREFIX + entry.getKey() + ".tooltip");
            if (raw instanceof UnmodifiableConfig subsection) {
                collectSection(subsection, keys);
            }
        }
    }

    @Test
    void everyScreenKeyHasANonEmptyLangEntry() {
        JsonObject lang = loadLang();
        List<String> keys = new ArrayList<>();
        keys.addAll(screenKeys(TensuraClientConfig.SPEC));
        keys.addAll(screenKeys(TensuraServerConfig.SPEC));

        for (String key : keys) {
            assertTrue(lang.has(key), "missing from en_us.json: " + key);
            assertTrue(lang.get(key).isJsonPrimitive() && !lang.get(key).getAsString().isBlank(),
                    "blank translation for: " + key);
        }
    }

    @Test
    void noTwoScreenEntriesShareOneTranslationKey() {
        // the screen labels by leaf name only, so two sections with the
        // same leaf name (or a section and a value sharing a name) would
        // show one wrong label for both
        List<String> keys = new ArrayList<>();
        keys.addAll(screenKeys(TensuraClientConfig.SPEC));
        keys.addAll(screenKeys(TensuraServerConfig.SPEC));
        keys.removeIf(k -> k.endsWith(".tooltip"));

        Set<String> unique = new HashSet<>(keys);
        assertEquals(keys.size(), unique.size(),
                "duplicate label keys derived from the specs: " + keys);
    }

    @Test
    void thePresetMappingEnumTranslatesItsName() {
        JsonObject lang = loadLang();
        for (TensuraServerConfig.PresetMapping mapping
                : TensuraServerConfig.PresetMapping.values()) {
            assertTrue(lang.has(mapping.translationKey()),
                    "missing from en_us.json: " + mapping.translationKey());
            Component name = mapping.getTranslatedName();
            assertTrue(name.getContents() instanceof TranslatableContents,
                    mapping + " must render as a translatable component");
            TranslatableContents contents = (TranslatableContents) name.getContents();
            assertEquals(mapping.translationKey(), contents.getKey(),
                    mapping + " must translate through its own key");
        }
        // and the screen never sees a raw constant
        assertFalse(TensuraServerConfig.PresetMapping.TAIL.translationKey()
                .equals(TensuraServerConfig.PresetMapping.TAIL.name()),
                "the key is namespaced, not the raw enum name");
    }
}
