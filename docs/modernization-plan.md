# Modernization plan

Audit date: **2026-09-22**. Status: **documentation baseline complete; implementation not started**.
The current task authorizes inspection and documentation only. This plan proposes later work;
it does not authorize implementation, installation, dependency upgrades or new gameplay features.

## Objective and completion criteria

Improve maintainability, robustness and ease of extension while preserving intended player
behavior, saved preferences and optional-mod compatibility. Prefer deleting unnecessary machinery
and clarifying ownership over introducing frameworks. Neither file length nor line reduction is
a success metric. Large cohesive classes may remain large.

The modernization pass is complete when:

1. The implemented feature/compatibility inventory below is still satisfied, or an intentional
   behavior change has a recorded rationale and migration. Retired features stay retired.
2. M1–M10 below are completed or explicitly deferred with evidence, impact and a revisit trigger.
   A hypothesis can close as “retain, no change” after investigation; it need not cause a refactor.
3. A fresh non-installing build and meaningful automated behavior/contract tests pass in CI.
   Tests cover configuration failure/migration, lifecycle isolation, room transforms and current
   solver decisions. No zero-test success is described as behavioral verification.
4. Required in-game checks have player verdicts for the affected build. Material failures remain
   open work; unavailable checks are recorded as pending or explicitly accepted limitations,
   never silently converted to PASS. A milestone may be code-complete and awaiting game checks.
5. Docs match shipped features and extension paths. Persistent instructions are concise, and
   machine-checkable contracts are checked automatically rather than merely repeated in prose.
6. The progress/decision log names what changed, the evidence and verification, remaining risk,
   and one concrete next task. A new session can resume without reconstructing this audit.

## Baseline and evidence quality

This audit examined the **working tree**, not just commit
`a23b418686400738a919dedab1139bc3afe39473`. At entry it contained 15 tracked changed/deleted files,
including `CLAUDE.md`, README, build configuration and config code, plus untracked `.codex/`, docs,
dungeon/screen/theme/mixin code and assets. Those changes are the user's baseline, not changes
made by this task. No source, build or instruction file was edited. The audit creates this file,
creates [architecture.md](architecture.md), and appends new checks to the existing check log.

Read sources: root `CLAUDE.md`, local Gradle rule, README, build/wrapper/CI/metadata, all existing
feature documents (with focused examination of implemented design sections), the complete
in-game check log, and implementation paths through initialization, config/UI/theme, dungeon
gate/rooms, both puzzles, Livid/mobs, rendering and shutdown. No `AGENTS.md` was found at the
repository or inspected ancestor levels. Sibling hub and shared-locraw contracts were read locally.
Upstream research in old plans was not independently revalidated against today's upstream/server.

Evidence labels in this plan:

- **Verified:** observed command result or directly traceable source behavior. A verified unsafe
  path is not proof that a player has experienced its failure.
- **Player report:** preserved verdict/note or feature-document report; not reproduced this audit.
- **Hypothesis:** plausible effect requiring a fixture, experiment or in-game check before repair.
- **Deferred feature:** documented future scope, not an existing feature regression.

