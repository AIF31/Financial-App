## Agent skills

### Development environment

The canonical development checkout is the Windows project at
`$env:USERPROFILE\StudioProjects\Financial-App`. Run builds, tests, and
source edits there. Treat WSL copies as reference-only unless the user explicitly
requests WSL development.

### Physical-device testing

Before any test or install on a physical Android device, read
`docs/agents/physical-device-testing.md` and follow its personal-data
preservation procedure.

### Issue tracker

Issues and specs are tracked in GitHub Issues for AIF31/Financial-App. See `docs/agents/issue-tracker.md`.

### Triage labels

The repository uses the five default mattpocock/skills triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository. See `docs/agents/domain.md`.

### Model delegation

- Before spawning an agent, read `.agents/skills/handoff/SKILL.md` and use its guidance to prepare the delegated context.

### For gpt-6-astra model:
- Delegate documentation changes to `gpt-5.6-luna max`.

## graphify

Local checkouts may have a generated knowledge graph at `graphify-out/`. Its output and local skill installation are ignored by Git.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json` exists and the command is available. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` when the command is available to keep the local graph current (AST-only, no API cost).
