# Rosterra iOS Native App — Staff-Only Feature Audit

> **Phase 1 deliverable** for the Native Android Staff App build. Scope: **staff role only** —
> manager screens, manager Firestore access, and manager-only business logic are explicitly
> excluded (see [ANDROID-STAFF-BUILD-PLAN.md](./ANDROID-STAFF-BUILD-PLAN.md) for the reasoning
> and the roadmap that follows this audit).
>
> **Method**: extracted and re-scoped from [`IOS-FEATURE-INVENTORY.md`](./IOS-FEATURE-INVENTORY.md)
> (a full-codebase source-level review of `Roster IOS NATIVE APP`, produced 2026-07-17,
> re-verified 2026-07-21), cross-checked against the live-deployed
> `Roster PWA/firestore.rules` (read directly this pass — confirmed byte-identical to
> `Roster IOS NATIVE APP/docs/reference/firestore.rules.deployed`) for every permissions claim
> in §17 below. Where the source inventory left a gap or an open question, it's flagged
> explicitly rather than guessed.
>
> Backend: shared Firebase project `roster-8a270` (Auth, Firestore, Storage, Crashlytics, FCM) +
> Cloudflare Worker at `https://sura-roster.com` — the same backend the Android app already
> talks to for auth/staff-directory work.

---

## 1. App shell & navigation flow

- **Tab bar** (`Features/Shell/MainTabView.swift`): 5 tabs — **Home, Roster, Tasks, Availability,
  Account**. There is no separate "Shifts" tab (shifts live inside Roster) and no separate
  "Settings" tab (settings live inside Account). **History and Payslips are not tabs** — History
  is pushed from Roster ("View Shift History" card), Payslips is pushed from Account.
- **Root routing** is a pure, unit-tested state machine (`AppRoute.determine`), gate order:
  `setup → restoring → login → profileLoading → forcedPasswordChange → profileCompletion →
  deviceAuthGate → staffMain`. Every gate must clear before the tab bar is reachable.
- **Sheet convention**: one `.sheet(item:)` per view, bound to a single `Identifiable` enum —
  presenting two sheets from the same view is explicitly banned in the iOS codebase (SwiftUI
  handles it unreliably). Android's Compose equivalent: one nullable-state-driven dialog/sheet
  per screen, not stacked booleans.
- **Deep-link routing**: notification taps and custom URL scheme links (`?submit=<id>`,
  `?absent=<id>`) map to a tab + a "pending action" id, consumed once the destination screen
  appears. Full event→destination table in §7 below.
- **Each tab root is independently navigable** (its own `NavigationStack` on iOS). Android's
  Compose equivalent (already in place): the existing `StaffTab` bottom-nav + per-tab
  `NavHost`/state pattern.

---

## 2. Bottom tabs — purpose & screen inventory

| Tab | Primary screen(s) | Pushed/sheet screens |
|---|---|---|
| **Home** | `HomeView` | `NotificationsSheet` (messages + Daily Jobs panel) |
| **Roster** | `RosterView` | `SubmitHoursSheet`, `ReportAbsenceSheet`, `HistoryView` (pushed) |
| **Tasks** | `TasksView` | `TaskCompletionDetailSheet`, `FullscreenImageView` |
| **Availability** | `AvailabilityView` | `DayEditSheet` |
| **Account** | `AccountView` | `PayslipsView`, `ChangeEmailView`, `VerifyPasswordSheet`, `AppVersionHistoryView`, `TermsOfServiceView`, `PrivacyPolicyView` |

Detail per screen:

1. **Home** (`HomeView`) — time-of-day-aware greeting ("Good morning/afternoon/evening
   {firstName}"), company-name pill top-left, bell icon with a combined badge (unread messages +
   pending Daily Jobs, display-capped at "9"), **Today** section (hero `ShiftCard` + inline
   `ClockInCard` when the shift is clockable), **Approved hours** 2×2 stat grid (week / month /
   year / all-time), **Upcoming** section (next 3 published shifts + "View roster" link).
   Pull-to-refresh. Long-press/context-menu on any shift card → "Add to Calendar".
2. **Roster** (`RosterView`, 416 lines) — Monday–Sunday `WeekSelector` strip, day-by-day
   `ShiftCard` list, an "Action needed" carousel at top (shifts needing submission/resubmission),
   locked-week indicator, swipe actions that change label by state (Submit Hours / Update Hours /
   Resubmit Hours / Report Absence / Undo Absence).
