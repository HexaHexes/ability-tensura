# Repo rules - Ability: Tensura

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/ability-tensura` - **Mod ID:** `ability_tensura` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/ability_tensura-<version>.jar`

**Scope (owner directive, 2026-11-06; #45):** the single integration layer
between *any and all* Tensura: Reincarnated ability systems - **battlewills,
skills, and magic** - and ability-core / ability-hotbar. This repo owns no
ability system: ARR registers them (battlewills under
`registry.battlewill.{MeleeArts,ProjectileArts,UtilityArts}`; skills and magic
through ManasCore's skill API) and ARR's pipelines stay the validating
authority - we expose, classify and route; native refusals fail safe with no
consumption. Both archetypes are in scope: active (cast) and passive
(always-on, player enable/disable at will). Battlewill classification is
datapack-driven (style tags over core definitions); execution adapters only
where custom behaviour demands it.

**Dependencies**
- Required: Minecraft, NeoForge, `ability_core`
  (`org.bitwisemadness.abilitycore:ability_core`, from GitHub Packages via
  `PACKAGE_READ_TOKEN`, scoped to the Gradle step only). The toml marks core
  `optional`, but pair declarations gate only on Tensura's presence - treat
  core as required; the discrepancy rides the dependency decision in #42.
- Compile-only: Tensura: Reincarnated API (`tensura-neoforge`, never
  bundled; ARR-facing code gates on `ModList("tensura")`).
- Optional: `ability_hotbar`, `tensura-better-spellcasting-bridge`,
  `better_spellcasting`, `irons_spellbooks` - never hard dependencies; all
  integration through soft hooks.
- **Menu tabs** (issue #75, owner's icon brief, hotbar#171's both-forms
  contract): `TensuraMenuTabs` registers Skills, Magic and Battlewill with
  hotbar's `MenuTabs` carrying **both identifier forms** - the draw
  preference (ARR's `tensura:battlewill_manual` for Battlewill, Skill Study
  Books' creative-tab icons for Skills/Magic - registry lookups, never
  copied) **and** ARR's own `*_tab_icon.png` sprite as the fallback. Which
  one draws is hotbar's call at draw time (`stackRendersInGui`): the item
  when its model produces pixels, else the sprite, else text only - the
  study book resolves but its model emits no quads in any GUI. Membership =
  the definition's category. The Skills tab also declares the owner's
  progression strip order through hotbar's `subCategoryOrder` (resistance,
  intrinsic, common, extra, unique, ultimate - the ultimate slot is
  reserved: nothing in the pack tags `type:ultimate`).
  It follows the `HotbarHygiene` guard pattern: the checked entry may touch
  hotbar classes, the unchecked half (`registerAll`) is the only link site -
  tests call it directly. Hooked at the end of `UnlockGate.register()`.

**Test:** `./gradlew runGameTestServer` - `AbilityTensuraGameTests` (15 tests). `expectedTests = 15`
in `build.gradle` must match the `@GameTest` count.

**Wiki descriptions** (owner's wiki pipeline, A + B):
`scripts/generate-wiki-descriptions.ps1` regenerates
`src/main/resources/assets/ability_tensura/wiki_descriptions.json` from
tensura.wiki.gg - discovery starts at the Abilities page's Magics and
Battlewills sub-pages, ids come from the MOD (ARR's jar for the three
magic tags, `BattlewillCatalog.ART_PATHS` for battlewills - never from
the wiki, whose icon/title signals disagree with the mod too often to
trust alone), and each id joins to a page by title, then icon name, then
the script's checked-in `$Overrides` table. The script pins the shipped
shape (BOM, LF, ordinal keys, PS 5.1 value escaping), fails below its
entry floor and lists any registered id no page covered.
`.github/workflows/wiki-descriptions.yml` runs it weekly and opens a
refresh PR when the prose changed; `WikiDescriptionsTest` pins the
shipped shape in CI. Both refresh paths run the same script.

**Gotchas**
- **Data-driven does not mean arbitrary behaviour.** Datapacks carry metadata,
  costs, restrictions and mappings; execution logic needs a code adapter. Do not
  advertise or attempt datapack-defined custom behaviour.
- ISS and Better Spellcasting must never become hard dependencies of this repo -
  the integration layer stands alone.
- Its registration crosses into ability-core contracts: schema changes there
  are `breaking` and must be coordinated with that repo's issues.
