# Currency-Safe Ledger Foundation and Payment Defaults Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the period-scoped currency, frozen transition, portable-backup, recurring-template, and default-payment foundations needed before online FX is exposed.

**Architecture:** Keep all accounting state in Room so period creation, currency transitions, rollover recalculation, payment-default clearing, and replacement restore remain transactional. Represent every boundary quote in the explicit prior-period-to-target-period direction and convert minor units only through a tested `BigDecimal` helper. Keep PR 2 offline: UI remains operationally SAR while labels and models become currency-aware for PR 3.

**Tech Stack:** Kotlin, Room 2.8, kotlinx.serialization JSON, Jetpack Compose Material 3, Robolectric/Compose host tests, Android migration tests, JUnit 4.

**Spec:** `C:\Users\alan1\AppData\Local\Temp\financial-app-historical-multicurrency-handoff.md` (locked decisions and PR 2 section)

## Global Constraints

- Supported currencies are exactly SAR, USD, and MXN, each with two fractional digits.
- Existing periods, Movement accounting amounts, and recurring templates migrate to SAR without loss.
- A pending currency change applies only to the first missing successor; later successors continue in the new currency.
- Boundary rates are frozen, directed from the prior period currency to the target period currency, and never fetched by catch-up or recalculation.
- All conversion uses `BigDecimal` and `RoundingMode.HALF_UP` into target minor units.
- Backups v1-v3 restore as SAR; backup v4 contains currency and transition provenance but never credentials or disposable caches.
- Production currency selection remains hidden until PR 3.
- The default payment method is an active `Tarjeta` for fresh/upgraded ledgers when available, may be `Ninguno`, is overridden by templates, and is cleared when archived.
- Preserve existing user data and migration compatibility; never uninstall or clear the physical-device app.

---

### Task 1: Supported currencies, directed conversion, and formatting

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/domain/DomainRules.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ui/MoneyText.kt`
- Modify: `app/src/test/java/com/aif31/pocket/domain/DomainRulesTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/ui/MoneyTextTest.kt`

**Interfaces:**
- Produces: `SupportedCurrency`, `FrozenRate`, `convertMinor`, and `MoneyText.format`.
- Consumes: Java `BigDecimal` and the existing two-digit `Money` representation.

- [ ] **Step 1: Write failing currency-domain tests**

```kotlin
@Test fun `supported currencies reject all other codes`() {
    assertEquals(SupportedCurrency.MXN, SupportedCurrency.fromCode("mxn"))
    assertFailsWith<IllegalArgumentException> { SupportedCurrency.fromCode("EUR") }
}

