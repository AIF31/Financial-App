# Online FX and Expense Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in, date-correct online FX quoting and a currency-first expense workflow while keeping same-currency entry and period catch-up fully offline.

**Architecture:** Keep the ledger authoritative in Room, with a disposable v6 FX cache beside—but outside—the portable backup model. Put quote composition behind `ExchangeRateRepository`, isolate Banxico HTTPS/JSON parsing behind a narrow client, and expose quote lifecycle through plain state holders so Compose renders deterministic loading, success, cached, and error states. Persist consent and default expense currency in DataStore; inject the Banxico token only through generated `BuildConfig`.

**Tech Stack:** Kotlin 2.3, Room 2.8, DataStore Preferences, kotlinx.serialization JSON, `HttpsURLConnection`, coroutines, Jetpack Compose Material 3, Robolectric/Compose host tests, Android migration tests, JUnit 4.

**Spec:** `C:\Users\alan1\AppData\Local\Temp\financial-app-historical-multicurrency-handoff.md` (locked decisions and PR 3 section)

## Global Constraints

- Supported currencies are exactly SAR, USD, and MXN, each with two fractional digits.
- `FxQuote(requestedDate, effectiveDate, base, quote, rate, source)` always expresses quote-currency major units per one base-currency major unit.
- Banxico series `SF43718` is MXN per USD; SAMA parity is exactly `3.75` SAR per USD; MXN/SAR quotes compose through USD.
- A Banxico observation must be numeric and no more than seven calendar days before the requested date.
- Online FX requires explicit consent. A network failure may fall back only to an eligible cached quote.
- Same-currency entry returns rate 1 offline. Period catch-up and historical recalculation never call `ExchangeRateRepository`.
- A saved Movement freezes accounting amount, rate, source, and effective date; editing preserves them until date, amount, or input currency changes.
- Requests contain only the series and requested date range. They never contain amounts, Movement metadata, Pocket data, or other financial state.
- The token comes only from an untracked `POCKET_BANXICO_TOKEN` Gradle property or environment variable. Tests and fixtures contain no token and make no live call.
- The v6 rate cache is excluded from portable backup and replacement restore.
- Primary-agent implementation only. Separate read-only Spec and Standards reviewers run before finalization.
- Physical-device work follows `docs/agents/physical-device-testing.md`; the Galaxy is never a Gradle connected-device target.

---

### Task 1: Quote model and deterministic composition

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/fx/FxModels.kt`
- Create: `app/src/main/java/com/aif31/pocket/fx/QuoteMath.kt`
- Create: `app/src/test/java/com/aif31/pocket/fx/QuoteMathTest.kt`

**Interfaces:**
- Produces: `FxQuote`, `ExchangeRateRepository`, `QuoteFailure`, `sameCurrencyQuote`, `inverseQuote`, and `composeQuotes`.
- Consumes: `SupportedCurrency`, `BigDecimal`, and `RoundingMode.HALF_UP`.

- [ ] **Step 1: Write failing quote-math tests**

Cover literal expected rates for USD→MXN, MXN→USD, USD→SAR, SAR→USD, SAR→MXN, and MXN→SAR. Assert mismatched bridge currencies, non-positive rates, and requested-date mismatches reject. Assert `FxQuote.convertMinor` rounds half up into two-digit minor units and same-currency returns exactly `1` without a provider.

- [ ] **Step 2: Verify RED with the focused host test**

Run through the compact wrapper:
`.\gradlew.bat :app:testDebugUnitTest --tests com.aif31.pocket.fx.QuoteMathTest`.
Expected: compilation fails because the quote model and composition functions do not exist.

- [ ] **Step 3: Implement the minimal quote contract**

Define:

```kotlin
data class FxQuote(
    val requestedDate: LocalDate,
    val effectiveDate: LocalDate,
    val base: SupportedCurrency,
    val quote: SupportedCurrency,
    val rate: String,
    val source: String,
) {
    fun convertMinor(amountMinor: Long): Long
}

interface ExchangeRateRepository {
    suspend fun quote(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
        forceRefresh: Boolean = false,
    ): FxQuote
}

