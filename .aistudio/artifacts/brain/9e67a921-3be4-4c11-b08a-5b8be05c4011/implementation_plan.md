# BoxBoy Zombies — Google Drive `DriveSyncManager` & `BoxBoyZombies/Saves` Cloud Vault

Deliver a dedicated `DriveSyncManager` that provisions and manages a hierarchical `BoxBoyZombies/Saves` folder structure in the player's Google Drive, uploading and downloading tamper-verified JSON game-save envelopes with multi-tier retention (in-place primary slot updates, rolling `.bak` backups, and timestamped archives) and automatic newest-timestamp conflict resolution.

## User Review & Critical Decisions

> [!IMPORTANT]
> All Phase 1 design choices have been confirmed and synthesized into this architecture:

- **Confirmed Decision 1 — Multi-Tier Upload Retention ("All" Modes Supported)**: Uploading a save slot to `BoxBoyZombies/Saves` supports all three retention strategies simultaneously:
  1. **Primary Slot Update/Create**: Updates the existing canonical slot file in-place (`PATCH` multipart upload by Drive `fileId`) or creates a new file if none exists.
  2. **Rolling Backup Rotation**: Automatically preserves the prior state as a rolling backup envelope before overwriting the primary slot.
  3. **Timestamped Chronicle Archive**: Allows players to snapshot immutable timestamped archives (`Wave`, `Kills`, `Timestamp`) alongside the active slot, with a UI mode selector in the Cloud Chronicles vault.
- **Confirmed Decision 2 — Conflict Resolution**: **Newest timestamp wins with manual restore option**. During two-way synchronization between local slots and `BoxBoyZombies/Saves`, the manager compares `timestampEpochMs` across local and remote envelopes so the newest state wins automatically, while surfacing explicit one-tap **Force Restore Cloud** and **Force Overwrite Cloud** actions in the UI.
- **Confirmed Decision 3 — JSON Game-Save Envelope Format**: **Encrypted payload plus readable metadata and SHA-256 checksum**. Every file stored in `BoxBoyZombies/Saves` wraps the encrypted game-state ciphertext with a human-readable metadata header (`slotName`, `highestWave`, `totalKills`, `points`, `loadoutSummary`, `mapSeed`, `timestampEpochMs`) and a deterministic `sha256Checksum` verified prior to restoring any save.

---

## 1. Overview & Core Concept

