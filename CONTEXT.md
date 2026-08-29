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
The SAR amount declared for one budget period. It limits that period's total Pocket budgets but is not income and does not create a movement.
_Avoid_: Income, deposit, account balance

**Pocket budget**:
The portion of a period's new funds assigned to a Pocket. Pocket budgets across a period cannot exceed its new funds.
_Avoid_: Expense, transfer

**Rollover**:
Positive availability carried from an opted-in Pocket into the next period. Negative availability never rolls over.
_Avoid_: Funds, income

**Movement**:
A manually recorded expense or refund assigned to one Pocket and one budget period. Expenses increase net spending; refunds reduce it.
_Avoid_: Transaction import, transfer

**Payment method**:
Optional descriptive metadata on a movement, such as cash or card. It does not represent an account or balance.
_Avoid_: Account, wallet

**Conversion status**:
Whether a USD or MXN movement's manually entered SAR value is estimated or confirmed. The SAR value is frozen until the user edits it.
_Avoid_: Live exchange rate, automatic conversion

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
A normalized, temporary candidate derived from an allowed app's notification. It becomes a Movement only after confirmation, never stores raw notification text, and expires after 30 days while pending.
_Avoid_: Imported transaction, automatic Movement

**Experimental notification parser**:
An opt-in beta parser for English and Spanish notifications from user-selected apps. It is generic, is tested with synthetic notifications, and makes no claim of supporting a particular bank.
_Avoid_: Bank integration, transaction feed

**Portable backup**:
A versioned plaintext JSON document containing the financial ledger and app-owned financial settings. Restore replaces existing data after preview and confirmation rather than merging ledgers.
_Avoid_: Encrypted vault, synchronized backup, merge import