sealed class QuoteFailure(message: String) : Exception(message) {
    class ConsentRequired : QuoteFailure("Activa la conversión en línea")
    class ConfigurationUnavailable : QuoteFailure("Proveedor de tipo de cambio no configurado")
    class Unavailable : QuoteFailure("No hay un tipo de cambio disponible para esa fecha")
}
```

Use canonical decimal strings (`stripTrailingZeros().toPlainString()`), explicit direction checks, and the earlier effective date when composing two legs.

- [ ] **Step 4: Verify GREEN**

Run the focused test and all domain tests. Expected: direct, inverse, bridged, rounding, and validation assertions pass.

- [ ] **Step 5: Commit**

Commit message: `feat: add deterministic FX quote math`.

### Task 2: Room v6 disposable rate cache

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Create: `app/src/main/java/com/aif31/pocket/fx/RoomFxQuoteCache.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt`
- Create: `app/src/test/java/com/aif31/pocket/fx/RoomFxQuoteCacheTest.kt`
- Create: `app/schemas/com.aif31.pocket.data.FinanceDatabase/6.json`

**Interfaces:**
- Produces: `FxRateCacheEntity`, DAO cache lookup/upsert/delete methods, `FinanceDatabase.MIGRATION_5_6`, and `FxQuoteCache`.
- Consumes: `FxQuote` from Task 1.

- [ ] **Step 1: Add failing migration and cache tests**

Seed schema 5 with SAR periods, a legacy manual-FX Movement, templates, and a pending transition. Migrate to v6 and assert every existing row is unchanged and `fx_rate_cache` is empty. In a host cache test, store multiple observations and assert lookup returns only the nearest effective date in `[requestedDate - 7 days, requestedDate]` for the exact base/quote direction.

- [ ] **Step 2: Verify RED**

Run the host cache test and the declared managed-device migration class through the wrapper. Expected: schema 6, migration 5→6, and cache APIs are missing.

- [ ] **Step 3: Add the cache entity and migration**

Use a cache entity keyed by `base_currency_code`, `quote_currency_code`, and `effective_epoch_day`, storing canonical `rate`, sanitized `source`, and `cached_at_utc_millis`. Add an indexed query bounded by requested epoch day and requested minus seven days, ordered newest first. Register `MIGRATION_5_6` in `open`, bump Room to version 6, and export schema 6.

- [ ] **Step 4: Verify GREEN and backup exclusion**

Run the focused cache/migration tests plus backup host tests. Assert exported backup JSON and restored Room state contain no cache field or cache row.

- [ ] **Step 5: Commit**

Commit message: `feat: add disposable Room FX cache`.

### Task 3: Banxico parsing, bounded HTTPS, and cached repository fallback

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/fx/BanxicoClient.kt`
- Create: `app/src/main/java/com/aif31/pocket/fx/DefaultExchangeRateRepository.kt`
- Create: `app/src/test/java/com/aif31/pocket/fx/BanxicoClientTest.kt`
- Create: `app/src/test/java/com/aif31/pocket/fx/DefaultExchangeRateRepositoryTest.kt`
- Create: `app/src/test/resources/banxico/fix_numeric.json`
- Create: `app/src/test/resources/banxico/fix_non_business.json`
- Create: `app/src/test/resources/banxico/fix_malformed.json`

**Interfaces:**
- Produces: `BanxicoRateSource.fetchUsdToMxn`, `HttpsBanxicoClient`, and `DefaultExchangeRateRepository`.
- Consumes: Task 1 quote math and Task 2 `FxQuoteCache`.

- [ ] **Step 1: Write failing parser and repository tests**

Use local token-free JSON fixtures and fakes. Assert the parser ignores `N/E`, comma-formatted values, malformed dates, extra series, and non-finite/non-positive numbers; chooses the nearest prior numeric observation within seven days; rejects an eighth-day observation; and sanitizes provider errors. Assert repository paths for direct, inverse, both USD bridges, forced refresh, successful cache writes, eligible fallback after network failure, stale-cache rejection, empty configuration, and coroutine cancellation.

- [ ] **Step 2: Verify RED**

Run the two focused test classes. Expected: Banxico boundary and repository are absent.

- [ ] **Step 3: Implement the provider boundary**

Request only:

```text
https://www.banxico.org.mx/SieAPIRest/service/v1/series/SF43718/datos/{start}/{end}
```

Send the token only as `Bmx-Token`, set finite connect/read timeouts, parse a bounded response body with kotlinx.serialization, and disconnect in `finally`. Preserve `CancellationException`; map all other provider/configuration errors to the safe `QuoteFailure` messages. Do not log the URL headers, response body, token, or exception details.

- [ ] **Step 4: Implement repository composition and fallback**

Return same-currency and SAMA legs locally. Use Banxico for every route that needs MXN/USD, compose through USD where required, cache successful final-direction quotes, and on provider failure query only the eligible final-direction cache entry.

- [ ] **Step 5: Verify GREEN**

Run both focused classes and the complete host unit-test suite. Expected: all provider cases pass without network access or a token.

- [ ] **Step 6: Commit**

Commit message: `feat: add secure Banxico FX repository`.

