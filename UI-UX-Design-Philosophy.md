# Pocket UI/UX Design Philosophy

> Durable design contract and implementation template for Pocket's Android UI.
>
> Status: approved direction for future UI/UX improvement work  
> Scope: presentation, interaction, navigation, accessibility, adaptive layout, and UI verification  
> Product behavior authority: Issue #1 acceptance criteria, the implemented domain model, and existing automated tests

## 1. Purpose

This document is the reusable UI/UX philosophy for Pocket. It converts the original implementation handoff, the current application, the installed skill guide, and the completed design grill into one durable contract.

Use it when planning, prototyping, implementing, reviewing, or testing any user-facing change. It is intentionally more stable than a one-time task handoff. A future implementation handoff should reference this document and describe only the slice-specific delta.

This document supersedes stale current-state observations in `/tmp/financial-app-implementation-handoff.md`. That handoff remains useful for original product scope, exclusions, domain language, and test seams, but the Android app is now implemented.

## 2. Decision hierarchy

When instructions overlap, resolve them in this order:

1. Explicit user decisions for the current task.
2. Product behavior and acceptance criteria in Issue #1.
3. Canonical domain language and invariants in `CONTEXT.md`.
4. Existing tests that protect accepted behavior.
5. This UI/UX philosophy.
6. The task-specific implementation handoff.
7. The narrowest applicable skill instructions.
8. General framework conventions.

A UI skill may improve presentation and interaction, but it must not silently change financial behavior, domain meaning, or an explicit product decision.

## 3. Experience thesis

Pocket should feel calm, trustworthy, and direct. It is a personal spending tool, not a trading terminal, bank dashboard, or decorative analytics showcase.

Every important surface should help the user answer one of three questions:

1. How much can I safely spend?
2. Where is my money allocated?
3. What should I do next?

The product should make routine capture fast, make the current financial state understandable, and keep uncommon controls available without placing them in the primary path.

### Priority order

1. Make daily expense capture exceptionally fast.
2. Make the dashboard actionable rather than merely informative.
3. Make Pocket allocation and progress easy to compare and manage.
4. Make movement history easy to search, filter, understand, and correct.
5. Keep configuration, backup, and restoration reliable without competing with daily work.

## 4. Product language

Use the canonical terms consistently in UI copy, semantics, tests, and code:

- Pocket
- Pocket availability
- Budget period
- New funds
- Pocket budget
- Rollover
- Movement
- Payment method
- Conversion status

Do not casually substitute category, envelope, account, wallet, bank balance, or transaction when the domain means Pocket or Movement. New terminology requires a domain review before it enters production UI.

Copy should be short, concrete, and nonjudgmental. Financial warnings should explain the state and the next available action without scolding the user.

## 5. Visual philosophy

The default visual character is calm and trustworthy with restrained, warm Material 3 Expressive accents.

### Principles

- Use semantic theme roles, not isolated hard-coded colors.
- Give numbers a clear hierarchy: availability and entered amount dominate; supporting metrics recede.
- Use color to reinforce meaning, never as the only carrier of meaning.
- Prefer whitespace, grouping, typography, and alignment over a stack of identical cards.
- Use elevation and shape to clarify hierarchy, not to decorate every container.
- Keep positive, warning, error, and neutral financial states distinguishable in light and dark themes.
- Avoid glass effects, novelty gradients, dashboard clutter, and stock-finance visual tropes.
- Prefer stable Material and Compose APIs. Experimental APIs require an explicit, documented benefit.

Expressive treatment belongs at moments of intent or accomplishment, such as saving a movement, completing onboarding, creating a period, or reaching meaningful Pocket progress. Routine navigation and data reading should remain quiet.

## 6. Navigation contract

The existing bottom navigation remains as implemented, with these four root destinations:

- Inicio
- Movimientos
- Pockets
- Ajustes

Do not remove, reorder, rename, or relocate these destinations as part of the UI improvement program. In particular, Ajustes remains in the bottom bar.

Navigation improvements may introduce subordinate routes for focused tasks such as quick entry, movement detail, Pocket editing, or restoration. Use Navigation 3 only where route ownership, state restoration, back behavior, or testing gains are observable. A framework migration is not a design outcome by itself.

### Navigation behavior

- Root destination state should survive switching between bottom-bar items.
- Back from a subordinate task returns to the correct originating surface.
- Back from a root destination follows Android platform expectations.
- The launcher shortcut and the in-app expense action open the same quick-entry route.
- Partially entered safe state should survive configuration changes and ordinary navigation where practical.
- Navigation actions need stable semantics for tests and accessibility.

