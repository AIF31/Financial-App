# Pocket UI prototype gate

These Compose surfaces are decision artifacts for the first milestone in
`UI-UX-Design-Philosophy.md`. They deliberately use fixed, realistic UI state
and are not connected to the ledger, navigation, or production app shell.

Open `PrototypePreviews.kt` in Android Studio. The main dashboard, quick
expense, and Pockets previews each render at representative phone and tablet
sizes in light and dark themes. Additional previews cover low availability,
long Pocket names, validation, save success, expanded details, empty content,
and large font scaling. The interactive Pockets preview reports management
intent without changing financial fixture values; ledger behavior belongs to
the later production slices.

The production shell and its four bottom destinations — Inicio, Movimientos,
Pockets, and Ajustes — remain unchanged. The prototypes use stable Material 3
APIs only; no experimental adaptive, navigation, Grid, FlexBox, or Styles API
is used.

Review these questions before approving production wiring:

1. Is the primary number or action obvious on every surface?
2. Can the common expense path be understood at a glance?
3. Are Pocket progress and status clear without relying on color?
4. Do the phone and tablet compositions feel intentional?
5. Is the Spanish copy calm, direct, and consistent with Pocket terminology?
6. Do large text and error states preserve the essential action?

After approval, use the accepted composables and immutable state boundaries as
the starting point for production slices. Do not connect these fixtures to the
ledger or ship the prototype package as a parallel app flow.
