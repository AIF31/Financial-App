# Changelog

Notable repository changes are recorded here for future maintainers.

## 2026-10-23

### PR #46 verification

- Consolidating the stacked features into one PR retains `1.0.11` (`versionCode` 12), exactly one increment above `main`, as required by the version policy. The intermediate `1.0.12` / 13 declaration caused Android CI's version check to fail before any Gradle tasks ran. The version gate and its self-check pass with the corrected metadata.
- The independent Standards review found an unnamed rate-alert switch and budget calculations in the suggestions dialog; the Spec review found that a concurrent auto-recorded expense could make merchant reassignment report success without changing memory. The switch now has an accessible name, final replacement totals and affordability live in `BudgetSuggestions`, and merchant reassignment checks the latest expense inside the transaction. All three regressions failed before the fixes and passed afterward (29 targeted tests). The merchant-memory host flow also waits for the updated row after the dialog closes, matching the ledger's asynchronous UI delivery. Details are in [the review record](../docs/audits/2026-10-23-pr46-review.md).
- The requested Claude Opus 5.5 high-effort adversarial review found that median-day projection could move a first-of-month subscription into the wrong budget period. Consistent monthly histories now retain their calendar date across shorter months; rolling day-interval schedules keep their prior behavior. The regression failed with November 30 instead of November 1, then passed with February, month-end and period-boundary cases (20 recurring-charge tests).
- Final verification: the gradle-run wrapper passed all 421 host tests, lint (zero errors, 26 existing warnings) and all APK builds. Two physical-layout test interactions were corrected and passed five focused host tests, then all 16 audited feature tests passed on a Samsung Galaxy S23 Ultra (API 36) under an isolated package. Its protected financial-file set stayed unchanged and device animation settings were restored. The signed production `1.0.11` / 12 APK matched the installed certificate, updated `1.0.9` in place, and launched into the existing ledger. Temporary test apps were removed. A second Opus review confirmed the fixes; shifted monthly histories retain their documented median-day estimate. Manual TalkBack and live FX notification delivery remain unchecked.

## 2026-10-21

### Rate alerts

