# LFS: BoxBoy Zombies — Oasis Aegis Suite (`v2.0.0 / CP-12`), Tailscale Mesh & TrashArcade Bridge

Upgrade `LFS: BoxBoy Zombies` with full production integration for the **LFS Oasis Aegis Suite (`v2.0.0 / CP-12`)**, **Zero-Trust Tailscale Mesh (`tail420c4d.ts.net` / `lfs-prod.tail420c4d.ts.net` / `100.106.12.104:13379`)**, **exact `U2` and `U3` server route contracts**, **TrashArcade Launcher Deep-Link & Cross-Game Asset Bridge (`lfs-local-abac-v1`)**, **6-Gate `aegisEnvelopeV1` 20-Slot SaveLab custody**, and **Stage 6 `G2_RATCHET_ZERO_EMOJI_AND_HOME_LEAK` source hygiene**.

## User Review & Critical Decisions

> [!IMPORTANT]
> All Phase 1 clarifications have been confirmed and synthesized into this plan:

- **Confirmed Decision 1 — Best-of-All-3 Tailscale Anchor Handshake Strategy**:
  - `probeTailscaleAnchorHandshake()` combines all three strategies: it monitors `NetworkHealthService` for active Tailscale/VPN interfaces, races the HTTPS Anchor (`https://lfs-prod.tail420c4d.ts.net/lfs-sso/handshake`) and direct Tailscale IP (`http://100.106.12.104:13379/lfs-sso/handshake`) with a `<= 1500ms` timeout, falls back sequentially if one transport is blocked, and synthesizes deterministic `Node.StableID` (`replicaId`), `subuserId`, `persona`, `trustRing`, and 4-Pillar `[PASS]`/`[WARN]` checks when offline.
- **Confirmed Decision 2 — Best-of-All-3 Relic & 6-Gate `aegisEnvelopeV1` Custody Sync**:
  - Cross-game `lfs-local-abac-v1` relics and 20-slot `aegisEnvelopeV1` saves are dual-written immediately to the local vault on milestone/save trigger, pushed asynchronously to `U2` / Tailscale Anchor custody (`POST /api/lfs/sync/saves`), and automatically flushed at wave completion or when joining an active Oasis room.
- **Confirmed Decision 3 — Zero-Emoji Source Hygiene (`G2_RATCHET_ZERO_EMOJI_AND_HOME_LEAK`)**:
  - Replace all raw non-ASCII symbols, stars, and emoji glyphs across Kotlin source files with Kotlin Unicode escape sequences (such as `\u2605`) or clean ASCII badges (`[PASS]`, `[WARN]`, `[TIER-5]`) so automated sovereignty ratchets pass with zero raw emoji hits.

---

## 1. Overview & Core Concept

