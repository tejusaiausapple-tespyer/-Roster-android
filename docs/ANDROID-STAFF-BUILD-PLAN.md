# Rosterra Android — Staff-Only Build Plan

> **Phase 2 deliverable**, built on [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) (Phase 1). Scope
> directive: **build the Native Android Staff App to production quality first; Manager
> functionality is out of scope for all new work until Staff ships.**
>
> **Relationship to the existing plan**: [ANDROID-BUILD-PLAN.md](./ANDROID-BUILD-PLAN.md) (the
> original 20-phase union-of-PWA-and-iOS plan) and its companion
> [PWA-FEATURE-INVENTORY.md](./PWA-FEATURE-INVENTORY.md) are **not superseded** — they remain the
> reference for when Manager work resumes. This document narrows that plan to the staff slice
> only, sequences it as its own roadmap, and adds the permissions/notification/caching/security
> detail the original plan left at a higher altitude.
>
> **Existing Manager-side code** (`ManagerDashboardScreen`, `StaffListScreen`, `StaffEditSheet`,
> `ManagerViewModel` family) is **left in place, untouched, not extended**. It already works
> against production data. Nothing in this plan asks for it to be removed.

---

## 1. Current status snapshot (honest baseline, not aspirational)

Read directly off the source tree, not from memory of what was intended. ✅ done · 🟡 partial ·
❌ not started.

| Area | Status | Notes |
|---|---|---|
| Gradle/Hilt/Compose M3 foundation | ✅ | Role-agnostic, reusable as-is |
| Firebase SDK wiring (Auth/Firestore/Storage/Crashlytics/unlimited cache) | ✅ | `FirebaseModule.kt` |
| Worker API client + all endpoints incl. `saveAvailability`, `sendNotification` | ✅ | `WorkerApiService.kt` — built, none yet *called* from a staff screen |
| Login, forced password change, forgot password, device-auth gate | ✅ | `AuthViewModel`, `LoginScreen`, `ChangePasswordScreen`, `DeviceAuthGateScreen` |
| Biometric **quick-login credential store** (layer 2 — skip re-typing password) | ❌ | Only the app-lock gate (layer 1) exists |
| Staff data models (`Shift`, `Timesheet`, `ShiftAttendance`, `RosterTask`, `TaskCompletion`, `DailyJob`) | 🟡 | Models + tolerant parsing exist; repositories are minimal single-query stubs, not the full listener-window pattern |
| `Payslip` model/repository | ❌ | Does not exist yet |
| `ClockSession` (local live-timer state) | ❌ | Does not exist yet |
| Staff Home tab | 🟡 | Greeting, today/upcoming shift cards. No clock-in card, no stat grid, no bell/badge |
| Staff Roster tab | 🟡 | Day-grouped shift list with status. No week selector, no swipe actions, no Submit Hours/Report Absence sheets, no History screen |
| Clock-in/out, GPS/geofence | ❌ | Not started |
| Tasks tab | ❌ | Data models exist, zero UI |
| Availability tab | ❌ | Worker endpoint exists, zero UI |
| Daily Jobs (Home bell panel) | ❌ | Not started |
| Payslips | ❌ | Not started |
| Account tab | 🟡 | Profile, appearance, security placeholder, about, delete-account, sign-out exist. No calendar integration, no notification settings, no biometric quick-login toggle |
| Calendar integration | ❌ | Not started |
| Local notifications (shift reminders) | ❌ | Not started |
| Push notifications (FCM) | ❌ | Not started |
| Haptics vocabulary | ❌ | Not started |
| Shimmer/skeleton loading states | ❌ | Placeholder screens exist; no skeleton components |
| Automated tests | ❌ | One test file (`AppRouteTest`) |

This is the honest starting line for every phase below — several "Phase 4/Auth" and "Phase 3/
Backend" items from the original plan are further along than the phase numbers below might
suggest, because that foundation work was role-agnostic and already done.

---

## 2. Phased roadmap (staff-only)

Each phase lists: features · dependencies · backend endpoints · Firestore collections · security
considerations · offline behavior · local cache strategy · testing checklist · completion
criteria. Complexity: **S** 2–5d · **M** 1–2wk · **L** 2–4wk · **XL** 4wk+.

### Phase A — Staff Data Layer Completion
- **Features**: extend `ShiftRepository`/`TimesheetRepository`/`ShiftAttendanceRepository` to the
  real **−28/+56 day** listener window (currently ad hoc single-date/single-staff queries); add
  O(1) index maps (`shiftsById`, `timesheetsByShiftId`) as `StateFlow<Map<...>>` derived from the
  raw listener flows, mirroring iOS's `didSet`-maintained dictionaries; add `Payslip` model +
  repository with the exact iOS cache-first month-scoped strategy (session memory → Firestore
  disk cache `source: CACHE` → server); add local `ClockSession` (DataStore-backed, per-uid).
- **Dependencies**: none — pure data-layer work, can start immediately.
- **Backend endpoints**: none new (all Worker endpoints already exist).
- **Firestore collections**: `shifts`, `timesheets`, `shift_attendance`, `payslips` (all
  staff-scoped queries only — see §4).
- **Security**: every new query must match the exact shape confirmed against
  `firestore.rules` (§4) and an already-deployed composite index — check
  `Roster PWA/firestore.indexes.json` before assuming any `.whereEqualTo().whereGreaterThanOrEqualTo()`
  compound query will succeed; do not add a new index requirement without confirming it's
  deployed.
