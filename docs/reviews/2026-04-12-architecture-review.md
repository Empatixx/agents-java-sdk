# Architecture Review — 2026-04-12

Audit zaměřený na **module isolation, maintainability, scalability a SDK API quality**. Proveden 4 nezávislými auditními agenty, konsolidováno + spot-checknuto proti skutečnému kódu.

Projekt: `agents-java-sdk`, 9 Maven modulů, 303 `.java` souborů (main), ~19 000 LOC.

---

## TL;DR

1. **🔴 Split-package antipattern:** `-core` a `-agent` sdílejí package prefix `cz.krokviak.agents.agent.*`. Nevyhovuje JPMS, mate IDE refactoring, skrývá skutečný dependency graph.
2. **🔴 Adapter moduly v core namespace:** `-anthropic` a `-openai` používají `cz.krokviak.agents.model.*` a `cz.krokviak.agents.tracing.*` — tzn. poskytovatelské třídy žijí v "core" package prefixu. Polluje jmenný prostor SDK.
3. **🔴 Javadoc coverage na public API prakticky nulová** (14.8 % v `-agent-api`, nižší v `-core`). `SessionInfo`, `CostSummary`, `TaskInfo`, `AgentEvent`, `EventBus`, `HookPhase` — nic.
4. **🟠 Synchronní SQLite write per-message** v `AgentRunner.saveSession()`. Při 100 uživatelích × 1 msg/s = 100 zápisů/s na single-connection DB. Škáluje jen do ~10-20 concurrent.
5. **🟠 God classes:** `MarketplaceManager` (535 LOC, 5 domén), `PluginLoader` (371, 4 domény), `CliController` (343, 11 state machines).
6. **🟠 Frontmatter parsing duplicated 3×** ve `SkillLoader`, `OutputStyleLoader`, `TipLoader` — identický YAML parse loop, kvarovčí-handling mírně odlišný.
7. **🟠 Streaming turn loop duplicated** v `AgentRunner` a `AgentSpawner.runLoop` — ~40 LOC překryvu, ale `AgentSpawner` **nemá** `StreamingToolExecutor` (eager parallel tool execution). Sub-agent ≠ hlavní agent v chování.
8. **🟠 `CompletableFuture.get()` bez timeoutu** v `PermissionManager` a `AgentTool` — UI deadlock když dialog visí.
9. **🟠 Layer-3 compaction dělá blocking `model.call()` v hot pathu** `AgentRunner.run()` — 5–30 s mrtvo per-turn při překročení 80 k tokenů.
10. **🟡 Sealed `AgentEvent` + `HookResult`** — přidání subtype = breaking change. Bez dokumentace chování při major bump.
11. **🟡 `Hooks.dispatchTyped` má `@SuppressWarnings("unchecked")` cast** — uživatel může registrovat `Hook<PreTurnEvent>` na `PRE_TOOL` a runtime crash.
12. **🟡 In-memory history list unbounded** v `AgentContext` — 10 k+ zpráv = O(n) každý compact/save cyklus.
13. **ℹ️ Test-coverage ratio (files):** core 0.30, agent 0.31, cli 0.19, agent-api 0.00, adaptery 0.00.

---

## 🔴 Critical findings