- **Aviso de tipo de cambio** in **Moneda y conversión** sends the latest Banxico FIX for one chosen pair: SAR → MXN (default), MXN → SAR, USD → MXN or MXN → USD. SAR uses the SAMA peg of 3.75 per dollar. The notification shows `1 SAR = 4.9123 MXN`, the percent change from the previous FIX, and the average of every FIX dated in the seven days ending on the latest one, with how far today sits above or below it.
- Frequency is **Diario** (default; weekends and holidays repeat the last FIX), **FIX nuevo** (only for a FIX not yet alerted for that pair) or **Semanal** on a chosen day, at a chosen `Asia/Riyadh` time (default 09:00). An optional minimum rate holds the alert back until the rate reaches it; with **FIX nuevo**, the first qualifying FIX still alerts.
- The alert requires online conversion; while that is off the alert is paused and its switch can only be turned off. A daily `WorkManager` job, which requires a network connection, fetches 20 days of the FIX series in one request. Provider failures retry up to three times. Atomic per-family claims keep a scheduled occurrence, or a pair's FIX in **FIX nuevo** mode, from alerting twice. The rate is public, so the notification is visible on the lock screen. `WorkReminderScheduler` and the new scheduler share `DailyNotificationScheduler`.
- `CONTEXT.md` defines **Rate alert**. Nine DataStore preferences are added. No Room migration, backup-format change or version bump.
- Verification on Ubuntu with JDK 17 through the gradle-run wrapper: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed. The 412 tests in 49 suites had no failures, errors or skips. Lint retains the same 26 advisory warnings, none in changed files; its `MissingPermission` error on the first draft led to an explicit check before posting. New tests cover pair conversion, change and seven-day average arithmetic, Banxico history parsing and failures, each frequency, weekday gating, the minimum rate, slot de-duplication, preference round trips and fallbacks, the network-constrained schedule, and the settings card. Three existing settings tests failed when the card sat above the quote controls, so it moved to the end. `graphify update .` completed. No physical-device or TalkBack check was run.
- **Review follow-up.** GPT-6.1-Sol code and adversarial reviews (high effort) were verified against the code; all findings were fixed:
  - Fixed: a run delayed by its network constraint shifted every later run. The worker now realigns its next run to the chosen time through `updateWork` with a schedule override. The first enqueue keeps an initial delay, because WorkManager allows the override only with the UPDATE or KEEP policy.
  - Fixed: an alert that could not be shown, because notifications were off, the channel was blocked or posting failed, still consumed its claim. The worker checks the channel first, and the coordinator releases the claim when posting fails.
  - Fixed: one remembered slot let A → B → A pair switches repeat a FIX. Claims are now kept per family: one per pair for **FIX nuevo** and one for scheduled daily or weekly occurrences.
  - Fixed: a weekly or 23:59 daily run retried after midnight lost its occurrence. Daily and weekly claims now name the scheduled occurrence. A run up to a day late still serves it, and one up to five minutes early counts as on time.
  - Fixed: withdrawing online conversion or editing the alert during a fetch still posted. The settings are checked again before claiming.
  - Fixed: re-tapping the selected pair cleared its minimum rate.
  - Verification: the gradle-run wrapper passed `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. All 418 tests in 49 suites passed, with no failures, errors or skips. Lint kept 26 warnings, none in changed files. Each finding has a regression test. The schedule override's first-enqueue restriction surfaced as a test failure and was fixed before commit. `graphify update .` completed. No physical-device check was run.

Stacked on PR #45. The development version advances to `1.0.12` (`versionCode` 13). No Room schema or backup-format change.

### Upcoming recurring charges

- **Cargos recurrentes** in the current period's analysis lists expected merchant charges with their latest Pocket, date and amount, marks recent missed charges **Vencido**, and gives **Faltan por cobrarse** as a separate total. The dashboard details show the pending total and occurrence count below the pace projection; tapping opens the current analysis. A charge opens that merchant's past expenses in the same accounting currency through the existing MovementDrill route.
- Detection uses at least three expenses with consistently monthly (26–35 days) or weekly (6–8 days) gaps and each of the last three amounts within 10% of their median. Dates advance by the median gap, rounded down to a whole day; amounts use the latest expense. All pending weekly occurrences before the exclusive period end count. Each occurrence drops after it is more than half a cadence overdue; the merchant is treated as ended only after two consecutive expected charges pass that window. Refunds and expenses whose period uses another accounting currency are excluded; existing accounting amounts need no new conversion. No Movements, suggestions or persistent records are created. Pace projections, run-out dates and alerts stay separate.
- Verification on Ubuntu with JDK 17 through the gradle-run wrapper: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed. The 382 tests in 47 suites had no failures, errors or skips. Lint retains the same 26 advisory warnings, with no new issues in changed files. Sixteen new data tests cover monthly and weekly detection, gap and amount rejection, currency exclusion, paid cycles, overdue limits, refunds, exact totals and merchant history; two host tests cover the dashboard → analysis → past-charges flow, the overdue word, unchanged Movements and suggestions, and absence from historical analysis. Tests first failed on the missing detector, then on weekly, stability and expiry rules; the host tests failed on the missing section and dashboard line before UI implementation. `graphify update .` completed. No physical-device or TalkBack check was run.

### Manage merchant memory

- **Memoria de comercios** in Ajustes lists each merchant’s remembered Pocket, latest expense with its period’s accounting currency, and expense count. Case and punctuation variants share one entry; refunds never decide memory. Archived Pockets are labelled **Pocket archivado · no se registra automáticamente**.
- Choosing another Pocket explains and moves only the latest expense through one `AddMovement` edit. It preserves the original amount and currency, conversion metadata, payment method, note, date, time and stored time zone. The destination must be unarchived and active in that expense’s period; rejections stay in the dialog. Future automatic records use the new Pocket because the screen and auto-recorder share the latest-expense resolver. Existing movement edits have no undo action, so this screen follows their success-snackbar pattern.
- Merchant memory remains derived, with no separate storage. `CONTEXT.md` and ADR 0002 describe the correction screen. No version bump, Room migration or backup-format change.
- Verification on Ubuntu with JDK 17 through the gradle-run wrapper: `:app:testDebugUnitTest :app:lintDebug` passed, with 364 tests in 45 suites and no failures, errors or skips. Lint retains the same 26 advisory warnings, with zero new issues. New data tests cover grouping, latest-expense selection, refunds, archived marking, field preservation and invalid destinations; real-ledger tests prove the recorder follows a reassignment and preserves a foreign expense’s metadata and original time zone. Settings host tests cover changing the Pocket and the empty state. A review found that background conversion confirmation could race reassignment; a regression failed before an expected-movement snapshot was checked inside the write transaction. Stale reassignment now rejects instead of overwriting the confirmation. Tests first exposed the missing module/screen and the edit’s time-zone overwrite. `graphify update .` completed. No physical-device or TalkBack check was run.

### Cover an overspent Pocket

- **Cubrir con otro Pocket** appears in a current Pocket's management dialog when availability is negative or its status is **Ritmo alto**. The dialog prefills the exact overspending or the remaining pace shortfall rounded up to a whole unit, and lets the person edit it. Unassigned funds come first, followed by active Pockets with the most budget available to give. Archived, retired, exhausted, high-pace and 80%-used Pockets cannot donate; a donor's contribution is capped by its own budget so rollover stays in place.
- Confirmation applies both budgets through one atomic `SetAllocations` command, or raises only the target when using unassigned funds. The source is checked again against the latest ledger state; validation and ledger rejections appear in the dialog. Dashboard Pocket rows still open expense entry, so this adds no dashboard navigation. `CONTEXT.md` defines **Budget reassignment**. No version bump, Room migration or backup-format change.
- Verification on Ubuntu with JDK 17 through the gradle-run wrapper: `:app:testDebugUnitTest :app:lintDebug` passed. The 354 host tests in 43 suites had no failures, errors or skips; lint reported 26 existing advisory warnings, none in changed files. New tests cover overspending, forecast shortfalls and rounding, source eligibility and rollover caps, unassigned funds, command validation, fully allocated atomic coverage, and the management-dialog flow. The amount and source tests failed before their implementation; the host flow failed at the missing action before the dialog was added. `graphify update .` completed. No physical-device or TalkBack check was run.

- **Review follow-up.** The independent review findings were verified by the orchestrator:
  - Fixed P1: the remaining shortfall stays exact until one whole-unit ceiling; 1,009 minor over 10 days with 21 remaining and 1,410 available now suggests 800.
  - Fixed P3: expiry applies to individual recurring occurrences. Later dates remain listed after one missed charge; two consecutive expired charges end the forecast.
  - Fixed S6: an overflowing recurring total is absent, with individual expectations preserved and both screens hiding the total line.
  - Fixed S1, S2, S3 and S4: both dialogs share a radio row with a 48 dp minimum target; the merchant dialog body scrolls together at large fonts, and amount errors are exposed on the field itself.
  - Retained the user decision to exclude at-risk donors. Declined moving donor eligibility into the write transaction: availability may be negative, and `SetAllocations` already validates the final budget total.
  - Verification: the gradle-run wrapper passed `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` on Ubuntu with JDK 17. All 388 tests in 47 suites passed, with no failures, errors or skips. Lint has the same 26 existing warnings and none in changed files. Behavioral regressions were checked red then green; the large-font host test explicitly waits for the ledger rejection before scrolling to it. `graphify update .` completed. No physical-device or manual TalkBack check was run.

## 2026-10-18

### Analysis drill-through, budget suggestions, pace alerts, and a period recap

Stacked on the analysis work in PR #43. The development version advances to `1.0.11` (`versionCode` 12). No Room schema or backup-format change; three DataStore preferences are added.

- **Drill-through.** In **Análisis del periodo**, tapping a merchant, a weekday bar, a calendar day with spending, or a payment method opens a titled list of exactly the Movements behind that figure, with their count and net total. The list reuses Movements' cards, details, edit, and swipe-to-delete with undo. Saving an edit returns to the list. `MovementDrill` (in `data`) selects from the same Movements as the analysis, and weekday lists count only days through today, as the averages do. Calendar days with spending are now individual buttons for screen readers; the calendar's spoken summary moved to its weekday header.
- **Budget suggestions.** `BudgetSuggestions` proposes each Pocket's budget from the median net spend of up to three recent ended periods. Only periods in the same accounting currency count, each period is scaled to the target period's length, and the result is rounded up to a whole unit. A Pocket with no spending to go on gets no suggestion rather than zero, so applying suggestions never empties a budget kept for occasional spending. When at least two periods agree, the Pocket is flagged as always over its budget or using less than half. The Pocket dialog shows the suggestion with **Usar**. While the period needs review, **Aplicar presupuestos sugeridos** lists every change and the resulting total against new funds, then applies them through the new atomic `LedgerCommand.SetAllocations`; `SetAllocation` now runs through the same path. Only the final total must fit the new funds, so the order of changes never matters.
- **Pace alerts.** A payment auto-recorded from a notification that leaves a Pocket forecast to run out, at 80% of its budget, or without availability posts one notification per level, period, and Pocket. A worse level replaces the earlier alert. Levels reached while Pocket is open are marked as seen, since the dashboard already shows them, and the first check after updating records existing levels silently. There is a new **Ritmo de Pockets** channel, an **Aviso de ritmo de Pockets** switch (on by default) in notification capture settings, and a redacted lock-screen version. `CONTEXT.md` defines Budget suggestion and Pace alert; the [notification beta doc](../docs/product/NOTIFICATION_ASSISTANCE_BETA.md) describes the alert.
- **Period recap.** During the first seven days of a period, the dashboard shows how the previous one closed: spending against Pocket budgets plus rollover, Pockets that went over and by how much, the budgeted Pocket that used the smallest share, the top merchant, and the rollover received. **Ver análisis** opens that period's analysis, and **Cerrar** hides it for good. The card sits below the availability hero so the available amount stays the first figure on screen; an existing host test caught the first placement above it.
- Verification on Ubuntu with JDK 17, through the gradle-run wrapper. New `AnalysisActionsTest` (drill matching, suggestion median, scaling, rounding, fit, recap), `SetAllocationsTest` (an order-independent batch, and rejections that change nothing), `PaceAlertsTest` (levels, one alert per level, escalation, pruning, copy, the silent baseline, seen-in-app, alerts off), `AnalysisActionsHostFlowTest` (dashboard → analysis → merchant list → edit returns to the list; recap then applying suggested budgets), extended `AnalysisScreenTest`, and a DataStore round trip. `:app:testDebugUnitTest` passed 333 tests in 41 suites with no failures, errors or skips. `:app:lintDebug` reported the existing 26 advisory warnings, none in changed files, after it caught `BigInteger.longValueExact`, which needs API 31, and that call was replaced. `:app:assembleDebug`, `:app:assembleRelease` and `:app:assembleDebugAndroidTest` passed. The Pixel 6 API 35 managed-device suite (excluding `LargeTest`) passed 25 tests. The recap card and a drill-down list were rendered with a throwaway Robolectric capture and checked by eye.
- Limits: not checked on a physical device or with TalkBack. The suggestion confirmation and Pocket dialogs were not captured visually. The listener-to-alert path is covered by the coordinator's tests, not by a test that posts a real notification.
- **Review follow-up.** An independent GPT-6.1 review raised five findings:
  - Fixed: scaling truncated below a minor unit before the round-up, so SAR 3.33 over 31 days suggested SAR 3.00 for 28 days instead of SAR 4.00. The scaled values and the median now stay exact until one ceiling.
  - Fixed: a weekday list showed a total beside a tapped average. Its header now adds "2 días, media SAR 10.00 por día".
  - Fixed through the PR #43 follow-up: a Pocket funded only by rollover showed a run-out date but never alerted. It is now `FAST_PACE`, so it alerts at `RUN_OUT`; a test pins this.
  - Clarified rather than changed: a payment auto-recorded while the app is open may or may not alert, depending on whether the listener or the app checks first. `CONTEXT.md` now says so.
  - Declined: counting refund-heavy periods as negative in the suggestion median. A period that needed no budget counts as zero, which is now documented in the code.

### Possible duplicates stay in review, and a new period analysis

The development version advances to `1.0.10` (`versionCode` 11). No schema or backup-format change.

- **Duplicate guard.** Typing in a purchase by hand gave its merchant a known Pocket, so the bank alert that followed was auto-recorded a second time. A detected payment now stays in **Por revisar** when an expense for the same normalized merchant, with the same amount in the payment's currency, is dated within 12 hours of it. That covers manual entries and one purchase alerted by two apps. The alert and the inbox card say `Posible duplicado de un gasto ya registrado`. A payment without a merchant is never matched on amount alone. `merchantKey` moved from the notification parser to `data/Model.kt` so reports and merchant memory share it. `CONTEXT.md` (Auto-recording), [ADR 0002](../docs/adr/0002-auto-record-notification-payments-for-known-merchants.md) (dated amendment) and the [notification beta doc](../docs/product/NOTIFICATION_ASSISTANCE_BETA.md) describe the rule.
- **Run-out dates.** `PeriodInsights.runOutDateByPocket` gives the day a Pocket's availability runs out at its pace through today, when that falls before the period ends. Only Pockets spent on at least two days are forecast, so a single large expense such as rent is not extrapolated. Spending dated after today reduces what is left but not the pace. Dashboard Pocket cards show `Al ritmo actual, se agota el 12 mar`, and an on-track Pocket with a run-out date shows the new **Ritmo alto** badge (`PocketBudgetStatus.FAST_PACE`) on the dashboard and Pockets screen instead of **En buen ritmo**. The ledger's 80%/100% flags still take precedence.
- **Análisis del periodo.** A new screen opened from the dashboard, backed by the pure `data/SpendingAnalysis.kt`, which uses the same Movements as the period's totals. It shows:
  - average daily spending for up to 12 periods as bars (tap one to open that period), with a period table and a sparkline per Pocket; the trend stops at a change of accounting currency and says so;
  - top merchants, with purchase count, average purchase, and change against the previous period when it uses the same currency;
  - average spending by weekday and a calendar heatmap through today;
  - payment-method shares;
  - foreign-currency spending, with original and accounting amounts and pending estimates;
  - rollover received per period and what would carry forward.

  Every chart uses one hue, has a spoken summary, and comes with the figures as text.
- Verification on Ubuntu with JDK 17, through the gradle-run wrapper. The three new auto-recorder duplicate tests failed before the guard and pass after it; a same-amount purchase on another day still auto-records. New `SpendingAnalysisTest`, run-out and status tests in `PeriodInsightsTest`, `AnalysisScreenTest`, and a dashboard test cover the features. `:app:testDebugUnitTest` passed 313 tests in 37 suites with no failures, errors or skips. `:app:lintDebug` reported the existing 26 advisory warnings, none in changed files. `:app:assembleDebug`, `:app:assembleRelease` and `:app:assembleDebugAndroidTest` passed. The Pixel 6 API 35 managed-device suite (excluding `LargeTest`) passed 25 tests. The analysis screen (light and dark) and the dashboard were rendered with a throwaway Robolectric capture and checked by eye. That pass fixed a wrapped Pocket name, an orphaned ▲ arrow, and an **En buen ritmo** badge shown beside a run-out date.
- Limits: not checked on a physical device or with TalkBack. How far estimated conversions moved once confirmed is not shown, because the replaced estimate is not stored. Two genuine identical purchases at one merchant within 12 hours now need one confirmation.
- **Review follow-up.** An independent GPT-6.1 review found eight issues; seven are fixed, each with a test that failed first, and one is declined:
  - A huge budget spent slowly crashed the dashboard, because the run-out date overflowed the calendar. The distance is now bounded by the period first.
  - A hand entry converted from another currency (USD 10.00 recorded as SAR 37.50) did not stop the SAR bank alert from auto-recording. Amounts now also match as converted.
  - An expense saved between the recorder's check and its write was recorded twice. `ConfirmSuggestion` repeats the check inside its transaction for automatic records and returns the new `RejectionKind.DUPLICATE`.
  - A Pocket funded only by rollover could show a run-out date without **Ritmo alto**. It now gets that badge too.
  - The rollover history dropped a Pocket that carried money forward but later opted out.
  - "Mediana" used the upper middle value for an even count. It is now a true median.
  - The trend chart's tap handler could keep a stale period mapping, and the bars had no screen-reader action. The chart now reads the latest mapping and offers one "Ver periodo …" action per bar. Sparklines now speak their whole history.
  - Declined: drawing negative net spending (refund-heavy periods) below a zero line. Bars and sparklines still treat it as zero, while the spoken summary and the period table give the signed values.

## 2026-10-15

### Foreign expenses confirmed at their own date's FIX

A foreign-currency expense could be saved as confirmed at another day's rate. Banxico publishes the FIX around 12:00 Mexico City time, about 21:00 in Riyadh. An expense entered earlier on its own date therefore got the previous business day's FIX. A failed Banxico request could also fall back to a cached rate up to seven days older. In both cases the rate was frozen as confirmed. The development version advances to `1.0.9` (`versionCode` 10).

- `DefaultExchangeRateRepository` now marks each quote final or provisional (`FxQuote.isFinal`). A quote is final if it has an observation on the requested date. It is also final if the provider answered after that Mexico City day ended, so weekends and holidays correctly use the prior FIX. A cached older observation is never final.
- The expense form saves a provisional quote as **Estimado** and says it will update with that date's FIX once Banxico publishes it.
- On every return to the foreground with online FX enabled, `EstimatedConversionReconciler` re-quotes provider estimates. Once the rate is final, the new `LedgerCommand.ConfirmConversion` replaces the amount, rate, effective date and source, and marks the expense confirmed. Rollover is recalculated. If the movement was edited or deleted after the estimate was read, the command does nothing. Legacy manual estimates without a provider source are never changed. No schema or backup-format change.
- `CONTEXT.md` (Conversion status) and `Info/implementation-reference.md` describe the rule. Merged with `main`, whose `AGENTS.md` already supports Ubuntu development.
- Verification, run on Linux with Android Studio's bundled JDK: `TransactionDateRateTest` failed on the prior code for the same-day and cached-fallback cases, stamping a 2026-10-14 purchase confirmed at the 2026-10-13 and 2026-10-12 FIX. It passes after the fix, alongside new final, cached-final and weekend cases. `EstimatedConversionReconcilerTest` covers confirmation, provisional and failed quotes, legacy estimates and a concurrent edit against Room. `:app:testDebugUnitTest` passed 291 tests with no failures, errors or skips. `:app:lintDebug`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed. The debug APK was installed on a fresh API 37 emulator. Onboarding, enabling online FX and returning from the background ran without a crash. The Pixel 6 API 35 managed-device suite passed 31 tests. After merging `main` (PR #41), the gradle-run wrapper's CI-equivalent gate passed: 295 host tests, `:app:lintDebug`, and the debug, release and test APK builds.
- Live check against Banxico on 2026-10-15 at 10:30 Mexico City time, using a debug build with a local token on the API 37 emulator. A 100 MXN expense dated 2026-10-14 was saved `CONFIRMED` at that day's FIX (18.4888, SAR 20.28). One dated 2026-10-15, before that day's FIX was published, showed the Estimado notice and was saved `ESTIMATED` at the 2026-10-14 FIX. For the reconciliation check, the 2026-10-14 row was set in the emulator database to the bug's prior result: `ESTIMATED` at the 2026-10-13 FIX (18.3528, SAR 20.43). Relaunching the app confirmed it at the 2026-10-14 FIX (SAR 20.28). The 2026-10-15 row stayed estimated.
- Limits: no physical-device test was run. Finality relies on the device clock: a date counts as final once its Mexico City day has ended, even if Banxico has not yet published that day's FIX. A movement's date still defaults to the Asia/Riyadh day, so a late-evening purchase in Mexico defaults to the next day unless the date is changed.

## 2026-10-05

### Ubuntu development support

- Windows and Ubuntu, including Ubuntu on WSL, are now supported development hosts in `AGENTS.md`. Added an [Ubuntu setup and verification guide](ubuntu-development.md) and linked it from the project and installation documentation. Gradle already supports this host; no app or build configuration change was needed.
- Verification on native Ubuntu 26.04.1 x86_64 with JDK 17: the compact Gradle wrapper passed host tests, debug lint, and debug, unsigned release, and instrumentation APK assembly. Host tests were then explicitly rerun: 286 tests across 33 suites, with zero failures, errors, or skips. Build and lint outputs were up-to-date where applicable; lint reports 26 advisory warnings and zero errors. The workflow finished and removed only its owned logs, preserving build/test reports.
- Limits: managed-device tests were not locally executed because the emulator acceleration check reports missing `/dev/kvm`. No physical-device tests, performance measurements, or permanent release signing were performed. The [audit follow-up](../docs/audits/2026-10-05-reliability-performance-review.md#ubuntu-compatibility-follow-up-2026-10-05) supersedes the earlier Windows-availability limit for host validation.

### Independent review of PR #41

- Appended separate Standards and Spec reviews to the [dated reliability audit](../docs/audits/2026-10-05-reliability-performance-review.md), preserving the implementation report. Reviewed `352e2ed...4d81f15`: one P3 finding records development workflow departures; no introduced behavior defect was validated.
- Verification: GitHub CLI confirmed the reviewed PR head and all six successful checks. Source and tests were inspected read-only; builds, tests, performance measurements, and device behavior were not independently reproduced because the required Windows checkout is unavailable. Any implementation-session authorization for the workflow departures remains to be recorded, or the required Windows validation reproduced through the compact Gradle wrapper.

### Reliability and performance pass

A whole-app review of the ledger math, loading time, and test suite. The [dated audit](../docs/audits/2026-10-05-reliability-performance-review.md) lists each finding, its fix, and the measurements. The development version advances to `1.0.8` (`versionCode` 9).

- Archiving a Pocket now moves its whole incoming rollover to unassigned funds, like its budget. Before, it moved only the part not yet spent, so spending past the Pocket's own budget made funds disappear from the totals until a later historical edit recalculated the release.
- Archiving a Pocket that still has Movements in a pre-created later period is rejected with "Mueve o elimina primero los movimientos de este Pocket en periodos posteriores". Before, those Movements stopped counting in that period, and the next exported backup could not be restored. Undoing a delete is likewise rejected if the Movement's Pocket is no longer in its period.
- Saving, deleting, or editing a Movement computes rollover once instead of twice and writes only the allocations that changed. Ledger state is built on a background thread instead of the main thread, and a launch with nothing to catch up no longer reads the whole ledger or rebuilds state twice. On a synthetic five-year ledger, saving an expense dated in the oldest period dropped from about 230 ms to 76 ms in host tests.
- The app reports when its first real screen is fully drawn. Release builds remove unused resources (2.66 MB to 2.45 MB). An app baseline profile was measured and not kept, because it made no measurable difference.
- Tests: seeded random command sequences now check the accounting rules after every command. The device class `PocketLedgerBehaviorTest`, whose tests duplicated host tests, is removed after moving its one unique test to the host suite. Host tests run in up to four JVMs.
- Verification: each new regression test failed before its fix. The host suite, lint, debug and release assembly, and the Pixel 6 API 35 managed-device suite passed; the audit records the counts. A release-like build was smoke-tested on an emulator with a seeded five-year ledger. No physical-device test was run.

## 2026-09-30

### 1.0.6 hardware findings fixed

Fixes for the five findings in `Info/verification/2026-09-29-1.0.6-hardware-test.md`. The [dated audit](../docs/audits/2026-09-30-1.0.6-hardware-findings-fixes.md) lists each fix, its test, and the decisions. The branch builds on PR #38. The development version advances to `1.0.7` (`versionCode` 8).

- Onboarding and the loading screen keep clear of the status bar, display cutout, and keyboard. Onboarding's title had been drawn under the status bar.
- Restoring a file that is not a Pocket backup says "Este archivo no es un backup de Pocket o está dañado." instead of the English parser message, which also quoted the file. Other unexpected failures, including database errors during restore, get fixed Spanish sentences. The backup's own validation messages are unchanged.
- The restore preview and result use singular nouns for a count of one ("1 periodo", "1 Pocket", "1 movimiento"). The same applies to "1 gasto" in the Pockets period details and "1 día" in the spend pace and the chart's spoken summary.
- On Inicio, the "Sin asignar" and "Gastado" tiles stack at full width when an amount cannot fit on one line in a half-width tile, as at font scale 1.5. "SAR 4,200.00" no longer wraps between the currency and the number. The "Registrar gasto" button still overlaps "Ver todos" at rest at that scale; this is expected button behavior. Every item can scroll clear of it, and a new test guards that.
- After a restore, when no notification source app is selected, the result message says to choose the apps again in Ajustes > Captura desde notificaciones. Backups do not include capture settings, and the format stays at version 5, because source apps are package names from one device.
- Verification: each of the five new host tests failed before its fix and passed after it. `:app:testDebugUnitTest` passed 282 tests with no failures, errors, or skips. `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed. The Pixel 6 API 35 managed-device suite passed 31 tests. No physical-device test was run.