@Test fun `directed frozen rate converts minor units half up`() {
    val quote = FrozenRate(SupportedCurrency.SAR, SupportedCurrency.MXN, "4.533")
    assertEquals(453L, quote.convertMinor(100L))
    assertFailsWith<IllegalArgumentException> {
        quote.convert(Money(100, "USD"))
    }
}
```

- [ ] **Step 2: Run the two focused test classes and verify RED**

Run through the compact Gradle wrapper: `:app:testDebugUnitTest --tests com.aif31.pocket.domain.DomainRulesTest --tests com.aif31.pocket.ui.MoneyTextTest`.
Expected: FAIL because the currency types and generic formatter do not exist.

- [ ] **Step 3: Add the minimal domain and formatting APIs**

```kotlin
enum class SupportedCurrency { SAR, USD, MXN;
    companion object {
        fun fromCode(value: String): SupportedCurrency =
            entries.firstOrNull { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Moneda no compatible")
    }
}

data class FrozenRate(val from: SupportedCurrency, val to: SupportedCurrency, val value: String) {
    fun convertMinor(minor: Long): Long = BigDecimal.valueOf(minor)
        .multiply(BigDecimal(value))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}
```

Add `MoneyText.format(minor, currency)` and retain `sar` as a delegating compatibility helper until all callers are generalized.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Expected: both test classes PASS with explicit direction, rounding, and currency labels.

- [ ] **Step 5: Commit**

```text
feat: add supported currency primitives
```

### Task 2: Room v5 schema and lossless v4 migration

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Create: `app/schemas/com.aif31.pocket.data.FinanceDatabase/5.json`
- Modify: `app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt`

**Interfaces:**
- Produces: v5 entities for period boundary currency, pending transition, ledger preferences, generic Movement accounting amount, and template input currency.
- Consumes: `SupportedCurrency` and existing v4 tables/indexes/foreign keys.

- [ ] **Step 1: Add a failing v4-to-v5 migration test**

Seed a v4 database with one period, a manual USD Movement, a `Tarjeta` payment method, and a recurring template. Migrate with `FinanceDatabase.MIGRATION_4_5`, then assert:

```kotlin
assertEquals("SAR", period.string("accounting_currency_code"))
assertEquals(12_345L, movement.long("accounting_amount_minor"))
assertEquals(10_000L, movement.long("original_amount_minor"))
assertEquals("USD", movement.string("original_currency_code"))
assertEquals("CONFIRMED", movement.string("conversion_status"))
assertEquals("1.2345", movement.string("rate"))
assertEquals("SAR", template.string("input_currency_code"))
assertEquals(cardId, preferences.string("default_payment_method_id"))
```

Also assert the rebuilt Movement foreign keys and indexes, the empty pending-change singleton table, and Room schema validation.

- [ ] **Step 2: Run the migration test and verify RED**

Run through the wrapper: `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.data.FinanceDatabaseMigrationTest` when a device is available, or the declared managed-device equivalent.
Expected: FAIL because schema v5 and migration 4→5 do not exist.

- [ ] **Step 3: Define v5 entities and migration**

Add to `PeriodEntity`:

```kotlin
val accountingCurrencyCode: String = SupportedCurrency.SAR.name
val priorBoundaryRate: String? = null
val priorBoundaryEffectiveEpochDay: Long? = null
val priorBoundarySource: String? = null
```

Rebuild `movements` with `accounting_amount_minor` replacing `sar_amount_minor`, preserving all rows, foreign keys, and indexes. Add `input_currency_code TEXT NOT NULL DEFAULT 'SAR'` to `recurring_templates`. Add singleton `pending_currency_change` and `ledger_preferences` tables; seed the latter with active `Tarjeta` when present. Register `MIGRATION_4_5` and bump the database to version 5.

- [ ] **Step 4: Export schema 5 and verify GREEN**

Run the migration test, then `:app:assembleDebug` through the wrapper. Expected: Room validates v5 and every seeded legacy value is unchanged apart from generic naming/defaulted SAR metadata.

- [ ] **Step 5: Commit**

```text
feat: migrate ledger storage to currency-aware schema
```

### Task 3: Frozen pending transitions and deterministic catch-up

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`

**Interfaces:**
- Produces: `PendingCurrencyChange`, `LedgerCommand.ScheduleCurrencyChange`, `LedgerCommand.CancelCurrencyChange`, and period boundary provenance in `LedgerState`.
- Consumes: `FrozenRate.convertMinor` and v5 DAOs.

- [ ] **Step 1: Write failing ledger tests for scheduling and catch-up**

Cover all of these behaviors in focused tests:

```kotlin
ledger.execute(ScheduleCurrencyChange(MXN, "4.50", effectiveDate, "TEST"))
clock.advanceToTwoPeriodsLater()
ledger.execute(CatchUpPeriods(25))
```

Assert the first new period is MXN with the frozen boundary quote, copied new funds/budgets/rollover converted once, the second new period remains MXN without a new boundary quote, and the pending singleton is consumed. Run catch-up again and assert byte-for-byte-equivalent financial state.

- [ ] **Step 2: Run the focused tests and verify RED**

Expected: FAIL because scheduling and currency-aware period creation do not exist.

- [ ] **Step 3: Implement transactional scheduling and transition application**

Validate that the target differs from the current currency, rate is positive, and provenance is nonblank. In `createPeriodAfter`, consume the pending row only for the first successor, write the directed frozen quote to the target period, convert copied new funds, budgets, and calculated rollover, then delete the pending row. Subsequent successors copy in the already-current currency with no conversion.

- [ ] **Step 4: Run transition and existing catch-up tests and verify GREEN**

Expected: new transition tests and all existing catch-up/long-period tests PASS offline with no repository/network dependency.

- [ ] **Step 5: Commit**

```text
feat: apply frozen currency transitions during catch-up
```

### Task 4: Cross-currency rollover recalculation and comparisons

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`

**Interfaces:**
- Produces: boundary-aware rollover cascading and normalized previous-period comparison values.
- Consumes: each target period's frozen prior-boundary quote.

- [ ] **Step 1: Write failing mixed-currency cascade tests**

Create SAR → MXN → USD periods with distinct frozen rates, edit a Movement in the original SAR period, and assert rollover is recomputed sequentially through both target currencies without altering later new funds, budgets, or Movements. Assert current comparison uses the immediate target boundary rate and is labeled with the current period currency in state.

- [ ] **Step 2: Run the focused tests and verify RED**

Expected: FAIL because rollover and comparison currently sum the old `sarAmountMinor` unchanged across boundaries.

- [ ] **Step 3: Convert at every target boundary**

Calculate source-period availability in the source currency, then convert the resulting rollover with the target period's directed frozen rate before writing target allocations/releases. Normalize only the prior-period comparison through the same immediate boundary. Reject or omit a comparison when currencies differ and the target has no valid frozen provenance; never emit an unlabeled mixed-currency total.

- [ ] **Step 4: Run the focused and complete ledger host suite and verify GREEN**

Expected: mixed-currency cascades and all SAR regression tests PASS.

- [ ] **Step 5: Commit**

```text
feat: recalculate rollover across frozen currency boundaries
```

### Task 5: Portable backup v4 and legacy restore

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketLedgerBehaviorTest.kt`

**Interfaces:**
- Produces: backup payload version 4 and v1-v3 SAR-defaulting restore.
- Consumes: all v5 Room entities, including pending transition and ledger preference singletons.

- [ ] **Step 1: Write failing backup compatibility tests**

Assert a v4 round trip preserves period currencies, directed boundary quote metadata, generic Movement accounting/original conversion provenance, template input currency, pending transition, and default payment method. Restore representative v1, v2, and v3 payloads and assert every period/template/accounting amount defaults to SAR. Assert serialized output contains neither `POCKET_BANXICO_TOKEN` nor cache/token fields.

- [ ] **Step 2: Run focused backup tests and verify RED**

Expected: FAIL because the codec currently emits version 3 and omits v5 entities/fields.

- [ ] **Step 3: Implement version-4 DTOs and defaults**

Use nullable/defaulted DTO properties when decoding older versions, normalize legacy period and template currencies to SAR, restore all Room data in the existing replacement transaction, and include pending/default singleton rows only from supported payload fields. Keep the maximum-size and relationship validation gates.

- [ ] **Step 4: Run host and device backup tests and verify GREEN**

Expected: v1-v4 restore and v4 round trip PASS; malformed currencies/rates/relationships reject without changing the target ledger.

- [ ] **Step 5: Commit**

```text
feat: upgrade portable backups for currency state
```

### Task 6: Default payment method and template input currency

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify: `app/src/main/java/com/aif31/pocket/SettingsScreens.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`

**Interfaces:**
- Produces: `LedgerState.defaultPaymentMethodId`, `LedgerCommand.SetDefaultPaymentMethod`, and `RecurringTemplate.inputCurrency`.
- Consumes: active payment methods, saved Movement values, and template selection.

- [ ] **Step 1: Write failing ledger and host UI tests**

Assert fresh data selects active `Tarjeta`; Settings can select another active method or `Ninguno`; archiving the selected method clears it; new Movement entry uses the default; editing retains the Movement's saved method; applying a template uses the template method and input currency instead of the default.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: FAIL because the ledger exposes no default and expense entry starts without one.

- [ ] **Step 3: Implement the preference and precedence rules**

Expose the Room singleton through `LedgerState`, validate `SetDefaultPaymentMethod` against active methods, clear it transactionally in `ArchivePaymentMethod`, and add an active-method/`Ninguno` selector in Settings. Initialize expense state in this order: saved Movement method, applied template method, ledger default; templates also carry `inputCurrency` (SAR in production until PR 3).

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: ledger and Compose host tests PASS for default, none, archive clearing, edit preservation, and template override.

- [ ] **Step 5: Commit**

```text
feat: add default payment and template currency behavior
```

### Task 7: Currency-aware production labels and full PR gate

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MovementsScreen.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ui/ProductionDashboard.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Produces: all accounting labels formatted with the owning period currency.
- Consumes: `Period.accountingCurrency`, `Movement.accountingAmountMinor`, and `MoneyText.format`.

- [ ] **Step 1: Write failing host UI assertions for period-owned labels**

Render a synthetic MXN historical period and assert its Pockets totals/details and Movement accounting values use `MXN`; render the current SAR period and assert existing SAR production behavior remains unchanged. Assert no currency-change selector is visible in PR 2.

- [ ] **Step 2: Run the focused host UI test and verify RED**

Expected: FAIL at the remaining hardcoded SAR formatter/label sites.

- [ ] **Step 3: Generalize labels without exposing PR 3 controls**

Pass the owning period's `SupportedCurrency` to every amount formatter and rename SAR-specific model properties/usages to accounting semantics. Keep onboarding and expense currency selection behavior fixed to SAR except for template/default plumbing already required by PR 2.

- [ ] **Step 4: Run the focused UI suites and verify GREEN**

Expected: current SAR screens remain identical and historical non-SAR fixtures render only their original currency.

- [ ] **Step 5: Run review and verification gates**

Request separate read-only Standards and Spec reviews. Fix every validated Critical/Important issue test-first. Through one compact Gradle workflow run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:pixel6Api35DebugAndroidTest`. Then install with `adb install -r` on the Galaxy S23 Ultra, compare protected data hashes before/after, verify migration/UI/crash logs without reading personal values, and confirm the existing user data remains intact.

- [ ] **Step 6: Commit, push, and open the stacked PR**

```text
feat: make ledger presentation currency aware
```

Push `codex/currency-ledger-foundation` and open PR 2 with base `codex/historical-pockets` while PR #5 is unmerged. Confirm the `Android checks` GitHub job completes successfully.