## 7. Screen blueprints

### 7.1 Quick expense entry

Expense capture becomes a dedicated focused route rather than a dense all-fields-at-once dialog.

The immediate surface contains:

- Amount
- Recent or preferred Pocket
- Save action

The secondary surface contains:

- Merchant
- Payment method

A clearly labeled "More details" disclosure contains:

- Movement type or refund handling
- Foreign currency and conversion information
- Date and time
- Note

The in-app action and launcher shortcut must open this same route and preserve the same validation and save behavior.

#### Interaction requirements

- Amount entry receives deliberate initial focus without causing layout jumps.
- The primary action remains reachable with the keyboard open.
- Pocket and payment-method selection expose semantic selected state.
- Validation appears near the relevant field and is announced accessibly.
- Advanced fields do not block the common case.
- Dismissing or navigating back must not accidentally save.
- Save success is clear and brief; it does not delay the user's return to context.
- Existing refund, conversion, date/time, note, and payment behavior remains intact.

### 7.2 Dashboard

The dashboard is actionable. Its hierarchy is:

1. Pocket availability and unallocated money
2. Current spending status or comparison
3. Pocket progress and the most useful next actions
4. Projection and supporting metrics under progressive disclosure

The dashboard should lead naturally to recording an expense and managing a relevant Pocket. Avoid presenting every metric with equal weight or placing every item in an identical card.

Dashboard values must come from the same domain calculations used elsewhere. The UI must not reimplement financial rules.

### 7.3 Pockets

The Pockets surface supports quick comparison of:

- Budget
- Rollover
- Spending
- Availability
- Progress

Preserve create, edit, archive, reorder, allocation, and rollover behavior. Reordering needs an obvious affordance and accessible alternative. Editing should use a focused task surface when a dialog becomes crowded.

A Pocket's visual status must remain understandable without relying on color alone.

### 7.4 Movements

Movements should optimize recognition and correction.

- Replace opaque filter cycling with explicit controls.
- Show active filters and provide an obvious clear action.
- Give each row a stable hierarchy: amount and merchant or note first, then Pocket, payment method, date, and conversion status as relevant.
- Preserve search, combined filters, edit, delete, and undo behavior.
- Use a detail route or sheet when the row cannot present complete information cleanly.
- Keep destructive actions deliberate and recoverable where current behavior allows.

### 7.5 Settings

Ajustes remains a root bottom-bar destination. Within it, group controls into recognizable sections and use subordinate task surfaces for dense workflows:

- Budget period and funds
- Reminder
- Payment methods
- Templates
- Backup, restore, and CSV

Backup and restore must communicate scope, consequences, success, and failure clearly. They are reliability workflows, not secondary decoration.

### 7.6 Onboarding and restore

Onboarding should establish the minimum viable financial setup with clear progress and recovery. Restoration must remain discoverable where the existing product permits it. Completion may use a warmer expressive moment, but instructions and validation remain calm and precise.

## 8. App shell and layout

- Keep edge-to-edge behavior and verify every screen against system bars, IME, and gesture navigation.
- Use top app bars for subordinate routes or when they add meaningful title and navigation context.
- Place the primary daily action within comfortable thumb reach without hiding bottom navigation.
- Use a coherent 4 dp / 8 dp spacing rhythm.
- Maintain at least 48 dp interactive targets unless a documented platform component provides equivalent usability.
- Support large font scaling without clipping essential values or actions.
- Avoid fixed widths that only fit one phone.
- Keep dense metadata secondary to the decision or action the screen supports.

## 9. Adaptive strategy

Optimize first for the Samsung S23 class of phone while building adaptive foundations.

Every critical prototype and screen should be reviewed at:

- A representative phone size
- A representative tablet size
- Light theme
- Dark theme
- Large font scale where risk is high

Do not add a full multi-pane tablet architecture during the first improvement pass. Tablet layouts should remain usable and intentional through width-aware spacing, sensible content bounds, and component reflow. Introduce multi-pane navigation only when a later task establishes a concrete workflow benefit.

## 10. Motion philosophy

Motion explains state change; it does not compete with financial information.

Use restrained standard motion for:

- Route transitions
- Expanding "More details"
- Filter and selection changes
- Delete/undo feedback
- Content appearance after loading or save

Reserve warmer expressive motion for:

- Successful movement save
- Onboarding completion
- Budget-period creation
- Meaningful Pocket progress

Honor reduced-motion expectations. Avoid continuous motion, decorative bouncing, or animation that delays input.

## 11. Accessibility contract

