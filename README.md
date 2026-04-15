# Preservitory

A 2D isometric MMORPG client written in Java, inspired by RuneScape Classic and Old School RuneScape.
Connects to a dedicated game server over TCP. All game logic is server-authoritative; the client handles rendering, input, and UI only.

---

## 1. Project Overview

**Window size:** 765 × 503 px — 515 px game viewport + 250 px right panel  
**Target framerate:** 60 FPS  
**Server:** localhost:5555 (TCP, text-based line protocol)

### Startup sequence

1. `Main.main()` creates a `Game` instance and starts it on the Swing EDT.
2. `MusicManager` begins streaming the pre-login MP3 loop via JLayer.
3. `LoadingScreen` is shown while a background thread:
   - Runs `CacheDownloader` — downloads / extracts `sprites.dat` + `sprites.idx` if missing.
   - Loads all definitions: `ItemDefinitionLoader`, `ObjectDefinitionLoader`, `EnemyDefinitionLoader`, `NpcDefinitionLoader`.
   - Loads `ClientSettings` from disk.
4. `GamePanel` replaces the loading screen; the game loop starts.
5. `LoginScreen` is presented over the panel. On `AUTH_OK` the client enters the game world.

**Launcher:** `Run.bat` → `java -jar Preservitory.jar`

---

## 2. Core Architecture

### Rendering pipeline

```
GamePanel.paintComponent()
  └── GameRenderer.render()
        ├── WorldRenderer.render()     — isometric tile map, entities, loot, projectiles, floating text
        └── UIRenderer.render()        — chat, minimap, XP drops, target panel, overlays
              └── RightPanel.render()  — tab bars, active tab content
```

The game world occupies the left 515 px (`VIEWPORT_W`). The right 250 px (`PANEL_W`) is owned entirely by `RightPanel`.

### UI system

See **Section 3** for full detail.

### Input system

`GameInputHandler` is the single `MouseListener` / `MouseMotionListener` / `KeyListener`. It:
- Routes left-clicks to the world (movement, NPC talk, object gather, combat) or the right panel (tab selection, inventory actions, shop/prayer interactions).
- Routes key events to shortcuts (tab hotkeys, shift-drop toggle, music toggle, undo/redo in editor mode).
- Delegates all hover state directly to `RightPanel` — no local hover cache.

### Networking

`ClientConnection` manages a `Socket` to `localhost:5555`. Outgoing messages are sent via `PrintWriter.println`. Incoming messages are read line-by-line on a dedicated reader thread and dispatched to `ClientWorld` for state updates.

---

## 3. UI System

The UI system was fully refactored. There is one UI layer, no legacy code remains.

### Component hierarchy

```
RightPanel  (UIComponent, x=515, w=250, h=503)
  ├── TabBar  topTabBar    (y=0,  h=36)   — COMBAT … PRAYER
  ├── ContentPanel         (y=46, h=391)  — active tab renderer
  └── TabBar  bottomTabBar (y=437, h=36)  — SETTINGS, KEYBINDINGS, LOGOUT
```

Constants (`RightPanel`):
| Name | Value |
|---|---|
| `TAB_BAR_HEIGHT` | 36 |
| `CONTENT_PADDING` | 10 |
| `CONTENT_Y` | 36 |
| `CONTENT_H` | 431 |
| `BOTTOM_BAR_Y` | 467 |

### TabManager — single source of truth

`TabManager` holds the one active `Tab` reference. All reads go through `tabManager.getActiveTabType()`. There is no secondary tab field anywhere in the codebase.

### TabBar — slot-based layout

Each `TabBar` divides its full width evenly by the number of tabs it holds (`slotW = width / count`). Icons are centered within their slot. There is no compact icon group with gaps.

Visual states:
- **Inactive** — icon at 68 % alpha, `tabs_bar` sprite shows through.
- **Hovered** — faint warm overlay, icon at 92 % alpha.
- **Active** — slot filled with `Color(72,58,32,200)`, gold two-line highlight on top edge, icon lifted 2 px, bottom edge open.

The active tab's fill color is extended by `RightPanel.drawActiveTabConnection()` to cover the gap between the tab bar and content panel, eliminating the visible seam from the `inventory_box` background sprite.

### Tab renderers

Each tab type maps to a `TabRenderer` implementation:

| TabType | Renderer class | Notes |
|---|---|---|
| COMBAT | `CombatTab` | Attack style selector, auto-retaliate toggle |
| INVENTORY | `InventoryTab` | 28-slot grid, sell-price overlay when shop is open |
| SKILLS | `SkillsTab` | All 13 skills, XP progress bars, scrollable |
| EQUIPMENT | `EquipmentTab` | Equipped items by slot, unequip action |
| QUESTS | `QuestTab` | Quest log with state and progress |
| PRAYER | `PrayerTab` | 15-prayer grid, level gates, active highlight |
| SETTINGS | `SettingsTab` | FPS, ping, XP display, minimap, shift-drop toggles |
| KEYBINDINGS | `KeybindingsTab` | Rebind individual actions |
| LOGOUT | `LogoutTab` | Confirm-to-logout with countdown |

### Inventory (InventoryTab)

- **Slots:** 28 (`Inventory.MAX_SLOTS`)
- **Grid:** 4 columns × 7 rows (`INV_COLS=4`, `INV_ROWS=7`)
- **Slot size:** 44 px with 3 px gap (`SLOT_SIZE=44`, `SLOT_GAP=3`)
- **Actions:** drop, equip, sell (when shop is open), use
- **Shift-drop:** hold Shift + left-click, toggled in settings
- **Stack display:** count shown as `Xk` for ≥ 1000
- **Coin stacks:** rendered via `AssetManager.drawCoinStack()` with tier icons

---

## 4. Game Features

### Movement
- Click-to-move on isometric tiles (32 × 32 px).
- Client sends `MOVE_TO destCol destRow`; server runs A* and streams back position updates.
- Player speed: 150 px/s. Walk animation driven by `AnimationController` (procedural bob).
- **Server authoritative.** Client never self-moves.

### Combat
- Click an enemy → client sends `ATTACK enemyId`.
- Server tick rate: 0.6 s/tick. Server calculates and sends `DAMAGE playerId damage`.
- Client plays attack animation (0.1 s/frame) and floating damage text.
- Auto-retaliate toggle sent as `AUTO_RETALIATE bool`.
- **Fully server authoritative.** Client displays results only.

### Skills

13 skills are registered and tracked with the OSRS XP table (max level 99):

| Category | Skills |
|---|---|
| Combat | Attack, Strength, Defence, Hitpoints, Magic, Range, Prayer |
| Gathering | Woodcutting, Mining, Fishing |
| Artisan | Cooking, Crafting, Fletching, Smithing |

XP and levels are server-sent (`SKILLS`, `SKILL_XP` packets). The client stores and displays only.

**Gathering actions (confirmed):**
- **Woodcutting** — click tree object → `GATHER_REQUEST woodcutting objectId`. Server responds with `START_GATHERING\twoodcutting\tvalue`. Tree respawn handled server-side.
- **Mining** — same pattern with `GATHER_REQUEST mining objectId` and rock objects.
- **Fishing** — `GATHER_REQUEST fishing objectId` opcode exists; full interaction depends on server implementation.

**Artisan skills** (Cooking, Crafting, Fletching, Smithing) — skill entries exist and XP can be received, but no dedicated client-side interaction flow is confirmed in code.

### Prayer
- 15 prayers available, gated by prayer level.
- Prayers toggled via `TOGGLE_PRAYER prayerId`.
- Active prayers highlighted in `PrayerTab`.
- **Altar interaction:** `ALTAR_CLICK` restores prayer points; `ALTAR_BONE_USE boneId` buries bones for XP.
- Prayer state is server-sent (`ACTIVE_PRAYERS` packet).

### Shop
- NPC shopkeeper click → server sends `SHOP\tid\tname\titemData`.
- `ShopParser` decodes items and sell-price map.
- `ShopWindow` renders a scrollable item grid with buy prices.
- Inventory overlays sell prices when a shop is open.
- Actions: `BUY itemId`, `SELL itemId`, `SHOP_CLOSE`.