| # | Finding | File / lokace | Dopad | Návrh fixu |
|---|---|---|---|---|
| C1 | **Split-package: `cz.krokviak.agents.agent.*` je v `-core` (4 souborů: Agent, AgentBuilder, Prompt, ToolUseBehavior) i v `-agent` (AgentContext, engine, spawn, …)** | `agents-java-sdk-core/src/main/java/cz/krokviak/agents/agent/*` + `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/*` | JPMS-incompatible; v IDE je zmatek která class odkud; refactoring napříč moduly selhává; skrývá že `-core` definuje typy co `-agent` rozšiřuje | Přejmenovat core package na `cz.krokviak.agents.spec` (nebo `cz.krokviak.agents`); agent modul drží `cz.krokviak.agents.agent.*` solo |
| C2 | **Adapter moduly používají core namespace:** `AnthropicModel` v `cz.krokviak.agents.model`, `OpenAISpanExporter` v `cz.krokviak.agents.tracing` | `agents-java-sdk-anthropic/src/main/java/cz/krokviak/agents/model/AnthropicModel.java`, `agents-java-sdk-openai/src/main/java/cz/krokviak/agents/tracing/OpenAISpanExporter.java` + další 9 souborů | `model.*` a `tracing.*` vypadají jako core typy, ale jsou poskytovatelské implementace; uživatel neví kam kliknout pro "model" interface vs Anthropic konkrétně; možné duplicitní class names mezi adaptery | Přesunout do `cz.krokviak.agents.adapter.anthropic.model` a `cz.krokviak.agents.adapter.openai.{model,tracing}` |
| C3 | **`-agent-api` javadoc coverage ~14.8 %.** 10 DTO (`SessionInfo`, `CostSummary`, `TaskInfo`, `AgentInfo`, `TeamInfo`, `ModelInfo`, `PermissionRule`, `RunTurnRequest`, `RunTurnResult`, `HistorySnapshot`) má 0 class-level javadoc; `EventBus`, `Subscription` taky 0 | `agents-java-sdk-agent-api/src/main/java/**` | Zapojit další frontend (GraphQL, IDE plugin) vyžaduje číst source kód; IDE hover je prázdný | Přidat 2–3 věty class-level javadoc na všechny public DTO + interface; přidat `@param`/`@return` na `AgentService` metody (30+) |

## 🟠 High findings

| # | Finding | File / lokace | Dopad | Návrh fixu |
|---|---|---|---|---|
| H1 | **Synchronní DB write per-message** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/engine/AgentRunner.java:225-237` → `SQLiteSession.save()` | Blokuje turn loop o každém turnu; scale ceiling ~20 concurrent users | Batch queue (flush 5 s / 50 items) nebo async write thread |
| H2 | **Layer-3 compaction dělá blocking `model.call()` v hot pathu** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/context/ContextCompactor.java:66-71` používá `model.call()` synchronně v `CompactionPipeline.reactiveCompact()`; volá `AgentRunner.run()` při 80k+ tokenech | Turn zamrzne na 5–30 s během kompakce | Přesunout layer-3 na background post-turn thread; použít cached summary namísto re-call |
| H3 | **`CompletableFuture.get()` bez timeoutu** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/permission/PermissionManager.java:61-63`; `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/tool/AgentTool.java:63,66` | Pokud UI dialog visí, CLI deadlock bez záchrany | `.get(60, TimeUnit.SECONDS)` + timeout handler |
| H4 | **God class `MarketplaceManager` (535 LOC, 5 domén)** | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/plugin/marketplace/MarketplaceManager.java` | Jedna PR mění 8+ metod najednou; těžko testovat v izolaci | Split: `MarketplaceRegistry` + `PluginInstaller` + `MarketplaceReconciler` + `FileOps` (~100 LOC každé) |
| H5 | **God class `PluginLoader` + inner `CommandHook` (371 LOC)** | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/plugin/PluginLoader.java` (CommandHook inner řádky 280-371, 100 LOC sám o sobě) | Plugin discovery + command loading + hook parsing + subprocess spawn v jednom souboru | Vyextrahovat `SkillCommandLoader`, `HookParser`, `CommandHook.java` (own file + tests) |
| H6 | **God class `CliController` (343 LOC, 11 state machines)** | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/render/tui/CliController.java` | `setPermissionPrompt()` clears `multiQuestions` jako side-effect; přidat file picker dialog = další státní bucket | Split do `OutputState`, `PromptState` (union type), `AgentState`, `PlanState`; CliController agreguje |
| H7 | **Markdown frontmatter parsing duplicated 3×** s drift rizikem | `agents-java-sdk-cli/.../skill/SkillLoader.java:85-121` + `.../style/OutputStyleLoader.java:68-95` + `.../tips/TipLoader.java:85-110` | Quote-stripping se mírně liší (`"` vs `'` handling); bugfix potřebuje 3 edity; přidání nového metadata klíče = 3 změny | Extrahovat `cz.krokviak.agents.agent.util.FrontmatterParser` (public v `-agent-api`? nebo interně v `-agent`); tři loadery ho volají |
| H8 | **Streaming turn loop duplicated** v `AgentRunner` a `AgentSpawner`, sub-agent **nemá** eager tool execution | `agents-java-sdk-agent/.../engine/AgentRunner.java:80-119` vs `.../spawn/AgentSpawner.java:174-207` | Sub-agent bez parallel tool execution = pomalejší; bugfix v streaming loop = 2 PR; behavior drift | Vyextrahovat `StreamingTurnEngine` helper; oba delegují; strategy pattern pro tool execution |
| H9 | **CliContext importuje 5 manager tříd z `-agent`** | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/CliContext.java` (imports: `AgentContext`, `ContextCompactor`, `MailboxManager`, `PermissionManager`, `TaskManager`) | CLI obchází `AgentService` kontrakt; protokol Phase-1 decoupling je částečně neúplný | Zachovat přímé deps jen pro DTO/enum; manager přístup přes `ctx.agent()` kde lze; dokumentovat zbytek jako "bootstrap-only escape hatch" |
| H10 | **Hardcoded adapter ve výběru modelu** | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/CLI.java` — provider switch `ANTHROPIC` vs `OPENAI` přes compile-time závislost; `-agent` závisí na `-anthropic` | Přidání nového providera = upravit `-agent` + `-cli`; nelze runtime plugin | Provider registr v `-core`; adaptér modul registruje `Model.Factory` service; CLI vybírá dle config |

