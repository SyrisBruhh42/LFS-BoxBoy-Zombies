# LFS Oasis Imagineering Translator, Persistent Shell Ribbon & Sanctuary Storyteller (`v2.0.0 / CP-12`)

This upgrade completes the **LFS Oasis "Disney Imagineering Engagement Translator" (Grade <= 8.0)** across `LFS: BoxBoy Zombies`, embeds a persistent **Oasis Universal Shell Ribbon (`OasisMultiplayerBar`)** with `Shift+Tab` / `Ctrl+K` overlay pause-and-chat and 1-click Wing switching, and integrates the **AudioBookshelf & OpenAudible Sanctuary Storyteller (`cb64f9e7`)** pill with a 15m/30m/45m Starlight Sleep-Fade timer and procedural lullaby fallback.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices incorporate your confirmed answers from our planning check:

- **Confirmed Decision 1 — `zt9_comfort_overrides` Family-Friendly Arcade Mode ("Best Of All")**:
  - Enabled by default for an all-ages (Ring 1 Child through Ring 3 Owner) experience, automatically enforced when the active subuser's Trust Ring is Ring 1 or Ring 2, and manually toggleable by Ring 3/4 players between **"Family Arcade Mode"** (*Cardboard Critters, Neon Arcade Bots, Confetti KOs, Tag-Team Rescue*) and **"Classic Tactical Labels"** while keeping all underlying JSON/API schema keys untouched.
- **Confirmed Decision 2 — Wing Switcher & TrashArcade Hall Navigation ("Deep-link to Oasis Hub plus in-app Wing preview sheet")**:
  - Selecting any destination from the persistent top ribbon (`TrashArcade Hall`, `Commonplace Wing I`, `Scriptorium Wing II`, `Chapterhouse Wing III`, `3D Vigil Wing IV`, `Code Runner`, `Boxhead Fort`, `BoxBoy Defense`) opens an instant in-app **Oasis Wing Portal Card** (preserving the live `OASIS-XXXX` / `LFS-XXXXXX` party room and 16-Channel Rmblyn Family Chat) with a 1-click deep-link/Intent launch button and WebSocket `oasis:wing-switch` event dispatch.
- **Confirmed Decision 3 — Sanctuary Storyteller (`cb64f9e7`) Audio Engine ("Stream from Oasis OpenAudible API with procedural lullaby fallback")**:
  - Queries `/api/lfs/openaudible/library` and streams `/api/lfs/openaudible/stream/[key]` authenticated via the Tailscale `:13379/lfs-sso/handshake` Hearth Key identity, and automatically falls back to a warm, low-latency **Procedural Crystalline Music-Box Lullaby Synthesizer** (`AudioTrack` 16-bit PCM) whenever offline or unreachable, complete with a smooth 15m / 30m / 45m **Starlight Sleep-Fade** volume attenuator.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Disney Imagineering Engagement Translator (Grade <= 8.0)**: Replaces intimidating cryptographic, network, and grim combat terminology in the UI with warm, inviting, family-friendly Oasis language while keeping all wire-level API and JSON keys intact:
     - Tailscale Auto-SSO (`Node.StableID`) -> **"Hearth Key (Instant Family Pass)"**
     - `aegisEnvelopeV1` AES-256-GCM Save Slots -> **"Time-Machine Memory Vaults"**
     - `HOST_LOCAL_PC` / `HOST_LOCAL_DEVICE` & `LAUNCH_U2_HOSTING_SERVICE` -> **"Host From My Desk"** & **"Open a Family Cloud Room"**
     - `ZT-6` Error Banners -> **"Friendly Guide Cards"** structured into 4 clear steps: *1. What Happened*, *2. Why*, *3. Your Progress Is Safe*, and *4. One-Click Next Step*
     - `Z_1440` Curfew -> **"Starlight Quiet Hours (Rest & Recharge Time)"**
     - TrashArcade CMY Rupees & Assets -> **"Starlight Rupees & Wonder Relics"**
     - `zt9_comfort_overrides` Family-Friendly Arcade Mode -> Frames opponents and knockouts as **"Cardboard Critters / Neon Arcade Bots"**, **"Confetti KOs"**, and **"Tag-Team Rescue"**.
  2. **Uninterrupted Top Shell Ribbon (`OasisMultiplayerBar`) & `Shift+Tab` / `Ctrl+K` Overlay**:
     - Remains mounted at the top of the screen across Title Hub, active gameplay, modals, and Wing switches without ever dropping the active party room or 16-Channel Rmblyn Family Chat (`@willow`, `@cinder`, `@mote`).
     - Listens for and dispatches `oasis:overlay-toggle` (`Shift+Tab` or `Ctrl+K` on hardware keyboards, plus a 1-tap ribbon pill) so single-player action physics pause cleanly while family chat, Wing navigation, and menus stay live.
  3. **Sanctuary Storyteller Pill (`cb64f9e7`)**:
     - Compact, always-accessible audio player pill embedded in the top shell ribbon displaying Play/Pause, active book & chapter title, and a **15m / 30m / 45m Starlight Sleep-Fade timer**.