- **What It Does**: `DriveSyncManager` automates hierarchical Google Drive folder discovery and creation (`BoxBoyZombies` root directory $\rightarrow$ `Saves` child directory) via Google Drive REST API v3 (`https://www.googleapis.com/auth/drive.file` and `drive.appdata`), serializing and deserializing verified JSON game-save envelopes with offline queue resilience.
- **Target Audience / Persona**: Survival players switching between phones, tablets, and TV/Desktop cast setups who want transparent, inspectable save files organized cleanly inside their own Google Drive (`BoxBoyZombies/Saves`) alongside automatic conflict resolution and rollback safety.
- **Key Value**: Eliminates lost survival progress and opaque cloud blobs by storing human-inspectable JSON headers, cryptographic integrity checksums, and rolling/timestamped recovery points in a dedicated user-accessible Drive folder.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Automatic Folder Provisioning**: When syncing or opening the Cloud Chronicles Vault, `DriveSyncManager` resolves the `BoxBoyZombies` folder ID and nested `Saves` folder ID in Google Drive (creating either folder with MIME type `application/vnd.google-apps.folder` if missing) and displays the verified Drive path badge (`BoxBoyZombies/Saves`).
  2. **Multi-Mode Save Upload**: Players can trigger a quick backup from the `TitleHub` or choose their retention mode in the Cloud Chronicles dialog (**All-in-One: Primary + Rolling Backup + Timestamped Archive**, **Update Primary + Rolling Backup**, or **Timestamped Snapshot Only**).
  3. **Newest-Wins Auto-Sync & Conflict Inspector**: On refresh, `DriveSyncManager` compares each remote envelope in `BoxBoyZombies/Saves` against local slots. If a timestamp discrepancy is detected, the newest version is promoted automatically while flagging any superseded version for optional manual restore.
  4. **Checksum-Verified Download & Restore**: Selecting any primary slot, rolling backup, or timestamped archive downloads the JSON envelope from `BoxBoyZombies/Saves`, validates its `sha256Checksum`, decrypts the snapshot, and restores the exact wave, points, weapons, perks, and armor rig.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Final Fantasy XVI / VII Remake Crystalline Astral Command interface.
  - *Color Palette & Mood*: Valisthea Obsidian (`--background: #070A14`), Astral Navy Surface (`--surface: #0F1629`), Luminous Aether Cyan (`--primary: #38E8FF`), Brushed Champagne Gold (`--secondary: #F5C469`), Materia Violet (`--tertiary: #B784FF`), and Emerald Lifestream (`#2EE59D`) for verified SHA-256 checksum badges.
  - *Typography & Hierarchy*: Engraved `Cinzel` serif headers for dialog titles and chronicle headers paired with crisp `Plus Jakarta Sans` metadata rows and tabular checksum/timestamp readouts.
  - *Component Styling & Layout*: Translucent astral cards with 1dp brushed gold/cyan borders, pill badges distinguishing `PRIMARY`, `ROLLING .BAK`, and `ARCHIVE` envelopes, and clear conflict-status chips (`SYNCED`, `CLOUD NEWER`, `LOCAL NEWER`, `CHECKSUM OK`).
- **Interactive Feedback & Motion**: Live sync progress indicators, SHA-256 verification badges, folder path status readouts (`BoxBoyZombies/Saves`), and crystalline audio-haptic confirmation chimes via `CombatAudioEngine` upon save upload or restoration.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hierarchical `BoxBoyZombies/Saves` Folder Resolution with ID Caching**
  - *Chosen Approach*: Query Google Drive for `name = 'BoxBoyZombies' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`, create if absent, then query/create child folder `name = 'Saves'` with `'boxBoyFolderId' in parents`, caching both folder IDs in local preferences to avoid redundant round-trips on subsequent uploads.
  - *Why*: Guarantees all save envelopes land in a single clean `BoxBoyZombies/Saves` hierarchy without creating duplicate folders on rapid uploads.
  - *Alternatives Considered*: Storing only in `appDataFolder` was passed over as primary storage because the user specifically requested a dedicated `BoxBoyZombies/Saves` folder in their Google Drive; `appDataFolder` is retained as a secondary fallback if folder scope is restricted.
- **Decision 2: All-Mode Retention (Primary Update + Rolling Backup + Timestamped Archive)**
  - *Chosen Approach*: Before updating a slot's primary file in-place (`PATCH`), `DriveSyncManager` copies or promotes the previous envelope to a rolling backup file and optionally emits a timestamped archive envelope according to the selected upload policy.
  - *Why*: Satisfies the user's "All" preference—keeping the folder tidy with canonical slot files while guaranteeing both instant rollback (`.bak`) and historical milestone snapshots.
- **Decision 3: SHA-256 Checksummed JSON Envelope + Newest-Timestamp-Wins Resolution**
  - *Chosen Approach*: Compute a deterministic SHA-256 digest over the canonical metadata fields and `encryptedPayload` when constructing the JSON envelope, and verify the digest before applying any downloaded save. Compare `timestampEpochMs` during sync so the newest save wins automatically while keeping manual restore buttons accessible.
  - *Why*: Protects against partial network writes or manual Drive file edits corrupting player progression, while keeping multi-device play frictionless.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI (Final Fantasy Theme)                  │