- **What It Does**: Connects `LFS: BoxBoy Zombies` to the sovereign `Lewisfamilysystems` infrastructure across `U1` (Android / TrashArcade Hub), `U2` (`play.lewisfamilysystems.com` & `lfs-prod.tail420c4d.ts.net`), and `U3` (`:19448` `oasis_server_hoster.py` & `Laputa` `100.115.219.120:19448`), while enforcing formal ABAC security, deterministic LWW state convergence, 6-Gate `aegisEnvelopeV1` save custody, and cross-game progression with `willow-desktop-pet`, `undercity-crawler`, and `lfs-tycoon`.
- **Target Audience / Persona**: Players and operators within the `Lewisfamilysystems` Oasis ecosystem launching `LFS: BoxBoy Zombies` standalone or via the **TrashArcade** hub across Android handhelds, TV cast setups, and Tailscale-meshed workstations.
- **Key Value**: Guarantees sub-1500ms non-blocking 3-tier networking with exact `U2`/`U3` wire compatibility, mathematically verified access/conflict resolution, and cross-capsule relic/rupee continuity.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **TrashArcade Deep-Link Launch Intake**: When launched via Android `Intent` extras or URI query parameters (`oasis_room_code`, `oasis_host_mode`, `oasis_subuser_id`, `oasis_display_name`, `oasis_lds_theme`, `oasis_autostart`), the app automatically configures the squad room code, host mode, subuser identity, and theme, and transitions straight into the bunker survival run when `oasis_autostart = true`.
  2. **Tailscale 4-Pillar Handshake & ABAC Status Inspector**: Opening the **Multiplayer & Oasis Sync** modal displays the live **TrashArcade Satchel & Tailscale 4-Pillar Status** card with Machado CVD-safe `[PASS]` / `[WARN]` badges for `tailscale_tunnel`, `abac_perimeter`, `isolated_profile`, and `user_data_backup`, plus the active `Node.StableID` (`replicaId`), `trustRing`, and 32-State ABAC Lattice bitmask.
  3. **CMY Rupee Satchel & Cross-Game Relic Unlocks**: As players earn Survival Points, defeat bosses, survive Wave 5 in Abyssal Bio-Noir lighting, upgrade weapons at the Pack-a-Punch, or revive squadmates, the satchel decomposes their balance into the 9 canonical CMY gem denominations (`<= 24` static sprites) and unlocks the 4 cross-game `lfs-local-abac-v1` relics.
  4. **6-Gate `aegisEnvelopeV1` 20-Slot SaveLab**: Saving a run seals the state into a 6-Gate `aegisEnvelopeV1` payload (`G1_MAGIC_HEADER` through `G6_NON_EMPTY_STATE`) and commits it to local storage, Google Drive, and Oasis SaveLab custody (`slot_01`..`slot_20`).
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Final Fantasy Crystalline Astral Command paired with Machado CVD-safe status badges (`[PASS]`, `[WARN]`) and full support for the 7 LDS theme packs (`evergreen-dark`, `evergreen-light`, `high-contrast`, `calm`, `dusk`, `amber-terminal`, `cobalt-deck`).
  - *Color Palette & Mood*: Valisthea Obsidian (`--background: #070A14`), Astral Navy (`--surface: #0F1629`), Aether Cyan (`--primary: #38E8FF`), Brushed Champagne Gold (`--secondary: #F5C469`), Materia Violet (`--tertiary: #B784FF`), and high-visibility WCAG AAA contrast mode.
  - *Typography & Hierarchy*: Engraved `Cinzel` serif display headers paired with `Plus Jakarta Sans` UI controls and tabular telemetry readouts.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Automatic `U2` vs. `U3` Endpoint Route Dispatch**
  - *Chosen Approach*: Inspect each candidate base URL during Happy-Eyeballs racing and room/hoster operations: endpoints targeting `:19448` (`U3` Local / `Laputa` Hoster) automatically dispatch to `/v1/hoster/rooms`, `/v1/hoster/migrate`, `/v1/hoster/wake`, `/v1/hoster/join`, `/v1/hoster/status`, and `/v1/overlay/rmblyn`, while endpoints targeting `U2` (`play.lewisfamilysystems.com` or `lfs-prod.tail420c4d.ts.net`) dispatch to `/api/lfs/sync/rooms`, `/api/lfs/sync/hoster`, `/api/lfs/sync/state`, `/api/lfs/sync/events`, `/api/lfs/sync/presence`, `/api/lfs/sync/rmblyn`, and `/api/lfs/sync/saves`.
  - *Why*: Ensures zero protocol mismatches whether the player connects to a local/Tailscale `:19448` Python hoster daemon or the global `U2` Sovereign Web Platform.
- **Decision 2: Strict 4-Layer HTTP Request Gate Compliance**
  - *Chosen Approach*: Every mutating `POST` request explicitly sets `Content-Type: application/json; charset=utf-8` and includes canonical Oasis protocol headers so the server's 4-Layer HTTP Request Gate (`415` JSON Content-Type check, Host allowlist, Origin check, and `tailscaled.sock` peer identity) succeeds deterministically.
- **Decision 3: Deterministic Formal Math Primitives on `Companion`**
  - *Chosen Approach*: Implement `isWithinCurfewZ1440`, `evaluateAbac32Lattice`, `resolveTotalOrderLww`, and `decomposeCmyRupees` as pure, deterministic functions exposed on `OasisHostedServerClient.Companion` alongside `sealSixGateAegisEnvelopeV1` / `unsealSixGateAegisEnvelopeV1` on `CryptoSyncEngine`.
  - *Why*: Guarantees bit-exact behavior across JVM unit tests and live Android runtime without external dependencies.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                TrashArcade / Android Intent Deep-Link Intake                 │