| Baseline check | Result and limit |
| --- | --- |
| Fresh build | PASS: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew build -x installMod --offline --rerun-tasks --console=plain`. Five actionable tasks executed; compilation/resources/jar/sources completed. |
| Test execution | `compileTestJava`, `processTestResources`, `test`: **NO-SOURCE**. No `src/test`, test dependency, meaningful test report or explicit behavior check configured. |
| Artifact inspection | PASS: `build/libs/cherrypicking-1.0.0.jar` contains expanded mod version `1.0.0`, mixin metadata, theme/beam data, shader and `LICENSE_cherrypicking`. Does not establish runtime linkage or shader compilation. |
| Resource inspection | PASS via temporary Python read-only assertions: 26 `Flavour` entries each have 26 valid hex slots; `_notice` retained. Beams has 13 six-integer rows, 12 unique. These are ad hoc checks, not committed regression tests. |
| Registry inspection | 46 unique literal entry keys: 44 settings and `blaze.reset`/`beams.reset` actions. Source-level extraction only; runtime initialization/default capture was not exercised by a test. |
| Display-only inspection | Local `Chat.say` uses `sendSystemMessage`; no outbound chat/command, packet-send, attack/use or synthetic-key call found in the targeted source search. This is a review aid, not a formal guarantee. |
| Existing game evidence | 32 checks before this audit: 17 PASS, 15 OPEN, zero FAIL/PARTLY. PASS notes still contain reported issues and caveats. No new live game session was run and no player verdict was changed. |
| Environment limits | Initial sandbox build failed on a Gradle cache lock; approved rerun succeeded. JDK 25.0.1 used. Gradle warns about three invalid auto-detected Java 8 installations. No project failure followed. |
| Reproducibility limits | Offline cached build only, not a cold Linux/Windows build. Loom request is `1.17-SNAPSHOT`; cached resolution reports 1.17.21. Dependency locking/checksums are not configured. CI execution was inspected, not run remotely. |

The first build was entirely up-to-date, so a forced task rerun established a fresh compile.
Installation was excluded in both runs; only ignored build/cache outputs were written.
Two preliminary resource probes had overly strict audit-script assumptions (counting `_notice`
as a theme and reading a quoted Javadoc word as a slot); corrected probes passed. Those were
inspection-script errors, not resource defects. Live configs, installed jars and game logs were
not used to assert success; S3-03's shader-log caveat therefore remains unverified.

## Feature and compatibility inventory

The current package/component map and extension procedure are in [architecture.md](architecture.md).
Preserve these behaviors while changing implementation:

| Area | Implemented contract | Evidence / pending compatibility |
| --- | --- | --- |
| Platform | Client-only Minecraft 26.2, Java 25, Fabric. Exact development dependency pins and broader metadata ranges are listed in architecture. | Fresh compile passed. Runtime optional-mod matrix still pending. No server component. |
| Screen access | `/cherry`, `/cherry config`, optional ModMenu and `jeffhub` all use the native screen; parent navigation; standalone use. | `CherryCommand`, `ScreenOpener`, `ConfigScreen`, adapters. MOD-01. |
| Preferences | Registry-based live values, captured owner defaults, dependencies, five control kinds, actions excluded from JSON; stable keys and enum names. Unknown keys ignored/dropped, missing keys default. | `Settings`, `Setting`, `ConfigFile`; S4-10, S6-03. Preserve current swatch syntax and document migration limits. |
| Screen/theme | Tabs, folding cards, responsive columns, all-tab search, scrolling/tooltips, reset shown/tab/all, live theme preview, colour picker with outline/fill, 26 themes. | `client.screen`, `client.theme`; S6-01/04/05, MOD-07. No YACL. |
| Dungeon context | Sidebar plus optional shared locraw; developer forcing/logs/room overlay; room shape and signature rotations. | `DungeonState`, `SharedLocraw`, `RoomWatch`; MOD-02/03. F7/M7 boss detection absent. |
| Blaze | Per-room health ordering, auto chest heuristic and manual override, next markers/lines, local completion message, reset. | `Blaze`, registry; MOD-04. Unknown direction stays neutral. |
| Creeper Beams | Pair active lanterns, optional connecting lines, separate outline/fill settings, chest completion, reset. | `CreeperBeams`, data; MOD-05, S6-02. Teammate progress is read from world state. |
| Livid | F5/M5 identity lock, blindness fallback, box style/colour, local chat, colour title with size/hold/fade and F1/F3 hiding. | `Livid`, `LividTitle`; existing PASS evidence, S3-04, S5-01, MOD-06. Invulnerability timer intentionally removed. |
| Mobs | Starred mobs only in current room, entity ownership fallback, kind colours, distance labels, optional hidden Fels, 64-mob cap. | `StarMobWatch`/renderer; S3-08/10, S6-01/02, MOD-09. Hidden Fels can include unstarred mobs by design. |
| Rendering | Through-wall no-fog boxes/lines; puzzle depth option; interpolated moving boxes; mob overlap ordering; independent fill/outline. | `draw`, mob renderer; S3-03 caveat, S6-01/02, MOD-06. GPU/modpack compatibility unverified here. |
| Clean exit | Default-on `quitting.cleanExit`, post-main-only hook, normal JVM exit with shutdown hooks. | `CleanExit`, required mixin; MOD-08. Comments about third-party hanging threads are not fresh reproduction. |
| Packaging/integration | Shipped `jar`, own config file, optional imports isolated; no sibling-library dependency; shared map and hub shapes remain additive. | Metadata/build and locally read sibling contracts. MOD-01/02. |
| Resource provenance | Existing Odin notice, theme `_notice` and project license retained. | Source/resource/jar inspection only; full upstream provenance/notice completeness not established. |

**Not implemented:** Boulder, Teleport Maze, Water Board, arbitrary room-name database, nametag-free
miniboss detection, friend cosmetics/relay/text settings. Keep their design documents; do not
implement them as modernization prerequisites. Do not revive YACL, the placeholder, global box
fill control, mob opacity control or Livid timer merely because an older document mentions them.

## Prioritized findings and component decisions

Paths below are relative to `src/main/java/jeff/cherrypicking/` unless linked otherwise.

| ID / priority | Evidence and impact | Retain, simplify or replace |
| --- | --- | --- |
| E1 / P0 | **Verified:** no automated behavior tests. `build` can pass with `test NO-SOURCE`. All current safety nets are compilation, source review and manual checks. | Retain Gradle/CI. Introduce a small behavior suite alongside M1, then extend per subsystem. No generalized test framework or coverage-percentage target. |
| E2 / P0 | **Verified:** `client/config/ConfigFile.save` opens the destination directly with `newBufferedWriter`; no temp-file replacement. A write failure after opening can damage the previous config. **Hypothesis:** actual player data loss; not reproduced. | Replace the write procedure with complete serialization then same-directory temporary write/replace and explicit failure handling. Retain flat keys and format. M1. |
| E3 / P1 | **Verified:** `ConfigFile.apply` trusts Gson coercions and bypasses `Control` numeric bounds. `Setting.value` delegates; e.g. `Puzzles.drawDistance`, Blaze numeric setters and beams alpha setters store values directly. No uniform finite-number check. | Give saved-input decoding one explicit validation policy; preserve valid values and owner-field defaults. Test wrong shapes, overflow/non-finite values and numeric boundaries. Avoid a second settings framework. M1. |
| E4 / P1 | **Verified:** legacy fill migration only visits present saved colour keys and does not migrate beams' new fill value. **Hypothesis:** old partial configs or non-default beam opacity change appearance. Existing S6-03 remains OPEN. | Characterize documented legacy formats, decide absent-key/beam behavior using old fixtures, then repair narrowly. Do not invent support for every retired preference. M1. |
| E5 / P1 | **Verified:** `DungeonState.tick` on new non-null level clears only shared-locraw authority; prior sidebar/title/floor remain until poll, and no generation bump is guaranteed when floor/phase match. Downstream room/puzzle/mob state depends on generation. | Make level identity a run boundary, retaining tick order and Livid's separate fight reset semantics. Stale marks/entity-ID reuse are **hypothesized runtime effects**, especially if no null-level tick occurs. M2. |
| E6 / P1 | **Verified:** build finalizer installs into a live directory and uses a versioned filename despite a “fixed name” comment. Wrapper validation exists, but checksums/locks do not; Loom request is a snapshot. | Make verification/install boundaries explicit; choose one-owned-jar installation behavior and test in a temporary directory. Retain Gradle/Loom; investigate reproducibility before selecting a different version. M3. |
| E7 / P1 | **Player report:** `future-work.md` describes miniboss nametags disappearing beyond roughly 20 blocks; S3-08 is still OPEN. **Verified:** current detector requires the stand except hidden Fels. | Retain name-based detection for delivered stands. Capture evidence before adding an entity-only fallback. Room database and networking are not justified fixes yet. M9 investigation, then separate feature design if needed. |
| E8 / P2 | **Verified:** `RoomWatch` treats unloaded seams as empty and gives up after bounded retries; frame resolution stops retries. `Blaze` accepts the first chest in its scan and treats a one-to-zero stand count as solved. **Hypotheses:** late-load/partial-room false decisions. | Retain bounded scanning, coordinate transforms and neutral unknown-order behavior. Add recorded-input cases and repair demonstrated failures, not a dungeon-map rewrite. M4/M5. |
| E9 / P2 | **Verified:** UI drawing and hit-testing independently rebuild registry-derived cards/layout; most controls are manually handled rather than native focusable widgets. Tiny-window sizing and external close-save behavior lack checks. | Retain the custom screen and cohesive coordinator. Test interactions first; share a layout result only if it prevents a demonstrated mismatch or measured cost. Keyboard/narration limits need investigation. M7. |
| E10 / P2 | **Verified:** `Marks` copies its list but mesh/solution/mob arrays remain exposed; related state is published through separate volatiles. Theme comments overstate universal immediate recolouring. **Hypotheses:** mutation/race/stale-colour effects. | Preserve snapshot publication and explicit render sources. Tighten ownership only at a demonstrated mutation/thread boundary; do not add global locks or move world reads off-thread by default. M2/M8. |
| E11 / P2 | **Verified:** clean exit is a required version-sensitive mixin invoking `System.exit(0)` on `post-main`; no automated or logged runtime check covers the condition here. | Retain feature pending isolated process/target checks and player verification; do not broaden the hook or remove it solely because it uses a mixin. M10. |
| E12 / P2 | **Verified:** `CLAUDE.md` mentions YACL/placeholder and incomplete architecture; dungeon design mixes future/current features; old checks reference removed controls. | Use current architecture/inventory now. Later reconcile persistent instructions and README surgically. Preserve design rationale and every player record. M0/M3. |
| E13 / P3 | **Verified:** empty datagen entrypoint/config and unused `Settings.pretty`/`Solutions.reals` are future scaffolding. Generic loaders, sealed controls and rendering helpers already serve coherent boundaries. | Remove unused scaffolding only after checking task/metadata consumers. Retain simple local helpers; do not consolidate sibling mods or replace cohesive large files to meet size targets. M3 or the relevant subsystem. |

No FAIL/PARTLY verdict exists in the baseline, but “zero confirmed failures” would be misleading:
PASS notes S4-04/S4-07 reported timing/layout issues subsequently changed in code. Retests are
still OPEN. S3-12's timer concern was addressed by intentional removal, confirmed by S4-06 PASS.

Current check mapping: S5-01 remains the title timing retest. S6-01 supersedes the old opacity
layout/shared-fill expectations in S5-02/03; S6-02 supersedes shared-fill behavior in S5-04/05.
Use S6-01/02 for current per-colour behavior rather than asking players to find removed controls.
S4-09 is historical combined-opacity behavior; S6-01/02 cover its current replacement. S4-10
and S6-03 test different generations of migration. All old records remain unchanged.

## Milestones

All implementation milestones are **NOT STARTED**. Each should be a reviewable change around a
complete behavior. Split further if its evidence requires it; never mix unrelated rewrites.
For every milestone: run the affected behavior tests and non-installing build, inspect its diff,
update this log, and add new game checks only if an existing OPEN check does not cover the behavior.

| Milestone | Deliverable and completion boundary | Automated verification required | Outstanding game checks |
| --- | --- | --- | --- |
| M0 — audit and navigation | This plan, current architecture and uncovered manual checks; preserve working tree. **DONE, docs only.** | Documentation links, append-only check records, unchanged non-doc hashes. Fresh baseline results above. | New MOD-01–09 are OPEN; no game verification claimed. |
| M1 — reliable saved settings | Config round-trip/migration with explicit value validation and failure-safe replacement. Keep stable keys, enums, swatches, defaults and action exclusion. Introduce only the small IO/codec seam needed to test this behavior. | Current/legacy/partial/malformed fixtures; valid-value compatibility; reset/default capture; per-key failure isolation; unknown keys; missing files; boundary/non-finite inputs; simulated write/move failure leaves old file byte-identical; successful save/reload; runtime registry uniqueness. Wire suite to `check` and reject zero executed tests for this suite. | S4-10, S6-03, S6-01/02 for appearance, MOD-01/07 for screen integration. |
| M2 — isolated dungeon runs | New world clears prior gate/floor/room/solver/mob data before consumers run, even on same floor/coordinates. Preserve Livid's intentional within-fight identity lock and sidebar fallback. | Event sequences with null and direct non-null world replacement, same-floor runs, delayed sidebar/share, absent/malformed/stale share, boss transitions, forced mode, disable/re-enable. Assert no previous run's published marks survive. | MOD-02; retest S3-07 if reset behavior changes; S3-04 for M5 lock. |
| M3 — predictable build and install | Safe non-installing verification path and explicit installation behavior, with one intended CherryPicking artifact after upgrade. Reconcile build comments/instructions/README. Investigate immutable Loom resolution without gratuitous upgrades. | CI fresh JDK 25 build/test; task graph excludes install during verification; two-version install into temp directory leaves intended artifact and preserves other mods; failed build does not install stale artifact; jar metadata/resources/notices; wrapper/dependency integrity policy recorded. | MOD-01 after installed artifact changes. User checks actual launch/upgrade; installation is a later scoped action. |
| M4 — room geometry and loading | Preserve grid/rotation conventions and correct room membership while chunks arrive; resolve late-load/retry policy from evidence. No arbitrary room-name database. | All four round trips plus independently specified world-coordinate examples; doorway/seam, 1×1/1×2/L/2×2 fixtures; unloaded-to-loaded inputs; retry bounds; generation changes when consumer-visible room identity changes. | MOD-03, S3-08. |
| M5 — Blaze decisions | Establish reliable ordering, unknown-direction behavior, progress/reset and local-only announcement, including partial entity delivery. Retain manual override. | Recorded/synthetic labelled stands, formatting, commas, malformed/overflow health, both orders/ties, missing/ambiguous chest, delayed names, disappearance vs completion, teammate progression, reset/leave/re-entry and disabled output. Change completion heuristic only with justified evidence. | MOD-04. |
| M6 — Creeper Beams lifecycle | Verify signatures, pairing, deduplication, completion and reset across rotations and teammate changes; strengthen resource parsing only where needed. | Fixture-derived expected pairs in four frames, one/both endpoints dark, duplicate row, malformed data fallback, air-to-chest and already-solved entry, master/individual toggles, independent opacity, reset/re-entry. Tests use explicit expected coordinates, not the production transform to compute expectations. | MOD-05, S6-02. |
| M7 — settings interactions and themes | Preserve access/search/reset/preview behavior; address evidenced small-window, keyboard/focus and save-lifecycle problems. Retain coordinator/helpers unless a concrete shared responsibility needs extraction. | Registry/default contracts; search across sections and reset scope; escaping/focus event sequences where feasible; finite layout/scroll bounds; swatch parse/write/alpha/fill; all theme slots and fallback. Native input/narration still needs game verification. | MOD-01/07, S6-01/04/05; S4-10 if save timing changes. |
| M8 — Livid and rendering ownership | Preserve detection identity, box interpolation/overlap, no-fog visibility and title hold/fade. Clarify ownership of published data and colour refresh; fix only supported defects. | Wool-change/fallback/lock sequences; title at hold/fade boundaries using controllable time; disable/reset behavior; mesh bounds/winding and independent alpha; resource packaging. Confirm actual callback thread rules before concurrency changes. | S3-04, S5-01, S6-01/02, MOD-06; S3-03 log caveat. |
| M9 — mob association and limits | Protect current nametag association, room filtering, player exclusion, hidden-Fels semantics and bounded snapshots. Investigate distant miniboss evidence separately from adding a new detector. | Delayed/removed stands, crowded owner-ID/fallback cases, Withermancer offset, real-player exclusion, death, room transitions, duplicate/cap handling and hidden-Fels toggle. Deterministic fixtures for any proposed fallback before adopting it. | S3-08/10, MOD-09, S6-01/02. Missing nametag signal may leave a documented limitation pending a separately approved design. |
| M10 — normal shutdown compatibility | Verify the feature addresses clean shutdown without swallowing crash behavior; retain its opt-out and narrowly scoped hook. | Resolve injection target against the pinned jar; test dispatch on post-main vs other paths; if exercising exit, use a forked process to verify exit code/hooks. Never call `System.exit` inside the test runner. No fabricated production crash as a routine check. | MOD-08; repeat with the actual modpack when versions affecting shutdown change. |

Priority can advance a verified bug ahead of this order. M1 provides the first test setup; M2
should precede solver lifecycle changes. M4 provides room fixtures for M5/M6/M9. M3 need not wait
for gameplay changes. Pending in-game verification must remain visible across those dependencies.

## Test quality and automated rules

There are no existing automated tests to classify as useful or implementation-mirroring. The
manual checks mostly express observable behavior and have useful player notes, but some specify
obsolete UI paths or source line numbers. Preserve their evidence and use explicit retest links.

Tests should assert what a player/consumer relies on: old preferences survive, a failed save
preserves the old file, a new run has no old marks, expected targets/pairs are correct, and
optional integrations are absent-safe. Use small JSON/input fixtures and plain JUnit-style tests
with a minimal dependency selected at implementation time. Avoid loading a full Minecraft client
for codec/geometry tests where a narrow adapter suffices. Do not replace world behavior with
extensive mocks that merely encode today's private-call sequence.

Independently specified examples are essential: round-trip tests alone can miss two mutually
wrong transforms; tests counting private helper calls do not prove correct markers. Include
counterexamples and malformed input. Before merging a repair, demonstrate its regression test
fails against the defective behavior. No test quota, arbitrary coverage target or snapshot of
every implementation detail is required. GPU readability, server packet timing and modpack
shutdown still need the real client.

Practical checks to add with the relevant milestones:

- `check` executes the behavior suite and fails if that expected suite executes zero tests.
- Registry keys are unique; preserved key/enum fixture changes require a migration decision;
  saved outputs contain settings, never actions. Resource schemas allow provenance metadata.
- CI's verification task graph cannot write to live Minecraft directories. Artifact checks verify
  client metadata, adapters/mixin references, resource presence and expanded version.
- A small import/call check can flag sibling imports or outbound chat/input-automation APIs for
  review. Use narrow rules with reviewed exceptions; a text scan cannot prove display-only behavior.
- Keep wrapper validation. Evaluate checksums/locking and immutable tool versions during M3;
  do not bolt on an unrelated formatter, architecture framework or broad dependency upgrade.

## Proposed persistent instructions

Proposal for a short root instruction file, or a surgical reconciliation of `CLAUDE.md` after
review. **Not installed by this audit**; this avoids silently changing the existing working-tree
instructions or creating competing rule files. Detailed rationale belongs in these docs.

> Read `docs/architecture.md`, `docs/modernization-plan.md` and applicable in-game checks first.
> Preserve unrelated working-tree changes and the task's authorization boundary.
>
> Keep gameplay display-only: no movement, interaction, synthetic input or player-chat automation.
> Use local `Chat` messages and the optional shared locraw contract.
>
> Register tunables in `Settings`; keep defaults with their owners and keys/enum spellings stable.
> Treat saved formats and optional-mod entrypoints as compatibility contracts; test migrations.
>
> Keep explicit registration, client-thread world reads and complete published render snapshots.
> Prefer small behavior changes and removal of unnecessary machinery. Extract around ownership or
> tested boundaries, never a file-size target. Add dependencies only for demonstrated needs.
>
> Verify intended outcomes and failure cases. Run a non-installing build/check for code changes;
> install only within the current task's scope. Do not suggest `runClient` as Hypixel verification.
>
> Preserve all player verdicts/notes. Reuse OPEN checks, add stable retest IDs when behavior changes,
> and report code-complete separately from game-verified. Update the plan's next task and evidence.

Enforce keys/resources/tests/install boundaries automatically as described above. Display-only
intent, contract changes, behavioral test quality and player verdicts also require review.

## Progress, decisions and resumption

| Date | Progress / decision | Evidence and remaining work |
| --- | --- | --- |
| 2026-09-22 | M0 completed. Read-only audit preceded documentation edits. | Fresh build and resource/artifact checks passed; zero automated behavior tests; no game session. Non-doc files preserved. |
| 2026-09-22 | Retain registry, native screen, puzzle lifecycle, mark pipeline and optional adapters. | Existing explicit extension points already support current features. No evidence warrants a project rewrite or shared framework. |
| 2026-09-22 | Preserve old designs/verdicts; document current-vs-future and superseded checks here. | New checks MOD-01 through MOD-09 appended OPEN. Existing 32 checks unchanged. |
| 2026-09-22 | Recommend M1 first; implementation remains unauthorized in this task. | Config writes and decoding affect every feature and can be tested without Hypixel. M2 is the next cross-feature correctness priority. |

**Concrete next task, when implementation is requested:** start M1 by adding current and legacy
config fixtures and the smallest testable codec/path seam around `ConfigFile`, keeping its public
load/save entrypoints. First prove a successful round-trip and a failing save preserves the prior
bytes, then implement complete serialization plus safe replacement and validated decode. Include
`boxes.fillOpacity` with present/absent colour keys, explicit `/xx`, and beams' former shared-fill
behavior as separate cases; record the migration decision before changing expected results.
Wire these behavior tests into the existing build and use `-x installMod` for verification.

Before resuming, read `git status` and new player notes; do not reset or stage the user's unrelated
work. Record the changed behavior, command/results, relevant check IDs and remaining limitations
in this table. The rationale for starting at M1 is broad protection of player preferences with a
small boundary change and immediate behavioral evidence, without waiting on unverified server
heuristics or introducing new architecture.
