# Contributing

Welcome. This doc covers the constraints that guide how code lands in this
repo. Read it end-to-end if you're modifying the SDK contract — things
like `AgentEvent`, `HookPhase`, `AgentService`, or anything in
`agents-java-sdk-agent-api/`.

## Module layout

| Module | Role | Dependencies |
|---|---|---|
| `agents-java-sdk-core` | Framework primitives: `Model`, `Session`, `Tool`, `InputItem`, `Runner` | Jackson, SLF4J |
| `agents-java-sdk-agent-api` | UI-agnostic contract: `AgentService`, `AgentEvent`, `EventBus`, `Hook`, DTOs | core |
| `agents-java-sdk-agent` | Engine: `AgentRunner`, `ToolDispatcher`, `AgentServiceImpl`, managers | core, agent-api, sessions, anthropic |
| `agents-java-sdk-cli` | TUI + commands + plugins + marketplace + MCP | everything above |
| `agents-java-sdk-anthropic` | Anthropic model adapter | core |
| `agents-java-sdk-openai` | OpenAI model adapter | core |
| `agents-java-sdk-sessions` | Persistent session storage (SQLite) | core |
| `agents-java-sdk-mcp` | MCP protocol implementation | core |

Hard rules — enforced by `ArchitectureBoundariesTest` in CI:

- `agents-java-sdk-core` does not depend on `-cli`, `-agent`, `-mcp`.
- `agents-java-sdk-agent-api` depends only on `-core`.
- `agents-java-sdk-agent` does not depend on `-cli`.
- No module depends on TUI libraries (`tamboui`, `jline`) except `-cli`.

## Backwards-compatibility policy

### Sealed hierarchies are frozen between majors

`AgentEvent` and `HookResult` (in `agents-java-sdk-agent-api`) are sealed
interfaces. Adding a new record subtype is a **breaking change** for any
downstream code that uses exhaustive pattern matching:

```java
// This breaks when a new variant arrives without a default.
switch (event) {
    case AgentEvent.ToolStarted e -> ...
    case AgentEvent.ResponseDelta e -> ...
    // ... all known variants ...
}
```

Rules:

1. **Between minor versions**, do not add new subtypes to sealed public
   interfaces. Collect candidates and release them together in the next major.
2. **Downstream guidance**: always include a `default -> {}` or
   `default -> { /* ignore unknown */ }` branch when matching public
   sealed types. This makes your code forward-compatible.
3. **When a new subtype ships in a major**, mention it explicitly in the
   changelog and migration notes.

### `HookPhase` enum additions are breaking

`HookPhase` is a plain enum. Adding a value breaks any exhaustive
`switch` on it. Same rules as sealed types apply — batch new phases
into majors, document in changelog.

### Record component additions break consumers

Adding a field to a public `record` changes its canonical constructor
signature. Don't do it between minors. If you need a new field, prefer a
new record and convert via a builder.

### `@Deprecated(forRemoval = true)` carries a deadline

Flagging something for removal is a promise. Give it a target version
(e.g. `// removal target: v2.0`) in the javadoc. Remove on that version;
don't let deprecated things accumulate indefinitely.

## Tests

- New features require tests. Look at `FrontmatterParserTest` /
  `RetryingModelTest` for the expected density: every behavioural branch
  gets a case, including the edge cases that would silently break in
  production.
- Use ArchUnit (`ArchitectureBoundariesTest`) for structural invariants —
  don't replicate runtime checks there.
- `mvn clean verify` must pass. CI enforces.

## Commit format

Use conventional commits with a finding-reference tag when applicable:

```
refactor(H7): extract FrontmatterParser — end 3× duplication
feat(M6): RetryingModel decorator — exponential backoff
fix(M3): add missing HISTORY constants to AgentDefaults
docs: architecture review — maintainability + scalability + isolation audit
```

The finding tag (e.g. `H7`, `M6`) refers to
`docs/reviews/2026-04-12-architecture-review.md` rows so the commit
trail is traceable back to a documented concern.

## Where things live

- **Utilities used by many modules** → `agents-java-sdk-core/cz.krokviak.agents.util`
  (`StringUtils`, `FutureTimeouts`).
- **Engine-only utilities** → `agents-java-sdk-agent/cz.krokviak.agents.agent.util`
  (`FrontmatterParser`). Tests live in the same module.
- **Adapter classes** → `agents-java-sdk-<adapter>/cz.krokviak.agents.adapter.<name>.*`
  (never the `model.*` root — that's reserved for core types).
- **CLI features** → `agents-java-sdk-cli/cz.krokviak.agents.cli.<feature>/*`.

## Architecture reviews

Big refactors start with an architecture review in
`docs/reviews/YYYY-MM-DD-<topic>.md` — findings by severity, actionable
fixes, priority matrix. See the
[2026-04-12 review](docs/reviews/2026-04-12-architecture-review.md)
for format.

## Running locally

```bash
./mvnw clean verify
./mvnw -pl agents-java-sdk-cli exec:java
```
