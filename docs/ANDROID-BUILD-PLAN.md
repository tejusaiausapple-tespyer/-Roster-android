# Rosterra Android — Master Build Plan

> Companion documents: [`PWA-FEATURE-INVENTORY.md`](./PWA-FEATURE-INVENTORY.md) (full detail on `Roster PWA`) and [`IOS-FEATURE-INVENTORY.md`](./IOS-FEATURE-INVENTORY.md) (full detail on `Roster IOS NATIVE APP`). Both were produced by exhaustive source-level review — every screen, Firestore collection, security rule, business-rule test, and Worker endpoint in each codebase — not from documentation alone. This document synthesizes them into a parity plan and a 20-phase roadmap for a fully native Android app. **No Android code has been written.** This is the discovery + planning deliverable requested before implementation begins.

Shared backend for all three clients: Firebase project `roster-8a270` (Auth, Firestore, Cloud Messaging, Storage, Crashlytics) + Cloudflare Worker at `https://sura-roster.com` (`worker/index.ts` — the only holder of Firebase service-account credentials, source of server time, and the sole channel for privileged writes). Android is a **third client of the same backend**, not a new system — most "backend work" is Android-side integration, not server-side build-out.

---

## 1. Cross-App Comparison: What Each App Actually Has

The two apps are **not** parity twins of each other today. They diverged: the PWA leads on manager back-office breadth (Excel tooling scaffolding, broadcast messaging, wage-rate CRUD UI, audit log UI, danger-zone data tools), while the iOS app leads on native depth (GPS-verified clock-in/out, full AU payroll/payslip generation, Tasks + Daily Jobs with photo evidence, biometric auth, local shift-reminder notifications). Android needs the **union** of both, reconciled where they conflict.

### 1.1 Present in both (build to the more complete/newer spec)

| Feature | PWA | iOS | Android should follow |
|---|---|---|---|
| Email/password auth, manager-provisioned staff accounts, no public signup | ✅ | ✅ | Either (identical) |
| Forced first-login password change | ✅ | ✅ | Either (identical) |
| Roster CRUD, draft→published lifecycle, week/day views | ✅ (+ staff view) | ✅ (+ adaptive grid) | Union: all 3 manager views + iOS's width-driven adaptive layout |
| Drag-and-drop draft shifts (desktop/tablet) | ✅ | ✅ | Either — same rules (draft-only, overlap guard) |
| Publish & Lock Availability | ✅ | ✅ | Either (identical) |
| Copy Last Week | ✅ | ✅ | Either (identical) |
| Weekly availability editor (−2…+12 weeks), server-time-enforced lock | ✅ | ✅ | Either (identical — **must** go through the Worker, never a direct Firestore write) |
| Timesheet submit / resubmit / approve / reject / bulk-approve | ✅ | ✅ (+ manager correction, verified-attendance cross-check) | iOS's superset |
| Absence report / undo / manager confirm | ✅ | ✅ | Either (identical) |
| Notifications: push via FCM, event registry, deep-link routing | ✅ | ✅ | Union of both event sets |
| Pre-shift / post-shift reminder scheduling | ✅ (server cron, all platforms) | ✅ (**also** local `UNUserNotificationCenter` scheduling as a same-device backup) | Both — server cron already fires to Android once FCM token registers; **additionally** replicate iOS's local-scheduling pattern via `WorkManager`/`AlarmManager` so reminders still fire if push is delayed/dropped |
| TFN capture, masked display, AU checksum validation | ✅ (capture only) | ✅ (capture + payslip use) | iOS's superset |
| ATO-safe account deletion (30-day grace, manager approve/decline/cancel) | ✅ (staff+manager UI) | ✅ (staff+manager UI) | Either (identical, Worker-driven) |
| Dark mode, offline indicator, Firestore offline persistence | ✅ | ✅ | Either (identical) |
| Terms of Service / Privacy Policy in-app | Web-only (`/terms`, `/privacy`) | ✅ native views | iOS's approach (native, plus keep the web URLs as fallback/legal source of truth) |

### 1.2 PWA-only (Android should build — these have no iOS equivalent)

- Manager broadcast **Messages** (Task-type, 500-char, expiry presets, sent-history with read receipts) — inbox exists on iOS (bell badge counts "unread messages") but the manager **compose/sent-history** screens were not found in the iOS inventory; verify during Phase 8/9 discovery-refresh whether iOS manager Messages screens exist under a folder not yet grepped, otherwise treat PWA's `MessagesPage` as the spec.
- **Wage Page** CRUD UI (Awards + Classification Levels/Pay Items tabs, per-staff wage-assignment modal, search/filter, typed-confirmation deletes) — as of commit `703a612` this now runs on the same `kind`-discriminated model as iOS (old flat-rate docs demoted to a read-only legacy recovery section); see §1.4 item 1 for the resolution and the casual-loading correction it surfaced.
- **Reports** page (Weekly/Monthly/Yearly, CSV/Excel/PDF export, bar chart)
- **Tenure Summary** analytics (service length, milestones, 12-month chart) — iOS has an equivalent (`ManagerTenureView`), build to iOS's implementation since it's newer and already unit-tested (`TenureMetricsTests`)
- Manager **Settings** page: audit log viewer, Danger Zone / Clear Data flow (production-gated, typed confirmation, password re-entry)
- Master Sheets (template download + arbitrary file storage) — low value, evaluate necessity in Phase 9
- Device auth via WebAuthn platform authenticator — **not applicable to native Android**; use `BiometricPrompt` instead (see §1.3)
- Google Places / OpenStreetMap address autocomplete with automatic fallback — iOS uses MapKit only; Android should use Google Places (already the native ecosystem fit) with the same automatic-fallback philosophy if a free-tier key isn't configured

### 1.3 iOS-only (Android should build — these are the "native app" value proposition)