## 🟡 Medium findings

| # | Finding | File / lokace | Dopad | Návrh fixu |
|---|---|---|---|---|
| M1 | **Sealed `AgentEvent` + `HookResult` bez BC policy** | `agents-java-sdk-agent-api/src/main/java/cz/krokviak/agents/api/event/AgentEvent.java`, `.../hook/HookResult.java` | Přidání subtype = breaking pro `switch(event) { ... }` exhaustive statements u uživatelů | Dokumentovat "subtypes jsou frozen do major bump" + jak bezpečně přidávat `default` větve v user code |
| M2 | **`Hooks.dispatchTyped` unchecked cast** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/hook/Hooks.java:40-45` (`@SuppressWarnings("unchecked") ((Hook) h).execute(event)`) | `Hook<PreTurnEvent>` registrovaný na `PRE_TOOL` compile OK, runtime `ClassCastException` | Typed phase-event pairing: `<E> register(HookPhase<E>, Hook<E>)` s `HookPhase` jako sealed hierarchy s type parameter |
| M3 | **In-memory `history` list unbounded** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/AgentContext.java` field `history = Collections.synchronizedList(new ArrayList<>())` | 10 k+ zpráv = O(n) compact/save; `List.copyOf(history)` v každém turnu alokuje | Cap na 5k items (ring buffer) nebo eviction policy s CompactionMarker |
| M4 | **`DefaultEventBus` synchronní dispatch** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/event/DefaultEventBus.java:31-49` | 50+ listenerů × 100 events/turn = 5000 invocations blokujících emit thread | Virtual thread per listener; nebo queue + single consumer thread; přidat queue-depth metriku |
| M5 | **Magic numbers bez config (30 s summary, 8192 max tokens, 200 char truncate, 120000 ms tool timeout)** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/spawn/AgentSpawner.java:30,65,105,175`; `AgentRunner.java`; `PlainRenderer.java:23` | Nelze tune pro slow network, long-form output; 200-char truncate je 3× (2× "..." 1× bez) | Centralizovat v `AgentDefaults` + `RuntimeConfig` loadable z ENV/config |
| M6 | **No retry/backoff na model 429** | `agents-java-sdk-anthropic/src/main/java/cz/krokviak/agents/model/AnthropicModel.java:29-41`; `agents-java-sdk-openai/.../OpenAIOfficalModel.java` | Transient rate limit = turn fail; user musí ručně retry | 3-tier retry s jitter (50/100/200 ms); expose config v `ModelSettings` nebo per-model-adapter |
| M7 | **`setPermissionPrompt(...)` má implicit side-effect** (nulluje `multiQuestions`) | `agents-java-sdk-cli/src/main/java/cz/krokviak/agents/cli/render/tui/CliController.java` | Komponenty vyvolávající prompt musí vědět, že zruší jiné dialogy; bugs při překrývajících se promptech | Union type `PromptState` (sealed) — `None \| TextInput \| Permission \| MultiQuestion \| FilePicker`; setter vynucuje explicitní transition |
| M8 | **Inconsistent return types v `AgentService`** (13× `void`, 6× `CompletableFuture<T>`) | `agents-java-sdk-agent-api/src/main/java/cz/krokviak/agents/api/AgentService.java` | Volající nepozná fire-and-forget vs async; chybí error propagation | Standardizovat: write ops → `CompletableFuture<Void>`; pure reads `sync` je OK, dokumentovat |
| M9 | **`ScheduledExecutorService summaryScheduler` daemon thread** | `agents-java-sdk-agent/src/main/java/cz/krokviak/agents/agent/spawn/AgentSpawner.java:37-42` | Neurčité chování na shutdown; pending summary task se zahodí uprostřed I/O | Non-daemon + explicit `shutdown()` v SESSION_END hook |
| M10 | **Test coverage ratio `-cli` 0.19, `-agent-api` 0.00** | `agents-java-sdk-cli/src/test/java/` má 25 souborů / 131 main; `-agent-api` žádné | TUI + plugin integrace fragile; DTO contract rizika neviděná | Tier 1: `SpinnerControl`, `PromptHandling`, `EventEmission`. Target cli ratio 0.35; agent-api aspoň contract tests |