Accessibility is part of each slice, not a final audit.

Every production UI change must consider:

- Content descriptions only where visual content is not already named by text
- Role, selected state, enabled state, error state, and progress semantics
- Logical traversal and focus order
- TalkBack announcements for validation and meaningful success or failure
- 48 dp target sizing
- Contrast in light and dark themes
- Non-color indicators for financial states
- Keyboard and IME behavior
- Large-font layout resilience
- Focus preservation on navigation and dynamic content

Automated accessibility checks supplement, but do not replace, manual TalkBack review of the critical flow.

## 12. Component philosophy

Split the current monolithic UI along stable product boundaries, not arbitrary file length.

Prefer:

- An app shell that owns root navigation and global actions
- Screen composables that accept immutable UI state and event callbacks
- Focused task composables for quick entry, editing, restoration, and similar flows
- Reusable components only when multiple real call sites share behavior or visual policy
- A `Modifier` parameter on reusable Compose components
- Slot APIs only where callers genuinely need structural variation
- Previewable states that do not require databases, navigation controllers, or Android services

Avoid:

- Domain calculations in composables
- Hidden state ownership in reusable components
- Wrapper components with only one call site and no policy value
- Boolean-heavy APIs that encode multiple visual modes
- Premature design-system abstractions
- Duplicating ledger rules for the sake of UI convenience

State ownership, effects, focus, and navigation should be explicit enough that a test can explain the behavior.

## 13. Prototype gate

Before production implementation, create Compose previews for three critical surfaces:

1. Actionable dashboard
2. Quick expense entry
3. Pockets overview and management

Each prototype set should include realistic fixed data, light and dark themes, phone and tablet sizes, and important edge states such as long labels, low availability, empty content, and validation.

The prototype is a decision artifact. Review hierarchy, copy, reachability, density, and task flow before connecting it to production state. User approval is the gate to production implementation.

## 14. Implementation philosophy

Use `implement` as the master execution workflow and `tdd` continuously inside every behavior-changing slice. Do not treat testing as a final phase.

### Recommended vertical sequence

1. Baseline and prototypes
   - Capture the current critical flows.
   - Build and approve the three preview prototypes.
   - Record any accepted deviation from this philosophy.

2. Structural extraction without behavior change
   - Split the app shell, screens, and focused tasks.
   - Preserve observable behavior.
   - Keep the full existing suite green.

3. Theme and component foundation
   - Establish semantic color, typography, shape, spacing, and common component policy.
   - Verify light, dark, large-font, and edge-to-edge behavior.

4. Quick expense entry
   - Introduce the shared dedicated route.
   - Wire the in-app action and launcher shortcut to it.
   - Preserve all current movement behavior.

5. Dashboard and Pockets
   - Apply the approved actionable hierarchy.
   - Improve Pocket comparison and management without changing financial rules.

6. Movements, Settings, onboarding, and restore
   - Make filters explicit.
   - Break dense workflows into focused surfaces.
   - Preserve recovery and data-management behavior.

7. Whole-app quality pass
   - Accessibility, adaptive layout, motion, state restoration, performance, and visual regression checks.

8. Review
   - Run code review against the fixed baseline commit `abda56a` unless the task establishes a newer explicit baseline.

Each step should end in a buildable, testable, reviewable state. Commit by coherent behavior slice, not by file type.

## 15. TDD contract

For every behavior change:

1. Write one focused failing test.
2. Run it and observe the expected failure.
3. Make the smallest production change that passes it.
4. Run the focused test.
5. Refactor while green.
6. Run the relevant suite before moving to the next behavior.

Do not write a large batch of tests before implementation. Do not weaken assertions to accept a regression. Do not add production-only escape hatches for tests.

### Confirmed test seams

- Plain state-driven screen composables for host-side UI tests and previews
- Navigation integration tests for root destinations, subordinate routes, back behavior, state preservation, and launcher shortcut parity
- Existing ledger and domain tests as the authority for financial behavior
- Deterministic screenshot tests for dashboard, quick entry, and Pockets in light/dark and phone/tablet configurations
- Accessibility semantics assertions and automated accessibility checks in each slice
- Focused tests for IME handling, filters, undo, validation, and restore where behavior changes

Use test tags sparingly. Prefer visible text and semantics that represent the user contract.

## 16. Skill routing

Use the narrowest skill that matches the current problem. Read its complete instructions before acting.