- **Offline**: rely on Firestore SDK's own persistent cache and write queue — no bespoke outbox.
- **Local cache**: `Payslip` cache-first tiering as above; `ClockSession` in DataStore, not
  Firestore (mirrors iOS's deliberate "rules block early writes" reasoning — see
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §6).
- **Testing**: repository unit tests against the Firestore emulator confirming window boundaries
  and index correctness; no screen should render a false "empty" state before first snapshot
  (`pendingFirstSnapshot` gating — port this pattern now, before screens are built on top of it).
- **Completion criteria**: every staff-facing repository exposes the correct window, the correct
  indices, and gates loading state on first-snapshot arrival — verified by emulator-seeded tests,
  not manual inspection.
- **Complexity**: M.

### Phase B — Core Business Logic (pure, unit-tested)
- **Features**: port `BusinessRules`/`RosterCalendar` exhaustively — `calcWorkedHours`,
  `isSubmittable`, `canReportAbsence`, `displayStatus`, DST-correct wall-clock shift instants
  (Adelaide timezone), `ClockSession` paid-time math (rostered-start-anchored, break-overlap
  partial deduction), `RosterTask.isActive` scheduling logic, `payrollGaps` detection (for the
  "uncompleted tasks" submit-hours warning's sibling concept). TFN utilities already ported
  (`Tfn.kt`) — reuse as-is.
- **Dependencies**: Phase A (shapes to operate on).
- **Backend endpoints**: none.
- **Firestore collections**: none directly — pure functions over already-fetched data.
- **Security**: n/a (no I/O).
- **Offline**: n/a.
- **Local cache**: n/a.
- **Testing**: every named test case in `IOS-FEATURE-INVENTORY.md`/`RosterraTests` that touches
  staff-visible logic gets a bit-for-bit Android counterpart (`testShiftInstantsAcrossDSTStart`
  is the one most likely to be silently wrong if skipped).
- **Completion criteria**: 100% of ported test cases pass with identical expected values to the
  Swift originals.
- **Complexity**: L (highest-precision phase — time/attendance math errors are expensive).

### Phase C — Home Tab Completion
- **Features**: clock-in card (inline on the today shift, only when clockable — depends on Phase
  D existing first, so this sub-piece slips until then), Approved Hours 2×2 stat grid
  (week/month/year/all-time via `HoursMetrics`-equivalent), notification bell with badge
  (pending Daily Jobs count, capped at "9" — depends on Phase G; no Messages — see Phase G's scope
  note), "Add to Calendar" context action (depends on Phase F).
- **Dependencies**: Phase A/B for data; bell badge and clock-in card can ship as visual
  placeholders until Phases D/G land, then wire live.
- **Backend endpoints**: none new.
- **Firestore collections**: `shifts`, `timesheets`, `daily_job_assignments` (own only).
- **Security**: bell badge count must only ever query staff-own-scoped collections — never a
  collection-wide count.
- **Offline**: standard live-listener behavior.
- **Local cache**: none beyond Phase A.
- **Testing**: Compose UI test for the greeting/stat-grid/upcoming-shifts render states (loading,
  populated, empty).
- **Completion criteria**: visually and functionally matches
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §2 item 1, minus the pieces gated on later phases.
- **Complexity**: M.

### Phase D — Roster Tab Completion + Clock-In/Out
- **Features**: week-strip selector, "Action needed" carousel, swipe actions
  (Submit/Report Absence/Undo/Resubmit with state-dependent labels), `SubmitHoursSheet`
  (pre-fill priority: timesheet → clock session → rostered; uncompleted-tasks warning),
  `ReportAbsenceSheet` + undo, locked-week indicators, History screen (pushed, month-grouped,
  filterable/searchable); **GPS-verified clock-in/out**: `FusedLocationProviderClient`
  single-fix pattern, geofence policy split by start-vs-end exactly as documented (start:
  enforced=hard-block, lenient=250m-warn; end: never restricted), device-vs-server clock-skew
  capture (`clockInDeviceAt`/`clockOutDeviceAt` alongside the server-timestamped fields), a
  `ServerClock`-equivalent skew-measurement service.
- **Dependencies**: Phase A (data), Phase B (business rules), location permission flow.
- **Backend endpoints**: none new — `shift_attendance` writes are direct Firestore (rules-gated,
  not Worker-mediated, matching iOS).
- **Firestore collections**: `shifts` (read), `timesheets` (staff-scoped create/update/delete per
  §4), `shift_attendance` (staff-scoped create/update per §4).
- **Security**: `clockInAt`/`clockOutAt` **must** be written as `FieldValue.serverTimestamp()` —
  the deployed rules reject anything else (`request.time` equality check). Do not let a client
  convenience "fix" ever send a device-computed timestamp for these two fields.
- **Offline**: local `ClockSession` always works regardless of connectivity; a failed
  `shift_attendance` sync must show "recorded on device, couldn't sync," never fail silently or
  crash.
- **Local cache**: `ClockSession` (Phase A).
- **Testing**: emulator test that a client-supplied (non-`serverTimestamp`) clock time is
  rejected by rules; manual device-matrix test of the geofence start/end policy split.
- **Completion criteria**: a staff test account can complete a full daily cycle — clock in
  (inside and outside geofence, both policies), clock out, submit hours, see history reflect it.
- **Complexity**: XL (GPS + rules-edge-case correctness + the highest-liability data on the whole
  staff surface).

### Phase E — Tasks Tab
- **Features**: today's applicable tasks (frequency/assignment filtered via ported
  `isActive`/`isAssigned` logic), priority/due-time/photo-required indicators, CameraX
  camera-only capture (no gallery), up to 4 photos compressed to the 2MB budget, completion
  detail sheet, fullscreen pinch-zoom photo viewer, week-strip completion dots, local photo
  retention sweep (delete at end of the week taken).