│  • oasis_room_code, oasis_host_mode, oasis_subuser_id, oasis_display_name    │
│  • oasis_lds_theme (7 LDS themes), oasis_autostart -> BoxBoyViewModel        │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 BoxBoyViewModel & Cross-Game Asset Bridge                    │
│  ┌────────────────────────────────────┐ ┌──────────────────────────────────┐ │
│  │ CMY Rupee Satchel Decomposer       │ │ lfs-local-abac-v1 Relic Bridge   │ │
│  │ • 9 denominations [1000..1]        │ │ • charm_valkor9_brass_collar     │ │
│  │ • Bounded <= 24 static sprites     │ │ • undercity-delver-torch         │ │
│  │ • POUCH_TIER_1..ASTRAL_CHEST_TIER_4│ │ • crucible-atomic-signet         │ │
│  └─────────────────┬──────────────────┘ │ • willow-visor-emerald           │ │
│                    └─────────┬──────────┴─────────────────┬────────────────┘ │
└──────────────────────────────┼────────────────────────────┼──────────────────┘
                               ▼                            ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│               OasisHostedServerClient & CryptoSyncEngine                     │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ Formal Verification & 6-Gate SaveLab Core                              │  │
│  │ • Z_1440 Curfew Modulo • 32-State ABAC Lattice • Total Order LWW       │  │
│  │ • sealSixGateAegisEnvelopeV1 / unsealSixGateAegisEnvelopeV1 (20 slots) │  │
│  └───────────┬────────────────────────────────────────────┬───────────────┘  │
│              ▼                                            ▼                  │
│  ┌───────────────────────────────────┐  ┌─────────────────────────────────┐  │
│  │ U2 Sovereign Web & Tailscale      │  │ U3 Local & Laputa Hoster (:19448│  │
│  │ • play.lewisfamilysystems.com     │  │ • 127.0.0.1 / 100.115.219.120   │  │
│  │ • lfs-prod.tail420c4d.ts.net      │  │ • POST /v1/hoster/rooms,migrate │  │
│  │ • 100.106.12.104:13379/lfs-sso    │  │ • POST /v1/hoster/wake,join     │  │
│  │ • /api/lfs/sync/{rooms,state,...} │  │ • GET /v1/hoster/status         │  │
│  └───────────────────────────────────┘  └─────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

- **4-Pillar Tailscale Auto-SSO Handshake (`probeTailscaleAnchorHandshake`)**:
  - Races `GET https://lfs-prod.tail420c4d.ts.net/lfs-sso/handshake` and `GET http://100.106.12.104:13379/lfs-sso/handshake` (`<= 1500ms` timeout) with sequential fallback and `NetworkHealthService` Tailscale interface correlation.
  - Updates `TailscaleSsoHandshakeState` (`stableNodeId`, `subuserId`, `persona`, `trustRing`, `generationEpoch`, and the 4 `[PASS]`/`[WARN]` pillar checks: `tailscale_tunnel`, `abac_perimeter`, `isolated_profile`, `user_data_backup`).
- **`U2` & `U3` Route Dispatcher**:
  - Automatically detects `:19448` (`U3`) vs. `U2` / Anchor endpoints and formats requests/responses with `Content-Type: application/json; charset=utf-8` for room creation, `<300ms` host migration, `<500ms` wake, rate-limited join, CRDT state delta push, event polling, presence heartbeats, and 16-channel `Rmblyn` wave challenge cards.
- **6-Gate `aegisEnvelopeV1` SaveLab (`commitSaveToOasisCustody`)**:
  - Seals any `GameSaveSnapshot` with `G1_MAGIC_HEADER` (`AEGISENV`), `G2_SCHEMA_VERSION` (`aegisEnvelopeV1`), `G3_SHA256_INTEGRITY`, `G4_MONOTONIC_GENERATION`, `G5_AAD_BINDING`, and `G6_NON_EMPTY_STATE`, storing locally and posting to `POST /api/lfs/sync/saves` across `slot_01`..`slot_20`.
- **Stage 6 Zero-Emoji Ratchet & `capsule.manifest.json` (`cp12`)**:
  - Sanitizes all Kotlin source strings to use Unicode escapes (`\u2605`) or ASCII badges and ships `capsule.manifest.json` (`schema_version: "cp12"`) at root and in Android assets.