## 2026-09-29

### 1.0.6 physical-device test

- Recorded in `Info/verification/2026-09-29-1.0.6-hardware-test.md`. The release-signed `1.0.3` upgraded in place to `1.0.6` with a byte-identical backup before and after. All 31 device tests passed, and the manual journeys passed, including the launcher shortcut over an unsaved edit and the dark-theme onboarding heading. Five non-blocking UI findings are listed there. The phone was left on a release-signed `1.0.6` with the user's backup restored.

### Notification replay after backup restore

- A restored auto-recorded Movement now blocks the same notification identity at the suggestion store, including conversation reposts after its Pocket is archived and direct capture when auto-recording is off. No new pending suggestion or detection alert is produced. The development version advances to `1.0.6` (`versionCode` 7); no APK was published.
- Verification: both new regressions failed on the prior code and passed after the fix. Four notification host test classes passed; the full host suite passed 276 tests with no failures or skips. `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`, and the Pixel 6 API 35 managed-device suite passed; the latter executed 30 tests. See the [dated review](../docs/audits/2026-09-29-notification-restore-review.md).
- Limits: pre-existing pending replays are not cleaned up by this store guard. Backups still omit suggestion tombstones, so a Movement deleted before backup has no retained identity after restore. No physical-device test was run.

### Release 1.0.5