### Quests & Dialogue
- **Quest log:** Tracks all quests with states: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETE`.
- Per-quest: current stage, description text, gather-objective progress (`progressAmount / requiredAmount`).
- **Dialogue:** NPCs initiate dialogue via `DIALOGUE\tnpcId\ttext`. Multi-option responses supported (`DIALOGUE_OPTIONS`). Client sends `DIALOGUE_NEXT` or `DIALOGUE_OPTION index`.
- **Quest completion window:** shown on `QUEST_COMPLETE` packet.

### Equipment
- Equipment slots displayed in `EquipmentTab`.
- Server sends `EQUIPMENT slotName itemId;...`.
- Unequip action routes through registered listener.

### Loot
- Ground loot rendered as item sprites over tiles.
- Server sends `LOOT` packets with item positions.

---

## 5. Networking

**Connection:** `ClientConnection` → `localhost:5555`, plain TCP, UTF-8 text lines.

### Selected outgoing opcodes

| Opcode | Description |
|---|---|
| `LOGIN` / `REGISTER` | Authentication |
| `MOVE_TO col row` | Request pathfinding to tile |
| `ATTACK enemyId` | Initiate combat |
| `GATHER_REQUEST skill objectId` | Start woodcutting / mining / fishing |
| `EQUIP itemId` | Equip from inventory |
| `DROP itemId` | Drop item |
| `USE itemId` | Use item |
| `BUY` / `SELL itemId` | Shop transaction |
| `TOGGLE_PRAYER prayerId` | Activate/deactivate prayer |
| `ALTAR_CLICK` / `ALTAR_BONE_USE boneId` | Altar interaction |
| `TALK npcId` | Begin NPC dialogue |
| `DIALOGUE_NEXT` / `DIALOGUE_OPTION n` | Advance dialogue |
| `AUTO_RETALIATE bool` | Toggle auto-retaliate |
| `PING:timestamp` | Heartbeat (4 s interval) |

### Selected incoming opcodes

| Opcode | Description |
|---|---|
| `AUTH_OK` / `AUTH_FAIL` | Login result |
| `PLAYERS` | All remote player states |
| `ENEMIES` | All enemy states |
| `PLAYER_HP` | Current / max HP |
| `SKILLS` | Full skill dump |
| `SKILL_XP` | Single skill XP update |
| `DAMAGE` | Damage event for floating text |
| `INVENTORY` / `INVENTORY_UPDATE` | Inventory state |
| `EQUIPMENT` | Equipment state |
| `LOOT` | Ground items |
| `SHOP` | Shop contents |
| `DIALOGUE` / `DIALOGUE_OPTIONS` / `DIALOGUE_CLOSE` | Dialogue flow |
| `QUEST_LOG` / `QUEST_START` / `QUEST_COMPLETE` | Quest state |
| `ACTIVE_PRAYERS` | Active prayer set |
| `TREES` / `ROCKS` | Gatherable object states |
| `NPCS` | NPC positions and types |
| `PROJECTILES` | In-flight projectile data |
| `CHAT` | Chat messages |

**Client vs server authority:**

| System | Authority |
|---|---|
| Pathfinding | Server (A*) |
| Combat damage | Server |
| XP and levels | Server |
| Prayer state | Server |
| Inventory state | Server |
| Movement interpolation | Client (visual only) |
| Animation | Client |
| Floating damage text | Client (from server data) |

---

## 6. Assets & UI Rendering

Sprite naming is string-based (not ID-based).
All UI elements resolve sprites via AssetManager using keys such as:

- client_screen/tabs_bar
- client_screen/inventory_box
- client_screen/<tab_icon>

This allows UI rendering to be data-driven rather than hardcoded.

### Packed sprite cache

- **Files:** `sprites.dat` (data) + `sprites.idx` (index).
- Downloaded at startup by `CacheDownloader` if not present.
- `SpriteCache` decodes entries into `BufferedImage` and caches them in memory.
- `AssetManager.getOrLoad(key)` is the single access point used throughout all renderers.

### Sprite key conventions

| Prefix | Contents |
|---|---|
| `client_screen/` | UI chrome: `tabs_bar`, `inventory_box`, tab icons, login background |
| `login_screen/` | Login and loading screen assets |
| `prayer_icons/` | One icon per prayer (e.g. `prayer_icons/thick_skin`) |
| `objects/trees/` | Tree sprites by type |
| `objects/rocks/` | Rock sprites by type |
| `items/` | Item sprites by name key |

### Definition files

JSON definition files in `cache/items/`, `cache/objects/`, `cache/enemies/` are loaded at startup into in-memory registries (`ItemDefinitionManager`, `ObjectDefinitionRegistry`, `EnemyDefinitionManager`). Renderers look up sprite keys and metadata from these registries — no hardcoded item data remains in client rendering code.

### UI rendering approach

All UI panels use named sprites from the cache as their visual base. Fallback to primitive drawing (solid colors, `fillRect`) only occurs when a sprite has not yet been loaded. Tab icons, inventory slots, prayer buttons, and shop items all resolve their appearance through `AssetManager`.

### Sound

- **Music:** Looping MP3 stream via JLayer's `AdvancedPlayer`. Plays pre-login music; stops on successful login. Toggle via M key or the music button on the login screen.
- **Sound effects:** Synthesized at runtime (44.1 kHz, 8-bit mono). No audio files required. Effects: `CHOP`, `MINE`, `HIT`, `LEVEL_UP` (ascending arpeggio), `ITEM_PICKUP`. Managed by `SoundSystem`.

---

## 7. Map Editor

Enabled by setting `Constants.EDITOR_MODE = true` before building.

**Features:**
- **Tile painting** — four tile types: Grass, Path, Water, Pavement. Selected by toolbar buttons or keys 1–4.
- **Object placement** — expandable category grids for Trees and Rocks. Click grid cell to select, click map to place.
- **Object rotation** — rotation controls in the toolbar.
- **Clear object** — remove a placed object from a tile.
- **Minimap** — toggleable overview of the current map.
- **Undo / Redo** — full history via `EditorHistory`.
- **File operations** — New Map, Save Map, Load Map. Maps serialized to JSON via `MapIO`.
- **Coordinate display** — shows tile col/row under the cursor.

The editor runs inside the same `GamePanel`; the game loop continues while editing.

---

## 8. Current State of the Project

### Stable and functional

- Full rendering pipeline (world, entities, UI)
- Right panel UI system — single, non-duplicated, no legacy code
- Tab navigation with visual active-tab integration
- Inventory: display, drop, equip, sell, shift-drop
- Combat: animation, floating damage, auto-retaliate
- Woodcutting and Mining gather loops
- Prayer: all 15 prayers, altar interaction
- Shop: buy/sell flow
- Quest log, dialogue system, quest completion window
- Skills tab with XP tracking for all 13 skills
- Equipment display and unequip
- Chat display
- Music and synthesized sound effects
- Login, registration, loading screen
- In-client map editor

### Functional but being refined

- **Tab bar visuals** — slot-based layout and active-tab seam connection are newly implemented; visual polish ongoing.
- **Equipment tab** — display is functional; item sprite coverage depends on cache contents.
- **Fishing** — opcode exists, server-side implementation determines availability.
- **Artisan skills** (Cooking, Crafting, Fletching, Smithing) — XP tracking works; no confirmed client-side interaction flows beyond receiving XP packets.

---

## 9. UI Design State

The UI architecture refactor is complete:

- `UITab` legacy enum — **deleted**.
- Duplicate inventory hit-testing (`GamePanel.getInventorySlotIndexAt`) — **deleted**.
- Duplicate hover state (`GameInputHandler.hoveredTabIndex`) — **deleted**.
- Mismatched grid constants (`UIRenderer.SLOT_SIZE=32`) — **deleted**; `InventoryTab` is the single source of truth.
- Dead rendering branches — **deleted**; `UIRenderer` has no conditional UI-mode guards.

Current single-system flow:

```
Input event
  └── GameInputHandler
        └── RightPanel.openTab(TabType) / getClickedInventoryItemId()
              └── TabManager  (single active tab state)
                    └── ContentPanel → active TabRenderer
