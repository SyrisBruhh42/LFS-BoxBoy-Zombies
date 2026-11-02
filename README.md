<!-- HUMAN-NOTES:BEGIN -->
# LFS: BoxBoy Zombies — Bio-Noir 2D Tactical Survival & Co-Op Arena

Retro emulator-styled 2D Boxhead & Black Ops Zombies tactical survival capsule featuring procedural rooms, custom gamepad overlay & key-mapping (`ControllerProfile`), P2P socket co-op (`MultiplayerRoom`, `RoomPeer`), encrypted cross-device save sync (`SaveSlot`), and AI Tactical Director.

- **Capsule / Project ID**: `lfs-boxboy-zombies` (`lfs-boxboy-zombies`)
- **Primary Runtime Stack**: Native Android & Web Hybrid (`capsule.manifest.json`) + Procedural 2D Arena + P2P Co-Op + Save Lab Sync
<!-- HUMAN-NOTES:END -->

<!-- STEWARD:BEGIN -->
## LFS Oasis Sovereign Steward Manifest (`v2.0.0-cp12-argon4-apex`)

| Property | Value |
| :--- | :--- |
| **Repository** | `SyrisBruhh42/LFS-BoxBoy-Zombies` |
| **Capsule / Project ID** | `lfs-boxboy-zombies` (`lfs-boxboy-zombies`) |
| **Suite Release Alignment** | `LFS Oasis v2.0.0 / CP-12 + v3.8-sovereign-forge` (`v2.0.0-cp12-argon4-apex`) |
| **Runtime & Architecture** | Native Android & Web Hybrid (`capsule.manifest.json`) + Procedural 2D Arena + P2P Co-Op + Save Lab Sync |
| **Local Storage Engine** | Local SQLite WAL / IndexedDB Save Slots (`aegisEnvelopeV1` AES-256-GCM) |
| **Governance & ABAC** | `AUTH-1` Total Typed ABAC (`lfs-local-abac-v1`) · `ZT-6` Zero-Trust Scrubbing |

### 1. Architecture & Four-Umbrella (`U1..U4`) Integration
Retro emulator-styled 2D Boxhead & Black Ops Zombies tactical survival capsule featuring procedural rooms, custom gamepad overlay & key-mapping (`ControllerProfile`), P2P socket co-op (`MultiplayerRoom`, `RoomPeer`), encrypted cross-device save sync (`SaveSlot`), and AI Tactical Director.

- **Offline-First Autonomy**: Operates 100% locally by default with deterministic state persistence and zero mandatory cloud dependencies.
- **LFS Oasis Suite Dockability**: Integrates with `2-lfs-server` (`127.0.0.1:3000`), `3-lfs-aegis-linux` (`Willow v0.2.0` / `lfs-ctl` Spine / Local Sovereign Forge `:19447`), and `4-lfs-aegis-android` via `aegisEnvelopeV1` AES-256-GCM Save Lab envelopes and `lds-tokens.w3c.json` theme packs (`evergreen-dark`, `evergreen-light`, `high-contrast`, `calm`).

### 2. Zero-Trust Credential Vault & `RATCHET-11` Secret Hygiene
- **Zero Committed Secrets**: Static configuration files (`firebase-applet-config.json`, `.env.example`) carry `"VAULT_INJECTED_AT_RUNTIME"` placeholders. No literal `AIzaSy*`, `GOCSPX-*`, `ya29.*`, `1//*`, or `sk-*` keys are ever stored in Git history or working trees.
- **Runtime Injection & Offline Fallback**: Client loaders resolve credentials strictly at runtime via `import.meta.env.VITE_FIREBASE_API_KEY` or the loopback Sovereign Vault endpoint (`/api/lfs/vault/client-config`) and automatically fall back to local IndexedDB / SQLite WAL storage when offline.
- **Privacy & Third-Party Disclosures**: See [`PRIVACY.md`](./PRIVACY.md) and [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md).

### 3. Copyright Management Information (`17 U.S.C. § 1202`) & TDM Opt-Out (`EU DSM Art. 4`)
> [!IMPORTANT]
> - `Copyright (c) 2026 SyrisBruhh42 / Lewis Family Systems. All Rights Reserved.`
> - `PROPRIETARY AGENT ARCHITECTURE & SKILL SPECIFICATION: Any unauthorized copying, commercial repackaging as a standalone agent skill (including but not limited to conversion into SKILL.md packages or listing on third-party AI skill marketplaces such as Agensi.io), redistribution, or automated AI training/scraping is strictly prohibited without prior written license from the copyright holder.`
> - `Removal or alteration of this Copyright Management Information (CMI) is a violation of 17 U.S.C. § 1202 (DMCA).`
> - **Machine-Readable TDM Reservation (`tdm-reservation: 1`)**: Enforced via [`.well-known/tdmrep.json`](./.well-known/tdmrep.json), [`.aiexclude`](./.aiexclude), [`.noai`](./.noai), and [`robots.txt`](./robots.txt).
<!-- STEWARD:END -->