| Task | Primary skill | Supporting skill or rule |
| --- | --- | --- |
| Execute an approved implementation handoff | `implement` | Keep the handoff updated; run `code-review` at completion |
| Change observable behavior | `tdd` | One red-green-refactor loop at a time |
| Prototype a design question | `prototype` | Pair with `mobile-app-ui-design` |
| Establish hierarchy, flow, and mobile interaction | `mobile-app-ui-design` | Apply this product philosophy first |
| Define theme and Material component treatment | `material-design-3-ui` | Use semantic roles and stable APIs |
| Check Android conventions and platform behavior | `android-design` | Current platform guidance, not domain behavior |
| Design reusable Compose APIs | `compose-component-design` | Only after multiple real call sites exist |
| Change state ownership or effects | `compose-state-and-effects` | Keep UI state explicit and previewable |
| Add subordinate routes or migrate navigation | `navigation-3` | Preserve the four-item bottom bar contract |
| Add or review motion | `compose-animations` | Follow the restrained motion philosophy |
| Change insets or system-bar handling | `edge-to-edge` | Validate IME and gesture navigation |
| Build UI tests and semantics assertions | `compose-ui-testing-patterns` | Add `structuring-a-compose-test` when creating classes |
| Choose host versus device coverage | `setting-up-host-vs-device-tests` | Use the cheapest reliable layer |
| Fix flaky synchronization | `synchronizing-with-idle` | Do not add arbitrary sleeps |
| Enable automated accessibility validation | `enabling-accessibility-checks` | Still perform manual critical-flow review |
| Adapt layouts across device sizes | `adaptive` | S23 first; tablet-safe foundation |
| Run Gradle | `gradle-run` | Follow repository Gradle execution rules |
| Investigate a reproducible defect | `diagnosing-bugs` | Diagnose before changing production code |
| Resolve a disputed module boundary | `codebase-design` | Use only when the seam is genuinely unclear |
| Verify volatile platform guidance | `research` | Prefer primary Android sources |

If several skills overlap, one skill owns the task and the others provide constraints. For example, `mobile-app-ui-design` owns a flow prototype, while `material-design-3-ui` constrains visual tokens and `android-design` checks platform conventions.

## 17. Non-goals for the first improvement program

- Changing the four root bottom-navigation destinations
- Rewriting financial calculations
- Expanding the product beyond the accepted MVP without a new issue
- Introducing cloud sync, authentication, or bank integrations
- Building a full tablet multi-pane experience
- Adopting experimental Compose APIs without a documented need
- Replacing clear UI with animation or decorative visual effects
- Performing a framework migration that has no user-visible or testability benefit

## 18. Review checklist

### Product and hierarchy

- Does the screen answer a clear user question?
- Is the next action obvious?
- Are canonical Pocket terms used?
- Is uncommon detail progressively disclosed?
- Does the change preserve accepted behavior?

### Interaction

- Are the common path and recovery path both clear?
- Does back navigation behave predictably?
- Is state preserved appropriately?
- Does the launcher shortcut match the in-app expense path?
- Are destructive actions deliberate and recoverable?

### Visual system

- Are semantic theme roles used?
- Is numerical hierarchy clear?
- Does the layout avoid equal-weight card clutter?
- Do light and dark themes communicate the same meaning?
- Is expressive treatment reserved for meaningful moments?

### Accessibility and adaptation

- Are semantics, focus, errors, and progress exposed?
- Are targets large enough and states understandable without color?
- Does the UI survive large fonts, the IME, system bars, and gesture navigation?
- Is it usable on the target phone and a representative tablet?

### Engineering and tests

- Is domain logic kept outside composables?
- Are state and callbacks explicit?
- Did the change follow a focused TDD loop?
- Are navigation, screenshots, and accessibility covered at the right seams?
- Are existing ledger and flow tests still green?
- Has the final diff been reviewed against the fixed baseline?

## 19. Completion standard

A UI/UX improvement program is complete when:

- The three critical prototypes were reviewed and approved.
- The bottom navigation remains Inicio, Movimientos, Pockets, and Ajustes.
- The in-app expense action and launcher shortcut use the same quick-entry route.
- The dashboard presents availability, spending status, Pocket progress, and supporting metrics in the approved hierarchy.
- Existing allocation, movement, refund, conversion, rollover, reminder, backup, restore, search, filter, edit, delete, and undo behavior is preserved.
- Accessibility, IME, edge-to-edge, large-font, back, and state-restoration behavior has been verified.
- Focused tests and the full relevant suite pass.
- Screenshot coverage exists for the three critical surfaces across the agreed themes and sizes.
- A final code review has been completed from the agreed baseline.
- Commits are coherent, scoped, and leave the repository buildable.