3. **Tasks** (`TasksView`, 675 lines — the single largest staff feature file) — today's applicable
   tasks (frequency+assignment filtered), priority badge, due time, photo-required indicator,
   camera-only completion (never gallery), up to 4 photos + optional note, week-strip dots marking
   days with this user's tasks.
4. **Availability** (`AvailabilityView`, 335 lines) — 7-day grid, navigable −2…+12 weeks, tap a
   day → `DayEditSheet` (Available toggle → All-day toggle → From/Until pickers). Locked weeks
   (always past/current, plus any future week the manager explicitly locked) show a read-only
   banner.
5. **History** (`HistoryView`, 309 lines, pushed from Roster, **not a tab**) — past timesheets
   grouped by month, filter by status + time period, search by location/notes.
6. **Account** (`AccountView`, 775 lines) — profile photo (local-only, never synced — see §4),
   email + verification badge, employee ID (read-only), member-since, mini-stat row, **Payslips**
   link, Notifications section, Appearance (Dark Mode toggle), Security (biometric toggle, change
   password), About (version history, Privacy Policy, Terms of Service, contact support), Delete
   Account section, Sign Out.
   - **Payslips** (`PayslipsView`, 317 lines) — glassmorphic month-picker pill, cache-first
     loading, grouped payslip history, PDF view/share/print/save.

---

## 3. Authentication flow

- **Sign-in only** — email + password. **No public sign-up, no invite-link flow.** Staff accounts
  exist only because a manager created them (out of scope for the Staff app itself, but staff
  land in a **forced first-login password change** as a direct consequence).
- **Login** (`LoginView`, 576 lines): email/password fields → `AuthViewModel.login`:
  1. Firebase Auth sign-in.
  2. Fetch `users/{uid}`, reject `locked`/`inactive` status with a typed error + force sign-out.
  3. Session-scoped: fresh manual login skips the biometric app-lock gate for that session.
  4. Writes `lastLoginAt`.
  - **Forgot password** → Firebase-hosted reset page (no custom backend screen).
  - Error state: shake animation, gated behind Reduce Motion.
- **Forced password change**: `mustChangePassword: true` on manager-created accounts routes
  straight to `ChangePasswordView(isForced: true)` before anything else is reachable.
- **Biometric app-lock** (two independent layers — **both apply to staff**):
  1. **Device-auth gate**: `LAContext` Face ID/Touch ID **with device-passcode fallback**, local
     only (not server-verified), re-required after **≥2 minutes backgrounded**.
  2. **Biometric quick-login**: Keychain-backed email+password store gated by
     `.biometryCurrentSet`, offers "Continue with Face ID" without re-typing the password.
     **7-day staleness rule**: refused if the last *manual* password login was >7 days ago.
- **Passkeys**: fully wired plumbing but **no UI entry point calls it** — dead code on iOS.
  **Do not build this on Android.**
- **Session persistence**: automatic (Firebase SDK). **Mid-session forced sign-out** if the
  account becomes `locked`/`inactive` while the app is open (e.g. a manager locks the account
  from elsewhere).
- **Password rules** (`BusinessRules`, must port exactly): **required** ≥8 chars, ≥1 uppercase,
  ≥1 digit; **recommended-only** (shown, not enforced) 1 symbol.

---

## 4. Profile / Account features

- **Profile photo**: native image picker with square-crop, stored **locally only**
  (`profile_photo.jpg` in the app sandbox) — **never uploaded to Firebase Storage/Firestore**, so
  it does not sync across devices or survive a reinstall. Android equivalent: local-only storage
  (e.g. app-private file / DataStore blob), not a Firestore/Storage write.
- **Editable self-service fields** (confirmed against `firestore.rules`, see §17): `fullName`,
  `phone`, `dob`, `address`, `emergencyContact`, `theme`. Everything else on the user doc is
  either manager-only or server(Worker)-only.
