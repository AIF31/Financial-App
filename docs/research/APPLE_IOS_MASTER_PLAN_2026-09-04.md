# Pocket for Apple platforms: master integration plan

Status: research and planning; no Apple implementation has started  
Research snapshot: 2026-09-04  
Primary target: iPhone and iPad  
Android baseline: Pocket 1.0.0 / `com.aif31.pocket`

## Executive decision

Build a native iPhone/iPad application in Swift and SwiftUI, in this repository, while treating the Android v1.0 behavior and the version-4 `.pocketbackup` format as conformance specifications. Do **not** begin by converting the existing Android application to Compose Multiplatform or moving the whole ledger to Kotlin Multiplatform (KMP).

The lowest-risk sequence is:

1. Freeze platform-neutral behavior as documented invariants, JSON fixtures, and expected-results tests.
2. Build a small native iOS vertical slice on a Mac: onboarding -> one Pocket budget -> one expense -> persistence -> relaunch.
3. Prove byte-level backup interchange in both directions before scaling the UI.
4. Complete feature parity and native iPhone/iPad UX.
5. Add automated Apple CI, TestFlight, privacy/signing/review gates, and only then release.
6. After parity, run a bounded KMP experiment on pure calculation code. Adopt it only if measured maintenance savings justify the build and interoperability cost.

This recommendation is specific to the repository. The current UI is entirely Android Compose/Material, navigation and lifecycle are Android-specific, persistence is Room, preferences are DataStore, reminders are WorkManager, and the file flows use Android activity-result contracts. The most portable code is the financial rules, but even those currently depend on JVM `java.math` and `java.time`. A shared-UI migration would therefore rewrite most layers and put the stable Android 1.0 app at risk before producing an Apple build.

## Requirement legend

- **Mandatory**: needed to compile/run the chosen target, meet an enforced Apple submission rule, or preserve an existing Pocket invariant.
- **Recommended**: the plan's default for quality, security, or maintainability; a recorded decision may replace it.
- **Conditional**: needed only if the relevant feature, distribution path, entitlement, territory, or dependency is selected.