- **Target Audience / Persona**: Multi-generational families (Ring 1 kids to Ring 3 parents/owners) playing together across Android handhelds, TV Cast, and the LFS Oasis Mesh.
- **Key Value**: Combines zero-trust security and 6-Gate save durability with warm, approachable Disney-grade storytelling and uninterrupted family co-op presence.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  - **Persistent Top Shell Ribbon**: Always visible at the very top of the viewport. Shows the active **Party Room Pill** (`OASIS-XXXX` / `LFS-XXXXXX`), **Hearth Key Badge**, **Sanctuary Storyteller Pill (`cb64f9e7`)**, **16-Channel Rmblyn Family Chat Toggle** (`Shift+Tab`), and **Switch Wing / TrashArcade Hall** selector.
  - **`Shift+Tab` / `Ctrl+K` Universal Overlay**: Pressing `Shift+Tab` or `Ctrl+K` (or tapping the Ribbon Overlay button) pauses single-player physics immediately and slides open the **Oasis Universal Overlay** containing live Rmblyn Family Chat (`@willow`, `@cinder`, `@mote`), Wing Switcher cards, Family Arcade Mode comfort toggle, and Friendly Guide status.
  - **Friendly Guide Cards (`ZT-6`)**: Whenever a network failover, curfew check, or save sync notice occurs, a warm card reassures the player with:
    1. *What Happened*
    2. *Why*
    3. *Your Progress Is Safe*
    4. *One-Click Next Step*
- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Warm Astral Sanctuary & Final Fantasy Crystalline polish (`AstralObsidian`, `ChampagneGold`, `AetherCyan`, `PhosphorGreen`, and WCAG AAA High-Contrast support).
  - **Typography & Hierarchy**: Clean, scannable Grade <= 8.0 labels using `CinzelDisplayFamily` for headers and `RajdhaniBodyFamily` / `ShareTechMonoFamily` for badges and timers.
  - **Ratchet Hygiene**: Strictly `0` raw Unicode emoji characters in `.kt` files (all icons use Material Vector icons or escaped ASCII badges like `[PASS]`, `[WARN]`, `[SAFE]`) and `0` `/home/` path leaks.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Dual-Layer Vocabulary (Imagineering UI Layer over Canonical Wire Layer)**:
  - *Chosen Approach*: Keep all wire/schema keys (`Node.StableID`, `aegisEnvelopeV1`, `HOST_LOCAL_DEVICE`, `LAUNCH_U2_HOSTING_SERVICE`, `zt9_comfort_overrides`, `lfs-local-abac-v1`) 100% intact in JSON payloads and unit-tested data models while mapping user-facing titles, subtitles, and HUD callouts through an `OasisImagineeringTranslator`.
  - *Why*: Guarantees full backward compatibility with `U2`/`U3` servers and existing unit tests while delivering a Grade <= 8.0 family-friendly UI.
- **Decision 2 — Persistent Top Shell Ribbon Outside Modal Destruction**:
  - *Chosen Approach*: Mount `OasisMultiplayerBar` at the top of `GameScreen` (and mirrored in `TitleHub` header) backed by `BoxBoyViewModel` state so opening/closing dialogs, switching Wings, or toggling `Shift+Tab` never disconnects the WebSocket or resets Rmblyn chat history.
  - *Why*: Ensures uninterrupted squad communication and audio playback across every screen state.