- **Dependencies**: Phase A/B, camera permission flow.
- **Backend endpoints**: none new (photo upload is direct Firebase Storage).
- **Firestore collections**: `tasks` (read, any authenticated staff), `task_completions`
  (create own, update own/reopened-only per §4).
- **Security**: completion `completedBy` must always equal the signed-in uid; never allow
  overwriting another staff member's completion unless the doc's `completed == false` (a
  manager-triggered redo) — this is rules-enforced, but the client should never construct a
  write that would only work by accident of a rules gap.
- **Offline**: photo upload should queue/retry (Storage SDK has its own resumable-upload
  behavior); task list itself is live-listener-driven like everything else.
- **Local cache**: photo files in app-private storage only, never the shared media store — with
  the end-of-week sweep from [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §8.
- **Testing**: compression-budget test (verify output never exceeds 2MB), scheduling-logic
  parity tests (`isActive` for once/daily/weekly + endDate).
- **Completion criteria**: a staff account can complete a photo-proof task and a tick-complete
  task end-to-end, photos compress correctly, local copies age out on schedule.
- **Complexity**: L.

### Phase F — Availability Tab + Calendar Integration
- **Features**: 7-day grid, −2…+12 week navigation, day-edit bottom sheet (Available → All-day →
  From/Until), locked-week read-only banner (current/past always locked, future locked only if
  manager locked it); calendar write (`CalendarContract` + 1hr-before reminder) with `.ics`
  share-file fallback on permission denial, wired into Home/Roster shift-card context actions.
- **Dependencies**: Phase A/B.
- **Backend endpoints**: `POST /api/staff/availability` (**already implemented** —
  `WorkerApiService.saveStaffAvailability`, just needs a caller).
- **Firestore collections**: `settings/availabilityLocks` (read-only, any authenticated user),
  availability itself is **never a direct Firestore write** — always through the Worker so week
  locks stay server-enforced.
- **Security**: do not add a "just write it to Firestore directly" shortcut even though the
  staff's own `weeklyAvailability` field would otherwise be self-writable per the rules'
  `isValidSelfUserUpdate` allow-list gap — **it is deliberately excluded from that list** (see
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §17 note under `users/{uid}`) specifically so week
  locks can't be bypassed client-side. Always route through the Worker.
- **Offline**: availability save requires connectivity (Worker call, not a queued Firestore
  write) — show a clear "couldn't save, check connection" state rather than a silent local-only
  save that looks successful.
- **Local cache**: none beyond the live `users/{uid}` listener already in place.
- **Testing**: verify a locked week's save attempt is rejected server-side, not just hidden
  client-side; calendar-permission-denied path exercises the `.ics` fallback.
- **Completion criteria**: a staff account can set/save availability for an editable week, see a
  read-only locked week, and add a shift to their device calendar (or receive the `.ics` fallback
  if denied).
- **Complexity**: M.

### Phase G — Daily Jobs (Home bell panel)
> **Scope update (2026-07-24, product owner)**: Messages is explicitly **not needed** — not
> deferred, dropped — for both iOS and Android. Every earlier reference to a staff Messages
> inbox in this plan (bell badge formula, Home's Firestore reads, the `message-task` FCM event)
> is superseded by this phase, which is Daily Jobs only. A re-verification this pass found PWA's
> Messages feature fully live (nothing was actually removed there — see the note in `ANDROID-BUILD-PLAN.md`
> §1.4) and iOS's staff-side inbox (`NotificationsSheet`) also genuinely live, not absent as
> earlier assumed — but per this decision neither is a build target for Android regardless.
- **Features**: `NotificationsSheet`-equivalent panel reachable from the Home bell — today's Daily
  Job assignments (complete/undo, title-only stable sort, full-shift-date visibility). No messages
  list.
- **Dependencies**: Phase C (bell badge), Phase A.
- **Backend endpoints**: none new.
- **Firestore collections**: `daily_job_assignments` (own, read + completion-field-only update).
- **Security**: the update write must touch **only** `completed/completedAt/completedBy` — any
  client code that spreads the whole assignment object into an update call risks tripping the
  rules' `hasOnly([...])` check and getting rejected (correct behavior, but build the client to
  send a minimal diff, not a full-object merge).
- **Offline**: standard listener behavior; a completion toggle made offline should show as
  pending/optimistic and reconcile silently on reconnect (see §5 optimistic-UI note) or, at
  minimum, not lose the tap.
- **Local cache**: none new.
- **Testing**: verify a completion write with extra fields is rejected by the emulator (proves
  the client only ever sends the minimal diff).
- **Completion criteria**: matches [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §8's Daily Jobs
  section exactly, including the deliberately-stable sort order.
- **Complexity**: M.

### Phase H — Payslips
- **Features**: Account → Payslips, glassmorphic-or-Material-equivalent month picker, cache-first
  month-scoped loading (session → Firestore disk cache → server, "downloaded months" tracked
  locally), grouped payslip history, in-app A4 monochrome PDF renderer (`PdfDocument`/`Canvas`)
  matching iOS's layout, view/share/print/save via `Intent.ACTION_SEND`.
- **Dependencies**: Phase A (`Payslip` model/repo).
- **Backend endpoints**: none.
- **Firestore collections**: `payslips` — **read-only**, and the rules already restrict this to
  `status in ['submitted','archived']` server-side (§4) — client-side filtering would be
  redundant, not a substitute.
- **Security**: never construct a query or doc-get that assumes a `draft`/`under_review`/
  `approved` payslip might be visible — it will correctly 403 at the rules level, but the UI
  should never present a state implying staff have any payroll-admin visibility.
- **Offline**: cache-first is the whole point here — a previously-downloaded month must render
  fully offline with zero network dependency.
- **Local cache**: the 3-tier cache described above; "downloaded months" in local preferences so
  an empty month doesn't re-hit the server every session.
- **Testing**: verify a staff query against a `draft` payslip returns nothing (emulator test);
  verify offline rendering of a previously-cached month.
- **Completion criteria**: PDF output is structurally comparable to the iOS-generated one for the
  same underlying data (not pixel-identical, but the same sections/ordering/values).
- **Complexity**: L.

### Phase I — Account Tab Completion
- **Features**: local-only profile photo (never uploaded), biometric **quick-login credential
  store** (layer 2 — Keystore-backed encrypted credentials, 7-day manual-login staleness rule),
  notification preferences section (Phase J dependency), About section (version history,
  Terms/Privacy — **already built**, wire up remaining links), delete-account flow polish
  (already has the repository/Worker calls — verify UI matches
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §4 exactly).
- **Dependencies**: Phase J for the notification-settings sub-section.
- **Backend endpoints**: none new (`AccountDeletionRepository` already covers request/status).
- **Firestore collections**: `users/{uid}` — self-update limited to the exact allow-listed field
  set in §4 (do not add a field to the client's update payload that isn't on that list; it will
  be rejected).
- **Security**: never attempt to self-write `tfn`, `deletion`, `role`, `status`, or
  `weeklyAvailability` from this screen — all four are outside the staff self-update allow-list.
- **Offline**: profile field edits queue via the standard Firestore write queue; local-only photo
  storage has no offline concern (it's never networked).
- **Local cache**: profile photo file, biometric credential store, appearance preference
  (already exists).
- **Testing**: verify an attempted self-write of an out-of-allow-list field is rejected
  (emulator).
- **Completion criteria**: Account tab reaches full parity with
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §4.
- **Complexity**: M.

### Phase J — Notifications (local + push)
- Full spec in §5 below. **Complexity**: L.

### Phase K — Offline Hardening & Performance
- **Features**: airplane-mode pass across every staff write path (clock-in/out, submit hours,
  report absence, task completion, availability save, Daily Job toggle) confirming each either
  succeeds-and-syncs or fails visibly — never silently or with a crash; pull-to-refresh on every
  listener-backed screen, scoped to staff-only queries; cold-start and scroll-jank profiling on a
  mid-tier device, not just an emulator.
- **Dependencies**: Phases C–I (needs real screens to test against).
- **Backend endpoints**: none.
- **Firestore collections**: all staff-scoped ones already listed above.
- **Security**: n/a (this phase is behavioral, not access-control).
- **Offline**: this phase's entire subject.
- **Local cache**: audit that nothing added a bespoke retry/outbox system where the SDK's own
  queue already suffices (matches the deliberate "no custom offline engine" iOS precedent).
- **Testing**: a written offline test matrix, one row per write path, each verified in airplane
  mode.
- **Completion criteria**: every row in the matrix passes.
- **Complexity**: M.

### Phase L — Design Polish (haptics, animation, accessibility, empty/loading states)
- **Features**: semantic haptic vocabulary (Android `HapticFeedbackConstants`/`VibrationEffect`
  mapped 1:1 to the iOS event taxonomy in
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §13); shimmer skeleton components for every
  listener-backed screen's loading state; `pendingFirstSnapshot`-style gating verified on every
  screen (not just Phase A's repositories); Reduce-Motion-equivalent respect
  (`Settings.Global.ANIMATOR_DURATION_SCALE` / Compose accessibility APIs); a real-device TalkBack
  pass (explicitly not just inherited from iOS's own "not exhaustively audited" baseline).
- **Dependencies**: all screen phases (C–I) substantially complete.
- **Backend endpoints / Firestore**: none.
- **Security**: n/a.
- **Offline**: n/a.
- **Local cache**: n/a.
- **Testing**: a haptics audit checklist (confirm every named event actually fires, not just that
  the API exists); TalkBack walkthrough of every staff screen.
- **Completion criteria**: full manager... *(n/a — staff)* — full **staff** walkthrough at normal
  animation speed feels fluid on a mid-tier device; TalkBack walkthrough completes with no
  unreadable/unreachable elements.
- **Complexity**: M.

### Phase M — Testing, QA, Production Readiness (staff scope)
- **Features**: unit test coverage audit against Phase B's ported business logic; Compose UI
  tests for critical flows (login, clock-in/out, submit hours, complete a task, save
  availability); Firestore-emulator integration tests for every repository; device-matrix manual
  QA (phone, tablet/foldable if available, one lower-end/older-OS device); Crashlytics +
  Performance Monitoring wired and verified with a deliberate test crash; structured logging
  (two-tier best-effort/must-succeed, matching §14 of the audit); production Firebase config
  separated from any dev/staging override.
- **Dependencies**: everything above.
- **Backend endpoints / Firestore**: none new.
- **Security**: final pass re-confirming every write path in this document against the live
  rules — not a re-read of this document, an actual rejected-write test per collection.
- **Offline**: re-run Phase K's matrix once more after all screens are final.
- **Local cache**: n/a (verification phase).
- **Testing**: this phase *is* the testing checklist — see §7.
- **Completion criteria**: all automated tests green in CI; manual QA checklist fully executed,
  no open Sev1/Sev2; at least one real staff beta tester completes a full day's workflow (clock
  in → tasks → clock out → submit hours) without a blocking issue; **only then** does Manager
  work resume, per the scope directive at the top of this document.
- **Complexity**: L.

---

## 3. Staff App Structure

The app is **5 tabs**, matching iOS exactly (already the shape of the existing `StaffTab` enum —
no navigation restructuring needed). The user's brief names some concepts that iOS treats as
sub-screens rather than tabs; each is mapped to its actual home below rather than force-fit into
a tab that doesn't exist on iOS (which would break parity, not improve it).

| Requested concept | Where it actually lives |
|---|---|
| Home | Tab: **Home** |
| Roster | Tab: **Roster** |
| Shifts | Part of **Roster** (no separate tab on iOS) |
| Jobs / Tasks | Tab: **Tasks** (Tasks feature); **Daily Jobs** via the Home bell panel (separate feature, not a tab — see [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §8) |
| Availability | Tab: **Availability** |
| Notifications | Home bell panel (Daily Jobs — no Messages, see Phase G's scope note) + system push/local notifications — not a tab |
| Payslips | Pushed from **Account** (not a tab) |
| Profile | Part of **Account** |
| Settings | Part of **Account** (Appearance, Security, About) |

### Home
- **Purpose**: today-at-a-glance — current shift, quick stats, what needs attention.
- **Screens**: Home root; sheet: Daily Jobs bell panel (Phase G).
- **User actions**: pull-to-refresh, tap shift card → Roster detail, tap bell → notification
  panel, long-press shift → Add to Calendar, complete/undo a Daily Job from the bell panel.
- **Firestore reads**: `shifts` (own, windowed), `timesheets` (own), `daily_job_assignments`
  (own, today).
- **Firestore writes**: none directly from Home itself (writes happen in the sheets it opens).
- **Cached data**: none beyond the standard live listeners.
- **Refresh strategy**: live listeners + pull-to-refresh fallback.
- **Offline capability**: fully readable from cache; actions requiring a write (completing a
  Daily Job) queue via the standard Firestore write queue.

### Roster (incl. Shifts, History)
- **Purpose**: the staff member's schedule, submitting hours, reporting absence.
- **Screens**: Roster root (week strip + day list), Submit Hours sheet, Report Absence sheet,
  History (pushed).
- **User actions**: navigate weeks, swipe a shift to submit/report-absence/undo/resubmit, search
  and filter History.
- **Firestore reads**: `shifts` (own, published, windowed), `timesheets` (own).
- **Firestore writes**: `timesheets` create/update (submit/edit hours) and delete
  (undo-absence-only) — all staff-scoped per §4.
- **Cached data**: standard listener cache; no bespoke cache.
- **Refresh strategy**: live listeners + pull-to-refresh.
- **Offline capability**: readable offline; a submit/report-absence write while offline queues
  and shows a pending/error state rather than silently failing (§2 Phase K).

### Tasks
- **Purpose**: complete manager-assigned tasks (photo-proof or tick-complete).
- **Screens**: Tasks root (today's list + week-strip dots), completion detail sheet, fullscreen
  photo viewer.
- **User actions**: complete via camera (photo tasks) or tick (simple tasks), view completion
  detail, pinch-zoom a photo.
- **Firestore reads**: `tasks` (any authenticated), `task_completions` (any authenticated, to see
  who completed what).
- **Firestore writes**: `task_completions` create (own) / update (own, or a manager-reopened
  doc). Photo bytes go to Firebase Storage, not Firestore.
- **Cached data**: task photo files in app-private storage, end-of-week local sweep.
- **Refresh strategy**: live listener on `tasks` + `task_completions`.
- **Offline capability**: task list readable offline; photo upload queues (Storage SDK's own
  resumable-upload retry) if offline at completion time.

### Availability
- **Purpose**: set weekly availability within the editable window.
- **Screens**: 7-day grid, day-edit sheet.
- **User actions**: navigate −2…+12 weeks, toggle available/all-day, set from/until times, save.
- **Firestore reads**: `users/{own uid}` (for `weeklyAvailability`), `settings/availabilityLocks`
  (read-only, any authenticated user).
- **Firestore writes**: **none directly** — save goes through
  `POST /api/staff/availability` (Worker), which enforces week locks server-side.
- **Cached data**: none beyond the standing `users/{uid}` listener.
- **Refresh strategy**: live listener for lock state; explicit save call for edits.
- **Offline capability**: requires connectivity to save (Worker call, not a queued Firestore
  write) — show a clear failure state offline rather than a false "saved."

### Account (Profile, Settings, Payslips)
- **Purpose**: identity, preferences, security, payroll history, legal, account lifecycle.
- **Screens**: Account root, Payslips (pushed), Change Email, Change Password, Verify Password
  (re-auth), App Version History, Terms of Service, Privacy Policy.
- **User actions**: edit allow-listed profile fields, toggle appearance/biometric, change
  password/email, view/download/share a payslip, request account deletion, sign out.
- **Firestore reads**: `users/{own uid}`, `payslips` (own, `submitted`/`archived` only).
- **Firestore writes**: `users/{own uid}` update (allow-listed fields only — §4).
- **Cached data**: profile photo (local file, never synced), payslip 3-tier cache (Phase H),
  appearance preference, biometric-enabled flag, device-auth-related local state.
- **Refresh strategy**: live listener on own user doc; Payslips is explicitly cache-first, not
  live (§2 Phase H).
- **Offline capability**: profile view fully offline; profile edits queue; Payslips is designed
  to be *the most* offline-capable screen in the app for previously-viewed months.

---

## 4. Staff Permissions

Grounded directly in the deployed `firestore.rules` (read this pass, cross-checked against the
iOS reference copy — see [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §17 for the raw table). **This
is the actual security boundary.** Everything the Android client does — role checks, UI hiding,
navigation gating — is a convenience layer on top of these rules, never a substitute for them. A
malicious or buggy client cannot exceed what's below no matter what the UI shows, but the UI
should never *offer* an action the rules would reject either.

### Staff CAN

| Action | Scope |
|---|---|
| **View** | Their own user profile; their own published shifts; their own timesheets; their own shift-attendance records; their own submitted/archived payslips; any task definition; any task completion (read, to see who completed what); their own Daily Job assignments; their own received messages; global `settings` docs (company name/address, shown read-only); global `tasks` list |
| **Create** | Their own timesheet (submittable-gated); their own shift-attendance clock-in record (server-timestamp-gated); their own task completion; their own notification-token doc |
| **Edit** | Allow-listed fields on their own profile (`fullName, phone, dob, address, emergencyContact, theme`); their own timesheet while `pending/rejected/draft/absent_reported`; their own shift-attendance clock-out (server-timestamp-gated, identity fields frozen); their own task completion (or a manager-reopened one); their own Daily Job assignment's `completed/completedAt/completedBy` fields only; their own message's `read` flag only |
| **Submit** | Hours (timesheet create/update), an absence report, a task completion, weekly availability (via the Worker, not a direct write) |
| **Upload** | Task-completion photos (camera-only, ≤4 per completion, compressed) |
| **Download** | Their own submitted/archived payslip PDFs |
| **Sync** | Everything above via the standard Firestore live-listener + offline-cache mechanism; nothing requires a bespoke sync engine |

### Staff CANNOT

Explicitly, by rules (not merely hidden in the UI):

- **Cannot** read another user's profile document — not even another staff member's.
- **Cannot** read any shift that isn't their own and published (draft shifts, other staff's
  shifts — invisible).
- **Cannot** read another staff member's timesheet, shift-attendance, or payslip.
- **Cannot** read `wages` (award/classification/earnings-line/pay-rate data) — **zero access**,
  not even their own resolved rate.
- **Cannot** read `auditLogs` — zero access.
- **Cannot** read `masterSheets` — zero access.
- **Cannot** read `daily_job_templates` (the reusable job library) — only their own assignments.
- **Cannot** create, update, or delete any `shifts` document.
- **Cannot** approve, reject, or otherwise touch `approvedBy`/`approvedAt`/`managerNotes` on any
  timesheet, including their own.
- **Cannot** set their own timesheet status to `approved` or `absent` (manager-only terminal
  states) — only `pending`, `draft`, or `absent_reported` (self-report).
- **Cannot** write `tfn`, `deletion`, `role`, `status`, `hourlyRate`, `superRate`,
  `weeklyAvailability`, or `emailChangeRequired` on their own user doc — all excluded from the
  self-update allow-list even though it's their own document.
- **Cannot** delete their own user profile, any shift, any approved/absent timesheet, or any
  shift-attendance record.
- **Cannot** create or write to `wages`, `payslips`, `messages` (beyond the `read` flag),
  `daily_job_templates`, `masterSheets`, `importBatches`, or `auditLogs` under any circumstance.
- **Cannot** see a payslip in any status other than `submitted`/`archived` — `draft`/
  `under_review`/`approved` payslips are invisible even if they know the doc id.

### Explicitly-named exclusions (per the brief)

All of the following are **manager-only surfaces the Staff app must not expose, link to, or
provide a route to reach**, even a hidden/deep-link one: Manager dashboard, payroll
administration (wage awards/classifications/earnings lines, payslip generation/editing),
staff management (directory, create/edit/lock/deactivate accounts, reset password, change
email), roster publishing (draft creation, publish, lock availability), administrative/business
settings (company details, locations/geofences), reports, financial/analytics data (labour cost,
tenure metrics), system configuration, and any Firestore collection outside the staff scope
listed above (`wages`, `auditLogs`, `masterSheets`, `daily_job_templates`, `importBatches`, and
any other user's documents in any collection).

**Enforcement is two-layered, and both layers matter**: (1) the Firestore rules above are the
real boundary and need no Android-specific backend change — the Android staff client simply never
gets a token that can do more than this; (2) the Android **app itself** should have no
navigation route, no screen, no menu item that leads toward manager functionality — not because
the rules wouldn't stop a write, but because presenting a control that always fails is a broken
UX, and because a role check that's only enforced server-side but not reflected in the client
invites confusing dead-end taps. The existing (untouched) manager screens live in a separate
navigation graph (`ManagerRootScreen`) that a staff-role user never enters — `AppRoute.determine`
already branches on role before either graph is reachable, so this separation is structural, not
just a missing menu item.

---

## 5. Notifications

### Local (device-scheduled shift reminders)

Port the exact slot table from [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §10 verbatim, using
`AlarmManager.setExactAndAllowWhileIdle` (survives app kill, unlike a plain coroutine delay) with
a `WorkManager` periodic re-sync as a Doze-safety net, keyed to absolute instants so a timezone
change can't shift fire times:

24h / 6h / 1h / 30m / 5m before rostered start · 10m after start ("forgot to clock in," cancelled
on clock-in) · 10m after rostered end ("forgot to clock out," armed only while clocked in) · 15m
after rostered end ("submit your hours," only if unfiled, 48h look-back).

- **Idempotent re-sync**: clear and rebuild every `shift-reminder.*` alarm on every
  shifts/timesheets listener update.
- **Cap**: mirror iOS's 8-shift cap as a starting point (Android has no 64-pending OS ceiling, but
  cap anyway for battery/Doze sanity).
- **Notification channels** (Android's typed-category system, roughly mapping to iOS's slot
  taxonomy): `shift-upcoming`, `timesheet-action`, `tasks`, `general` — each user-mutable via
  system settings, which the Account → Notifications section should deep-link to.

### Remote push (FCM)

- Token → `users/{uid}/notificationTokens/{urlEncodedToken}`, exact schema
  `token, platform: "android-native", userAgent, enabled, createdAt, updatedAt` —
  **`android-native` is already whitelisted** in the deployed rules, confirmed this pass. No
  backend change needed.
- **Staff-relevant event set** (from the shared registry): `roster-published`,
  `timesheet-approved`, `timesheet-rejected`, `timesheet-reminder`,
  `shift-changed`, `shift-cancelled`, `shift-started`, `shift-ended`, `job-assigned`,
  `jobs-all-completed`, `payslip-generated`. (`message-task` excluded — Messages is out of scope,
  see Phase G's scope note.)
- **Background handling**: a data-only FCM payload with no `notification` block needs explicit
  handling in `onMessageReceived` to show a heads-up notification while foregrounded — this is
  the exact class of bug already fixed once on iOS (`apns.payload.aps.alert` missing); do not
  reintroduce the Android equivalent gap.
- **Local-alert backup**: fire a local notification immediately for timesheet approve/reject and
  newly-published shifts even before push confirms delivery — primed on first snapshot so login
  doesn't spam stale state as "new."

### Deep-link routing

Map exactly onto iOS's table: `timesheet-rejected` → Submit Hours for that shift;
`timesheet-approved`/`roster-published`/`shift-changed`/`shift-cancelled` → Roster tab;
`submit-hours`/`forgot-end` local slots → Submit Hours; other shift-reminder slots → Home tab;
unrecognized → URL-path substring fallback (`roster`/`history`→Roster, `tasks`/`job`→Tasks,
`availability`→Availability, `account`→Account, `home`→Home).

### Badge counts

Home bell badge = pending Daily Jobs count (no Messages — see Phase G's scope note), capped
display at "9" — query must be staff-own-scoped (§4), never a collection-wide count.

### Notification history

**Do not build a scrollable notification-history screen** — it doesn't exist on iOS (confirmed in
[IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §10). The Home bell's live panel (pending Daily Jobs)
is the entire "history" surface on the reference platform; building more here would be scope
creep past parity, not toward it.

### Permission UX

Request on every login (the OS de-dupes the actual system prompt); show an explainer before the
*first* ask ("Enable shift & hours reminders?"); Account → Notifications shows pending-reminder
count + a link to system notification settings.

---

## 6. Local Cache & Performance

- **No bespoke local database.** Firestore's own persistent cache (unlimited size, already
  configured) handles every synced entity — matching both source apps' explicit "rely on the
  SDK's built-in offline queue" design choice. Do not introduce Room for staff-scoped data; it
  would duplicate what the SDK already does and risk drifting out of sync with it.
- **Offline-first**: every staff screen must render correctly from cache with zero network
  present — verified per-screen in Phase K's airplane-mode matrix.
- **Background synchronisation**: none, deliberately — matching iOS's explicit "no
  `BGTaskScheduler`, local notifications are pre-scheduled at sync time instead" decision (see
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §12). Do not build `WorkManager` periodic background
  sync as a feature; it isn't one on the reference platform and adds battery cost for no parity
  gain.
- **Refresh strategy**: live listeners are the default; pull-to-refresh is a one-shot
  `Source.SERVER` fallback layered on top, always scoped to the exact staff-only query shapes in
  §4 (a manager-shaped refresh accidentally running under a staff session was a real,
  previously-fixed iOS bug — don't reintroduce the equivalent).
- **Cache invalidation**: not applicable in the traditional sense — the Firestore SDK invalidates
  its own cache via listener updates. The one place with bespoke cache bookkeeping is Payslips'
  "downloaded months" tracking (local preference, not a TTL — a month either has been fetched
  from server this session/ever, or hasn't).
- **Optimistic UI**: apply for low-risk, easily-reversible actions (e.g. a Daily Job
  completion toggle) — show the new state immediately, reconcile silently against the listener's
  next emission, and only surface an error if the write is ultimately rejected. Do **not** apply
  optimistic UI to anything money- or time-adjacent (timesheet submission, clock-in/out) — those
  should show a real pending/working state, not a faked-successful one, given the domain's low
  tolerance for silent discrepancies.
- **Image caching**: task-completion photos and the local-only profile photo live in app-private
  storage with the retention sweeps in [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §8 — this *is*
  the image cache; no separate image-loading library cache layer is needed since there's no
  remote-image-gallery browsing feature anywhere in the staff app.
- **Network state monitoring**: surface a lightweight offline indicator (banner/pill) app-wide,
  matching both source apps' pattern — not per-screen bespoke handling.
- **Retry logic**: none custom beyond what Firestore's SDK and Storage's resumable-upload already
  provide — this is a deliberate constraint, not an oversight (see §2 Phase K).
- **Pagination / lazy loading**: History (month-grouped) and Payslips (month-scoped) are the only
  screens with meaningfully large lists; both are naturally paginated by their month-scoping
  already, so no separate infinite-scroll/pagination library is needed. Use `LazyColumn` with
  `key = { it.id }` everywhere a list is rendered (already the pattern in the existing Roster/
  Staff-directory screens) for basic recomposition efficiency.

---

## 7. Security Review

A living checklist — each row should be verified by an actual rejected-write test against the
Firestore emulator during Phase M, not signed off from reading this document alone.

| Boundary | How it's enforced | Verified how |
|---|---|---|
| Staff cannot read another user's profile | `users/{userId}` rule: `isManager() \|\| own uid` | Emulator: attempt read of a different uid, expect deny |
| Staff cannot read draft/other-staff shifts | `shifts` rule: published + own-staffId only | Emulator: attempt read of a draft or another staff's shift |
| Staff cannot approve/reject their own or anyone's timesheet | `timesheets` update rule excludes `approvedBy/approvedAt/managerNotes`, and status can only move to `pending/draft/absent_reported` | Emulator: attempt to set `status: approved` or write `approvedBy` |
| Staff cannot forge attendance timestamps | `shift_attendance` create/update requires `clockInAt`/`clockOutAt == request.time` | Emulator: attempt a client-supplied timestamp value |
| Staff cannot read payroll/wage data | `wages` rule: `isManager()` only | Emulator: attempt any read |
| Staff cannot read non-final payslips | `payslips` read rule: own + `status in [submitted, archived]` | Emulator: attempt read of a `draft` payslip (own uid) |
| Staff cannot read audit logs | `auditLogs` rule: `isManager()` only | Emulator: attempt any read |
| Staff cannot read the Daily Job template library | `daily_job_templates` rule: `isManager()` only | Emulator: attempt any read |
| Staff cannot escalate their own role/status | `users` self-update allow-list excludes `role`, `status`, `deletion` | Emulator: attempt to write `role: "manager"` on own doc |
| Staff cannot bypass availability week locks | Availability is Worker-mediated, not a direct Firestore write path in the client at all | Code review: confirm no direct `weeklyAvailability` Firestore write exists anywhere in the staff client |
| FCM tokens can't be spoofed to another user | `notificationTokens` subcollection scoped to `request.auth.uid == userId` at every level | Emulator: attempt to write a token doc under a different uid |
| No client-side-only trust assumptions | Every gate above has a server-side rule, not just a UI hide | Manual: grep the staff client for any write path that assumes a role check already happened client-side without a corresponding rule |

**No Firestore rules changes are required for the Android Staff app** — every boundary above is
already deployed and shared across PWA/iOS/Android. This phase is entirely about the Android
client never attempting a write the rules would reject (which fails safely) and never *offering*
a UI path toward one (which fails confusingly) — not about negotiating new backend access.

---

## 8. Quality Standards

- **Material Design 3** throughout — already the app's baseline (Material 3 Expressive theme,
  brand-locked palette matching iOS's exact hex values per
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §16).
- **Animations**: shimmer skeleton loading states, `AnimatedContent`/`AnimatedVisibility` for
  state transitions, animated numeric transitions (worked-hours figure, badge counts) mirroring
  iOS's `.contentTransition(.numericText())`, Reduce-Motion-equivalent respect throughout.
- **Haptics**: the full semantic vocabulary from
  [IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md) §13, not just a couple of button-press taps —
  treated as a real feature, not decoration.
- **Responsiveness**: cold-start and scroll-jank profiled on a mid-tier device (not just a
  flagship emulator), per Phase K/M.
- **Offline support**: the entire point of §6 — every staff screen must degrade gracefully, never
  crash, when connectivity drops.
- **Production-ready**: Crashlytics + Performance Monitoring wired and verified, structured
  two-tier logging, real device-matrix QA, before Phase M is considered complete.
- **Feature parity with iOS Staff, Android-native patterns where appropriate**: this means
  matching *behavior* (the exact business rules, the exact permission boundaries, the exact
  notification slots) while using idiomatic Android UI patterns rather than literally porting
  SwiftUI sheets/gestures — e.g. Material bottom sheets instead of iOS presentation detents,
  `HapticFeedbackConstants` instead of `UIImpactFeedbackGenerator`, swipe-to-reveal actions tuned
  for Android touch ergonomics rather than a literal `.swipeActions` port. The audit
  ([IOS-STAFF-AUDIT.md](./IOS-STAFF-AUDIT.md)) is the behavioral spec; this plan's phases are
  where Android-native execution choices get made.

---

## 9. What "production quality" means before Manager work resumes

Per the brief's own gate — Phase M's completion criteria (§2) is the literal bar: all automated
tests green, manual QA checklist clear of Sev1/Sev2, and at least one real staff beta tester
completing a full day's workflow without a blocking issue. That's the signal to pick
`ANDROID-BUILD-PLAN.md`'s Phase 9 (Manager Module) back up — not a calendar date, not "most of it
works."
