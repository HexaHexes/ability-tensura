package org.bitwisemadness.abilitytensura.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The bundled tensura.wiki.gg descriptions (owner's wiki pipeline):
 * the shipped resource parses, its keys are registered ability ids in
 * the tensura namespace, and every value is literal prose - hotbar's
 * pane reads a space-less value as a translation key and would never
 * render it. The count floor mirrors the generator's tripwire
 * ({@code scripts/generate-wiki-descriptions.ps1 -MinimumEntries}), so
 * a gutted file turns CI red exactly as the cron turns red.
 */
class WikiDescriptionsTest {

    private static final String RESOURCE =
            "/assets/ability_tensura/wiki_descriptions.json";

    /** Keep in step with the generator's -MinimumEntries default. */
    private static final int MINIMUM_ENTRIES = 100;

    @Test
    void bundledDescriptionsAreProseKeyedByAbilityId() throws IOException {
        JsonObject root;
        try (InputStream in = WikiDescriptionsTest.class
                .getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "the shipped wiki descriptions must exist");
            root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        assertTrue(root.size() >= MINIMUM_ENTRIES,
                "at least " + MINIMUM_ENTRIES + " entries, got " + root.size());
        root.entrySet().forEach(entry -> {
            String id = entry.getKey();
            assertTrue(id.matches("tensura:[a-z0-9_]+"),
                    "id-shaped key, got " + id);
            String prose = entry.getValue().getAsString();
            assertFalse(prose.isBlank(), "no blank prose for " + id);
            assertTrue(prose.contains(" "),
                    "prose (not a translation key) for " + id);
        });
    }
}