- **Decision 3 — Hybrid OpenAudible Stream + Procedural Music-Box Lullaby Fallback**:
  - *Chosen Approach*: `SanctuaryStorytellerController` queries `/api/lfs/openaudible/library` and `/api/lfs/openaudible/stream/[key]` with the Tailscale `:13379/lfs-sso/handshake` Hearth Key header, and seamlessly synthesizes soothing multi-chapter music-box arpeggios via `AudioTrack` PCM when offline, with real-time linear/cosine sleep-fade gain attenuation over 15m, 30m, or 45m.
  - *Why*: Works on live Oasis home servers and 100% offline flights or bedtime sessions without bundling large binary audio files.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   PERSISTENT OASIS UNIVERSAL SHELL RIBBON                  │
│  [Party: OASIS-XXXX] [Hearth Key] [Sanctuary Storyteller cb64f9e7] [Wings] │
└───────────────┬──────────────────────────────┬─────────────────────────────┘
                │ Shift+Tab / Ctrl+K           │ Play / Pause / Sleep-Fade
                ▼                              ▼
┌───────────────────────────────────┐  ┌─────────────────────────────────────┐
│   OasisUniversalOverlaySheet      │  │ SanctuaryStorytellerController      │
│ • Pauses 1P Physics cleanly       │  │ • GET /api/lfs/openaudible/library  │
│ • 16-Ch Rmblyn Chat (@willow...)  │  │ • GET /api/lfs/openaudible/stream/* │
│ • Wing Switcher & Hub Deep-Link   │  │ • 16-bit PCM Music-Box Fallback     │
│ • ZT-6 Friendly Guide Cards       │  │ • 15m / 30m / 45m Sleep-Fade Timer  │
└───────────────┬───────────────────┘  └─────────────────────────────────────┘
                │
                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│           Disney Imagineering Engagement Translator & ZT-9 Comfort         │
│ • Hearth Key (Instant Family Pass)   • Time-Machine Memory Vaults          │
│ • Host From My Desk / Family Cloud   • Starlight Quiet Hours (Z_1440)      │
│ • Starlight Rupees & Wonder Relics   • Cardboard Critters & Confetti KOs   │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `Zt9ComfortOverridesState`: Holds `familyFriendlyArcadeMode: Boolean = true`, `autoEnforceForChildRings: Boolean = true`, and helper methods translating enemy names (*Cardboard Critters / Neon Arcade Bots*), KO toasts (*Confetti KOs*), and revive prompts (*Tag-Team Rescue*).
  - `FriendlyGuideCardState`: Models `ZT-6` alerts with `whatHappened`, `whyItHappened`, `progressIsSafe`, and `nextStepLabel` + action callback.
  - `OasisWingDestination`: Enum covering `TRASH_ARCADE_HALL`, `COMMONPLACE_WING_I`, `SCRIPTORIUM_WING_II`, `CHAPTERHOUSE_WING_III`, `VIGIL_3D_WING_IV`, `CODE_RUNNER`, `BOXHEAD_FORT`, and `BOXBOY_DEFENSE`.
  - `SanctuaryStorytellerState`: Tracks `isPlaying`, `activeBookId` (`cb64f9e7`), `bookTitle`, `chapterTitle`, `sleepTimerMinutes` (`0`, `15`, `30`, `45`), `sleepRemainingSeconds`, `sleepFadeMultiplier`, and `sourceLabel` (`OpenAudible Sanctuary Stream` vs `Offline Starlight Music-Box`).
- **Interactive Component & State Mapping**:
  - Pressing `Shift+Tab` or `Ctrl+K` in `GameScreen` dispatches `viewModel.toggleOasisUniversalOverlay()`, which emits `oasis:overlay-toggle`, pauses single-player physics (`isPaused = true` when solo), and opens the overlay without dropping `oasisServerClient` or `p2pManager`.
  - Selecting a Wing in `OasisMultiplayerBar` updates `selectedOasisWing`, sends an `oasis:wing-switch` event, and displays the in-app Wing preview & deep-link launcher card while keeping the active room connected.
  - Toggling the Sanctuary Storyteller pill starts/pauses chapter audio and ticks the 15m/30m/45m Starlight Sleep-Fade countdown.