### Task 4: Build configuration, manifest security, and preference defaults

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/main/java/com/aif31/pocket/settings/AppPreferences.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApplication.kt`
- Create: `app/src/test/java/com/aif31/pocket/settings/AppPreferencesTest.kt`

**Interfaces:**
- Produces: `BuildConfig.POCKET_BANXICO_TOKEN`, `AppPreferences.onlineFxEnabled`, `AppPreferences.defaultExpenseCurrency`, `PreferencesStore.setOnlineFxEnabled`, and `PreferencesStore.setDefaultExpenseCurrency`.
- Consumes: `DefaultExchangeRateRepository` from Task 3.

- [ ] **Step 1: Write failing preference tests**

Assert an upgraded preference file with no new keys resolves online FX to false and default expense currency to SAR. Assert explicit SAR/USD/MXN choices round-trip and an unsupported stored code safely resolves to SAR.

- [ ] **Step 2: Verify RED**

Run `AppPreferencesTest`. Expected: the new properties and setters do not exist.

- [ ] **Step 3: Add secure build/runtime configuration**

Enable `buildConfig`; define a safely escaped string field from Gradle property `POCKET_BANXICO_TOKEN`, falling back to the environment variable and then empty. Add `INTERNET`, bind a network security config with cleartext disabled, and instantiate the cache/client/repository in `PocketApplication` without exposing the token through state, logs, backup, or exceptions.

- [ ] **Step 4: Add DataStore settings**

Persist consent and default input currency under new stable keys. Missing keys represent an upgrade and resolve to `false` and SAR. Onboarding will explicitly write its chosen accounting currency after initialization, making new-install behavior distinct without a migration sentinel.

- [ ] **Step 5: Verify GREEN**

Run preference tests, backup tests, lint, and debug/release assembly through the wrapper. Inspect generated source only for field presence; never print the generated value.

- [ ] **Step 6: Commit**

Commit message: `feat: configure opt-in online FX`.

### Task 5: Initial currency and quoted next-period transitions

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/main/java/com/aif31/pocket/SettingsScreens.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ui/ProductionSettingsHub.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`

**Interfaces:**
- Produces: `LedgerCommand.Initialize(accountingCurrency=...)`, onboarding currency selection, and the `Moneda y conversión` settings flow.
- Consumes: `ExchangeRateRepository`, Task 4 preferences, and existing frozen `ScheduleCurrencyChange` ledger command.

- [ ] **Step 1: Write failing ledger and plain-UI tests**

Assert initialization stores the selected accounting currency and does no quote. Assert onboarding selects SAR/USD/MXN and writes the same value as default expense currency. Render currency settings in consent-off, ready, loading, cached, configuration-error, provider-error, and pending-transition states. Assert scheduling requires an explicitly confirmed quote, leaves the current period unchanged, shows target/effective date/source/rate, and cancel removes only the pending record.

- [ ] **Step 2: Verify RED**

Run focused ledger and host UI methods. Expected: initialization is fixed to SAR and the currency settings section is absent.

- [ ] **Step 3: Implement ledger and UI seams**

Extend `Initialize` with a defaulted `SupportedCurrency.SAR` argument and store it on the first period. Add `SettingsSection.CURRENCY`; pass immutable currency-settings state and callbacks to a plain content composable. Keep repository calls in a screen-level coroutine owner keyed by requested date/base/target and cancel the running job when consent is revoked, target changes, or the user cancels.

- [ ] **Step 4: Verify GREEN**

Run the focused tests and all host UI tests. Expected: onboarding/default behavior and every currency-settings branch pass; catch-up tests prove there is no repository dependency.

- [ ] **Step 5: Commit**

Commit message: `feat: add currency onboarding and transition settings`.

### Task 6: Quote-driven expense state holder

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/expense/ExpenseEntryState.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Create: `app/src/test/java/com/aif31/pocket/expense/ExpenseEntryStateTest.kt`

**Interfaces:**
- Produces: immutable `ExpenseEntryUiState`, `QuoteUiState`, `ExpenseEntryStateHolder`, and a validated Movement draft.
- Consumes: `ExchangeRateRepository`, `AppPreferences`, `Movement`, templates, period currencies, and `LedgerCommand.AddMovement`.

- [ ] **Step 1: Write failing state-holder tests**

Assert new entry uses the preferred input currency, same-currency computes rate 1 without repository access, and foreign entry requotes when amount/date/currency changes. Assert stale async results cannot overwrite a newer request. Assert save is disabled while required quote is loading or failed. Assert an edited Movement preserves its frozen accounting amount/rate/source/effective date until date, amount, or currency changes; changing only Pocket, merchant, note, type, time, or payment method does not requote. Assert applying a template replaces input amount/currency/payment method and quotes for the current Movement date.

- [ ] **Step 2: Verify RED**