- **Email**: shown with a verification badge; changing it goes through `ChangeEmailView`
  (staff-initiated, Firebase's own verified-email-change flow) — **separate from** the
  manager-initiated "ask staff to change their email" request flag (`emailChangeRequired`),
  which just shows a banner prompting the staff member to go make the change themselves.
- **Employee ID**: manager-assigned, **read-only** to staff.
- **Mini-stat row**: approved hours, timesheet count, pending count.
- **Appearance**: Dark Mode toggle, `system`/`light`/`dark`, independent of "follow system."
- **Security section**: biometric toggle (re-auth via `VerifyPasswordSheet`), Change Password.
- **About**: app version → `AppVersionHistoryView` (hardcoded changelog, not Firestore-backed),
  Privacy Policy, Terms of Service (both native in-app views **and** published at
  `sura-roster.com/privacy` / `/terms`), Contact Support (`mailto:` deep link).
- **Delete Account**: staff-initiated, ATO-safe lifecycle — full detail in §15 of the source
  inventory; summarized here since it's staff-facing: `Account → "Request account deletion" →
  confirm → status becomes "requested"`. Manager approval **locks the account immediately** and
  starts a 30-day cancellable countdown; after 30 days a server cron purges the Auth login only
  — name/DOB/address/TFN/timesheets/payslips/attendance are **retained forever** for ATO record
  keeping. Staff cannot see or influence anything past their own "requested" state.

---

## 5. Roster & shift features

- **Shift status** staff sees (`StaffShiftDisplayStatus`): `scheduled, awaiting_submission,
  draft, pending, approved, rejected, absent_reported, absent`.
- **Staff can only ever see their own PUBLISHED shifts** — drafts are invisible (enforced by
  Firestore rules, not just UI, see §17).
- **Submit hours**: `SubmitHoursSheet` pre-fills from (priority order) existing timesheet →
  recorded clock session → rostered times; break minutes default from the clock session;
  validates `workedHours > 0`; **warns (dismissibly) about uncompleted tasks** before allowing
  submit; **editable until approved** (button reads "Update hours" while `pending`).
- **Report / undo absence**: `ReportAbsenceSheet` creates a `timesheets/{shiftId}` doc with
  `status = absent_reported`, zero hours, optional reason — only allowed before the shift becomes
  submittable and only if there's no existing timesheet or the existing one was rejected.
  Undoable (hard delete) while still `absent_reported`.
- **"Add to Calendar"**: EventKit write with a 1-hour-before alarm; if calendar permission is
  denied, **falls back to sharing an `.ics` file** instead of failing outright.
- **History**: all past timesheets, grouped by month, filterable (status, time period),
  searchable (location/notes).
- **Locked-week indicators**: current + all past weeks are *always* locked; future weeks are
  locked only if a manager explicitly locked them.
- **Business-rule constants that must port bit-for-bit**: Monday-first weeks,
  `Australia/Adelaide` timezone hardcoded everywhere, break clamped 0–90 min (step 5), shift
  visibility window **28 days back / 56 days forward**, `calcWorkedHours = (end−start−break)/60`
  rounded to 2 decimals with midnight-crossing handled by +1440 min, `isSubmittable = now >=
  (submittableAfter ?? computed shift end)`.

---

## 6. Timesheets & time/attendance

Two parallel systems:

1. **`ClockSession`** — device-local only (`UserDefaults`), the live-timer UX. **Deliberately
   never written to Firestore mid-shift** — the deployed rules block a staff timesheet write
   until `submittableAfter`, so there's nowhere trustworthy to put it early. Paid time starts at
   the **rostered start**, not actual clock-in (an early check-in's pre-shift minutes don't
   count); a late check-in's paid start is the actual clock-in time. Break-minute rounding: to
   nearest 5, clamped 0–90.
2. **`ShiftAttendance`** (`shift_attendance/{shiftId}`) — the server-authoritative audit trail.
   `clockInAt`/`clockOutAt` **must** equal `request.time` (Firestore-rules-enforced server
   timestamp — a manipulated device clock cannot forge them); `clockInDeviceAt`/`clockOutDeviceAt`
   record the device's own clock at the same instant so a **>2 minute skew** can be flagged.
   - **GPS geofence policy — different rules for start vs end**:
     - *Start*, geofence enforced: outside radius = **hard blocked**.
     - *Start*, not enforced: 250m lenient allowance; further out **warns but allows**.
     - *End*: **never restricted** — best-effort capture only.
   - Offline/failure: the local session always still works; a failed sync shows "recorded on
     device, couldn't sync" rather than silently losing the attempt.
3. **Manager correction / approve / reject** are manager-side actions staff only *observe* the
   result of (a push + a status change on their own timesheet).

**Timesheet status**: `draft, pending, approved, rejected, absent_reported, absent`.
**Staff-editable**: `draft, pending, rejected, absent_reported`. **Locked**: `approved, absent`.

---

## 7. Availability

- 7-day grid per week, Monday-first, `DayAvailability{available, allDay, start?, end?}`, default
  day = available all-day 09:00–17:00.
- **Saved via the Worker API, never a direct Firestore write** — so week locks are
  server-enforced, not just a client-side check that could be bypassed.
- Week bounds: **−2 to +12 weeks** from the current week.
- Locked-week banner (read-only) — see §5.
- **No formal PTO/leave-accrual system exists anywhere in this codebase.** Absence reporting
  (§5/§6) is the only leave-adjacent feature. Do not build a leave system unless separately
  scoped — it would be new product surface, not iOS parity.

---

## 8. Jobs & Tasks (two entirely separate features — do not conflate)

### Tasks
- Manager-created (out of scope to build, but staff consume the result): one-off/daily/weekly,
  optionally assigned to specific staff (empty = everyone), priority, due time, photo-proof **or**
  tick-to-complete per task.
- **Scheduling logic** (`RosterTask.isActive`, exact port needed): inactive flag → never active;
  past `endDate` → not active; `"once"` → only on its exact date; `"weekly"` → only on matching
  weekdays; default `"daily"` → always active.
- **Completion**: camera-only capture (never gallery — anti-fraud), up to 4 photos, each
  compressed to fit a 2MB cap (downscale to 1600px longest edge, step JPEG quality 0.7→0.1).
  Completion doc id is `{taskId}_{date}` — **one completion shared across all assignees for that
  day**, not per-assignee.
- **Photo lifecycle (staff side)**: local copies auto-delete at the end of the week they were
  taken; after that staff see a "Photo submitted" placeholder and the app **never re-downloads**
  a staff's own submitted photo from the cloud.

### Daily Jobs (completely separate — no tab, surfaced via the Home bell)
- Manager maintains a permanent reusable job-template library; assigns a selection to one staff
  member's one specific shift.
- **Staff never reads the template library directly** (confirmed in `firestore.rules` — staff
  has zero read access to `daily_job_templates`), only their own **assignments**.
- **Visibility**: visible for the entire shift date (not gated by rostered end time), disappears
  once the date rolls over.
- **Completion surface**: the **Home bell notification panel**, not a dedicated tab — staff
  complete/undo Daily Jobs there. Sort order is title-only and deliberately does **not**
  reorder on toggle (avoids mis-taps from a row jumping after completion).
- Staff may only toggle `completed`/`completedAt`/`completedBy` on their own assignment — nothing
  else on that doc is staff-writable (confirmed in rules).

---

## 9. Payslips (staff-visible slice of Payroll)

- **Staff visibility is a hard security boundary, not just UI filtering**: staff can only ever
  read their own payslips with `status in ['submitted', 'archived']` — `draft`/`under_review`/
  `approved` payslips are **invisible at the rules level**, confirmed directly in
  `firestore.rules`. **Staff can never write to `payslips` at all.**
- **Loading strategy**: never a live listener — cache-first, **month-scoped**
  (`staffPayslips(monthKey:)`): session memory → Firestore's on-disk cache (`source: .cache`,
  zero reads, offline-capable) → server (only for never-downloaded months, current month once
  per session, or explicit pull-to-refresh). "Downloaded months" tracked locally so an empty
  month doesn't keep re-hitting the server.
- **PDF**: A4, monochrome, same renderer produces both the export and the (manager-side) live
  preview — WYSIWYG guarantee staff benefit from indirectly (what they download matches what was
  approved). View/share/print/save via the OS share sheet.
- **TFN**: staff never see their own TFN in this app (manager-entry-only, masked display is a
  manager-side feature) — but note the field **is present** on their own `users/{uid}` doc since
  Firestore can't redact individual fields from a doc a user is otherwise allowed to read. Treat
  it as sensitive-in-transit on Android even though there's no staff-facing TFN screen to build.
- Employee ID (manager-assigned) is snapshotted onto payslips and shown alongside them.

---

## 10. Notifications

### Local (device-scheduled, survive force-quit)

Exact reminder slots, all relative to **rostered** (not verified) start/end — port verbatim:

| Slot | Timing | Title | Body |
|---|---|---|---|
| `24h` | 24h before start | "Shift tomorrow" | "You have a shift tomorrow at {start}." |
| `6h` | 6h before start | "Shift today" | "Your shift starts in 6 hours, at {start}." |
| `1h` | 1h before start | "Shift soon" | "Your shift starts in 1 hour, at {start}." |
| `30m` | 30m before start | "Shift in 30 minutes" | "...at {location}." |
| `5m` | 5m before start | "Ready to start?" | "Start Shift is now available." |
| `forgot-start` | 10m after start | "Don't forget to start your shift" | cancelled the instant clock-in succeeds |
| `forgot-end` | 10m after rostered end | "Your shift has ended" | only armed while clocked in |
| `submit-hours` | 15m after rostered end | "Submit your hours" | only if no filed timesheet, 48h look-back |

- Rebuilt **idempotently** on every shifts/timesheets update (clears and re-schedules everything
  named `shift-reminder.*`). Capped at 8 shifts (iOS's own cap, driven by its 64-pending-local
  limit — Android has no such OS ceiling but should still cap for battery/Doze sanity).
- "Filed" statuses that suppress the `submit-hours` reminder: `pending, approved,
  absent_reported, absent`. `draft`/`rejected`/none still need the reminder.
- Scheduled against the **absolute instant**, not a calendar trigger — a device timezone change
  can't shift the fire time.

### Remote push (FCM)

- Token written to `users/{uid}/notificationTokens/{urlEncodedToken}` — **exact schema**:
  `token, platform, userAgent, enabled, createdAt, updatedAt`. `platform` must be one of the
  whitelisted values in the deployed rules (confirmed this pass:
  `'ios-pwa','android-pwa','macos-pwa','macos-browser','desktop','ios-native','android-native'`
  — **`android-native` is already whitelisted**, no backend change needed for Android push).
- **Event registry** (shared with the Worker, staff-relevant subset): `roster-published`,
  `timesheet-approved`, `timesheet-rejected`, `timesheet-reminder`, `message-task`,
  `shift-changed`, `shift-cancelled`, `shift-started`, `shift-ended`, `job-assigned`,
  `jobs-all-completed`, `payslip-generated`.
- **Local-alert backup**: two events also fire a **local** notification as a same-process-alive
  backup even before push arrives — timesheet approve/reject decisions, and newly-published
  shifts. (Primed on first snapshot so login doesn't spam old state as "new".)
- **Deep-link routing** (event → destination): `timesheet-rejected` → open Submit Hours for that
  shift; `timesheet-approved`/`roster-published`/`shift-changed`/`shift-cancelled` → Roster tab;
  `submit-hours`/`forgot-end` local slots → open Submit Hours; other `shift-reminder` slots →
  Home tab; unrecognized → falls back to a URL-path substring match.
- **Permission UX**: requested on every login (OS de-dupes the actual system prompt); an
  explainer alert ("Enable shift & hours reminders?") is shown before the *first* ask.
- **Badge**: the Home bell shows unread messages **+ pending Daily Jobs count**, capped display
  at "9".
- **No dedicated "notification history" screen** — the closest thing is the bell's
  `NotificationsSheet` (current unread messages + pending jobs), not a scrollable log of past
  notifications. Don't over-build a history feature that doesn't exist on iOS.

---

## 11. Calendar integration

- `CalendarService` — EventKit **write-only** access (iOS 17+ scoped API), creates an event with
  a 1-hour-before alarm on the device's default calendar.
- **Graceful degradation**: if calendar permission is denied, generates and shares an `.ics`
  file via the OS share sheet instead of failing. Android equivalent: `CalendarContract`
  ContentProvider write, with an `.ics` file-share fallback via `Intent.ACTION_SEND` if the
  write is denied/unavailable.

---

## 12. Offline behavior, local storage, caching

- **Firestore offline persistence**: enabled, **unlimited** on-disk cache. Every live listener
  transparently serves cached data offline; writes queue and flush on reconnect via the SDK's
  own built-in queue — **no custom outbox/retry system exists**, and none should be built on
  Android either (the plan is to rely on the same SDK behavior, which the Android Firestore SDK
  supports identically).
- **Cache-first exception**: Payslips only (§9) — everything else is live-listener-driven.
- **Local persistence outside Firestore** (staff-relevant):
  - `ClockSession` → local key-value store, per-uid.
  - Task/profile photos → app-private storage, never the shared photo library.
  - Biometric credentials → platform secure storage (Keychain on iOS; Android equivalent is the
    Keystore-backed encrypted credential store already used for `DeviceAuthPreferences`).
  - "Last manual login date", "downloaded payslip months" → local preferences.
  - Pending local notifications → OS-managed (`AlarmManager`/`WorkManager` on Android).
- **Pull-to-refresh**: a one-shot server-source re-fetch layered on top of the always-on
  listeners, as a fallback — must mirror the exact staff-scoped queries (a past iOS bug ran the
  wrong role's queries on refresh; don't repeat it).
- **`pendingFirstSnapshot` gating**: loading state stays true until every relevant listener has
  delivered its first snapshot — prevents a "flash of empty roster" before data streams in. This
  is a real, previously-fixed bug class; build it in from day one on Android rather than
  discovering it later.
- **No background sync / no `BGTaskScheduler`-equivalent** — this is a deliberate product
  decision (documented in App Store review notes), not a gap. Local notifications are
  pre-scheduled at sync time instead of keeping a background process alive. **Do not build
  `WorkManager` periodic background sync as a "feature"** — match iOS's actual behavior.

---

## 13. Animations & haptics

- **Motion**: spring animations for sheet/dropdown presentations, `.contentTransition(
  .numericText())` for animated numeric changes (worked-hours figure, break-minutes stepper,
  bell badge count), shimmer `Skeleton`/`SkeletonCard` loading placeholders, geometry-driven
  scroll-fade hints (top/bottom gradient + optional chevrons) on scrollable content, a
  collapsing nav-bar title pill driven by scroll offset.
- **Reduce Motion respected explicitly**: login-error shake is gated behind
  `accessibilityReduceMotion`; any drag/highlight animation likewise checks it.
- **Haptics** (`Services/Haptics.swift`) — a full semantic vocabulary layered over primitive
  feedback types (`light/medium/heavy/soft/rigid/selection/success/warning/error`) **plus** named
  semantic events used pervasively throughout the staff app: `tabChange`, `signIn`, `signOut`,
  `forcedSignOut`, `saveSuccess`/`saveError`, `submitSuccess`/`submitError`,
  `authSuccess`/`authFailure`, `Notification.delivered`/`opened`/`urgent`. This granular mapping
  is called out explicitly in the source inventory as **a deliberate "premium feel" signal
  Android should replicate**, not skip as a nice-to-have.

---

## 14. Error handling, loading states, empty states

- **Two-tier error discipline**: writes that **must** surface errors to the user (timesheet
  submit, availability save) vs. writes that are **best-effort** (push-notification triggers,
  audit-log writes) — best-effort failures are logged, never silently swallowed, and never block
  the primary user action. Android should adopt the same split rather than treating every write
  identically.
- **Offline write failures are visible, not crashes**: "recorded on device, couldn't sync" style
  messaging rather than a hard failure — explicitly smoke-tested in the iOS repo's own test plan.
- **Loading**: shimmer skeleton placeholders (`SkeletonCard`) during initial load;
  `pendingFirstSnapshot` gating (§12) prevents a flash of incorrect empty state.
- **Empty states**: a shared `EmptyStateView` component used consistently across tabs (icon +
  title, optionally a message/action) — this maps directly onto the Android app's own existing
  `EmptyState` composable, already in the same shape.

---

## 15. Accessibility

- Content text uses **semantic, Dynamic-Type-scalable** styles throughout (no fixed point sizes
  for body/label text) — Android equivalent: respect system font-scale via `sp` units and
  Compose's built-in scaling, never hardcoded `dp`-sized text.
- **Reduce Motion** respected explicitly for custom animations (§13).
- **VoiceOver**: labels/hints added to icon-only controls (password show/hide, etc.);
  `.accessibilityElement(children: .combine)` merges a card's info block into one VoiceOver stop
  while keeping action buttons individually focusable — Android equivalent: `mergeDescendants`
  semantics grouping with focusable action targets left ungrouped.
- **Explicitly flagged by the source team as not exhaustively audited** — "solid baseline, not
  exhaustively audited" is the iOS team's own characterization. Android should not assume iOS
  parity here is a finished target to hit; it's a floor, and a real device TalkBack pass is still
  warranted independently (this is also called out as a gap in the existing
  `ANDROID-BUILD-PLAN.md`, Phase 17).

---

## 16. UI components & gestures (staff-relevant)

**Shared components** (`DesignSystem/Components/`) staff screens draw on: `Banner`
(info/warning/error/success inline), `Buttons` (primary/secondary/inline-pill styles),
`CameraPicker`, `EmptyStateView`, `Card`/`HeroCard`, `ScreenTitlePill`/`ToolbarLeadingTitlePill`
(fixed nav-bar title pill), `ScrollFadeHints`, `SectionHeader`, `Skeleton`/`SkeletonCard`,
`StatTile`, `StatusPill`, `TitlePillCollapse`, `Toast`, `WeekSelector`.

**Gestures**:
- Swipe actions on shift/timesheet rows (context-dependent label/action).
- Pull-to-refresh on every listener-backed scroll surface.
- Pinch-zoom on the full-screen task-photo viewer.
- Long-press/context-menu on shift cards ("Add to Calendar").
- Camera-only capture flow for task photos (no gallery picker at all).

**Design tokens for visual parity** (exact values, from `DesignSystem/Theme.swift`):
- Brand indigo: light `#4F46E5` / dark `#818CF8`. Accent (success) emerald: `#059669`/`#34D399`.
  Warning amber: `#B45309`/`#F59E0B`. Error red: `#DC2626`/`#EF4444`.
- Status-pill palette (7 states): scheduled=blue `#3B82F6`, awaiting_submission/pending=amber
  `#B45309`, draft=gray `#6B7280`, approved=emerald `#059669`, rejected=red `#DC2626`,
  absent_reported=orange `#C2410C`, absent=red `#DC2626`.
- Corner radii: small 8, medium 14, large 20. Screen padding 16. (These already match what the
  Android app's `ui/theme/Shape.kt` uses — confirms the existing Android theme work is
  correctly aligned to iOS, not a coincidence worth re-deriving.)

---

## 17. Firestore permissions ground truth (staff role)

Read directly from `Roster PWA/firestore.rules` this pass (confirmed byte-identical to the
deployed-rules reference iOS keeps in `docs/reference/firestore.rules.deployed`). **This is the
actual security boundary** — client-side role checks in any app are a UX convenience layered on
top, not the enforcement mechanism. Full detail and the Android-side implications are in
[ANDROID-STAFF-BUILD-PLAN.md](./ANDROID-STAFF-BUILD-PLAN.md) §"Staff Permissions", this section
is the raw source-of-truth extract.

| Collection | Staff read | Staff write |
|---|---|---|
| `users/{uid}` | **own doc only** (not other users, not even other staff) | update only: `fullName, phone, dob, address, emergencyContact, theme, profileUpdateRequired, updatedAt, lastLoginAt`. No create, no delete. |
| `users/{uid}/notificationTokens/{id}` | own only | full CRUD, own only, strict schema |
| `shifts/{id}` | own, **published only** (drafts invisible) | none |
| `timesheets/{id}` | own only | create own (submittable-gated); update own while `pending/rejected/draft/absent_reported`; delete own only while `absent_reported` |
| `auditLogs` | **none** | none |
| `wages` | **none** | none |
| `payslips/{id}` | own, **status `submitted`/`archived` only** | none |
| `settings/{doc}` | any authenticated user | none |
| `masterSheets` | **none** | none |
| `tasks/{id}` | any authenticated user | none |
| `task_completions/{id}` | any authenticated user | create own; update own (or a manager-reopened one); no delete |
| `daily_job_templates` | **none** | none |
| `daily_job_assignments/{id}` | own only | update own, **only** `completed/completedAt/completedBy` fields; no create/delete |
| `shift_attendance/{id}` | own only | create own (server-timestamp-gated); update own (identity fields frozen, times must equal `request.time`); no delete |
| `messages/{id}` | own (`recipientId == self`) | update own, **only** to flip `read` to `true`; no create/delete |

---

## 18. Explicitly out of scope for the Staff app

Carried forward from the source inventory's own "not present" list, plus staff-specific
exclusions:

- Everything manager-side (screens, Firestore write access, Worker admin endpoints) — see
  [ANDROID-STAFF-BUILD-PLAN.md](./ANDROID-STAFF-BUILD-PLAN.md).
- Passkeys (iOS has the plumbing, zero UI entry point).
- Multi-tenant/organization model, public sign-up.
- Formal PTO/leave-accrual system (doesn't exist in either source app).
- Widgets, Siri/App Intents, background app refresh (`BGTaskScheduler`).
- Third-party analytics SDK (Crashlytics only, no GA4-equivalent, matching iOS's own
  product-owner-confirmed decision already recorded in `ANDROID-BUILD-PLAN.md` §1.4).
- A scrollable "notification history" screen (doesn't exist on iOS — see §10).