```

The UI is now in **visual polish phase**. Architecture is stable; ongoing work is appearance (sprites, color tuning, spacing).

---

## 10. Known Limitations

- **No offline mode** — the client requires a running server at `localhost:5555`. Without it, the login screen will hang on connection.
- **Server not included** — this repository contains the client only.
- **Artisan skill interactions** — Cooking, Crafting, Fletching, and Smithing track XP but have no confirmed client-side interaction flow (no dedicated use-item or station-click paths confirmed in client code).
- **Fishing** — gather opcode is wired; full interaction depends on server object definitions.
- **Magic and Ranged** — skills are tracked; no dedicated combat UI or projectile-casting flow confirmed on the client beyond the `PROJECTILES` render path.
- **Sprite coverage** — UI is fully sprite-driven but visual fidelity depends on which keys are present in the packed cache. Missing sprites fall back to solid-color placeholders.
- **Single server address** — host and port are compile-time constants (`Constants.LOCALHOST`, `Constants.PORT`). There is no server-select UI.


## 11. Client Layout (Work In Progress)

The client currently follows a fixed layout:

- Left: Game viewport (515px)
- Right: RightPanel (250px)
- Bottom: Chatbox (rendered in UIRenderer)

Planned direction:
- Fully sprite-driven client frame (client_screen assets)
- Tabs visually integrated with panel (no floating UI)
- Minimap and additional panels to be positioned within the frame

Note:
Layout is currently functional but undergoing visual refinement.


## Development Rules

- Do not duplicate UI systems — RightPanel is the only UI layer
- Do not manually render UI elements that already exist as panels
- Always reuse existing systems before creating new ones
- UI changes should be visual/styling, not architectural