- `README.md` and `Info/installing-pocket.md` now link to `Pocket-v1.0.5.apk` in the `v1.0.5` GitHub release, replacing 1.0.0. The release is built from `main` at `49032e3` (`versionName` 1.0.5, `versionCode` 6), which includes everything merged through PR #36. 1.0.5 updates 1.0.0 in place; database schema 6 migrates to 7.
- The release docs had said `MainActivity` was the only app-owned exported component. They now also name the notification listener service, which is exported only to the system through `BIND_NOTIFICATION_LISTENER_SERVICE`. An android-intent-security review before the build found nothing to fix: no nested intents are forwarded, `onCreate` and `onNewIntent` read only explicit actions and no extras, both PendingIntents are explicit and `FLAG_IMMUTABLE`, and the FileProvider and shortcut target are non-exported.
- Verification: `49032e3` passed Android CI. Locally, `:app:testDebugUnitTest` passed 274 host tests with no failures, errors or skips, and `:app:lintRelease` passed. `scripts/build-signed-release.ps1` produced the APK. `apksigner` verified it with APK Signature Scheme v2 and one signer, `CN=Pocket, O=AIF31`. `aapt2` reported `com.aif31.pocket` 1.0.5, code 6. APK SHA-256: `dc2689ca54559ba213e70e59f8f9d244da78098757c3ab2c215be7e886409eba`.
- Not verified: the signer's certificate was not compared with the fingerprint in the secret store; reading it from the keystore needs its password. Managed-device and physical-device instrumentation were not rerun for this build, and 1.0.5 was not installed over 1.0.0 on a device.