│  ┌───────────────────────────────┐     ┌─────────────────────────────────┐  │
│  │           TitleHub            │     │   Cloud Chronicles Save Dialog  │  │
│  │ • Quick Drive Backup          │     │ • Folder Badge: BoxBoyZombies/  │  │
│  │ • Sync Status & Checksum Chip │     │   Saves                         │  │
│  └───────────────┬───────────────┘     │ • Upload Mode Selector (All /   │  │
│                  │                     │   Primary+Bak / Archive)        │  │
│                  │                     │ • Manual Restore & Conflict UI  │  │
│                  └──────────┬──────────┴────────────────┬────────────────┘  │
└─────────────────────────────┼───────────────────────────┼───────────────────┘
                              ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BoxBoyViewModel                                │
│  • Coordinates CryptoSyncEngine snapshots, local SaveSlots, & Drive sync    │
│  • Newest-Timestamp-Wins reconciliation + manual restore override handlers  │
└─────────────────────────────────────────────┬───────────────────────────────┘
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DriveSyncManager                               │
│  ┌──────────────────────────┐  ┌──────────────────────────┐                 │
│  │ Folder Hierarchy Engine  │  │ Envelope & Checksum Core │                 │
│  │ • Ensure BoxBoyZombies/  │  │ • JSON Envelope Builder  │                 │
│  │   Saves folder IDs       │  │ • SHA-256 Digest & Verify│                 │
│  └────────────┬─────────────┘  └─────────────┬────────────┘                 │
│               ▼                              ▼                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Multi-Tier File Operations (Google Drive REST v3 + Local Vault Cache) │  │
│  │ • Upsert Primary Slot (PATCH existing fileId / POST new multipart)    │  │
│  │ • Rotate Rolling Backup (.bak.json) & Emit Timestamped Archive        │  │
│  │ • List & Download Envelopes from BoxBoyZombies/Saves                  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & JSON Save Envelope Specification

Each file uploaded to `BoxBoyZombies/Saves` is a UTF-8 JSON document with the following structure:

```json
{
  "schemaVersion": 2,
  "envelopeFormat": "BOXBOY_DRIVE_SAVE_ENVELOPE_V2",
  "folderPath": "BoxBoyZombies/Saves",
  "fileKind": "PRIMARY_SLOT",
  "metadata": {
    "slotName": "Wave 14 Citadel Run",
    "highestWave": 14,
    "totalKills": 482,
    "points": 12450,
    "loadoutSummary": "Railgun Lv4 • Aether Rig",
    "mapSeed": 19960227,
    "timestampEpochMs": 1762031200000
  },
  "sha256Checksum": "8f4b2e9c...",
  "encryptedPayload": "v1:GCM:..."
}
```

### Interactive Component & State Mapping

- **Folder Hierarchy Provisioning (`ensureSavesFolderId`)**: Queries Drive v3 for `BoxBoyZombies` in root, creates it if missing, then queries/creates `Saves` inside `BoxBoyZombies`, updating `DriveSyncUiState.remoteFolderPath` (`"BoxBoyZombies/Saves"`) and `folderReady = true`.
- **Upload Flow (`uploadSaveEnvelope`)**:
  - Serializes `GameSaveEnvelope` with readable metadata, `encryptedPayload`, and `sha256Checksum`.
  - If an existing primary file for `slotName` exists in `BoxBoyZombies/Saves`, rotates its previous content into `<slot>_rolling.bak.json`, updates the primary `<slot>.json` via `PATCH`, and (when `ALL_TIERS` or `TIMESTAMPED_ARCHIVE` is active) writes `<slot>_<epoch>.archive.json`.
- **Download & Conflict Resolution (`syncAndReconcileSaves` / `downloadSaveEnvelope`)**:
  - Lists all envelopes inside `BoxBoyZombies/Saves`, downloads and verifies each `sha256Checksum`, compares `timestampEpochMs` with local save slots, auto-promotes the newest save when auto-sync is triggered, and exposes one-tap manual restoration for any primary, rolling backup, or archive envelope.