## 🔵 Low findings

| # | Finding | Lokace | Návrh |
|---|---|---|---|
| L1 | Inner record `CommandHook` má 100+ LOC, nested pattern matching, subprocess spawn | `PluginLoader.java` | Extract do vlastního souboru + `CommandHookTest` |
| L2 | String truncation `if (s.length() > 200) ... + "..."` duplicated 3× | `AgentSpawner.java:65,105`, `PlainRenderer.java:23` | `StringUtils.truncate(s, max, suffix)` util |
| L3 | `ModelSettings` má Builder; `ThinkingConfig`, `Usage` jsou prosté records — nekonzistence | core | Decide pattern; ThinkingConfig má factory methods `on()/off()` — OK, ale dokumentovat |
| L4 | `InputItem.ImageContent` má dual constructor (s/bez description) | `agents-java-sdk-core/src/main/java/cz/krokviak/agents/runner/InputItem.java:10-13` | Inline default v primary ctor nebo static factory |
| L5 | `HookPhase` enum — nelze přidat variant v minor | `agents-java-sdk-agent-api/.../HookPhase.java` | Dokumentovat "breaking change on new phase" |
| L6 | 5 `TODO/FIXME/HACK` comments napříč kódem | grep main | Audit každého: fix nebo smazat |

## ℹ️ Info findings

| # | Finding | Poznámka |
|---|---|---|
| I1 | Deprecated `CliEvent` + `CliEventBus` (forRemoval=true) — plán odstranění? | V1.0? V2.0? Bez target date zůstanou forever |
| I2 | Unused DTO `CostSummary`, `SessionInfo`, `TaskInfo`, `TeamInfo` — kromě `AgentService` list metod nikdo nevolá | OK v API contract ale dokumentovat "reserved for Phase 2" |
| I3 | `-core` má 80 souborů / 2.6k LOC — healthy size | Keep strict, don't bloat |
| I4 | Není Maven enforcer plugin — nic nevynucuje "no dep on cli" z agent | Volitelné: `maven-enforcer` `bannedDependencies` rule |

---

## Per-module cards

### `agents-java-sdk-core` — 🟡 B+
**Purpose:** Framework primitivy (Model, Session, Tool, InputItem, Runner, hook API).
**Strengths:** Minimální deps (Jackson + SLF4J); record-based DTOs; sealed types; 80 souborů / 2.6k LOC — healthy.
**Weaknesses:** 4 soubory v `cz.krokviak.agents.agent.*` (Agent, AgentBuilder, Prompt, ToolUseBehavior) — **split-package s `-agent` modulem** (🔴 C1). Javadoc density nízká.
**Action:** Vyřešit split-package (přejmenovat core namespace nebo přesunout Agent do `-agent` module).

### `agents-java-sdk-agent-api` — 🟠 B-
**Purpose:** UI-agnostic kontrakt pro frontendy (AgentService + events + DTO + hooks).
**Strengths:** ✓ Čistá závislost jen na `-core`; sealed AgentEvent; typed hook events; 25 souborů.
**Weaknesses:** **Javadoc 14.8 %** (🔴 C3); sealed AgentEvent bez BC policy (🟡 M1); nulové testy.
**Action:** Dokumentovat public API; přidat contract tests (např. že `AgentServiceImpl` splňuje `AgentService` metody bez `UnsupportedOperationException` pro stable subset).