### Test reliability

- `PocketAppHostFlowTest.movement_search_survives_saved_state_restoration` no longer fails intermittently with `CalledFromWrongThreadException`. `createComposeRule()` runs composition coroutines on an unconfined test dispatcher. The ledger state collector therefore resumed on Room's `arch_disk_io` transaction thread and applied UI state there. The test now delivers ledger state on the main thread, as production's `AndroidUiDispatcher` already does. The app code is unchanged.

### PR #34 review follow-up fixes

Fixes for the five findings of the review at `74a214b`, which were still open when PR #34 merged. The [2026-09-29 audit](../docs/audits/2026-09-29-pr34-review.md) records each finding, its fix and its test. The development build advances to `1.0.5` (`versionCode` 6).

- Auto-recorded expenses in the Movement's own currency show the **Detectado** badge in Movements again. PR #34 had limited the badge to refunds, which made the check unreachable. Refunds keep **Devolución**, and ordinary manual expenses stay unlabelled.
- In "Comparar periodos", each Pocket's daily average for an in-progress period counts only spending dated through today, like the headline pace. The same applies when the in-progress period is the baseline. Net spend totals still include later-dated Movements.
- Budget used and the comparison's percentage change are computed with `PocketMath.percent`, the checked calculation the Pocket summary already used. Previously, amounts above about 9.2 × 10¹⁶ minor units wrapped around and gave wrong percentages. A percentage too large to represent is not shown.
- `Info/UI-UX-Design-Philosophy.md` section 6 now describes the launcher shortcut exactly. An edit or suggestion form gets a new-expense form stacked above it; an already open new-expense form is reused with its draft.
- The dated audits for PR #34's review rounds, `docs/audits/2026-09-28-pr34-review.md` and `docs/audits/2026-09-29-pr34-review.md`, are now committed with the resolution of every finding. The 2026-09-28 changelog entry cited the first audit before it was in the repository.

