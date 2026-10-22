# Business Rules

## Units and dates

- Filled bottles delivered to a shop are **Sodas In**.
- Empty bottles returned from a shop are **Sodas Out**.
- Stock is an integer movement sum: opening + delivered − returned. Payments never affect bottle stock.
- A return is accepted only when every chronological balance remains nonnegative. A historical backdated return that precedes available stock is rejected.
- Business dates are ISO local dates (`yyyy-MM-dd`); event timestamps are epoch milliseconds.

## Money and ledger

- Money is persisted in integer paise; never use binary floating-point amounts for accounting.
- Each transaction records its unit price. Delivery value and default return credit each equal their quantity multiplied by the saved unit price.
- Payments are cash actually received, not sales value. Ledger movement is delivery value − return credit − payment.
- Positive signed balance means amount due. Negative signed balance is shown as a nonnegative advance credit.
- Saved transaction prices are independent of subsequent edits to a shop's default price.
- Calculations use checked long multiplication/addition to detect overflow.

## Reports

- Shops supplied are distinct shops having at least one positive delivery on that date/range.
- Returns and payments alone do not count as a shop supplied.
- Delivery value, return credits, and actual payments are separately reported.
- Current stock and balance use the full transaction history; period reports filter by inclusive local calendar dates.

## Data protection

- A backup contains shops and transaction events in versioned JSON, not photographs.
- Restore validates format/version, IDs, foreign keys, dates, quantities, prices, and chronological stock before replacing data in a Room transaction.
- Stock and money figures are derived from persisted events, not cached totals.