Run `ExpenseEntryStateTest`. Expected: the state holder and frozen provenance fields are missing.

- [ ] **Step 3: Add frozen provenance to Movement storage**

Extend Movement persistence with conversion effective date and source through an additive v6 column migration adjustment made before schema 6 is finalized. Same-currency saves rate `1`, effective Movement date, and source `MISMA_MONEDA`. Foreign saves use the successful quote's accounting amount, canonical rate, effective date, and source. Validate the quote direction and requested date in the ledger command boundary.

- [ ] **Step 4: Implement one-owner quote state**

Keep draft fields and quote lifecycle in the state holder; Compose runtime objects remain in composition. Use a monotonically increasing request identity or structured child-job cancellation so late results are ignored. Parse input once, derive accounting amount through `FxQuote.convertMinor`, and expose sanitized user messages only.

- [ ] **Step 5: Verify GREEN**

Run the focused state-holder tests, Movement validation tests, and backup round-trip tests. Expected: the complete frozen/edit/template matrix passes.

- [ ] **Step 6: Commit**

Commit message: `feat: add quote-driven expense state`.

### Task 7: Currency-first expense Compose workflow

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Produces: visible input-currency control beside the primary amount, accounting preview/provenance, and quote-aware Save enablement.
- Consumes: Task 6 state holder and existing navigation/ledger callbacks.

- [ ] **Step 1: Write failing plain Compose tests**

Render fixed UI states and assert currency selection is next to the primary amount; foreign success shows converted accounting amount, source, and effective date; loading disables Save; failure disables Save and leaves same-currency selection available; changing date/currency dispatches the correct callback; and template selection visibly updates amount/currency. Add one full-app wiring test for a successful fake quote save and one for edit-preservation.

- [ ] **Step 2: Verify RED**

Run the focused host UI methods. Expected: the old form asks for manual accounting/original amounts and exposes estimated/confirmed controls.

- [ ] **Step 3: Replace manual-FX controls with the state-driven form**

Use Material 3 controls and stable semantics on the actual currency selector. Keep the amount field primary, show the converted accounting value immediately below it, keep date in the details section, and expose provider/cache status without credential or raw exception content. Save receives only a validated draft from the state holder.

- [ ] **Step 4: Verify GREEN**

Run all host UI tests. Then run the focused managed-device production expense test through the wrapper; do not use a connected-device Gradle task.

- [ ] **Step 5: Commit**

Commit message: `feat: add currency-first expense workflow`.

### Task 8: Review, release gates, stacked PR, and terminal CI

**Files:**
- Modify only files required to remediate validated Critical/Important review findings.

**Interfaces:**
- Produces: reviewed PR 3 branch and stacked GitHub pull request.
- Consumes: every prior task and `docs/agents/physical-device-testing.md`.

- [ ] **Step 1: Run read-only reviews**

Dispatch separate Spec and Standards reviewers over `codex/currency-ledger-foundation..HEAD`. The Spec reviewer checks every locked PR 3 decision and release gate. The Standards reviewer checks Kotlin/Room/DataStore/Compose lifecycle, HTTPS/privacy/security, migration/backup safety, test quality, and personal-device preservation. The primary agent validates and fixes every Critical/Important finding test-first.

- [ ] **Step 2: Run the final compact Gradle gate**

Through one wrapper workflow run:

```text
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:pixel6Api35DebugAndroidTest
```

Expected: host tests, lint, both app builds, test APK, Room migrations, accessibility/navigation, and the declared Pixel 6 API 35 suite all pass.

- [ ] **Step 3: Decide physical-device scope from the safety guide**

Audit a non-destructive allowlist before any Galaxy command. If every target is proven safe, use serial-qualified `adb install -r`, `adb install -r -t`, and direct `am instrument`; parse instrumentation status codes, compare protected fingerprints without reading values, restore animation settings, and leave the app installed. Otherwise record the managed-device evidence and stop physical testing.

- [ ] **Step 4: Verify the exact diff and commit state**

Run `git diff --check`, inspect `git status`, compare against `codex/currency-ledger-foundation`, and confirm no token, cache data, personal values, build output, or local configuration is tracked.

- [ ] **Step 5: Push and create the stacked PR**

Push `codex/online-fx-expense-workflow` and open a pull request against `codex/currency-ledger-foundation`. The description lists behavior, migration/backup guarantees, privacy constraints, test counts, managed-device evidence, and any intentionally omitted physical-device test.

- [ ] **Step 6: Watch GitHub checks to terminal state**

Poll the PR head workflow with the connected GitHub app until `Android checks` succeeds or fails. On failure, inspect the bounded job evidence, reproduce through the compact wrapper, remediate test-first, push, and watch the replacement run to terminal state.
