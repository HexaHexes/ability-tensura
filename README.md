# Ability: Tensura

Registers **Tensura: Reincarnated**'s Battlewills - the pack's non-magical
combat arts, martial techniques bound to how you fight, from sword and spear
discipline to unarmed brawling - as native, datapack-first abilities:
discovered through Ability Core, unlocked through Tensura's own learning,
and activated natively first, with the shared Ability Hotbar and combo
bridges as optional seams rather than requirements. Built for the Tensura
RPG pack.

## What it does

- **Catalogs** - all 23 native battlewills plus the skill and magic
  catalog (332 native abilities from their category tags) registered as
  Ability Core definitions with discovery and metadata.
- **Wiki descriptions** - the pane's description slot resolves in
  order: the foxablaze wiki mod's key when it covers the art, the
  bundled tensura.wiki.gg prose, then ARR's own lang key. The bundle
  regenerates from the wiki with `scripts/generate-wiki-descriptions.ps1`
  (ids from the mod, prose from the wiki) and refreshes weekly through
  the Wiki Descriptions cron, so the magic and battlewill arts the wiki
  mod never carried keep real prose without a manual curation.
- **Unlock gate** - every registered ability is gated on the Tensura
  unlock (ManasCore's learned-state read), and unlearnt abilities are
  removed from the player's bars as they are forgotten.
- **Native activation parity** - a shared activation service resolves
  preset slots server-side, so native casting and the ability pipeline
  agree on what fires; battlewills activate natively first, with the hotbar
  seam as an addition.
- **Mode switching** - the hotbar's mode keybind intent routes through
  Ability Core's mode-change contract into ARR's own cycle packet
  (per-slot mode presets included), so both paths move the same native
  state (the keybind and its transport ride the Ability Hotbar). 
- **Native economy** - skill, magic and battlewill costs defer entirely
  to Tensura: Reincarnated: the catalog carries no prices, and the
  activation pipeline never charges or affordability-gates these arts -
  Tensura pays and regenerates aura and magicule itself, so this pack
  adds no regeneration either.
- **Mastery** - every cataloged art opts into Ability Core's experience
  system: an art earns XP only once ARR's own system has **fully
  mastered** it (the gain gate reads ARR's mastery live), and from then
  on a successful activation - bar cell, wheel or the native keybind,
  each funnels exactly once - grants the configured XP
  (`mastery.skillXpGain`, server config, read live at award time) and
  routes half of it to the art's category mastery through core's opt-in
  declarations. Packs retune the share or any spec field through core's
  `mastery_spec` datapack overlay; the Enable/Disable control grants
  nothing, and no effect axis is declared yet (ARR's cost reads are
  uninvestigated upstream).
- **Tensura RPG System menu** - with TRS installed, its equip-skills
  menu opens this pack's ability menu instead: one menu, no extra
  hotkey registered; without TRS, nothing changes.
- **Datapack-first** - definitions, tags, progression and restrictions
  live in datapack data, so content is editable without code. Iron's
  Spells and Better Spellcasting are not hard dependencies.
- The rest of the plan - the style system, passives, weapon enhancements,
  the additive-art schema and the Ability Hotbar provider adapter - is
  tracked in this repository's open issues.

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | - |
| Required | NeoForge | - |
| Optional | Ability Core | The ability contracts battlewills register against: discovery, metadata, activation (`[1.0.0-alpha.42,)` in the toml); without it the mod carries only its datapack data |
| Optional | Tensura: Reincarnated | The battlewills themselves and the unlock state the gate reads |
| Optional | ManasCore | SkillAPI: the unlock gate's learned-state read |
| Optional | Ability Hotbar | The shared hotbar seam (the provider adapter is tracked in #42); absent = native activation alone |
| Optional | Tensura - Better Spellcasting Bridge | Battlewills fire from weapon combos; runtime-gated, absent = no combo hooks |

## Credits

- Built on [NeoForge](https://neoforged.net/).
- Derived from [neoforge-mod-template](https://github.com/HexaHexes/neoforge-mod-template).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
