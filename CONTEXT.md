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