### `agents-java-sdk-agent` — 🟡 B
**Purpose:** Engine implementace (AgentRunner, ToolDispatcher, AgentServiceImpl, managers).
**Strengths:** Konzistentní streaming pattern (po poslední fázi); event bus dispatch; hook lifecycle.
**Weaknesses:** `AgentRunner` + `AgentSpawner.runLoop` duplikované (🟠 H8); blocking `model.call()` v compact hot path (🟠 H2); split-package s `-core` (🔴 C1); hardcoded Anthropic dep.
**Action:** Extract `StreamingTurnEngine`; přesunout layer-3 compaction async; provider registry.

### `agents-java-sdk-cli` — 🟠 C+
**Purpose:** TUI + commands + plugins + marketplace + MCP + tools (131 souborů, 10k LOC).
**Strengths:** Rozumná decomposition (render/ commands/ tools/ plugin/ tips/ style/); používá AgentService contract pro většinu operací.
**Weaknesses:** 3 god classes (🟠 H4, H5, H6); markdown parsing duplicated 3× (🟠 H7); 5 direct imports z `-agent` v `CliContext` (🟠 H9); test coverage 0.19.
**Action:** Split 3 god classes; extract `FrontmatterParser` utility; target test coverage 0.35.

### `agents-java-sdk-anthropic` — 🟠 C+
**Purpose:** Anthropic Claude model adapter.
**Strengths:** Streaming impl; vision support; extended thinking; 4 soubory, compact.
**Weaknesses:** **Package `cz.krokviak.agents.model.*`** (🔴 C2); žádná retry/backoff logika (🟡 M6); 0 test souborů v modulu.
**Action:** Přesunout do `cz.krokviak.agents.adapter.anthropic.*`; přidat retry + tests.

### `agents-java-sdk-openai` — 🟠 C+
**Purpose:** OpenAI model adapter (ChatCompletions + Responses API).
**Strengths:** Dva modely (Chat + Responses); 1k LOC, 6 souborů.
**Weaknesses:** **Package `cz.krokviak.agents.model.*`** + `OpenAISpanExporter` v `.tracing.*` (🔴 C2); 0 testů.
**Action:** Přesunout namespace; otestovat aspoň happy-path mocked HTTP.

### `agents-java-sdk-mcp` — 🟡 B
**Purpose:** MCP protokol implementace (client side).
**Strengths:** Vlastní package `cz.krokviak.agents.mcp.*` — ✓ clean; 8 souborů.
**Weaknesses:** 1 test soubor (MCPTest, 295 LOC — jeden bratr testů).
**Action:** Rozdělit `MCPTest` do 3-4 focused test tříd.

### `agents-java-sdk-sessions` — ✓ A-
**Purpose:** Session storage (SQLite, Encrypted, Advanced).
**Strengths:** ✓ Clean wrapper nad core `Session` interface; 5 souborů / 3 testy (0.60 ratio — nejlepší v projektu).
**Weaknesses:** `AdvancedSQLiteSession` listSessionsWithMetadata() je O(n), chybí pagination (🟠 H1 související).
**Action:** Pagination + index cache.

### `examples` — ℹ️ N/A
5 souborů, 217 LOC. Demo/sanity snippety, neškodí.

---

## Metriky

| Module | Main files | LOC | Test files | Public classes | Test ratio |
|---|---|---|---|---|---|
| `-core` | 80 | 2 566 | 24 | 80 | 0.30 |
| `-agent-api` | 25 | 427 | 0 | 25 | **0.00** |
| `-agent` | 39 | 2 908 | 12 | 39 | 0.31 |
| `-cli` | **131** | **9 932** | 25 | 131 | 0.19 |
| `-anthropic` | 4 | 595 | 0 | 3 | **0.00** |
| `-openai` | 6 | 1 068 | 0 | 6 | **0.00** |
| `-mcp` | 8 | 630 | 1 | 8 | 0.13 |
| `-sessions` | 5 | 656 | 3 | 5 | **0.60** |
| **Total** | **298** | **18 782** | **65** | **297** | **0.22** |

TODO/FIXME/HACK: **5 výskytů** napříč main code.

---

## Follow-up priority

