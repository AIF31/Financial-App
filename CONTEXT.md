# Personal Spending

This context describes how one person records and understands personal spending against self-defined budget periods. It is not a bank-account ledger: the app tracks only funds the person declares and movements the person records.

## Language

**Pocket**:
A spending classification that also carries a user-defined monthly budget; expenses assigned to it reduce the amount available for that month.
_Avoid_: Category, envelope, account

**Pocket availability**:
The Pocket's budget plus eligible rollover, less expenses, plus refunds, within one budget period. Availability may be negative.
_Avoid_: Account balance, bank balance

**Budget period**:
A contiguous date range used for funds, Pocket budgets, movements, and reporting. Periods default to starting on day 25 in `Asia/Riyadh`; a later start-day preference affects only future periods.
_Avoid_: Calendar month, billing cycle

**New funds**:
The accounting-currency amount declared for one budget period. It limits that period's total Pocket budgets but is not income and does not create a movement.
_Avoid_: Income, deposit, account balance

**Accounting currency**:
The single currency that owns every budget, rollover, availability, and movement accounting amount within one budget period.
_Avoid_: Display currency, preferred input currency

**Currency boundary quote**:
A frozen, directed conversion rate from the prior period's accounting currency into its successor's accounting currency. It is used for rollover and historical comparison across that boundary.
_Avoid_: Live rate, reversible rate, unlabeled conversion

**Pocket budget**:
The portion of a period's new funds assigned to a Pocket. Pocket budgets across a period cannot exceed its new funds.
_Avoid_: Expense, transfer

**Budget reassignment**:
An increase to one Pocket's budget covered by a decrease to another Pocket's budget or by unassigned funds in the same budget period. A giving Pocket must be active, with availability left, and neither exhausted, at risk nor forecast to run out. It can give at most the lesser of its availability and budget. Reassignment changes neither Movements nor rollover.
_Avoid_: Transfer

**Rollover**:
Positive availability carried from an opted-in Pocket into the next period. Negative availability never rolls over.
_Avoid_: Funds, income

**Movement**:
An expense or refund assigned to one Pocket and one budget period. Expenses increase net spending; refunds reduce it. The person records it, or Pocket auto-records a notification payment for a known merchant (see ADR 0002).
_Avoid_: Transaction import, transfer

**Payment method**:
Optional descriptive metadata on a movement, such as cash or card. It does not represent an account or balance.
_Avoid_: Account, wallet

**Conversion status**:
Whether a foreign-currency movement's accounting amount is estimated or confirmed. A provider rate is confirmed only when it is the final Banxico FIX for the movement's own date; an earlier observation, used before that day's FIX is published or from the cache, is an estimate that Pocket replaces once the final rate is available. Confirmed and manually entered amounts are frozen until the user edits them.
_Avoid_: Live exchange rate, period rate

**Automatic period catch-up**:
The transactional, idempotent creation of every missing budget period in sequence when the app launches or resumes. Only the current period requires review.
_Avoid_: Skipping periods, creating only the latest period

**Long transition period**:
The single extended period used after changing the preferred start day. It ends at the first new-schedule boundary after the old schedule's next expected end and is compared using daily pace rather than absolute spending.
_Avoid_: Short transition period, retroactive boundary change

**Rollover release**:
An explicit accounting adjustment that moves positive rollover from a Pocket being archived into unassigned funds. It reclassifies existing availability; it does not create new funds.
_Avoid_: Income, refund, discarded rollover

**Movement suggestion**:
A normalized, temporary candidate derived from one message in an allowed app's notification. It becomes a Movement after confirmation or through auto-recording, never stores raw notification text, and expires after 30 days while pending.
_Avoid_: Imported transaction

**Auto-recording**:
Turning a Movement suggestion into a Movement without review, only when its merchant's most recent expense sits in an active Pocket, its currency equals the period's accounting currency, and no expense for the same merchant and amount, as entered or as converted, is dated within 12 hours of it. Anything else stays a suggestion; the last case is a possible duplicate.
_Avoid_: Bank sync, automatic categorization

**Merchant memory**:
The Pocket of the most recent expense whose merchant matches after ignoring case and punctuation. It is derived from existing Movements and is not stored separately. Changing a merchant’s Pocket in Settings moves its most recent expense to that Pocket, which also directs future automatic records there.
_Avoid_: Rule, category mapping

**Recurring charge**:
A detected expectation of a merchant's next expense, derived from regular dates and stable amounts in existing Movements using the same accounting currency. A consistent monthly date follows calendar months, clamping to shorter months; other regular schedules follow their median day interval. Each expected occurrence is overdue for at most half a cadence, then drops from the list; later occurrences remain expected. After two consecutive expected occurrences pass that window without a charge, the merchant is treated as ended and no occurrences are listed. It is not stored and never records a Movement automatically. It is distinct from `RecurringTemplate`, a shortcut that prefills a movement form.
_Avoid_: Subscription billing, scheduled expense, Recurring template

**Budget suggestion**:
A proposed Pocket budget: the median net spend of up to three recent ended periods in the same accounting currency, each scaled to the target period's length and rounded up to a whole unit. A Pocket with no spending to go on gets no suggestion. Applying suggestions replaces the chosen budgets in one step and only when their total fits the period's new funds.
_Avoid_: Automatic budget, forecast

**Pace alert**:
A notification that a Pocket is forecast to run out before the period ends, has reached 80% of its budget, or has no availability left. Each level alerts once per Pocket and period, and only after auto-recording; levels reached through changes made in the app count as seen. When a payment is auto-recorded while the app is open, the alert may or may not post, depending on which check runs first.
_Avoid_: Budget warning, reminder

**Rate alert**:
An opt-in notification of the latest Banxico FIX for one chosen pair of MXN against USD or SAR, with SAR derived through the SAMA peg. It also gives the change from the previous FIX and the average of every FIX dated in the seven days ending on the latest one. It posts daily, weekly on a chosen day, or only for a FIX not yet alerted, at a chosen `Asia/Riyadh` time, and requires online conversion. An optional minimum rate holds it back until 1 unit of the first currency buys at least that much of the second. It never changes a Movement or a currency boundary quote.
_Avoid_: Live rate, rate tracker, Pace alert

**Experimental notification parser**:
An opt-in beta parser for English and Spanish notifications from user-selected apps. It is generic, is tested with synthetic notifications, and makes no claim of supporting a particular bank.
_Avoid_: Bank integration, transaction feed

**Portable backup**:
A versioned plaintext JSON document containing the financial ledger and app-owned financial settings. Restore replaces existing data after preview and confirmation rather than merging ledgers.
_Avoid_: Encrypted vault, synchronized backup, merge import