## 2026-09-28

### PR #34 physical-device test

- Recorded the PR #34 hardware run in `Info/verification/2026-09-28-pr34-hardware-test.md`: 29 of 31 device tests passed. Found: the launcher shortcut does not preserve an unsaved edit, the onboarding heading is unreadable in dark theme, and clipped Pocket cards lose their accessibility label. The phone was left on a release-signed `1.0.3` with the user's backup restored.
- The "Nuevo gasto" static shortcut now targets `NewExpenseShortcutActivity`, an invisible, non-exported activity in its own task. Launchers always start static shortcuts with `NEW_TASK | CLEAR_TASK`, which destroyed the running `MainActivity` and any unsaved form. The trampoline forwards only the fixed action, with `CLEAR_TOP | SINGLE_TOP`, so a running `MainActivity` receives `onNewIntent` and keeps the edit underneath.
- `PocketTheme` provides the palette's `onBackground` as the default content color. Screens drawn directly on the window, such as onboarding, were using Compose's black default, which was unreadable on the dark window background added in this PR.
- The dashboard and Pockets-list Pocket cards each carry a single accessibility label (name, availability, budget, status). The visible texts it repeats are hidden from accessibility services but stay available to tests. A card clipped at the list edge still announces its Pocket, and screen readers do not read each card twice. The Pockets list "Gestionar" button remains reachable.
- The "Comparar periodos" summary table measures its widest amount. When an amount cannot fit beside the label, as at large font scales, each label moves above its values, so numbers such as "7,500.00" are no longer split across lines. Each row is still announced once: its cells are hidden from accessibility services, because the row's spoken description already covers them.

### PR #34 review findings

Fixes from `docs/audits/2026-09-28-pr34-review.md`. Behavior that was new rather than wrong is now written into `Info/UI-UX-Design-Philosophy.md`.

- Saving a new expense opened by the launcher shortcut over an unsaved edit now returns to that edit with its draft. Previously only closing the new form did; saving reset navigation to a root screen and discarded the edit.
- Root navigation labels shrink to fit on one line instead of ending in an ellipsis, so "Movimientos" is shown in full at large font scales.
- Comparing periods in different accounting currencies with no frozen boundary rate between them now shows the compared period's daily average in its own currency, with no difference computed. The summary table gains a "Gasto diario promedio" row.
- The Movement form's Pocket choices show availability for the period that contains the entered date, in that period's currency, and name that period when it is not the current one. They previously showed the current period's availability for past dates.
- Future-dated Movements no longer inflate the pace of the current period. The daily average counts spending through today, and the projection extrapolates that pace and adds later-dated spending once instead of scaling it up. Period totals, availability, and budget used still include it. The implementation reference also now describes the shortcut trampoline Activity.
- Follow-up review: the "Gasto guardado" confirmation after saving over an edit now appears on that edit; the Movement form hosts the app's snackbar instead of leaving it queued until the form closes. Inicio's "Ver detalles" shows the previous period's daily average in its own currency when no frozen rate links the currencies, instead of only a warning.

### Time format and notification setup

- The time picker in the Movement form and the daily reminder follows the phone's 12- or 24-hour clock setting instead of always using a 24-hour dial. The stored time is unchanged. On a 12-hour phone, the Movement form's "Hora (HH:mm)" field also shows the time in that format, for example "10:16 p. m.".
- In "Captura desde notificaciones", the "Ninguna app seleccionada" setup step has an "Elegir apps" button. It scrolls to the "Buscar app" field and focuses it, so the keyboard opens ready to search.

### PR #34 review follow-up