- **GPS-verified clock-in/out** with geofencing (`shift_attendance` collection, server-timestamped, device-clock-skew detection, enforced-vs-lenient geofence policy) — this is the single highest-value native feature; the PWA cannot do this at all (browsers can't reliably background-verify location + trusted server time the way a native app can)
- **Full AU payroll**: wage awards / classification levels / earnings lines / staff wage profiles, draft payslip auto-generation, manager payslip editor, staff payslip viewer, in-app A4 PDF renderer
- **Tasks** (photo-proof or tick-complete, camera-only capture, redo workflow, photo lifecycle/retention sweeps)
- **Daily Jobs** (separate from Tasks — permanent template library, per-shift assignment, home-bell completion UX)
- **Biometric app-lock** (`BiometricPrompt` equivalent) with background re-lock threshold
- Local shift-reminder notification scheduling (survives app kill without relying on push)
- Calendar write integration with `.ics` fallback share
- Server-clock-skew measurement service (`ServerClock.swift` pattern) for clock-in integrity
- Semantic haptic vocabulary (~20 named events) — a deliberate "premium feel" signal to replicate, not skip
- In-app version history / changelog screen
- Crash reporting (Crashlytics — already available for Android via the same Firebase project)

**Explicitly do not build for Android v1** (present nowhere, or present but dead/unused in both source apps): Passkeys (iOS has the plumbing but zero UI entry point), Widgets, Siri/App Intents, BGTaskScheduler-style background refresh, bulk Excel roster import (parser exists in the PWA but has no UI caller anywhere — treat as unimplemented), formal PTO/leave-accrual (doesn't exist in either app — absence reporting is the only leave-adjacent feature), multi-tenant/Super Admin portal (draft PRD only, explicitly deferred).

### 1.4 Open Product Decisions (resolve before the phases that touch them — do not let engineering silently pick)

> **Update 2026-07-21**: Items 1 and 6 below were re-verified against a fresh, independent line-by-line read of both live codebases (not just this document) and are now resolved — see the correction notes under each. Item 2 was re-verified and found to be *worse* than originally described. All other items unchanged.

1. ~~**Wage model conflict** (blocks Phase 10)~~ — **RESOLVED, no longer a conflict.** As of commit `703a612` ("PWA: match iOS wage model, and remediate notification audit findings"), the PWA has fully migrated its `wages` collection to the same `kind`-discriminated model (`award` / `earningsLine` / `staffProfile`) iOS already used — the old flat-rate `WageRate` shape is now explicitly commented `LEGACY — superseded` in `src/types/index.ts`, is read/delete-only in the manager UI (`WagePage.tsx`'s collapsed "Legacy wage rates (read-only recovery view)" section), and `firestore.rules` now rejects any `wages` create/update that lacks a valid `kind`. Android's Phase 10 (and Phase 7's rate-resolution port) should target the unified `kind`-discriminated model directly — there is nothing left to reconcile.
   **However, this surfaced a real correction to this plan**: the PWA's rewritten rate-resolution code (`resolveShiftLoadedRate` in `src/lib/utils.ts`) carries an explicit comment that it now "matches the native iOS app's model exactly — no automatic casual-loading multiplier anywhere in this model." **The automatic 1.25× casual-loading multiplier referenced elsewhere in this plan (§3 checklist, Phase 7 scope) no longer exists in either app's live rate-resolution path** and must not be built for Android. **Confirmed intentional (product owner, 2026-07-21)**: casual staff are paid a loaded rate only if one is explicitly set on their assigned classification/earnings-line — Android must port the exact 4-step precedence chain (`hourlyRateOverride` → classification-level `EarningsLine` → legacy `AwardClassification` → generic assigned `EarningsLine`) with no casual-flag multiplier applied at any step, and must resolve to `nil`/failure rather than a silent $0 when nothing matches.
   One live PWA-only bug surfaced by this migration, **not to be ported to Android**: `src/components/staff/ViewStaffModal.tsx` still displays a "Loaded Rate" computed as `hourlyRate × 1.25` for casual staff — a stale display disconnected from the new wage-profile system, inconsistent with what payroll/forecast will actually charge. Treat as a PWA bug ticket, not an Android spec.
2. **"Never Expire" message bug** (Phase 8) — **confirmed and worse than originally described.** PWA sets `expiresAt` to +1 year on every message regardless of the expiry option chosen; no code path ever writes a `'9999-'`-prefixed value. The edit-affordance UI's `expiresAt?.startsWith('9999-')` check (`MessagesPage.tsx`, 3 call sites) can therefore never be true for *any* message ever sent — **the entire "edit a sent broadcast" feature is unreachable dead code today**, not an edge case limited to never-expiring messages. Separately, iOS has no sentinel concept at all: its `Message.expiresAt` is a plain optional ISO string, and `nil` simply means "always active" — a different mechanism to PWA's "+1 year" hack that happens to produce a similar practical effect. **Resolved (product owner, 2026-07-21): Android adopts iOS's nil-is-forever model** — `expiresAt` is optional/absent for a never-expiring message, "active" is simply "no expiry set or expiry in the future," no sentinel string anywhere. Do not port the `'9999-'` check or the +1-year-cap workaround.
3. **Excel import scope**: fully implemented parser (`parseRosterExcel`) with zero UI callers in the PWA (re-confirmed by direct grep — only `downloadRosterTemplate` is called, from `MasterSheetsModal.tsx`). Recommend backlog (Phase 20), not v1.
4. **Analytics parity** — **Resolved (product owner, 2026-07-21): Android follows iOS's precedent.** Crashlytics + Performance Monitoring only; no `firebase-analytics`/GA4 dependency.
5. **FCM token platform string**: the deployed Firestore rules already whitelist `'android-native'` as a valid `platform` value in `notificationTokens` (re-confirmed directly at `firestore.rules:98`) — **no backend rule change needed** to onboard Android push, which meaningfully de-risks Phase 6.
6. ~~**Manager Messages on iOS**~~ — **RESOLVED: confirmed structurally absent, not just unfound.** An exhaustive grep of the entire iOS source tree found exactly one Firestore write path touching `messages` (mark-as-read); there is no `.setData`/`addDocument`/compose call anywhere, no `Features/Manager/Messages/` directory, and no such tab in `ManagerNavigation.swift`. Manager Messages must be built directly from the PWA's `MessagesPage` as the spec (with the correction in item 2 above) — no further iOS discovery-refresh is needed.

---

## 2. Recommended Android Architecture

Chosen to mirror the proven patterns in both existing apps (server-authoritative security rules, repository-as-single-source-of-truth, O(1) index maintenance, two-tier best-effort-vs-must-succeed error handling, `Australia/Adelaide`-pinned calendar math) while using idiomatic modern Android tooling.

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Standard; enables Coroutines/Flow, which map cleanly onto Firestore's listener model the way `@Observable`/Combine do on iOS |
| UI | Jetpack Compose, Material 3 (Dynamic Color) | Matches the "modern navigation, motion, premium micro-interactions" brief; Material 3 gives dynamic color + Material You theming as the Android analogue of Liquid Glass, without literally porting an iOS-specific visual language |
| Architecture pattern | MVVM with unidirectional data flow (`StateFlow`/`UiState`), one shared repository layer analogous to `RosterRepository` | Both source apps converge on "one repository, thin view layer" — proven for this domain; avoids one-ViewModel-per-screen boilerplate for what's fundamentally shared, listener-driven state |
| DI | Hilt | Standard Android choice; both apps use lightweight DI (SwiftUI `.environment()` / Zustand stores) — Hilt is the direct equivalent for constructor-injecting repositories/services into ViewModels |
| Navigation | Navigation Compose (type-safe routes) + `SharedTransitionLayout` for shared-element animations | Delivers the "beautiful transitions / shared element animations" requirement natively |
| Async | Kotlin Coroutines + Flow | Firestore Android SDK exposes `Flow` extensions natively; `callbackFlow` for anything raw-listener-based |
| Backend SDKs | Firebase Android SDK: Auth, Firestore (`PersistentCacheSettings` with **unlimited** size, matching iOS), Cloud Messaging, Storage, Crashlytics | Direct parity with iOS's `FirebaseBootstrap.swift` configuration |
| Worker API client | Retrofit + OkHttp (or Ktor client) | Thin REST layer mirroring `WorkerAPIClient.swift` — bearer Firebase ID token, calls to `/api/*` on `sura-roster.com` |
| Local persistence | Jetpack DataStore (Preferences) for `UserDefaults`-equivalent state (last-login date, download-tracking, throttle timestamps); Firestore's own on-disk cache handles the rest — **no bespoke Room database** needed for synced entities, matching both source apps' "rely on the SDK's built-in offline queue" philosophy |
| Local clock-session state | DataStore or a small in-memory/`SharedPreferences`-backed store, keyed per-uid | Mirrors `ClockSession`'s `UserDefaults`-only, deliberately-not-Firestore design (rules block early timesheet writes) |
| Background scheduling | `WorkManager` for anything deferrable/retryable; exact-time local reminders via `AlarmManager` (`setExactAndAllowWhileIdle`) + `NotificationManager` with typed Notification Channels | Android equivalent of `UNUserNotificationCenter` local scheduling; must survive process death like iOS's does |
| Camera | CameraX | Task-photo capture, camera-only (no gallery) to match iOS's anti-fraud policy |
| Location/Geofencing | `FusedLocationProviderClient` + Geofencing API, when-in-use only, single-fix pattern (not continuous tracking) | Direct parity with `LocationService.swift` |
| Maps/address autocomplete | Google Maps SDK + Places API (Android-native fit; PWA already uses Google Places with an OSM fallback pattern to reuse) | For manager geofence editor + staff address autocomplete |
| Biometric auth | `androidx.biometric.BiometricPrompt` + Android Keystore-backed encrypted credential storage | Android equivalent of the two-layer iOS pattern (app-lock gate + quick-login credential store), same 2-minute background re-lock threshold |
| PDF generation | Android `PdfDocument`/`Canvas` (or a maintained pure-Kotlin PDF library if drawing complexity warrants it) | Must reproduce iOS's exact monochrome A4 payslip layout so exports are visually consistent across platforms |
| Calendar | `CalendarContract` ContentProvider, with `.ics` file share fallback | Parity with `CalendarService.swift`'s graceful-degradation pattern |
| Crash/perf monitoring | Firebase Crashlytics + Firebase Performance Monitoring | Same Firebase project as both existing clients |
| Testing | JUnit5/Kotlin test for business-rule unit tests (must port every `RosterraTests`/`*.test.ts` case bit-for-bit), Turbine for `Flow` testing, Compose UI testing, `@firebase/rules-unit-testing`-equivalent (Firestore emulator) for any rules-adjacent verification | Both source apps treat business-rule tests as the executable spec — Android must match their exact numeric outputs, not just "look right" |
| Build/CI | Gradle (version catalogs), GitHub Actions (lint, unit tests, instrumented tests, assemble) mirroring the existing `.github/workflows/` pattern in the other two repos | Consistency with the monorepo's existing CI conventions |
| Min/target SDK | Min API 26 (Android 8.0) as a starting recommendation — revisit against actual staff-device data in Phase 2; target latest stable API | Balances "modern Android standards" against real-world device coverage for hourly/shift-based staff, who skew toward older/budget devices more than typical consumer apps |

---

## 3. Consolidated Feature Checklist (Union, De-duplicated)

This is the authoritative parity checklist for the roadmap below — the merge of PWA §15 and iOS §17 checklists from the companion inventories, with conflicts resolved per §1.4 above. Detailed acceptance criteria for each line live in the phase that builds it.

**Auth & Session** — email/password sign-in with friendly error mapping · manager-only account provisioning (no public signup) · forced first-login password change · manager: add/reset-password/change-email/lock/deactivate staff · voluntary change-password · biometric app-lock (2-min re-lock) · session persistence · forced sign-out on lock/deactivation · ATO-safe deletion lifecycle · TFN capture/validation/masking

**Roster & Scheduling** — full shift CRUD, draft/published lifecycle · week/day/staff views + adaptive tablet grid · drag-move/copy with overlap guard · Copy Last Week · Publish (+ optional lock) · availability-conflict indicator · labour-cost/super forecast (rate resolved from the staff's assigned classification/earnings-line — **no automatic casual-loading multiplier**, see §1.4 item 1) · missing-wage-rate warnings

**Timesheets & Attendance** — submit/resubmit hours · report/undo absence · approve/reject/bulk-approve/manual-correct · missing-submissions view · GPS-verified clock-in/out with geofencing · device/server clock-skew detection · live clock session timer with break tracking

**Availability** — weekly grid editor (−2…+12 weeks) · server-time-enforced lock · manager publish-lock · recurring/default propagation · manager read-only overview matrix

**Staff Management** — searchable directory · full profile CRUD · wage/classification assignment · profile-completion gate · "request profile update"/"request email change" flows

**Payroll (AU)** — wage awards/classifications/earnings lines · staff wage profiles · draft payslip auto-generation · manager payslip editor · approve/submit/archive lifecycle · staff payslip viewer · in-app PDF export/share

**Tasks & Daily Jobs** — task CRUD with photo-proof/tick-complete · camera-only capture, 4-photo cap, compression · redo workflow · photo retention sweeps · daily job template library · per-shift job assignment · home-bell completion UX

**Messaging** — manager compose broadcast · staff inbox with unread badge · sent-history with read receipts

**Reporting** — dashboard KPIs · weekly/monthly/yearly reports · CSV/Excel/PDF export · tenure & hours analytics

**Notifications** — FCM push (event registry shared with Worker) · locally-scheduled shift reminders surviving app kill · deep-link routing · notification-category preferences · in-app bell/inbox

**Native Integration** — calendar write + `.ics` fallback · camera · address autocomplete + interactive geofence map editor · haptic feedback vocabulary · crash reporting

**Offline & Sync** — Firestore offline persistence (unlimited cache) · cache-first payslip loading · pull-to-refresh · visible non-crashing error states on failed writes

**Admin/Legal** — in-app Terms/Privacy · version history/changelog · company details editor · support-contact link · Danger Zone data clearing (production-gated) · audit log viewer

**Design/Premium Feel** — full dynamic light/dark theming · Material You dynamic color · skeleton/empty/error/success states · shared-element transitions · Dynamic-Type-equivalent (font-scale respecting) text · Reduce-Motion equivalent respect · tablet/foldable-adaptive layouts · edge-to-edge + predictive back

---

## 4. Phased Implementation Roadmap

Complexity scale is relative effort for a small (2–3 engineer) Android team: **S** = 2–5 days, **M** = 1–2 weeks, **L** = 2–4 weeks, **XL** = 4+ weeks. Phases are sequenced so each is buildable/testable independently, backend-and-data-first, then business logic, then UI, then hardening — per the requested structure.

### Phase 1 — Discovery & Repository Analysis
**Status: complete, this deliverable.**
- **Objectives**: Produce an exhaustive, source-verified feature/architecture inventory of both existing apps and a reconciled parity plan before any Android code is written.
- **Scope**: Full read of `Roster PWA` and `Roster IOS NATIVE APP` — every screen, store/service, Firestore collection, security rule, Worker endpoint, cron job, and business-rule test file.
- **Dependencies**: None.
- **Deliverables**: `PWA-FEATURE-INVENTORY.md`, `IOS-FEATURE-INVENTORY.md`, this `ANDROID-BUILD-PLAN.md`.
- **Complexity**: L (delivered).
- **Risks**: Source apps continue to evolve after this snapshot — re-verify any inventory claim against current source before relying on it in a later phase, per the "before recommending from memory" discipline; the two open PWA-side findings (Excel import dead code, "Never Expire" bug) should be re-checked if this plan is picked up more than a few weeks after 2026-07-17.
- **Completion criteria**: ✅ All three documents exist, reviewed, and cross-referenced; Open Product Decisions (§1.4) captured for follow-up.

### Phase 2 — Android Architecture & Project Foundation
- **Objectives**: Stand up a buildable, empty-but-correctly-structured Android project matching the architecture in §2.
- **Scope**: Gradle setup (version catalogs, module structure — recommend a single `:app` module to start, splitting into `:core`, `:data`, `:feature-*` only once real screen count justifies it), Hilt wiring, Compose + Material 3 theme skeleton (brand colors ported from iOS's `Theme.swift` hex values — see §14 of the iOS inventory — as the starting palette, adapted for Material You dynamic color), base Navigation graph with placeholder screens for every tab, CI pipeline (lint, unit test, assemble on PR), lint/detekt/ktlint config, min/target SDK decision finalized against real device-share data.
- **Dependencies**: Phase 1.
- **Deliverables**: Empty app that builds, runs, navigates between placeholder tabs, passes CI.
- **Complexity**: M.
- **Risks**: Under- or over-engineering the module structure this early; keep it single-module until Phase 8–9 forces a split.
- **Completion criteria**: CI green on a trivial PR; app installs on a physical device and a tablet emulator; theme correctly switches light/dark and responds to Material You wallpaper color on a device that supports it.

### Phase 3 — Backend Integration
- **Objectives**: Wire the app to the real shared backend — Firebase project `roster-8a270` and the Cloudflare Worker at `sura-roster.com` — with no mock/stub data layer.
- **Scope**: Firebase Android SDK setup (`google-services.json`, Auth/Firestore/Messaging/Storage/Crashlytics dependencies), Firestore offline persistence configured with unlimited cache (matching iOS), Retrofit/Ktor client for `/api/*` Worker endpoints with bearer-Firebase-ID-token auth interceptor, environment config for dev vs prod (mirroring `wrangler dev`/local proxy pattern used by the PWA), resolve the Analytics decision from §1.4 item 4.
- **Dependencies**: Phase 2.
- **Deliverables**: A `FirebaseBootstrap`-equivalent init module; a `WorkerApiClient` with typed request/response models for every endpoint referenced in both inventories (`/api/create-auth-user`, `/api/reset-staff-password`, `/api/change-staff-email`, `/api/complete-password-change`, `/api/staff/availability`, `/api/send-notification`, `/api/account-deletion/*`, `/api/delete-staff-users`).
- **Complexity**: M.
- **Risks**: Firestore composite-index assumptions — any new Android query must be checked against the deployed `firestore.indexes.json` before assuming a compound `.whereEqualTo().whereEqualTo()` query will succeed (a documented `FAILED_PRECONDITION` bug hit both other clients — see iOS inventory §16); do not add Android as a fourth platform-specific hack around this, fix or extend the index file with the team that owns it if a genuinely new query shape is needed.
- **Completion criteria**: App can read a real (test-account) Firestore document and successfully call at least one authenticated Worker endpoint end-to-end against the dev/staging environment.

### Phase 4 — Authentication
- **Objectives**: Full auth/session parity per §1.1/§1.2/§1.3's auth rows.
- **Scope**: Login screen (error-code mapping identical to both source apps: wrong password, too-many-requests, disabled account), manager-profile-required gate (Firestore doc check beyond Auth success), forced password-change flow with live requirement checklist (8+ chars/uppercase/number required, symbol recommended-only — port the exact soft/hard rule split), forgot-password (Firebase-hosted reset page), session persistence, mid-session forced sign-out on `locked`/`inactive`, BiometricPrompt app-lock gate with 2-minute background re-lock (`AppConfig`-equivalent constant), manager-facing add-staff/reset-password/change-email/lock-unlock-deactivate flows, ATO-safe account deletion (staff request → manager approve/decline/cancel → 30-day purge) end to end against the real Worker endpoints.
- **Dependencies**: Phase 3.
- **Deliverables**: Working login → role-branched root navigation; all manager staff-account-lifecycle actions; biometric lock.
- **Complexity**: L.
- **Risks**: Reproducing the exact rate-limit/cooldown numbers (15-min per manager+staff pair, 5/hour per manager for password reset; 5-min cooldown for email change) requires the Worker's rate-limit responses to be handled gracefully, not just the happy path; TFN field must never appear in any Android log/crash report (Crashlytics scrubbing) since it's sensitive PII.
- **Completion criteria**: A real manager can create a staff account, the staff member can log in and complete forced password change, biometric lock engages/disengages correctly across app backgrounding, and the full account-deletion lifecycle (request→approve→cancel and request→approve→simulated-purge on a test account) works against staging.

### Phase 5 — Database Layer
- **Objectives**: Build the Android equivalent of `RosterRepository`/`useDataStore` — one repository layer that owns every Firestore listener, exposes `Flow`-based state, and maintains O(1) lookup indices.
- **Scope**: Kotlin data classes for every model in §3 of the iOS inventory (`User`, `Shift`, `Timesheet`, `Availability`, `Message`, `RosterTask`/`TaskCompletion`, `DailyJobTemplate`/`DailyJobAssignment`, `ShiftAttendance`, `TFN`, `Payslip`/wage models, `AccountDeletionState`, `RosterLocation`, `AppSettings`) with tolerant field parsing matching `FirestoreValue.swift`'s `FS` helper (several fields are inconsistently `Timestamp` vs ISO string across platforms — Android must handle both, not assume one); role-scoped listener windows exactly matching iOS's proven bounds (shifts: −28/+56 days; manager timesheets: 90 days back; staff timesheets: 5-year client-filtered cutoff; payslips: 26-week server query for managers, cache-first month-scoped for staff; messages: 30 days; tasks: unbounded `active==true`); `didSet`-equivalent index maintenance (`shiftsById`, `timesheetsByShiftId`, `usersById`, `attendanceByShiftId` as `StateFlow<Map<...>>` derived from the raw listener flows).
- **Dependencies**: Phase 3.
- **Deliverables**: A single `RosterRepository` (or small set of role-scoped repositories) that ViewModels depend on for all data access — no ViewModel talks to Firestore directly.
- **Complexity**: L.
- **Risks**: Getting the listener windows wrong either over-fetches (cost/perf) or silently drops data older than the window; copy the exact day/week counts from the inventories rather than re-deriving them. `pendingFirstSnapshot`-style gating (don't show an empty roster before every relevant listener's first snapshot has arrived) is easy to skip and produces a "flash of empty state" regression both other apps already fixed once.
- **Completion criteria**: Repository unit tests confirm correct window boundaries and index correctness against a Firestore emulator seeded with fixture data; no screen renders a false "empty" state during initial load.

### Phase 6 — Notifications
- **Objectives**: Full notification parity — FCM push receive/send-trigger and locally-scheduled reminders that survive app kill.
- **Scope**: FCM token lifecycle (register on login, `users/{uid}/notificationTokens/{urlEncodedToken}` doc with the **exact** schema — `token, platform: "android-native", userAgent, enabled, createdAt, updatedAt` — the platform string is already whitelisted server-side per §1.4 item 5, but the field schema must match exactly or writes will be rejected by rules); `POST /api/send-notification` client triggers for every event Android can fire (timesheet-submitted, timesheet-absent, availability-updated, message-task, etc. per the event tables in both inventories); Notification Channels (Android's typed-category equivalent, map roughly to iOS's local-reminder slot taxonomy: shift-upcoming, timesheet-action, tasks, general); deep-link routing from a tapped notification to the correct screen/sheet, matching `AppRouter.handleNotificationUserInfo`'s event→destination mapping; **local** reminder scheduling via `AlarmManager.setExactAndAllowWhileIdle` + `WorkManager` fallback, replicating the exact iOS slot table verbatim (24h/6h/1h/30m/5m before start; 10-min-after-start "forgot to clock in"; 10-min-after-end "forgot to clock out", armed only while clocked in; 15-min-after-end "submit your hours" if unfiled), idempotent re-sync on every roster/timesheet change, capped to a safe number of concurrent scheduled alarms (Android doesn't have iOS's 64-pending-notification hard limit, but cap anyway for battery/Doze sanity — recommend mirroring iOS's 8-shift cap as a starting point).
- **Dependencies**: Phase 4, Phase 5.
- **Deliverables**: Working push receive (foreground + background + killed-app), working local reminder scheduling, deep-link navigation.
- **Complexity**: L.
- **Risks**: Doze mode / battery optimization can delay or drop `AlarmManager` alarms on some OEM skins — test explicitly on a battery-aggressive device (e.g. a Samsung or Xiaomi unit) in addition to a stock/Pixel emulator; a data-only FCM payload with no `notification` block needs explicit handling in `onMessageReceived` to show a heads-up notification while foregrounded (Android's version of the iOS `apns.payload.aps.alert` bug already fixed once on that platform — don't reintroduce the equivalent gap on Android).
- **Completion criteria**: A test push notification delivers and deep-links correctly in all three app states (foreground/background/killed); a scheduled local shift reminder fires at the correct wall-clock instant across a timezone/DST boundary test.

### Phase 7 — Core Business Logic
- **Objectives**: Port every pure business-rule function from both source apps as standalone, exhaustively-unit-tested Kotlin code, before any UI consumes it — this is the "must reproduce bit-for-bit" layer.
- **Scope**: `RosterCalendar`-equivalent (Java `time` `ZonedDateTime` pinned to IANA `Australia/Adelaide`, Monday-first weeks, wall-clock-anchored DST correctness — port `testShiftInstantsAcrossDSTStart` verbatim as a test case); scheduled/worked-hours calculation (overnight-shift handling, 2-decimal rounding, break clamping 0–90 step 5); `resolveShiftLoadedRate` (rate resolved via the `kind`-discriminated award/classification/earnings-line/staff-profile chain — confirmed **no automatic casual-loading multiplier** in either app's current source, see §1.4 item 1; port the precedence chain exactly: override → classification-level earnings-line → legacy award classification → generic assigned earnings-line → nil, never silently $0); `computeRosterForecast` (super calculation respecting an explicit staff-level 0% override as deliberate, not "unset"); `isShiftSubmittable`/`canReportAbsence` gating; TFN checksum validation (weights `[1,4,3,7,5,8,6,9,10]`, reject all-same-digit); `PayrollCalculator` money math (`round2` half-up, super base excluding overtime and exempt earnings lines, weekend/PH default multipliers 1.5×/2.25×) — **pending the wage-model decision in §1.4 item 1**; `ClockSession` timer math (paid time starts at rostered start not actual clock-in, break-overlap partial deduction); `RosterTask.isActive`/assignment-set logic; missing-submission detection.
- **Dependencies**: Phase 5 (for the shapes these functions operate on), but this phase's actual logic has no Firestore/UI dependency and can be built/tested in parallel with Phases 4–6.
- **Deliverables**: A pure-Kotlin business-rules module with unit test coverage ported 1:1 from `RosterraTests/*.swift` and the PWA's root `*.test.ts` files — every named test case in both inventories should have an Android counterpart with the same assertion.
- **Complexity**: XL (this is the highest-precision phase — money and time math errors are the most expensive class of bug in this domain).
- **Risks**: Silent rounding/precision drift (Swift `.rounded()` half-up vs a naive Kotlin `Math.round` can diverge on `.5` boundaries depending on sign — verify explicitly); timezone bugs are easy to introduce by using device-local time anywhere instead of the pinned Adelaide zone.
- **Completion criteria**: 100% of the ported test cases pass with identical expected values to their Swift/TypeScript originals; a code reviewer with access to both source repos signs off that no formula was "close enough" rather than exact.

### Phase 8 — Staff Module
- **Objectives**: All staff-facing screens and flows, wired to the real repository/business-logic layers.
- **Scope**: Home (greeting, today's shift + inline clock-in card, approved-hours stat grid, upcoming shifts, notification bell), Roster (week strip, shift list, swipe/tap actions for submit/report-absence/undo, deep-link handling), Submit Hours + Report Absence sheets, History (grouped by month, filterable/searchable), Availability editor (−2…+12 weeks, locked-week banners, recurring/default propagation), Tasks tab (camera-only photo proof, redo handling), Daily Jobs (surfaced via the home notification panel, not a separate tab — matching iOS), Account (profile, biometric toggle, notification settings, payslip viewer link, account deletion request, Terms/Privacy, version history, sign out), Payslips viewer (cache-first month loading, PDF view/share).
- **Dependencies**: Phases 4–7.
- **Deliverables**: Fully functional staff role, feature-complete against §3's checklist.
- **Complexity**: XL.
- **Risks**: The clock-in flow's geofence policy differs between start (enforced-hard-block or lenient-250m-warn depending on the workplace's `geofenceEnforced` flag) and end (never restricted) — an easy detail to flatten by accident into one uniform rule; the "uncompleted tasks" warning before hours submission is a cross-feature dependency (Tasks → Timesheets) that's easy to build as two islands that don't talk to each other.
- **Completion criteria**: A staff test account can complete a full daily cycle — clock in (inside and outside geofence, both policies), complete a task with photo, clock out, submit hours, see it reviewed — with all data correctly landing in Firestore in the shapes Phase 5 expects.

### Phase 9 — Manager Module
- **Objectives**: All manager-facing screens and flows.
- **Scope**: Dashboard (KPIs, quick actions, today's roster, task logs), Roster (adaptive week-grid/agenda, drag-move/copy, publish + lock, copy-last-week, bulk delete), Timesheets (status tabs, approve/reject/bulk-approve/manual-correct, Verified Attendance card with geofence/skew display, Payroll Gaps sheet), Staff directory (CRUD, wage assignment, deletion controls, per-field edit pattern), Availability overview matrix, Reports (weekly/monthly/yearly, exports), Tenure & Hours analytics, Tasks (create/edit, review, redo requests, photo lifecycle management), Daily Jobs (template library, per-shift assignment sheet), Messages (compose/sent-history — pending §1.4 item 6 verification), Settings (company details with geofence map editor, audit log, danger-zone data clearing, password/notification settings).
- **Dependencies**: Phases 4–7 (and benefits from Phase 8 being done first for shared components like `ShiftCard`/sheets, but can proceed in parallel with a small amount of throwaway duplication if the team is split).
- **Deliverables**: Fully functional manager role, feature-complete against §3's checklist.
- **Complexity**: XL (largest phase — the manager surface is the broadest in both source apps, e.g. `ManagerStaffView.swift`/`ManagerWageView.swift` are the two largest feature files in the iOS codebase).
- **Risks**: The adaptive week-grid-vs-agenda layout switch (iOS: 720pt width threshold) needs an Android equivalent using `WindowSizeClass`, not a hardcoded dp value copied blindly, since Android's window-size buckets don't map 1:1 to iOS's raw point width; the drag-and-drop interaction needs a touch-and-tablet-appropriate Compose implementation (long-press-to-drag is the idiomatic Android pattern, not a direct port of iOS's drag gesture).
- **Completion criteria**: A manager test account can run a full weekly cycle — build a roster, publish + lock, review and approve/reject timesheets including a geofence-flagged one, assign tasks and jobs, generate a report export — entirely from the Android app.

### Phase 10 — Payroll
- **Objectives**: Full AU payroll — the highest-precision, highest-liability feature in the app.
- **Scope**: Wage award / classification-level / earnings-line management, staff wage profile assignment, draft payslip auto-generation (idempotent, "first manager session on/after Monday" trigger pattern or an explicit manual-generate button), payslip editor (hour buckets, allowances, PAYG/deductions/salary sacrifice, super), draft→under_review→approved→submitted→archived lifecycle with staff-visibility gating enforced by Firestore rules (not just client filtering), snapshot semantics (issued payslips never retroactively change), audit trail per payslip, in-app A4 PDF renderer matching iOS's monochrome layout so exports look identical across platforms, staff payslip viewer with cache-first loading.
- **Dependencies**: Phase 7 (money math), Phase 9 (staff/wage management screens). The §1.4 item 1 wage-model conflict is now resolved (both apps share the `kind`-discriminated model) — the remaining pre-Phase-10 confirmation needed from the product owner is narrower: that dropping the automatic casual-loading multiplier (per §1.4 item 1's update) is intentional, not a regression.
- **Deliverables**: Full payroll module, byte-for-byte-matching money math against the iOS test suite.
- **Complexity**: XL.
- **Risks**: This is the phase most likely to cause real financial/compliance harm if rushed — an incorrect super calculation or a silently-wrong wage-rate resolution (the iOS inventory notes this was a real fixed production bug — "never silently pays $0") is not a cosmetic bug. Recommend a dedicated QA pass with a second engineer independently re-deriving expected totals for a representative set of pay periods before this phase is considered done, not just automated test coverage.
- **Completion criteria**: Every `PayrollCalculatorTests`/`PayrollTests` case ported and passing; a manager can generate, edit, approve, and submit a payslip for a test pay period and the resulting PDF is pixel-comparable in structure to the iOS-generated one for the same data.

### Phase 11 — Offline Synchronisation
- **Objectives**: Verify and harden offline-first behavior across the whole app, not just individual screens.
- **Scope**: Airplane-mode test pass across every write path (visible, non-crashing error on failed writes while offline, matching both source apps' explicit smoke-test expectation); reconnect-and-sync verification; offline indicator UI (banner/pill, role-appropriate copy); confirm no bespoke outbox/retry system is needed beyond the Firestore SDK's built-in offline write queue (matching both existing apps' design — do not build a custom sync engine unless this phase reveals a genuine gap); pull-to-refresh as a fallback on top of live listeners, scoped correctly per role (a documented past bug on iOS was a manager refresh accidentally running staff-shaped queries).
- **Dependencies**: Phases 5, 8, 9.
- **Deliverables**: A written offline test matrix (mirroring `docs/smoke-test.md`'s style from the iOS repo) covering every major write flow in airplane mode.
- **Complexity**: M.
- **Risks**: Clock-in/out and payslip generation have the most offline-sensitivity (server-timestamp requirements, cache-first month logic) — test these explicitly, not just roster CRUD.
- **Completion criteria**: Every write flow in the test matrix either succeeds and syncs on reconnect, or fails visibly with a clear message — never a silent loss or a crash.

### Phase 12 — Native Android UI Foundation
- **Objectives**: Build the shared design-system layer once, before the screen-by-screen phase consumes it — mirrors both source apps' component-library approach (`DesignSystem/Components/*` on iOS, shared `src/components/*` on the PWA).
- **Scope**: Material 3 theme (colors ported from iOS's exact hex values as a starting brand palette, adapted for dynamic color); typography scale respecting system font-scale (Android's Dynamic Type equivalent); reusable components — buttons, cards, banners (info/warning/error/success), empty states, skeleton/shimmer loading placeholders, status pills (7-state palette from the iOS design system), stat tiles, week selector, bottom sheets, dialogs, toasts/snackbars; navigation shell (bottom nav for phone, adaptive nav rail/permanent drawer for tablet/foldable via `WindowSizeClass`); haptic feedback wrapper mapped to the same semantic event taxonomy as iOS's `Haptics.swift` (tab change, sign-in/out, save/submit success/error, notification delivered/urgent, etc. — using `HapticFeedbackConstants`/`VibrationEffect`); motion primitives (shared-element transitions, `AnimatedContent`/`AnimatedVisibility` presets, spring specs matching the "premium" feel brief); accessibility baseline (TalkBack labels, minimum touch targets, focus order, Reduce-Motion equivalent respect via `Settings.Global.ANIMATOR_DURATION_SCALE` or the Compose accessibility APIs).
- **Dependencies**: Phase 2.
- **Deliverables**: A component library and theme that every subsequent UI phase consumes — this phase can and should run in parallel with Phases 4–7, not after them.
- **Complexity**: L.
- **Risks**: Over-investing in visual polish before the underlying data/business-logic layers are proven leads to expensive rework; keep this phase's components data-agnostic (props/state in, no repository calls) so they're cheap to restyle later if needed.
- **Completion criteria**: A component gallery/showcase screen (dev-only) renders every shared component in both light and dark mode, at phone and tablet width, with TalkBack enabled, with no crashes or unreadable contrast.

### Phase 13 — Screen-by-Screen UI Implementation
- **Objectives**: Wire Phase 12's components to Phases 8/9's data and business logic into final, polished screens — effectively the "make it real" pass on top of the functional-but-unstyled work from Phases 8–9 (in practice, Phases 8/9 and 13 will interleave rather than run strictly sequentially — the separation here is conceptual: functional correctness first, polish confirmed second, per screen).
- **Scope**: Every screen listed in Phases 8 and 9, brought to final visual/interaction quality: correct empty/loading/error/success states on every list and form, pull-to-refresh everywhere a live listener backs a scrollable screen, gesture interactions (swipe actions, long-press, drag-and-drop) tuned for touch ergonomics, bottom sheets and dialogs using native Android patterns (not iOS sheet detents copied literally), tablet/foldable layout verification for every screen (not just the roster grid), responsive breakpoints tested at phone/unfolded-foldable/tablet/large-tablet widths.
- **Dependencies**: Phases 8, 9, 12.
- **Deliverables**: Every screen in the consolidated checklist (§3) fully implemented and polished.
- **Complexity**: XL.
- **Risks**: Screen count is large (16+ distinct routes across both roles per the source-app inventories); risk of inconsistent quality across screens if not tracked against a single checklist — use §3 as the literal tracking list, one checkbox per line item, not just per screen.
- **Completion criteria**: Every item in §3 has a corresponding shipped, polished screen; a design/product review pass confirms visual consistency across all screens (spacing, typography, color usage) rather than each screen having been built in isolation.

### Phase 14 — Animations & Haptics
- **Objectives**: A dedicated polish pass focused specifically on motion and haptic feedback, since these are easy to under-invest in during feature-by-feature screen work.
- **Scope**: Shared-element transitions between list and detail views (e.g. shift card → shift detail, staff card → staff profile), screen-transition choreography, micro-interactions (button press states, toggle animations, number-change transitions on stat tiles matching iOS's `.contentTransition(.numericText())`), skeleton-to-content cross-fade timing, pull-to-refresh custom indicator, haptic feedback audit against the full semantic event list from Phase 12 (confirm every event actually fires the intended haptic, not just that the API exists), predictive back gesture support (Android 14+) with correct in-progress preview animations.
- **Dependencies**: Phase 13.
- **Deliverables**: A motion/haptics audit checklist, fully passing.
- **Complexity**: M.
- **Risks**: Motion that looks good in isolation can feel sluggish in aggregate (e.g. stacking multiple 300ms transitions on a single navigation) — budget and test end-to-end navigation timing, not just per-animation.
- **Completion criteria**: A full manager-and-staff walkthrough (the same one used in Phase 8/9 completion criteria) performed with animations at normal speed feels fluid, not janky, on a mid-tier test device (not just a high-end flagship emulator).

### Phase 15 — Performance Optimisation
- **Objectives**: Ensure the app is fast on real, including lower-end, hardware — matching both source apps' documented perf-fix history (cached calendar instances, O(1) index maintenance, chunked Firestore batches, non-render-blocking asset loading).
- **Scope**: Cold-start time profiling and reduction (baseline profiles / macrobenchmark), Compose recomposition auditing (avoid unstable lambda/class parameters causing unnecessary recomposition on list-heavy screens like Roster/Staff directory), image loading/caching strategy for task and profile photos, Firestore listener count/scope audit (confirm no screen is over-subscribing beyond what Phase 5's windows define), memory profiling on long manager sessions (roster + timesheets + payslips all live-listening simultaneously), battery impact review of location/geofencing and `AlarmManager` usage, APK/AAB size audit (resource shrinking, R8/ProGuard rules verified not to break Firestore model reflection or biometric APIs).
- **Dependencies**: Phase 13 (needs real screens to profile).
- **Deliverables**: A performance baseline report (cold start, frame timing on key scroll surfaces, APK size) with before/after numbers for any optimization applied.
- **Complexity**: M.
- **Risks**: R8 obfuscation can silently break Firestore's reflective model (de)serialization if `-keep` rules aren't correctly scoped to the data-model package — test a full release-build smoke pass, not just debug builds, before considering this phase done.
- **Completion criteria**: Cold start under a defined target (recommend ≤2s to first interactive frame on a mid-tier reference device) and no dropped-frame jank on the Roster and Staff Directory list screens during a scroll benchmark; a release (R8-enabled) build passes the full manager+staff walkthrough with no functional regressions.

### Phase 16 — Security Hardening
- **Objectives**: Confirm Android relies on the same server-side security boundary as both other clients (Firestore rules + Worker-only privileged writes) and doesn't introduce a new client-side-only trust assumption.
- **Scope**: Audit every write path against `firestore.rules` to confirm the Android client cannot construct a write that would be accepted client-side but rejected server-side (i.e., no reliance on "the UI won't let you" as the actual security boundary — the rules are); Keystore-backed storage review for biometric credentials and any cached tokens (no plaintext secrets in `SharedPreferences`/DataStore, no sensitive data in logs sent to Crashlytics — explicitly scrub TFN and any auth material); network security config (certificate pinning evaluation for the Worker API — optional but worth a explicit yes/no decision, not a default); ProGuard/R8 rule review for anything that might strip a security-relevant check; root/tamper detection evaluation (optional, product decision — flag rather than silently building or skipping); dependency vulnerability scan (Gradle dependency audit / OWASP dependency-check) on the final dependency set from Phase 2 onward.
- **Dependencies**: Phases 4, 5, 10 (needs the full write-path surface to audit).
- **Deliverables**: A security review checklist/report, any findings remediated.
- **Complexity**: M.
- **Risks**: Treating client-side role checks as sufficient security instead of verifying against the deployed rules is the most likely mistake here, precisely because the UI will *appear* correct even if a rule gap exists — this is the same class of bug that caused the PWA's real production incident (`payslips` collection accidentally missing from deployed rules for five days, per the PWA inventory §3) — actively test against the emulator with a deliberately-malicious client to confirm rejection, don't just read the rules file and assume correctness.
- **Completion criteria**: A written security checklist (mirroring the rules matrix in PWA inventory §4) with every row confirmed by an actual rejected-write test against the Firestore emulator, not just code review.

### Phase 17 — Testing & QA
- **Objectives**: Comprehensive automated and manual test coverage before production readiness.
- **Scope**: Unit test coverage audit against the Phase 7 business-logic port (target: every ported test case passing, tracked against the source `RosterraTests`/`*.test.ts` file list as a checklist, not just an aggregate coverage percentage); Compose UI tests for critical flows (login, clock-in/out, submit hours, publish roster, approve timesheet, generate payslip); integration tests against the Firestore emulator for repository-layer correctness; manual QA pass across a device matrix (phone, tablet, foldable if available, at least one lower-end/older-OS device); accessibility audit with TalkBack enabled end-to-end (both source apps flagged this as "solid baseline, not exhaustively audited" — Android should not repeat that gap given it's starting fresh); beta distribution via Play Console internal/closed testing track with real staff/manager testers if available.
- **Dependencies**: All prior phases.
- **Deliverables**: Test suite, QA sign-off report, beta feedback summary.
- **Complexity**: L.
- **Risks**: QA time is often the first casualty of schedule pressure — protect this phase explicitly given the payroll/attendance domain's low tolerance for silent bugs.
- **Completion criteria**: All automated tests green in CI; manual QA checklist (built from §3) fully executed with no open Sev1/Sev2 issues; at least one real beta tester completes a full day's workflow (clock in → tasks → clock out → submit hours) without a blocking issue.

### Phase 18 — Production Readiness
- **Objectives**: Everything short of the Play Store submission itself — the operational and observability work that makes the app supportable in production.
- **Scope**: Crashlytics + Performance Monitoring dashboards configured and alerting; structured logging discipline (mirroring both apps' "must-succeed vs best-effort, never silently swallowed" pattern — `Log`/Timber wrapper with the same two-tier philosophy); remote config or feature-flag mechanism if staged rollout of individual features is desired (optional, evaluate need); versioning/release-notes process (mirroring iOS's in-app `AppRelease`/`ReleaseHistory` changelog pattern); production Firebase config finalized and separated from any dev/staging config used during Phases 2–17; final legal review of Terms/Privacy content and in-app placement; support-contact flow wired (mailto or equivalent); data-safety documentation drafted (feeds directly into Phase 19's Play Console Data Safety form — start it here so it isn't a Phase 19 fire-drill).
- **Dependencies**: Phase 17.
- **Deliverables**: Production-configured, monitored, supportable build.
- **Complexity**: M.
- **Risks**: Data-safety form accuracy is a common last-minute scramble — starting the documentation here rather than during Play Store submission avoids rushing a legally-relevant disclosure.
- **Completion criteria**: A production build variant exists, points at production Firebase, has monitoring wired and verified (a deliberately-triggered test crash appears in the Crashlytics dashboard within a few minutes), and the data-safety documentation draft is complete.

### Phase 19 — Google Play Release Preparation
- **Objectives**: Everything specific to the Play Store submission itself.
- **Scope**: Play Console app listing (title, description, screenshots across device sizes, feature graphic, icon — adaptive icon required), Data Safety form (using Phase 18's draft), content rating questionnaire, target API level compliance check against current Play requirements, App Bundle (AAB) signing setup (Play App Signing enrollment), internal → closed → open (or direct production) staged rollout plan, privacy policy URL live and accessible (reuse `sura-roster.com/privacy`), permissions justification review (location, camera, notifications — ensure each requested permission has a clear in-context rationale, since Play review scrutinizes this), pre-launch report review (Play Console's automated device-matrix crawl) and remediation of any findings.
- **Dependencies**: Phase 18.
- **Deliverables**: App submitted to Play Console, passing review, available on at least the internal testing track (production release timing is a business decision, not an engineering one).
- **Complexity**: M.
- **Risks**: First-time Play review can surface unexpected policy issues (e.g. sensitive-permission justification, data-safety mismatches) that cause a rejection cycle — budget calendar time (days, not hours) for at least one review round-trip; do not treat submission as the last task on a tight deadline.
- **Completion criteria**: App is live on at least the internal testing track and installable by a real test account via the Play Store; pre-launch report shows no crashes across the automated device matrix.

### Phase 20 — Post-Launch Improvements
- **Objectives**: Capture the deliberately-deferred backlog and establish an ongoing improvement cadence.
- **Scope**: Revisit every item explicitly deferred earlier in this plan — bulk Excel roster import (§1.4 item 3), the notification-preferences UI (backend already supports it per both inventories — Android could be the first client to expose it), passkey support if the product decides to finish that iOS-started feature on Android too, widgets/App Shortcuts if desired (Android's ecosystem-native equivalents of iOS's undone Widgets/Siri work), any findings from real production usage (crash trends, ANR reports, user feedback), performance re-baseline after real-world data volume accumulates (the 26-week/90-day/5-year listener windows were sized for the current data scale — revisit if any collection grows unexpectedly large), and — if the product moves forward with it — a coordinated multi-tenant/Super Admin rollout per the draft PRD (`docs/SUPER_ADMIN_PORTAL_PRD.md`), which would require Android changes wherever this plan assumed a single-tenant model.
- **Dependencies**: Phase 19 (real usage data).
- **Deliverables**: A living backlog, triaged and prioritized against real post-launch signal rather than pre-launch assumption.
- **Complexity**: Ongoing (not a fixed-scope phase).
- **Risks**: Scope creep if this phase isn't bounded by actual usage data — resist adding speculative features without a signal (crash rate, support ticket theme, explicit product ask) driving each addition.
- **Completion criteria**: N/A (ongoing) — recommend a monthly or per-release retrospective against crash-free-user-rate, ANR rate, and Play Store rating as the standing health check for this phase.

---

## 5. Summary

The two existing apps are complementary, not identical: the PWA is the stronger back-office/admin surface, the iOS app is the stronger native/field-operations surface (GPS attendance, payroll, tasks). Android's job is to unify both onto the same shared Firebase + Cloudflare backend — no server-side build-out required beyond what's already there (the `android-native` FCM platform value is already whitelisted). The roadmap above is backend-and-business-logic-first (Phases 3–7) precisely because the domain's highest-risk surface is money and time math, not visual polish — UI phases (12–14) are scoped to run in parallel with, not strictly after, the functional phases, so visual quality isn't crammed in at the end. The single highest-leverage open item to resolve before deep implementation is the wage-model conflict (§1.4 item 1) — it blocks Phase 10 outright and should go to the product owner now, not when Phase 10 starts.