### Quick wins (< 1 h každý)
- **F1:** Přidat class-level javadoc na 10 DTO v `-agent-api` (🔴 C3 partial fix)
- **F2:** `StringUtils.truncate` util + refactor 3 call sites (🔵 L2)
- **F3:** `.get(60, TimeUnit.SECONDS)` v `PermissionManager`, `AgentTool` (🟠 H3)
- **F4:** Non-daemon `summaryScheduler` + shutdown hook (🟡 M9)
- **F5:** Document sealed subtype BC policy v CONTRIBUTING.md (🟡 M1)

### High-impact (1–3 dny)
- **I1:** Extract `FrontmatterParser` util + migrace 3 loaderů (🟠 H7)
- **I2:** Extract `StreamingTurnEngine` + konsolidace AgentRunner/AgentSpawner loopu (🟠 H8)
- **I3:** Split `CliController` do `PromptState` union + substates (🟠 H6)
- **I4:** Asynchronous session save (batch queue) (🟠 H1)
- **I5:** Layer-3 compaction async (🟠 H2)
- **I6:** Model retry/backoff s jitter (🟡 M6)

### Structural (1–2 týdny)
- **S1:** Vyřešit split-package — přejmenovat core namespace nebo přesunout Agent/AgentBuilder do `-agent` (🔴 C1)
- **S2:** Přesunout adapter classes do `cz.krokviak.agents.adapter.{anthropic,openai}.*` (🔴 C2)
- **S3:** Split `MarketplaceManager` + `PluginLoader` na menší třídy (🟠 H4, H5)
- **S4:** Typed Hook phase-event pairing (eliminovat unchecked cast) (🟡 M2)
- **S5:** Add Maven enforcer — ban cross-module deps (`-agent` NE import `-cli`, atd.) (ℹ️ I4)
- **S6:** Provider registry — adapter jako pluggable service (🟠 H10)

### Accept-and-monitor
- 5 TODO/FIXME comments (ℹ️ I1, fix incrementally)
- Unused DTOs (ℹ️ I2) — OK v `-agent-api` reserved kapacitě
- `-mcp` a `-sessions` test coverage — acceptable current level
- Memory store lazy-load optimization (🔵) — premature

---

## Overall assessment

**Grade: B- (solid foundation, growing pains).**

Architektura je **v jádru zdravá**: moduly mají jasné role, `-agent-api` izolace drží, event-driven lifecycle dobře decoupluje UI od engine, AbortSignal + hook phases jsou idiomatické. Nedávné feature batches (output styles, thinking, hooks) se přidávaly čistě — dobrá známka škálovatelnosti designu.

**Hlavní bolesti jsou growth-related:**

1. **Namespace discipline** kulhá za strukturálním růstem. Modul `-agent` vznikl pozdě (Phase 2) a zdědil split-package s `-core`. Adapter moduly byly vytvořené před jasnou "kam s nimi" konvencí a zůstaly v core namespace. **Řešení:** jednorázový rename (3 × S1/S2) — má to být začátek before v1.0.
2. **God classes v `-cli`** vznikly organicky přidáváním featur. Marketplace + plugin loader + CliController **chtějí split** — ne refactor ale nutnost pro další práci.
3. **Blocking calls v hot pathech** (SQLite write, layer-3 compact, CompletableFuture.get()) = scale ceiling kolem 20 concurrent users. Pro single-user CLI je dnes OK, pro GraphQL backend (co je cílem Phase 1 decouplingu!) **nestačí**.
4. **Javadoc gap** je největší "před-v1.0" položka. Bez `@param/@return/@throws` na public API není projekt připravený jako SDK pro externí konzumenty.
5. **Test coverage** je uneven: `-sessions` je vzor (0.60), `-cli` propadá (0.19). Ne všude stejně důležité, ale `-agent-api` by mělo mít aspoň contract tests.

**Doporučená posloupnost:**

1. **Quick wins F1–F5** (~1 den) — low-risk, okamžitý value.
2. **High-impact I1–I3** (~1 týden) — odblokuje další featury bez technical debt.
3. **Structural S1–S2** (~1 týden) — namespace cleanup **před v1.0 tagem**.
4. **Scalability I4–I5** (~1 týden) — nutné před GraphQL backend ostrou.

Není nic v review co by bylo "stop-work blocker". Je to technical debt co se dá strategicky zahasit v průběhu příštích 2–3 týdnů.