- Merged `main` after PR #33 and advanced the development build to `1.0.3` (`versionCode` 4), because PR #33 had already taken `1.0.2`. The notification settings screen keeps PR #33's redesign under the shared top app bar, and PR #33's "open Movements" alert action sits beside the new-expense request counter in `MainActivity`.
- Pace status counts rollover as period funds. A period funded only by rollover gets a real pace status instead of "Asigna fondos". Negative availability now always reports overspending, even with zero new funds.
- A "Nuevo gasto" shortcut delivered while an edit or suggestion form is open now opens a new-expense form above it. Closing that form returns to the edit with its unsaved changes. Each Movement form route carries its own instance id, and its draft is kept in the route state holder until the route closes.
- Period labels always include the year ("25 feb – 24 mar 2026"), so pickers and the period-funds editor distinguish the same dates in different years.
- The cumulative spend chart for the current period says it runs through today. When Movements are dated later in the period, it states the net amount that is in the totals but not yet on the curve (`PeriodInsights.netSpendAfterTodayMinor`).
- Each comparison-table row is announced with each value's period, for example "Gasto neto. 25 feb – 24 mar 2026: SAR 100.00. 25 ene – 24 feb 2026: SAR 80.00".
- The PR's CI failure in [`PocketAppFlowTest`](https://github.com/AIF31/Financial-App/actions/runs/36359066486) came from temporary overlays, not an undersized control. Compose reports only a control's uncovered area, so a partly covered control looks small to the touch-target check.
  - In CI, the check after the dashboard details expand ran while the "Gasto guardado" snackbar was still showing. The snackbar sits directly above the floating action button, and its lower edge at y = 2001 px covered the top 36 px of "Comparar periodos", leaving 34 dp. The same failure recurred on [CI after merging `main`](https://github.com/AIF31/Financial-App/actions/runs/36434465204), so PR #33 did not cause it.
  - The test now waits for that snackbar to clear before acting on the dashboard. The accessibility checks are unchanged.
  - Locally, the floating action button's top edge (the same 2001 px line) cut the per-Pocket "+" buttons.
  - The Inicio Pocket card is now itself the "Registrar gasto en …" target, with the "+" as a visual cue.
  - The whole "Tus Pockets" header row now opens Pockets. It is labelled "Ver todos los Pockets" and replaces the separate "Ver todos" text button.
  - The accessibility checks are unchanged. A suppression for results cut by the floating action button or navigation bar was tried and removed in review, because it could also have hidden a genuinely small control.
  - Two alternatives were rejected. Ending root lists at the bottom bar made hidden controls show as clipped slivers. Scrolling the card to a fixed position before expanding it only moved the overlap to another control.

## 2026-09-27

### Notification capture: auto-recording, alerts, and settings

- Detected bank payments for a known merchant are now recorded as Movements in that merchant's last Pocket; new merchants and foreign currencies stay in a **Por revisar** inbox. Accepted in [ADR 0002](../docs/adr/0002-auto-record-notification-payments-for-known-merchants.md); `CONTEXT.md` and the [beta spec](../docs/product/NOTIFICATION_ASSISTANCE_BETA.md) are updated.
- Fixed missed captures from SMS apps: each message in a conversation notification is now parsed and identified separately, so new bank SMS no longer overwrite earlier ones and OTPs no longer hide purchases. The parser now reads SAB-style multi-line and point-of-sale texts, extracts their merchants, rejects credits, and reads a bare `$` as the USD or MXN default currency.
- Added a heads-up alert for each newly detected payment, which opens Movements when tapped; the lock screen shows only a redacted version.
- Redesigned **Captura desde notificaciones**: setup checklist, auto-record and alert switches, app search, suggested finance and messaging apps, and selected apps pinned on top, each shown with icon and name. Movements shows a **Detectado** label on auto-recorded Movements.
- Advanced the development build to `1.0.2` (`versionCode` 3).
- Follow-up fixes, each covered by a test that failed first: re-posted conversations no longer inflate beta parser counts; `Compra en línea … en OXXO` reads `OXXO` as the merchant; a bank app updating its notification with the same text no longer duplicates the payment; a purchase in a new, not-yet-opened budget period catches up periods and still auto-records; and foreign-currency alerts always ask for the conversion. Review-card times now use the budget zone, matching Movements.
- Verification: a fresh full host unit run passed 224 tests, and the release and androidTest sources compiled. The flow and each fix except the bank-app update were exercised on the Pixel_10_Pro emulator with synthetic SMS. It was not tested on the physical phone or with real bank messages.
- PR #33 review fixes: merchant memory now uses only the newest matching expense, and sends the payment to review if that expense's Pocket is archived rather than falling back to an older Pocket. Merchant matching also drops punctuation (`K.F.C` matches `KFC`). Both were test-first. Review hints use the currency of the suggestion's own period. Each app row in settings is a single toggleable checkbox keyed by package; this was checked on the emulator.
- Known issue: `PocketAppHostFlowTest.restore_confirmation_disables_duplicate_submissions_until_the_result_arrives` failed once (dialog created on a background thread) and then passed three isolated reruns and a full rerun. It is unrelated to this change and is flagged for a separate fix.

### Period insights, comparison, and UX quality pass

- Added `PeriodInsights` and `PeriodComparison` (`data/PeriodInsights.kt`): read-only metrics derived from the ledger's Pocket summaries and Movements — average daily spend, spend available per remaining day, projection, funds used versus period elapsed, cumulative daily spend, largest expense, and per-Pocket averages. Comparisons use average daily spending; a different accounting currency is converted only through the adjacent frozen boundary rate, otherwise the periods are shown side by side without deltas.
- Inicio: the Disponible card gains a "Ver detalles" disclosure with spend pace and the previous-period daily average, plus a "Comparar periodos" entry. Status wording and icon now reflect overspending and pace; day counts are pluralized. Each Pocket row has a quick "Registrar gasto en …" action that opens the shared quick-entry route with that Pocket preselected.
- New subordinate route "Comparar periodos": any two periods, cumulative spend curve (solid, dashed, and dotted lines so meaning never depends on color), summary table, and per-Pocket paired bars. The four bottom-bar destinations are unchanged.
- Pockets: the period summary expands into the same metrics and chart for the selected period (open by default for historical periods) with a comparison shortcut; period chips expose tab semantics, show currency and "Actual", and start scrolled to the selection. Pocket status uses one shared badge with container colors instead of low-contrast tertiary text.
- Selection controls across onboarding, quick entry, Currency, Payment methods, and Templates now use Material segmented buttons and filter chips with real selected semantics instead of "✓" text prefixes. Quick entry adds date and time pickers, "Hoy"/"Ayer", Pocket cards that show availability, keyboard actions and capitalization, and haptic confirmation on save.
- Movements: explicit filter chips that highlight when active, readable period labels, search clear button, daily totals, full detail dialog, swipe-to-delete with undo plus TalkBack custom actions, and an empty state with a record action.
- Navigation and shell: root tabs keep their scroll, search, and filters; back from another tab returns to Inicio; short fade between tabs; settings detail screens use a top app bar; centered loading state.
- Artwork and theme: Pocket artwork and logo re-encoded as right-sized WebP (≈1.8 MB of PNG to ≈140 KB); adaptive launcher icon with a monochrome themed-icon layer; night theme window background to avoid a light flash on dark cold starts; a light artwork plate keeps illustrations legible in dark theme.
- Two-axis review follow-up:
  - The reminder notification now uses a monochrome `ic_stat_pocket` vector. An adaptive launcher icon as a notification small icon can crash System UI on API 26.
  - Budget use is measured against Pocket budgets plus rollover, the same base as availability.
  - The spend curve and Movement stats count only Pockets in the period snapshot, matching net spend.
  - Pace status, Pocket budget status, per-Pocket deltas, and Movement net spend moved into the data layer (`SpendPaceStatus`, `PocketBudgetStatus`, `netSpendMinor`). "Sin presupuesto" again depends on the Pocket budget only.
  - The ledger's previous-period total is shown again beside the daily-average comparison.
  - Artwork colors are theme roles.
  - Experimental Material APIs are isolated in `PocketTopAppBar`, the time picker, and the date picker, each with its reason documented.
- Intent handling (android-intent-security audit): `MainActivity` now counts "new expense" launches across `onCreate` (skipped when restoring state) and `onNewIntent`, reading only the explicit action. Quick entry no longer reopens after rotation. The existing PendingIntent (`FLAG_IMMUTABLE`, explicit), the non-exported `FileProvider` limited to `shared_backups/`, and the permission-guarded notification listener already followed the guidance.
- Verification: 226 host tests passed, including new `PeriodInsightsTest` and `PocketQualityOfLifeHostTest`; `lintDebug`, `assembleDebug`, and `compileDebugAndroidTestKotlin` passed. Screens were reviewed from Robolectric renders at phone width in light, dark, and 1.5× font; no emulator or physical-device run was performed for this change, and TalkBack remains an open manual check.

## 2026-09-25

### App version policy

- Advanced the development build to `1.0.1` (`versionCode` 2) after merged PR #26. Future pull requests changing app, test, build, or project script files must increase both values by exactly one patch/code step; documentation-only pull requests are exempt. The required Android checks job validates the change before merge. The published APK remains `1.0.0` until a new release is signed and published.
- The version rule passed its self-check and comparison against the previous `main` commit. The physical test record includes the focused visual-tour navigation fix and its isolated passing rerun; the full physical suite was not rerun after that fix. The local Graphify update command could not start because its configured Python interpreter is unavailable.

### Local Codex and Graphify files

- Ignored generated `graphify-out/` data and local `.codex/` additions while keeping `.codex/config.toml`, `Info/`, and Android CI versioned. Verified the ignore rules and that Codex CLI can read the local Graphify skill.
- Kept 36 Android and agent workflow skills versioned, removed 16 optional skill directories and the local Skills Guide from Git's index without deleting local copies, and reduced `skills-lock.json` to the retained set.
- Scanned the public `main` tree for credential files and key signatures; none were found. The `v1.0.0` APK predates the current `main` by 40 commits.

### Closed-issues implementation review

- Reviewed the current `e58b14f` checkout against closed GitHub issues #1 and #9–#22 along independent standards and spec axes; recorded findings in `docs/audits/2026-09-25-closed-issues-final-implementation-review.md`.
- Found two remaining #1 product gaps: new foreign expenses cannot use an offline manual SAR equivalent, and historical Pocket budgets cannot be edited. Found two documented UI-standard breaches and one lower-confidence code smell.
- Verified the exact committed head's Android CI run `36047162329` passed both jobs. This was a static review; no local Gradle or physical-device tests were run. The existing maximum-font and TalkBack physical-release gaps remain open.

### Latest merged PR review

- Reviewed merged PR #26 specifically against issues #14, #15, #16, #17, #21, and #22; recorded separate Standards and Spec findings in `docs/audits/2026-09-25-pr26-merged-review.md`.
- Found a test-only production database opener, a lower-confidence string-operation smell, an incomplete #17 restore path for pre-created later periods, and partial #22 large-font coverage. Earlier #1 findings were outside this PR diff.
- Confirmed GitHub `main` at merge commit `f35f8c3`, the PR diff, the passing CI run for `e58b14f`, and a clean `git diff --check` on the PR range. No local Gradle or physical-device tests were run for this review.

## 2026-09-24

### Remaining issues in [PR #26](https://github.com/AIF31/Financial-App/pull/26)

- #14: Expense drafts keep a stable submission ID across repeated taps, failure, retry, and lifecycle changes, preventing duplicate Movements.
- #15 and #16: Portable backup version 5 includes the future period start day and reminder time. Replacement restore has a safety-export step and persistent plaintext warning; restored reminders require confirmation on the new device. Settings reports permission, channel, and scheduler status with retry.
- #17: Archived Pockets remain in the durable catalog and can be restored; history filters keep Pocket identity across period advances.
- #21 and #22: Added populated migration fixtures from schema version 1 through 7 and API 35 managed-device tests for large fonts, compact/wide/landscape layouts, keyboard saves, restore confirmation, permission denial, and scheduler retry.
- Updated the release recovery procedure for version 5 backups. Corrected host-test feedback assertions and kept document feedback in its intended screen while ledger state loads.

### Remaining-issue verification

- The full local gate passed 205 host tests and 29 Pixel 6 API 35 managed-device tests with no failures or skips, plus lint and debug/release builds. No personal Android device was used.
- All checks passed on commit `95bd018`, including [Android CI](https://github.com/AIF31/Financial-App/actions/runs/36046233080) and [CodeQL](https://github.com/AIF31/Financial-App/actions/runs/36046227335). PR #26 awaits merge; its issue-closing references will close #14, #15, #16, #17, #21, and #22 when merged.

### Changed

- [PR #24](https://github.com/AIF31/Financial-App/pull/24) merged the fixes for foreground date refresh (#18), exact-file backup share retry (#19), and managed-device CI reliability (#20) into `main`.
- Foreground checks now continue at most 60 seconds apart while the app is visible and recover after invalid clock signals. Backup share retry keeps the prepared file across recreation and prepares a new one if it is missing.
- Activated the `main` ruleset requiring `Android checks` and `Pixel 6 API 35 device tests`. Issues #10, #12, #13, #18, #19, and #20 are closed.

### Verification

- The local Gradle gate passed 191 host tests and 19 Pixel 6 API 35 managed-device tests, plus lint and debug/release builds, with no failures or skips. No physical device was used.
- [PR #24 CI](https://github.com/AIF31/Financial-App/actions/runs/35919284476) passed both required jobs. A temporary [failing-test PR #25](https://github.com/AIF31/Financial-App/pull/25) proved that a device-test failure blocks merging and uploads diagnostics; it was then closed and its branch deleted.

## 2026-09-23

### Documentation

- Added a dated audit for the post-consolidation standards and issue-spec review, including the then-open #18, #19, and #20 findings.
- Updated the root README to require reading and updating this changelog and maintaining dated audits after code reviews and fixes.

### Verification

- Checked the reviewed commit range, source locations, and GitHub's `main` branch protection and ruleset status at the time. No Gradle or device tests were run for this documentation change.
- A consolidated-main CI run passed. At this review, a deliberate failing-device-test exercise was unverified, `main` had no active required-check rule, and the `Basic` ruleset was disabled. The required-check rule and failure exercise were completed on 2026-09-24.

## 2026-09-17

### Added

- Added deterministic host regression coverage for coherent Room snapshots in `SnapshotConsistencyHostTest`.
- Covered initial state loading, repeated invalidations, period catch-up, backup restore, failed and canceled writes, and CSV export during an in-flight write.
- Used separate reader and writer Room connections in WAL mode so tests control transaction interleavings without sleeps or production-only test hooks.

### Changed

- Updated the operational README to link the changelog and name Pixel 10 Pro as the physical release-test device while retaining the managed-device automation workflow.
- Replaced the non-canonical phrase “Movement transfer between periods” with “moving/editing a Movement between budget periods” in the open-issues implementation plan.
- Added a README workflow rule requiring future contributors and agents to keep this changelog accurate, including verification limits and unresolved blockers.

### Verification

- Documentation links and the Pixel 10 Pro testing guidance were reviewed against the repository's physical-device preservation procedure; no device test was run for this documentation-only change.
- Static diff checks and independent standards/spec reviews passed.
- Gradle host tests and the managed-device pass/failure exercise were not run because the required `python3` compact-wrapper prerequisite was unavailable.

### External work identified at the time (resolved 2026-09-24)

- GitHub `main` had no active required-check rule and the `Basic` ruleset was disabled. A rule requiring the `Pixel 6 API 35 device tests` check was needed before issue #20 could be considered complete.
- The managed-device workflow needed a recorded passing run and deliberate-failure propagation exercise.