Apple changes submission rules regularly. Recheck [Upcoming Requirements](https://developer.apple.com/news/upcoming-requirements/), [Xcode system requirements](https://developer.apple.com/xcode/system-requirements/), and the [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) at project kickoff and before every release.

## 1. What exists on Android 1.0

The following is derived from the checked-in source, `CONTEXT.md`, `Info/implementation-reference.md`, schema files, and tests as of this plan.

### Product and trust boundary

- Spanish-first, single-user, local-first personal spending app; no bank accounts, login, backend, analytics, ads, or automatic transaction import.
- Financial records remain app-private. The only current network function is user-enabled exchange-rate lookup from Banxico over HTTPS.
- Money is stored as signed 64-bit integer minor units. Financial calculations avoid binary floating point.
- The accounting model fixes period and Movement interpretation around `Asia/Riyadh`, with a default period start day of 25.
- Portable backups are versioned plaintext JSON; restore previews, validates, and transactionally replaces the ledger. CSV is analytical export, not restore input.

### Implemented behavior that the Apple app must not silently reinterpret

- Configurable budget periods, automatic sequential catch-up, and long transition periods after a future start-day change.
- SAR, USD, and MXN accounting/original currency rules; frozen currency-boundary quotes and estimated/confirmed Movement conversion state.
- New funds, Pocket budgets, unassigned funds, opt-in positive rollover, refund and negative-availability behavior, rollover release, and archive invariants.
- Editable/reorderable/archivable Pockets and payment methods; recurring templates prefill but never automatically create Movements.
- Dashboard totals, progress thresholds, projection, prior-period comparison, search/filter history, edit/delete/undo.
- Local daily reminder with no financial amount in lock-screen text.
- Home-screen `Nuevo gasto` quick action.
- Version-4 `.pocketbackup` export/import and CSV export.
- A database currently at Room schema version 6, with migration and rollback tests.

### Portability inventory

| Android element | Apple equivalent or decision | Reuse level |
| --- | --- | --- |
| Kotlin domain models and rules | Swift value types plus conformance fixtures; possible later KMP module | Semantics reusable; code initially rewritten |
| Jetpack Compose + Material 3 | SwiftUI using Apple navigation, controls, Dynamic Type, VoiceOver, and iPad layouts | Information architecture and branding reusable; widget code is not |
| Navigation 3 | `TabView`, `NavigationStack`, and adaptive iPad composition | Routes/flows reusable conceptually |
| Room/SQLite schema v6 | Storage adapter behind a Swift `PocketLedger` protocol; choose SwiftData/Core Data/raw SQLite in a spike | Data model and invariants reusable; Room code is not |
| DataStore preferences | `UserDefaults` or a small platform settings store | Keys/behavior reusable, implementation is not |
| Kotlin coroutines/Flow | Swift structured concurrency, actors, `AsyncSequence`/Observation | Concurrency contract reusable |
| `HttpsURLConnection` | `URLSession`, with App Transport Security defaults | Protocol and parsing fixtures reusable |
| WorkManager reminder | `UNUserNotificationCenter` and repeating calendar trigger | User behavior reusable; scheduler code is not |
| Android Storage Access Framework | SwiftUI `fileImporter`/`fileExporter` or `UIDocumentPicker`; custom `UTType` | Backup bytes reusable |
| Android launcher shortcut | static/dynamic Home Screen quick action, optionally App Intents later | Action semantics reusable |
| Android instrumentation/Compose tests | Swift Testing for unit/integration; XCTest/XCUIAutomation for UI | Scenarios and fixtures reusable; test code is not |

## 2. Hardware, accounts, and software

### Development machine

- **Mandatory:** a Mac capable of running a supported Xcode/macOS combination. Apple’s current table lists Xcode 26.6 on macOS Tahoe 26.2 through 26.x, with the iOS 26.5 SDK and iOS 15–26.5 deployment targets. Xcode 27 is beta and should not be the release baseline. See [SDK and system requirements](https://developer.apple.com/xcode/system-requirements/).
- **Mandatory for App Store uploads now:** builds uploaded since April 28, 2026 must use Xcode 26 or later and an iOS 26 SDK. See [Upcoming Requirements](https://developer.apple.com/news/upcoming-requirements/).
- **Recommended:** Apple-silicon Mac with enough storage for Xcode, Derived Data, archives, and multiple simulator runtimes. Select an actual model only after checking the current Xcode/macOS support table; buying the minimum-supported machine shortens its useful maintenance life.
- **Mandatory operational fact:** Windows can continue to edit Swift/Kotlin and run platform-neutral tests, but it cannot be the authoritative iOS build, simulator, signing, archive, or submission host. KMP also requires a macOS host with Xcode for Apple targets; JetBrains states this explicitly in its [KMP quickstart](https://kotlinlang.org/docs/multiplatform/quickstart.html).
- **Recommended:** keep the existing Windows Android checkout as canonical for Android, but add a Mac checkout of the same Git repository for Apple work. Do not use an unofficial virtualized macOS environment as the release foundation.

### Test devices

- **Mandatory for confidence, recommended from the first milestone:** at least one physical iPhone. Apple warns that Simulator does not reproduce every hardware feature or performance characteristic and directs developers to validate on physical devices in [Running your app on simulated or physical devices](https://developer.apple.com/documentation/Xcode/running-your-app-on-simulated-or-physical-devices).
- **Recommended:** one current smaller iPhone class and one current large iPhone class in Simulator, plus a physical iPhone on the oldest supported major iOS version when practical.
- **Recommended if the binary supports iPad:** test at least one compact iPad and one large iPad class, portrait/landscape, Split View/multitasking, keyboard, and pointer.
- **Conditional:** disable iPad support for the first public version only through an explicit product decision. SwiftUI can make a universal binary feasible, but shipping an unverified stretched phone UI on iPad is not acceptable.

### Apple accounts and membership

- **Mandatory to start:** an Apple Account signed into Xcode. A free account can run an app on a personal device; Xcode automatic signing can create a development profile. See Apple’s [program enrollment FAQ](https://developer.apple.com/help/account/membership/program-enrollment) and [device-running instructions](https://developer.apple.com/documentation/Xcode/running-your-app-on-simulated-or-physical-devices).
- **Mandatory for TestFlight and App Store distribution:** Apple Developer Program membership. The current fee is USD 99 per membership year (local pricing may vary). See [Apple Developer Program](https://developer.apple.com/programs/) and [Choosing a Membership](https://developer.apple.com/support/compare-memberships/).
- **Mandatory account decision before enrollment:** individual versus organization. Individual enrollment displays the person's legal name as seller. Organization enrollment displays the legal entity and requires legal-entity status, authority to bind it, a work-domain email, public website, and usually a D-U-N-S number. See [Program enrollment](https://developer.apple.com/help/account/membership/program-enrollment).
- **Mandatory:** two-factor authentication for the relevant Apple accounts/App Store Connect access. See [Sign in to your developer account](https://developer.apple.com/help/account/access/sign-in-to-your-developer-account/).
- **Recommended:** enroll the long-term product owner, not a contractor. If contractors contribute, the distributing organization should own the membership and add them to the team; Apple documents this in [Program enrollment](https://developer.apple.com/help/account/membership/program-enrollment).
- **Recommended:** name one Account Holder and at least one operational backup with the minimum required role. The Account Holder must renew membership and accept changing agreements; expired membership makes apps unavailable and disables distribution services. See [Roles and access](https://developer.apple.com/help/account/access/roles) and [Resolving access issues](https://developer.apple.com/help/account/access/resolving-access-issues).

### Software toolchain

- **Mandatory:** current production Xcode 26.x, its iOS SDK/platform support, Simulator runtimes, Xcode Command Line Tools, and accepted license/first-launch setup.
- **Mandatory for this recommended architecture:** Swift, SwiftUI, Foundation, Observation, UserNotifications, UniformTypeIdentifiers, and an Apple persistence framework or SQLite adapter.
- **Recommended:** Swift Package Manager for any dependencies; prefer Apple frameworks and the standard library first.
- **Recommended:** Xcode's Instruments, Memory Graph, Organizer, Accessibility Inspector, and XCTest tooling. Xcode collects these tools under its [Xcode documentation](https://developer.apple.com/documentation/xcode).
- **Conditional:** Android Studio or IntelliJ IDEA and the KMP plugin only for a KMP experiment. JetBrains' current KMP setup requirements are in the [KMP quickstart](https://kotlinlang.org/docs/multiplatform/quickstart.html).
- **Conditional:** Transporter for a non-Xcode upload workflow. Apple supports Xcode, Transporter, and API/command-line upload paths in [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/).

### Initial target baseline

- **Recommended product decision:** iOS/iPadOS 17 or later for v1.0. This is not an App Store mandate. It permits SwiftData and modern SwiftUI APIs while retaining a multi-major-version range. Validate the supported-user/device need before locking it.
- **Alternative:** choose iOS 16 or earlier only if measured audience requirements justify additional persistence/UI compatibility work. Xcode 26.6 can deploy as low as iOS 15 according to the current support table.
- **Mandatory:** set one explicit deployment target in source-controlled project settings and run CI on both the oldest supported runtime and a current runtime.

## 3. Architecture choice

### Phase-0 Kotlin/Android/Xcode compatibility gate

The current repository uses Kotlin/KGP 2.3.20 and Android Gradle Plugin 9.3.1. JetBrains' current official [KMP compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) says KMP plugin 2.3.20–2.3.21 supports AGP only through 9.0.0 and Xcode 26.0. Therefore, simply adding iOS targets to the current Gradle build would use an unsupported AGP combination. This is a release-blocking fact for either KMP option, not a warning to ignore.

JetBrains currently lists Kotlin 2.4.0–2.4.10 with AGP through 9.1.0 and Xcode 26.4. That is newer, but still does not cover this repository's AGP 9.3.1 or Apple's current stable Xcode 26.6. Apple accepts Xcode 26+ uploads, so a deliberately pinned older production Xcode may satisfy Apple while remaining within a JetBrains-tested matrix; it still needs a compatible Android/Kotlin/AGP set.

Before selecting KMP or Compose Multiplatform, choose and prove one of these paths:

1. **Wait:** keep Android 1.0 on its current toolchain and build native Swift until JetBrains publishes support for the repository's AGP/Xcode combination.
2. **Align:** move the entire repository to an officially supported Kotlin/AGP/Xcode matrix, then rerun every Android build, host/device test, lint, release-minification, and signing gate.
3. **Isolate and prove:** prototype a separately versioned/published KMP module without changing the Android app toolchain, and prove Gradle/plugin/artifact compatibility. Do not assume multiple Kotlin plugin versions in one build are safe.

**Phase-0 exit gate for KMP:** the exact Kotlin, Gradle, AGP, Compose, Xcode, macOS, and JDK tuple is written down; it falls within vendor-supported ranges; clean Android Debug/Release and test gates pass; iOS device/simulator frameworks build; and CI reproduces both. Until that gate passes, KMP reuse is an option, not the master plan's foundation.

### Fair comparison for this repository

| Choice | Reuses Kotlin domain/data | Apple-native UI | Android disruption | Initial build risk | Long-term tradeoff | Rough first-release effort* |
| --- | --- | --- | --- | --- | --- | --- |
| Full native Swift/SwiftUI rewrite | No code; yes contracts/fixtures | Yes | Low | Lowest toolchain risk; highest semantic duplication risk | Two implementations kept aligned by conformance tests | 23–41 person-weeks |
| KMP shared logic/data + SwiftUI | Potentially substantial after refactor | Yes | Medium/high because current code is Android/JVM-coupled and the toolchain tuple is unsupported | Toolchain/interoperability/persistence refactor before feature delivery | One core, two native UIs; coordinated releases | 27–48 person-weeks |
| Compose Multiplatform shared logic/UI | Potentially highest | No, though native APIs remain usable | Highest; current Compose code uses Android-only libraries | Toolchain plus broad UI/platform/accessibility rewrite | One UI codebase, greater framework/release coupling | 26–46 person-weeks, highest uncertainty |

\*Order-of-magnitude planning ranges, not bids or schedule commitments. They assume one experienced full-time Apple engineer with regular help from the Android/domain owner, existing designs, no backend/account/monetization, and parity with the checked-in v1.0. Add roughly 50% contingency until A2/A3 complete. Enrollment, hardware procurement, App Review wait, unfamiliar-tool learning, and new scope are not included. Parallel staffing does not reduce the inherently sequential recovery/domain gates proportionally.

### Option A — native SwiftUI application (recommended)

Structure the Apple app as native UI and platform adapters around an intentionally platform-neutral Swift domain core.

**Benefits**

- Direct use of Apple UI, navigation, accessibility, files, notification, background, security, and lifecycle APIs.
- Lowest risk to the released Android application.
- Natural use of Swift Testing, XCTest, Xcode previews, Instruments, TestFlight, and Apple review tooling.
- The source remains debuggable by an Apple-platform developer without Kotlin/Native interop.

**Costs**

- Business rules are implemented twice until/unless a later KMP extraction succeeds.
- Drift is possible unless shared fixtures and parity tests are enforced.

**Mitigation:** make the v4 backup schema, command/result scenarios, date boundary cases, conversion vectors, and expected snapshots a cross-platform conformance suite. Duplicate code is less dangerous here than duplicate undocumented semantics.

### Option B — KMP shared business/domain logic with native SwiftUI (future candidate)

KMP can export a shared Kotlin module as an iOS framework consumed by Swift. JetBrains explicitly supports a spectrum from small shared logic to larger data/state layers and describes native SwiftUI with separated `sharedLogic` as a recommended project shape in [How to build Android and iOS apps](https://kotlinlang.org/docs/multiplatform/build-ios-android-app.html) and [Recommended KMP project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html).

**Good later candidates**

- `Money`, supported currencies, exact conversion and rounding.
- Budget-calendar boundary construction, long-transition scheduling, projections, Pocket summary, and rollover math.
- Backup DTO definitions/validation and shared golden fixtures.
- Pure ledger command validation once separated from Room transactions.

**Required migration work**

- Create a separate KMP library module. AGP 9+ requires Android entry points to be separate from common code in JetBrains' [recommended structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html).
- Replace or wrap JVM-only `java.math.BigDecimal`, `BigInteger`, `java.time`, regex/locale behavior, and Android dependencies with common APIs or small `expect`/`actual` boundaries.
- Add `iosArm64` and `iosSimulatorArm64`; without a simulator target local iOS debugging/tests cannot run. See [KMP project basics](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html).
- Design a stable Swift-facing API and concurrency boundary; generated framework APIs are not automatically idiomatic Swift.
- Choose direct Xcode integration initially. JetBrains documents direct, CocoaPods, and remote SwiftPM/XCFramework choices in [iOS integration methods](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html).

**Adoption gate:** implement a time-boxed spike containing `Money`, `BudgetCalendar`, `PocketMath`, and their tests. Measure clean-build time, incremental build time, Swift call-site quality, debugger behavior, CI complexity, and how many production lines/invariants it actually centralizes. Do not adopt it merely to maximize reuse.

If the Phase-0 alignment gate can be satisfied without destabilizing Android, this becomes the preferred *long-term* architecture: SwiftUI for the Apple surface, with only proven platform-neutral financial logic/data shared. It is not the preferred way to start today because the current version tuple is unsupported and the ledger is still Room/JVM-coupled.

### Option C — Compose Multiplatform shared UI (not recommended for the first Apple release)

Compose Multiplatform can share UI while retaining platform entry points, but this repository's UI is written against Android Jetpack Compose Material 3, Android Navigation 3, Android lifecycle collection, Android resources, and Android system bridges. “Compose” in the name does not make that code directly portable.

This option requires:

- moving UI and presentation code into multiplatform source sets;
- replacing Android-only libraries and resources;
- defining iOS navigation, safe-area, keyboard, pointer, accessibility, document, notification, and lifecycle integration;
- validating App Store privacy manifests for every packaged runtime/SDK;
- accepting a non-native UI dependency and its release cadence.

Revisit only after the native parity release or after a prototype proves that Apple's interaction, accessibility, performance, and review expectations remain excellent. JetBrains describes shared UI as one valid point on the KMP spectrum, not a universal default, in [its architecture guide](https://kotlinlang.org/docs/multiplatform/build-ios-android-app.html).

## 4. Proposed repository and module shape

```text
Financial-App/
├── app/                         # existing Android app
├── ios/
│   ├── Pocket.xcodeproj/        # or generated project only if generation is deliberately adopted
│   ├── Pocket/
│   │   ├── App/
│   │   ├── Domain/
│   │   ├── Ledger/
│   │   ├── Persistence/
│   │   ├── Features/
│   │   ├── Platform/
│   │   ├── Resources/
│   │   └── PrivacyInfo.xcprivacy
│   ├── PocketTests/
│   └── PocketUITests/
├── conformance/
│   ├── backup/v1-v4/
│   ├── domain-vectors/
│   └── ledger-scenarios/
└── docs/apple/
    ├── build-and-run.md
    ├── signing-and-release.md
    ├── physical-device-testing.md
    └── release-verification.md
```

- **Mandatory:** do not move the released Android source merely to make the tree look symmetrical.
- **Recommended:** use one repository so backup fixtures and behavior changes are reviewed atomically across platforms.
- **Recommended:** keep the Xcode project and shared scheme source-controlled. Avoid committing user state, Derived Data, archives, signing identities, profiles, tokens, or private test data.
- **Recommended:** add an Apple-specific physical-device procedure modeled on `docs/agents/physical-device-testing.md`; it must protect personal financial data and use synthetic fixtures.

## 5. Apple implementation design

### Domain and financial correctness

- **Mandatory:** preserve canonical vocabulary from `CONTEXT.md`; a Pocket is not an account/category/envelope and new funds are not income.
- **Mandatory:** use `Int64` minor units for all stored accounting/original amounts and checked arithmetic for overflow. Never use `Float` or `Double` for money.
- **Mandatory:** specify decimal rate parsing, canonical serialization, multiplication, and half-up rounding with cross-platform golden vectors. Swift `Decimal`/`NSDecimalNumber` behavior must be proved against the Kotlin `BigDecimal` results, not assumed equivalent.
- **Mandatory:** preserve stable string identifiers and enum wire values used by backup versions 1–4.
- **Mandatory:** preserve UTC occurrence time, local date, and IANA zone ID separately. Validate `Asia/Riyadh`, month-end clamping, leap years, DST-independent Riyadh cases, and imports containing supported historical data.
- **Recommended:** define a Swift `PocketLedger` protocol mirroring the current command/result boundary, with an actor-owned implementation. UI sends commands; persistence does not leak into SwiftUI views.
- **Mandatory:** a financial mutation that updates related rows, catch-up sequence, restore, or historical recalculation must be atomic.

### Persistence decision spike

Before building all screens, implement the same vertical slice with the leading persistence candidate and verify migrations, atomic restore, test isolation, and query needs.

Candidate choices:

1. **SwiftData (recommended starting evaluation, iOS 17+):** native SwiftUI integration and in-memory configurations for tests. Apple documents persistent model containers and optional CloudKit entitlements in [Preserving model data across launches](https://developer.apple.com/documentation/swiftdata/preserving-your-apps-model-data-across-launches). Do not enable CloudKit; it conflicts with Pocket's current no-sync boundary.
2. **Core Data:** mature native stack with explicit migration and transaction capabilities; consider it if SwiftData migration/query behavior does not satisfy the ledger.
3. **SQLite adapter:** closest conceptual match to Room and clearest control over relational constraints/transactions, but raw SQLite adds mapping/observation work. A third-party wrapper adds supply-chain/privacy-manifest review.

Acceptance criteria:

- persisted onboarding/budget/Movement survives termination and device reboot;
- schema migration preserves a fixture from every shipped Apple schema;
- failed restore and failed migration leave the prior ledger intact or recoverable;
- foreign-key, uniqueness, archive, rollover, and period invariants are enforced in the repository transaction, not only in UI;
- the FX cache is separated from user-created data and can be purged safely;
- no CloudKit/iCloud container entitlement is present unless a later ADR authorizes sync.

### Backup, restore, and file handling

- **Mandatory:** iOS must read the existing Android `.pocketbackup` v1–v4 variants accepted by Android and write v4 until a coordinated cross-platform version change is approved.
- **Mandatory:** preserve the plaintext warning, bounded read size (currently 10 MiB), strict schema/value/reference validation, preview counts, replacement confirmation, and transactional rollback.
- **Recommended:** declare an exported reverse-DNS Uniform Type Identifier for `.pocketbackup` conforming to `public.json`/`public.data`, so Files and AirDrop represent it correctly. Apple explains this in [Defining file and data types](https://developer.apple.com/documentation/uniformtypeidentifiers/defining-file-and-data-types-for-your-app).
- **Mandatory:** access security-scoped/imported document URLs only for the operation, copy/read safely, close access, and avoid logging contents or filenames that may expose personal information.
- **Mandatory:** neutralize spreadsheet-formula cells in CSV exactly as Android does, preserve encoding/header/date/currency meaning, and test Excel/Numbers opening with synthetic content.
- **Product decision required / ADR conflict:** Android disables OS backup and relies on explicit portable export. Automatic inclusion of the iOS ledger in iCloud device backup would introduce automatic off-device storage, the exact boundary that accepted `docs/adr/0001-plaintext-portable-backup-and-replacement-restore.md` says requires reconsidering the backup decision. Preserve the current promise by placing purgeable FX cache in `Library/Caches` and evaluating exclusion of the ledger/settings from iCloud backup, or explicitly supersede/amend ADR 0001 and revise the privacy/recovery promise. Apple documents default device backup behavior and `isExcludedFromBackup` in [Optimizing app data for iCloud Backup](https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup). That exclusion is guidance, not an absolute non-inclusion guarantee, so the user-facing promise must be worded accurately.
- **Recommended:** apply an appropriate iOS Data Protection class to ledger and settings files. Apple documents protection behaviors in [`FileProtectionType`](https://developer.apple.com/documentation/foundation/fileprotectiontype). Verify access after first unlock, locked-device background behavior, backup export, and migration.

### UI and adaptive behavior

- **Recommended:** preserve Pocket's visual identity and information hierarchy, but implement Apple-native controls and behaviors rather than reproducing Material components pixel-for-pixel.
- **Recommended:** use a tab-based compact layout and an iPad-appropriate split/navigation composition where it improves history/settings flows.
- **Mandatory quality gate:** all common tasks must work with VoiceOver, Voice Control semantics, Dynamic Type/Larger Text, dark appearance, sufficient contrast, and without color alone. Apple's [Accessibility Nutrition Label overview](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/overview-of-accessibility-nutrition-labels) says a feature should be claimed only if every common task works with it.
- **Mandatory:** financial values need meaningful localized spoken labels; progress colors also need text/symbol/state; focus order and destructive confirmations must be logical.
- **Recommended:** Spanish remains the initial product language; put every user-facing string in String Catalogs from day one. Use locale-aware display but keep parsing/storage rules deterministic. Decide whether App Store metadata also ships in English.
- **Mandatory:** handle safe areas, software keyboard, rotation, compact/regular width, 200%+ text, Reduce Motion, Bold Text, increased contrast, and right-to-left layout even if Arabic localization is not shipped. RTL support in Android is already declared.

### Reminders

- **Mandatory:** use local notifications, not remote push, for the existing daily reminder. Request authorization in context and check current settings before scheduling; authorization may change later. Apple documents this in [Asking permission to use notifications](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications).
- **Mandatory:** schedule/cancel a stable-identifier repeating `UNCalendarNotificationTrigger` request and make UI reflect actual authorized/denied/scheduled state. Apple documents scheduling and cancellation in [Scheduling a local notification](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app).
- **Mandatory:** notification title/body contain no financial amount or merchant and should use the least revealing preview consistent with the Android behavior.
- **Recommended:** do not add BackgroundTasks merely to imitate WorkManager. The notification center can deliver a scheduled local notification when the app is not running.
- **Conditional:** if future logic needs background computation, design for system-controlled, non-exact execution; Apple describes background-strategy selection in [Choosing Background Strategies](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app).

### Online FX

- **Mandatory:** keep core use offline and keep the network feature explicitly opt-in.
- **Mandatory:** use `URLSession` with HTTPS and default App Transport Security. ATS is on by default, expects secure URLs/TLS, and broad exceptions can require App Store justification; see [Preventing insecure network connections](https://developer.apple.com/documentation/security/preventing-insecure-network-connections).
- **Mandatory:** reproduce request timeout, 256 KiB response bound, quote-age window, series ID, business-date parsing, cancellation, malformed-response behavior, cache boundaries, and offline fallback through fixtures.
- **Security decision required:** Android injects `POCKET_BANXICO_TOKEN` into `BuildConfig`; an equivalent iOS build setting embedded in the app is also recoverable from the distributed binary. Confirm Banxico's credential and redistribution terms. Choose among a user-provided token, a deliberately public/restricted application credential with rotation, or a minimal proxy authorized by a separate architecture/privacy decision. Do not treat an `.xcconfig`, CI secret, or obfuscation as runtime secret storage.
- **Recommended:** never log tokens, full responses, financial input, or backup data. Store no Banxico token in source control.

### Quick expense action

- **Recommended parity:** add a static Home Screen quick action for `Nuevo gasto`, route cold and warm launches idempotently, and test submission of a Movement. Apple documents static/dynamic actions and scene launch handling in [Add Home Screen quick actions](https://developer.apple.com/documentation/uikit/add-home-screen-quick-actions).
- **Conditional later enhancement:** expose the same action through App Intents/Shortcuts only after privacy-safe parameters and lock-state behavior are specified. Do not put an amount or merchant in public shortcut metadata by default.

## 6. Build and run workflow

### First local build on the Mac

1. Install the supported production Xcode and launch it once to complete components/license setup.
2. Clone the repository on the Mac and create the `ios/Pocket` application target plus unit/UI test targets.
3. Set a unique bundle identifier owned by the Apple team, for example `com.aif31.pocket` if that identifier is available in Apple's system. Android and Apple may use the same reverse-DNS text but their registrations are independent.
4. Select an iOS Simulator and run from Xcode. Simulator builds do not require paid distribution membership.
5. Sign into Xcode, choose the team, enable automatic signing, enable Developer Mode on the iPhone if prompted, connect/trust the device, and run on hardware. Xcode can register the device and generate the development profile as described in [Running on simulated or physical devices](https://developer.apple.com/documentation/Xcode/running-your-app-on-simulated-or-physical-devices).

Source-controlled schemes should support command-line automation such as:

```bash
xcodebuild \
  -project ios/Pocket.xcodeproj \
  -scheme Pocket \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' \
  clean test

xcodebuild \
  -project ios/Pocket.xcodeproj \
  -scheme Pocket \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  archive \
  -archivePath build/Pocket.xcarchive
```

- **Mandatory:** pin/select the intended Xcode version in CI and record `xcodebuild -version` in logs.
- **Recommended:** resolve simulator IDs from available devices rather than hard-code a marketing name that may disappear.
- **Mandatory:** treat simulator `build/test`, unsigned or development device builds, and App Store archives as different gates.

### Signing

- **Recommended:** use Xcode automatic signing and cloud-managed distribution certificates for the initial small team. Apple describes the archive flow and automatic signing in [Distributing your app for beta testing and releases](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases) and rotation in [Cloud-managed certificates](https://developer.apple.com/help/account/certificates/cloud-managed-certificates/).
- **Mandatory:** register the App ID/bundle ID, select the correct team, and keep entitlements minimal. Pocket currently needs no CloudKit, APNs, HealthKit, financial/banking, location, contacts, camera, microphone, or tracking entitlement.
- **Mandatory:** protect Apple accounts, signing certificates, private keys, issuer/key IDs, API private keys, and recovery material outside Git. Apple identifies these as sensitive in [Certificates overview](https://developer.apple.com/help/account/certificates/certificates-overview).
- **Conditional CI path:** when using GitHub-hosted macOS runners with manual signing, store a base64 PKCS#12 certificate, its password, and `.mobileprovision` as GitHub secrets and use an ephemeral keychain. GitHub documents the workflow in [Installing an Apple certificate on macOS runners](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications).
- **Recommended:** prefer short-lived/cloud-managed/role-scoped automation over distributing one long-lived `.p12` to developers.

## 7. Test and verification master matrix

Apple recommends many isolated unit tests, fewer integration tests, and focused UI tests. Xcode 16+ includes Swift Testing for unit/integration work and XCTest/XCUIAutomation for UI tests; see [Testing](https://developer.apple.com/documentation/xcode/testing) and [Adding tests to your Xcode project](https://developer.apple.com/documentation/xcode/adding-tests-to-your-xcode-project).

### Fast conformance tests — mandatory on every change

- Money parse/format, overflow, exact rate multiplication, half-up rounding, unsupported currencies.
- Period boundary and catch-up vectors: start days 1/25/28–31, February/leap year/year rollover, zero/one/many missing periods, idempotency, long transition.
- Pocket availability, refund-only rollover, negative availability, threshold states, historical cascading recalculation, archive/release invariants.
- Search/filter predicates, recurring template behavior, currency-change boundary behavior.
- Backup versions 1–4 decode/validate, v4 deterministic semantics, malformed/truncated/oversized/duplicate/dangling-reference/overflow cases.
- CSV injection neutralization and encoding.
- Banxico parsing with existing synthetic fixtures and cancellation/timeout/status/oversize cases.

### Persistence/integration tests — mandatory

- Fresh database, every schema migration, failed migration recovery.
- Complete command transaction behavior and concurrent command serialization.
- Export Android fixture -> import iOS -> re-export -> import Android; compare normalized ledger state, not irrelevant JSON whitespace/order unless canonical bytes are deliberately specified.
- Replacement restore success, every injected failure rollback, future-start rejection, restored reminder disabled/reconfirmed according to the accepted ADR.
- Cache eviction cannot affect frozen financial values.
- UserDefaults/settings and ledger consistency after termination/relaunch.

### UI automation — mandatory release gate for critical flows

- First launch/onboarding and restore entry.
- Create Pocket, allocate budget, create/edit expense and refund, delete/undo.
- Foreign-currency estimated and confirmed paths; online disabled/unavailable.
- History search and each independent filter.
- Catch-up review, archive constraints, recurring template prefill.
- Backup preview/replacement confirmation and CSV export smoke flow.
- Reminder enable/deny/change/disable truthfulness.
- Home Screen quick action on cold and warm launch, including actual save.
- Navigation restoration and no double submission.

### Accessibility and layout — mandatory before release

- All common tasks with VoiceOver on physical hardware.
- Voice Control names/labels, hardware keyboard traversal where relevant.
- Dynamic Type through accessibility sizes; no clipped amount, action, warning, or confirmation.
- Dark appearance, increased contrast, differentiate without color, Reduce Motion.
- iPhone portrait/landscape where supported; iPad size classes, Split View/multitasking, keyboard and pointer if iPad ships.
- Accessibility Inspector and automated accessibility audits supplement, but do not replace, physical VoiceOver testing.
- Publish Accessibility Nutrition Labels only for features that satisfy Apple's all-common-tasks criterion; labels are currently voluntary but Apple says they will become required over time. See [Accessibility Nutrition Labels](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/overview-of-accessibility-nutrition-labels).

### Release and resilience — mandatory before TestFlight candidate

- Test an optimized Release build outside the debugger; Apple notes debugger and user environments differ in [Testing a release build](https://developer.apple.com/documentation/Xcode/testing-a-release-build).
- Install over prior TestFlight version and migrate; never test release only from clean install.
- Force quit/relaunch, device reboot, low storage, memory pressure, Low Power Mode, airplane mode, denied/revoked notification permission, locale/calendar/time-zone changes.
- Backup -> delete app -> reinstall -> restore with synthetic data.
- Instruments checks for launch, scrolling, large history, memory growth, hangs, and energy/network behavior.
- Archive validation, privacy report review, symbol upload, crash report symbolication, and exact version/build-number traceability.

## 8. CI/CD

### Recommended two-stage pipeline

1. **Existing Android/Linux workflow:** continue Android host tests, lint, and builds. If KMP is later adopted, run common tests here where supported.
2. **Apple/macOS workflow:** select production Xcode; resolve packages; build; run Swift Testing and UI tests on oldest/current simulator destinations; archive Release; retain `.xcresult`, test summaries, and logs. Sign/upload only from protected branches/tags or a manual release environment.

### CI provider choices

- **Xcode Cloud (recommended first distribution path):** integrated signing, Apple environments, TestFlight, and App Store Connect. It requires Developer Program membership, unique bundle ID, remote Git admin access, and an app record. Membership currently includes 25 compute hours/month; see [Getting started with Xcode Cloud](https://developer.apple.com/documentation/xcode/getting-started-with-xcode-cloud) and [Xcode Cloud plans](https://developer.apple.com/xcode-cloud/get-started/).
- **GitHub Actions macOS (recommended if one CI system is preferred):** transparent repository workflow and direct pairing with existing Android CI. It requires careful Xcode selection, macOS runner availability/cost controls, simulator stability, and signing-secret handling.
- **Self-hosted Mac (conditional):** useful for hardware-in-the-loop or cost control, but adds patching, physical security, keychain cleanup, runner isolation, availability, and maintenance responsibility.

### Mandatory CI controls

- Read-only default GitHub permissions; no signing/upload secrets for pull requests or forks.
- Concurrency cancellation and explicit timeouts.
- Dependency resolution lockfiles and reviewed updates.
- Never print credentials, profiles, financial fixtures, or full backup payloads.
- Archive `.xcresult` and bounded diagnostics even on failure; keep only synthetic data.
- Separate build/test permission from App Store submission permission.
- App Store Connect API keys must be least-privilege and securely stored. Team keys can span all apps, so prefer appropriately scoped individual keys where workable; see [App Store Connect API](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api).
- Re-run all gates when the production Xcode or deployment target changes.

## 9. Privacy, security, and compliance

### Privacy disclosures

- **Mandatory:** host a public privacy-policy URL and make the policy accessible inside the app. App Store Connect requires an iOS privacy policy URL, and App Review Guideline 5.1 requires an in-app link. See [Manage app privacy](https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy/) and [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/).
- **Mandatory:** answer App Privacy questions for the app and every third-party SDK. Apple's definition generally treats data as “collected” when it is transmitted off-device and retained beyond servicing the request in real time; see [App privacy details](https://developer.apple.com/app-store/app-privacy-details/). Do a concrete data-flow audit rather than assuming “No Data Collected.”
- **Current likely outcome, subject to verification:** the local ledger itself is not collected because it remains on-device. Verify Banxico request fields, provider retention/terms, crash/diagnostic settings, TestFlight diagnostics, and any later SDK before selecting the label.
- **Mandatory:** update the privacy answers whenever data behavior or dependencies change.

### Privacy manifest and dependency supply chain

- **Mandatory:** include `PrivacyInfo.xcprivacy` with approved reasons for every Required Reason API used by app code. Since May 1, 2024, missing declarations prevent App Store Connect acceptance. See [Describing use of required-reason APIs](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api).
- **Mandatory:** generate and review Xcode's aggregated privacy report from the Release archive; Apple documents this in [Describing data use in privacy manifests](https://developer.apple.com/documentation/bundleresources/describing-data-use-in-privacy-manifests).
- **Conditional:** listed third-party SDKs require their own privacy manifest and, for binary dependencies, signatures. The developer remains responsible for SDK behavior; see [Third-party SDK requirements](https://developer.apple.com/support/third-party-SDK-requirements/).
- **Recommended:** use no analytics/crash SDK in the first release. If diagnostics are added, make an explicit privacy/retention decision and update policy, manifest, and label before merging.

### Platform security

- **Mandatory:** remain within the iOS app sandbox; request no permission that is not needed.
- **Mandatory:** rely on ATS/URLSession, system trust evaluation, platform Data Protection, and standard cryptography; do not implement custom cryptography.
- **Recommended:** add a threat review around backups, document URLs, restore parser limits, database files, notifications, logs, screenshots/app switcher, and the Banxico credential.
- **Recommended:** consider privacy-sensitive screen behavior when backgrounding, but do not obscure the app switcher without testing usability and review implications.
- **Conditional:** if app-specific Face ID/Touch ID lock is added later, it is a new product feature, not a porting requirement; define recovery and backup access first.

### Export compliance

- **Mandatory:** answer App Store Connect encryption questions for every build and determine the correct exemption/documentation status. Use of OS HTTPS still requires the determination. Apple explains the process in [Overview of export compliance](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance).
- **Conditional:** if the app uses only encryption in Apple's OS, Apple currently lists no additional documentation as required; see [Export compliance documentation](https://developer.apple.com/help/app-store-connect/reference/export-compliance-documentation-for-encryption/). Confirm the actual linked dependencies before relying on this.
- **Recommended:** once correctly determined, set `ITSAppUsesNonExemptEncryption` in `Info.plist` so App Store Connect does not repeat the questionnaire; see [`ITSAppUsesNonExemptEncryption`](https://developer.apple.com/documentation/bundleresources/information-property-list/itsappusesnonexemptencryption).

## 10. App Store and TestFlight delivery

### App record and metadata

- **Mandatory:** Developer Program membership, accepted current agreements, unique bundle ID/App ID, SKU, primary language, app name, category, description, keywords, support URL, privacy-policy URL, age-rating questionnaire, copyright, price/tax category, territories, and a selected processed build.
- **Mandatory classification check:** Pocket is a personal budgeting/recording tool and does not hold funds, trade, lend, connect to banks, or provide regulated financial services. Before enrollment and review, confirm with qualified counsel/product ownership whether Apple's financial-services provisions or any target-country licensing rule applies. If Apple treats the offered functionality as a highly regulated financial service, the responsible legal entity—not an individual contractor—must submit it and availability must be limited to authorized regions. Do not inaccurately describe Pocket as a bank, wallet, investment, or financial institution. See sections 3.2.1 and 5.1.1 of the [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/).
- **Mandatory:** create the App Store Connect app record before the first upload. Apple documents the lifecycle in [App Store Connect workflow](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-workflow).
- **Mandatory:** produce an App Store icon and current required screenshots for every supported device class. Sizes change; use [Screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications) at release time.
- **Mandatory:** accurate metadata and screenshots must show the real production experience. Apple's [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) reject incomplete/crashing apps and require accurate metadata.
- **Conditional:** EU distribution requires verified Digital Services Act trader/non-trader status; current Apple requirements warn that missing trader status removes the app from the EU storefront. See [Upcoming Requirements](https://developer.apple.com/news/upcoming-requirements/).
- **Conditional:** tax/banking agreements are needed if Pocket becomes paid or offers in-app purchases. The current app has no digital purchase feature, so do not add StoreKit merely for the port.

### TestFlight

- **Mandatory before public release:** upload an archive with Xcode 26+/iOS 26 SDK, wait for processing, complete export-compliance details, and provide beta description/contact/test instructions.
- **Recommended:** internal testing first, then a small external group with synthetic/nonpersonal data guidance.
- Apple currently permits up to 100 internal App Store Connect users and 10,000 external testers; builds are testable for up to 90 days, and the first external build may require beta review. See [TestFlight Overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview).
- **Mandatory:** include review notes that clearly explain the no-login, local-only model, how to reach onboarding/restore/online FX/reminder/quick action, and that no demo credentials are required.

### Submission and release

1. Archive Release and validate it in Organizer.
2. Review entitlements, signing, included SDKs, privacy report, export compliance, symbols, app version/build number, and minimum OS.
3. Upload and wait for processing; resolve all warnings/errors.
4. Select the build, complete mandatory metadata/privacy/age-rating/review information, add it for review, then submit. Apple documents these steps in [Submit an app](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/submit-an-app).
5. Choose manual, automatic, or phased release. Apple's publishing flow is summarized in [Overview of publishing](https://developer.apple.com/help/app-store-connect/manage-your-apps-availability/overview-of-publishing-your-app-on-the-app-store).
6. Monitor crash/energy diagnostics, reviews, and support reports; keep an immediate rollback/expedited-fix procedure.

## 11. Delivery milestones and exit gates

Each milestone ends with evidence, not a percentage-complete claim. The ranges below are rough person-weeks under the assumptions in the architecture table and should be re-estimated after A2 and A3. They are not calendar promises.

### A-1 — toolchain alignment decision (1–2 person-weeks; KMP paths only)

Deliver:

- Inventory the exact current Gradle, AGP, Kotlin/KGP, Compose, JDK, macOS, and Xcode tuple.
- Select wait, align, or isolated-module strategy from the Phase-0 gate.
- Prove Android and Apple-target clean builds/tests on the vendor-supported tuple.

Exit gate: the Phase-0 compatibility gate in section 3 passes. If it does not, select the native Swift path rather than building production on an unsupported combination.

### A0 — ownership and release prerequisites (0.5–1 person-week, plus external enrollment/procurement time)

Deliver:

- Mac and physical iPhone available.
- Individual/organization enrollment decision and long-term Account Holder.
- Developer Program enrollment path, bundle ID, seller name, privacy/support website ownership.
- iPhone-only versus universal iPhone/iPad and minimum iOS decisions.
- Xcode 26 production baseline recorded.

Exit gate: blank signed app runs on Simulator and physical device; no unnecessary entitlement.

### A1 — conformance contract (2–4 person-weeks)

Deliver:

- Canonical backup v1–v4 fixtures with only synthetic data.
- Domain and ledger golden vectors extracted from Android tests.
- A platform-parity checklist mapped to the current Android capabilities.
- ADRs for native SwiftUI choice, minimum OS, persistence choice pending spike, and iCloud backup posture.

Exit gate: Android tests consume the new fixtures without behavior change.

### A2 — native vertical slice and persistence choice (3–5 person-weeks)

Deliver:

- SwiftUI onboarding/dashboard shell.
- Swift exact-money/calendar core.
- One Pocket budget and one Movement persisted through relaunch.
- Persistence spike report comparing the real acceptance criteria.
- Swift Testing and one XCTest UI flow in local Xcode and macOS CI.

Exit gate: release-config physical-device flow passes; migration/transaction test design is credible.

### A3 — interoperable recovery foundation (4–7 person-weeks)

Deliver:

- Full native ledger data model/repository and schema migration harness.
- v1–v4 import, v4 export, preview, replacement restore, rollback, plaintext disclosure, CSV.
- Android-to-iOS-to-Android normalized round trip.
- Data Protection and iCloud backup decision implemented and documented.

Exit gate: destructive/recovery tests pass under injected failures; a human recovery drill passes on a physical iPhone.

### A4 — complete feature parity (8–14 person-weeks)

Deliver:

- All dashboard, Pocket, Movement, history/filter, period/currency, template, settings, reminder, and quick-action behavior.
- Native iPhone/iPad layout if universal.
- Opt-in Banxico flow with credential decision resolved.
- Cross-platform parity matrix has no unexplained differences.

Exit gate: critical XCUI flows, conformance suite, performance baseline, and Android regression suite pass.

### A5 — accessibility, privacy, and beta readiness (3–5 person-weeks)

Deliver:

- VoiceOver/Dynamic Type/dark/contrast/reduced-motion/keyboard verification.
- Privacy policy in web metadata and app; App Privacy answers; privacy manifest/report.
- Export-compliance decision; App Store metadata/icon/screenshots/age rating/support path.
- Signed archive CI and internal TestFlight.

Exit gate: release checklist passes on clean install and update; internal TestFlight has no release blocker.

### A6 — external beta and launch (2–4 person-weeks, plus Apple/tester elapsed time)

Deliver:

- External TestFlight review/group and feedback triage.
- Migration/update, offline, reminder, backup/reinstall/restore, and large-data drills.
- App Review notes and production submission.
- Manual/phased release and rollback/support playbook.

Exit gate: App Review approval, intentional release, monitored production baseline, and archived synthetic verification record.

### A7 — post-launch sharing experiment (2–4 person-weeks; optional)

Deliver:

- Measured KMP domain spike only after both apps are stable.
- Decision record: adopt selected pure modules, defer, or reject.

Exit gate: no migration unless both platform suites pass and the measured benefit exceeds the added build/interop burden.

## 12. Definition of Apple parity

Parity means shared domain outcomes and recovery compatibility, not identical pixels.

| Area | Required parity | Allowed native difference |
| --- | --- | --- |
| Ledger/math | Identical integer outcomes, dates, rollover, transitions, historical recalculation, validation | Swift implementation details |
| Backup | Android-accepted historical versions import; v4 interchange; same destructive semantics | Native Files UI |
| CSV | Same facts, safe cells, documented formatting | Share sheet/export presentation |
| Navigation | Every workflow and back/cancel/save invariant reachable | Apple tab/stack/split conventions |
| Reminder | Same opt-in daily purpose and privacy | iOS authorization/scheduling UI |
| Shortcut | Opens a fresh expense reliably | Home Screen quick action presentation |
| Offline/FX | Full core offline; explicit online opt-in; equivalent quote/fallback meaning | `URLSession`/ATS implementation |
| Accessibility | All common tasks operable and understandable | Platform-native semantics |

Any intentional divergence requires a product decision and, if it changes domain or recovery meaning, an ADR plus updated fixtures on both platforms.

## 13. Maintenance after launch

- **Every pull request:** Swift format/static analysis policy, unit/integration tests, selected UI smoke tests, Android regression where conformance changed.
- **Every release:** current Xcode/upload rule audit, dependency/privacy manifest audit, oldest/current OS run, physical-device Release test, upgrade/migration, backup/recovery, accessibility common tasks, privacy/App Store metadata accuracy.
- **Every major iOS/Xcode beta cycle:** build and test in a non-release lane, file platform bugs, and do not move production CI until supported dependencies and release evidence pass.
- **Ongoing:** renew Developer Program; accept agreements; rotate/revoke signing/API credentials; update contact/support/privacy URLs; triage crashes, hangs, energy, reviews, and TestFlight feedback.
- **Data evolution:** never remove support for a backup version without an explicit retention/migration policy. Keep every shipped Apple persistence schema fixture and at least the Android-supported backup corpus.
- **Dependencies:** prefer few dependencies, pin/review them, monitor Apple third-party SDK requirements, license notices, signatures, privacy manifests, and minimum-Xcode support.
- **Store health:** an unmaintained/nonfunctional app can be removed under App Review policy; assign an owner and supported-release cadence.

## 14. Skills and team capability needed

The current repository skill catalog is Android-heavy and has no dedicated iOS/SwiftUI/Xcode skill. Useful existing skills are:

- `research` for rechecking first-party requirements;
- `codebase-design` and `domain-modeling` for the ledger seam and canonical terminology;
- `tdd` for conformance-first financial work;
- `mobile-app-ui-design` for flows, followed by Apple HIG validation rather than Android Material implementation rules;
- `wizard` only for enrollment/signing/dashboard steps a human must perform;
- `writing-for-agents` when adding Apple build/device procedures to `AGENTS.md`.

Create or install narrowly scoped Apple skills before implementation:

1. **ios-xcode-build:** supported Xcode selection, project/scheme conventions, `xcodebuild`, Simulator, result bundles, archive validation.
2. **swiftui-state-navigation:** Observation/concurrency, navigation restoration, adaptive iPhone/iPad composition, scene/quick-action handling.
3. **ios-persistence-migrations:** selected persistence framework, transactions, schema fixtures, failure-safe migration and replace-restore.
4. **ios-testing:** Swift Testing, XCTest/XCUIAutomation, deterministic launch arguments, simulator/device matrices, `.xcresult` evidence.
5. **ios-accessibility:** VoiceOver, Dynamic Type, Voice Control, contrast/reduced motion, Accessibility Inspector, Nutrition Label criteria.
6. **apple-signing-testflight-release:** roles, automatic signing, certificate/profile recovery, archives, App Store Connect, TestFlight, review and rollback.
7. **apple-privacy-security:** privacy manifests/reports, Required Reason APIs, App Privacy labels, ATS, Data Protection, export compliance.
8. **kmp-apple-interop** only if A7 proceeds: targets, framework export, Swift API design, direct integration, CI/toolchain compatibility.

Each skill should cite current Apple/JetBrains documentation, state which actions require a Mac or Account Holder, protect credentials and personal data, and avoid hard-coding a version that Apple may retire.

## 15. Decisions that block implementation versus decisions that can wait

### Decide before coding beyond the first blank app

- Long-term Apple membership owner and individual versus organization seller identity.
- Access to a supported Mac and physical iPhone.
- iPhone-only versus iPhone+iPad and minimum iOS version.
- Bundle identifier and repository location.
- Native SwiftUI direction.

### Decide during A1/A2

- Persistence framework based on the spike.
- iCloud device-backup exclusion versus revised product promise.
- Exact cross-platform backup canonicalization expectations.
- Banxico credential/redistribution approach.
- GitHub Actions versus Xcode Cloud as primary Apple CI.

### Safely defer

- KMP domain extraction and all shared UI.
- App Intents beyond the Home Screen quick action.
- widgets, CloudKit/sync, accounts, biometric app lock, analytics, crash SDKs, purchases, watchOS, macOS, visionOS, notification ingestion, OCR, or AI.

These deferred items are new products or material architecture changes, not prerequisites for an iOS version of Pocket 1.0.

## 16. First actionable checklist

- [ ] Acquire/assign a Mac supported by current production Xcode and a physical test iPhone.
- [ ] Decide individual versus organization enrollment; create/secure Apple Accounts with 2FA.
- [ ] Enroll in the Developer Program before TestFlight work.
- [ ] Record iPhone/iPad scope and minimum iOS ADR.
- [ ] Create blank `ios/Pocket` target, shared scheme, tests, bundle ID, and automatic signing.
- [ ] Add Apple macOS CI build/test without distribution secrets.
- [ ] Extract synthetic v1–v4 backups and domain golden vectors into `conformance/`.
- [ ] Make Android consume those fixtures and preserve its green gates.
- [ ] Implement the Swift vertical slice and persistence spike.
- [ ] Resolve iCloud backup posture and Banxico token design before storing real data or enabling network FX.
- [ ] Do not start full-screen porting until Android↔iOS backup round-trip succeeds.

## Source policy

Apple requirements in this plan cite Apple Developer or App Store Connect documentation. KMP claims cite official Kotlin/JetBrains documentation. GitHub Actions signing cites GitHub's official documentation. Repository-specific claims come from the local source and project documentation. Revalidate time-sensitive versions, fees, limits, and review rules at execution time.
