# Roster PWA ("Rosterra") — Full Feature & Architecture Inventory

Source: `/Users/sura/Desktop/Roster/Roster PWA`. Compiled for Android native-app parity planning. All paths below are relative to that root unless stated otherwise. Business SURA Investments Pty Ltd, brand "Rosterra", production URL `https://sura-roster.com`, Firebase project `roster-8a270`.

> **Note on scope**: this codebase also has Firestore collections (`payslips`, `tasks`, `task_completions`, `shift_attendance`, `daily_job_templates`, `daily_job_assignments`) and Worker notification events (`shift-started`, `job-assigned`, `payslip-generated`, `task-completed`, `jobs-all-completed`) that are **written/consumed only by the native iOS app**, not the PWA. They are documented here (§3, §11) because they exist in the shared backend and inform what Android *could* reach parity with, but they are explicitly **not PWA features** — flagged inline as "iOS-only, no PWA UI."

---

## 1. Tech Stack & Architecture

| Layer | Technology / Version |
|---|---|
| Framework | React 18.3.1, TypeScript 5.5.3 |
| Build tool | Vite 6.4.2 (`vite-plugin-pwa` 0.21.2, `registerType: 'prompt'`) |
| Routing | React Router DOM 6.26.2, all pages `React.lazy` + one top-level `Suspense` |
| State management | Zustand 5.0.0 — 4 stores: `useAuthStore`, `useDataStore`, `useThemeStore`, `useMessageStore` |
| Styling | Tailwind CSS 3.4.13, custom `brand`/`surface`/status token scales (`tailwind.config.js`), dark mode via `.dark` class |
| UI primitives | Radix UI (avatar, checkbox, dialog, dropdown, label, popover, scroll-area, select, separator, slot, switch, tabs, tooltip) |
| Forms | React Hook Form 7.53 + Zod 3.23.8 |
| Dates | date-fns 4.1.0 |
| Charts | Recharts 2.12.7 |
| Excel | xlsx (SheetJS) 0.18.5 |
| PDF | jsPDF 4.2.1 + jspdf-autotable 5.0.8 |
| Toasts | react-hot-toast 2.4.1 |
| Icons | lucide-react 0.447.0 |
| Animation | framer-motion 12.42.2 (marketing site only, isolated into its own bundle chunk) |
| Backend/Auth | Firebase 12.13.0 (Auth email/password, Firestore, Cloud Messaging, Analytics) |
| API/backend compute | Cloudflare Workers (Wrangler 4.93.1), `worker/index.ts` entry |
| KV | Cloudflare KV namespace `RATE_LIMITS` (persistent rate limiting; falls back to in-memory Map if unbound, e.g. local dev) |
| Hosting | Cloudflare Pages (Git-connected build of `dist/`) for the SPA; Cloudflare Worker for `/api/*` and marketing routes; DNS/zone routes `sura-roster.com/api/*` → Worker |
| Testing | Vitest 4.1.7 (root `*.test.ts` pure-logic suites, no emulator) + `@firebase/rules-unit-testing` (Firestore emulator, `firestore.rules.test.ts`, 48 tests, requires Java 21+) |
| Lint | ESLint 8.57.1, `--max-warnings 0` |

**Backend model — Firebase AND Cloudflare Workers, not either/or:**
- **Firebase Auth** — all sign-in/session identity.
- **Firestore** — primary data store, `persistentLocalCache` + `persistentMultipleTabManager` (offline + multi-tab support), initialized in `src/lib/firebase.ts`. Hardcoded config, `projectId: roster-8a270`.
- **Firebase Cloud Messaging** — Web Push; client registers tokens, server (Worker) sends.
- **Firebase Analytics** — lazy GA4 wrapper (`src/lib/analytics.ts`), all calls try/catch-wrapped so adblockers can't break navigation.
- **Cloudflare Worker (`roster`)** — holds the Firebase **service-account** credentials (`FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`) that the browser never sees. It is the only trusted writer for: account creation/reset/email-change/deletion, the server-time-trusted staff-availability week lock, and all outbound push notifications. It also runs the `*/30 * * * *` cron (6 jobs, see §11) and serves the static SPA + marketing pages via `wrangler.jsonc`'s `ASSETS` binding with `not_found_handling: "single-page-application"` and `run_worker_first: ["/api/*", "/home", "/contact", "/privacy", "/terms", ...]`.
- **How they interact**: the React app talks to Firestore directly (client SDK, subject to `firestore.rules`) for almost everything, and falls back to `fetch('/api/...')` Worker calls (bearer Firebase ID token) only for the handful of operations that need elevated trust or server time. `vite.config.ts` proxies `/api` → `http://127.0.0.1:8787` (`wrangler dev`) in local dev.

**Build pipeline**: `pnpm build` = `tsc && tsc -p tsconfig.worker.json --noEmit && vite build && node scripts/postbuild.js`. Worker is separately, strictly type-checked (`@cloudflare/workers-types`). `postbuild.js` rewrites the built `index.html`'s stylesheet `<link>` to non-render-blocking (`media="print" onload="this.media='all'"`), and **fails the build** (`process.exit(1)`) if the regex doesn't match — a deliberate guard against silent regressions if Vite's output format changes. Manual chunks: `firebase`, `recharts`/`d3`, `xlsx`, `framer-motion` (marketing-only), `icons` (lucide, shared), `react-core` (react/react-router, shared with marketing site), `vendor` (everything else, app-only — zustand, radix-ui, react-hook-form, react-hot-toast).

**Dev scripts**: `pnpm dev` (Vite, HMR, `:5173`), `pnpm dev:api` (`wrangler dev --port 8787`), `pnpm dev:worker` (build + full worker+assets), `pnpm deploy` (build + `wrangler deploy`), `pnpm test:rules` (Firestore emulator + Vitest), `pnpm lint`, `pnpm preview`.

**CI/CD**: GitHub Actions (`.github/workflows/ci.yml`, lives at the parent-repo level, not inside `Roster PWA/`) runs lint, typecheck (src + worker), unit tests, rules tests, build on every push/PR; a `deploy` job runs `wrangler deploy` + `firebase deploy --only firestore:rules,firestore:indexes` on push to `main` once all gates pass (requires `CLOUDFLARE_API_TOKEN` + `FIREBASE_SERVICE_ACCOUNT` repo secrets).

**Security headers**: CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Cross-Origin-Opener-Policy`, HSTS — set both in `vite.config.ts` dev/preview server headers and (implied) `public/_headers` for production. `public/_headers` also forces `no-store` on `/sw.js` and `/firebase-messaging-sw.js` (critical for the PWA update mechanism and push reliability — see §12).

**Marketing site**: a *separate* React app (`src/website/*`) built as extra Rollup entry points (`website/home.html`, `contact.html`, `privacy.html`, `terms.html`) served at `/home`, `/contact`, `/privacy`, `/terms` via a Worker rewrite (`worker/index.ts` `marketingPages` map → `env.ASSETS.fetch` of `/website/*.html`). Not part of the authenticated app; out of scope for Android parity except as a source of accurate feature copy (used to cross-check §15).

---

## 2. Authentication & Session Model

**Roles**: exactly two — `'manager' | 'staff'` (`UserRole` in `src/types/index.ts`). No separate "admin" tier exists in the shipped app (a multi-tenant Super Admin role is only a *draft PRD*, see §13). Single-tenant: all data lives in global Firestore collections with no `businessId`/tenant boundary.

**Manager bootstrap**: manager authority = an active Firestore `users/{uid}` doc with `role: 'manager'`, `status: 'active'`. Must be created manually in Firebase Console (Auth + matching Firestore doc) before first login — email alone does not grant access.

**Normal login** (`LoginPage.tsx`, `useAuthStore.login()`): `signInWithEmailAndPassword` → fetch `users/{uid}` → block if `status` is `locked`/`inactive` (sign out + friendly message) → if `mustChangePassword` redirect to `/change-password` → apply saved `theme` → update `lastLoginAt` (client ISO timestamp) → `trackLogin(role)` GA4 event → fresh manual login also sets `deviceAuthVerified: true` (no double biometric prompt right after password entry). Error-code mapping: `auth/invalid-credential`/`user-not-found`/`wrong-password` → "Incorrect email or password."; `auth/too-many-requests` → "Too many failed attempts."; `auth/user-disabled` → "This account has been disabled. Contact your manager."

**Staff creation (manager-initiated, direct)**: `AddStaffModal` → `addUser()` → `POST /api/create-auth-user` (Worker verifies caller is an active manager via ID token, rate-limited 10/hour/manager) → Worker creates the Firebase Auth account with service-account privileges (the manager's own session is never replaced) → client writes `users/{realUid}` Firestore doc.

**Excel-import placeholder accounts** — *data-model exists, but no import UI ships in this codebase* (see finding below): `createPlaceholderUser()` would create a Firestore-only doc `id: placeholder_{timestamp}_{random}`, `email: ''`, `needsSetup: true`, `mustChangePassword: true`, no Firebase Auth account (avoids rate limits). `EditStaffModal.tsx` still contains the "Complete setup" flow (`completePlaceholderSetup()` — creates real Auth user via the same Worker endpoint, migrates the placeholder doc to the real UID, batch-updates (`chunks of 500`) all `shifts.staffId` from placeholder→real, deletes the placeholder doc, all in one `writeBatch`) and `StaffPage.tsx` still has a "Delete placeholder" action. **Finding**: `parseRosterExcel()` in `src/lib/excelParser.ts` (the actual .xlsx→shifts parser) has **zero UI callers** anywhere in `src/` — no `ExcelImportModal.tsx` or `ImportHistoryModal.tsx` file exists, despite being referenced in `Docs/01-Architecture-Overview.md`'s file tree and the app's `importBatches` Firestore collection/security rules. `MasterSheetsModal.tsx` only does template generation (blank `.xlsx` download) and arbitrary-file upload/store/download — it does not parse a file into shifts. Treat "bulk Excel roster import" as a **documented-but-currently-unimplemented** feature when planning Android parity; the placeholder-account infrastructure is real and should be preserved either way (it is exercised by manual "Add staff"/"Complete setup" flows too).

**Forced password change**: `mustChangePassword: true` on the Firestore user doc → all route guards (`RequireManager`/`RequireStaff`/`RootRedirect`) force-redirect to `/change-password`. `ChangePasswordPage` re-authenticates (`reauthenticateWithCredential`), calls `updatePassword`, then `POST /api/complete-password-change` (Worker verifies the Firebase Auth password was changed **within the last 5 minutes** via `passwordUpdatedAt`, then clears `mustChangePassword` with service-account credentials — staff cannot clear this flag themselves via Firestore rules).

**Manager-initiated password reset** (Staff page → lock icon): `resetPassword()` → `POST /api/reset-staff-password`. Worker: verifies manager, validates target is an **active staff** profile (not manager/inactive/locked), rate-limited (**15-min cooldown per manager+staff pair**, **5/hour per manager** — `worker/lib/rateLimit.ts`), sets a temporary password via Firebase Auth REST `accounts:update`, sets `mustChangePassword: true`, writes a Worker-side audit log for attempts/blocks/failures/success plus a client-side `RESET_PASSWORD` audit entry.

**Manager-initiated staff email change**: `changeStaffEmail()` → `POST /api/change-staff-email`. Requires the **manager's own current password** re-entered client-side and verified server-side via `accounts:signInWithPassword` before proceeding (defense against a hijacked manager session). Rate-limited (**5-min cooldown** per pair, 5/hour per manager). Updates Firebase Auth email then Firestore `email` field; if the Firestore write fails after the Auth email already changed, the Worker **rolls back** the Auth email to the old value. Sends an email-verification email.

**Password validation rules** (`validatePassword()`, `worker/handlers/auth.ts` + `src/lib/utils.ts`): minimum 8 characters, ≥1 uppercase letter, ≥1 number, ≥1 symbol ("recommended" — several call sites filter out this specific rule so it's soft, e.g. StaffAccount/ChangePasswordPage change-password flows only enforce the first 3). The visual `PasswordRequirements` component only shows 3 checks (8 chars / uppercase / number) — the symbol rule is invisible in the UI.

**Session lock (manager-only UI lock, not sign-out)**: `useSessionLock` hook starts a **5-minute inactivity timer** (`INACTIVITY_TIMEOUT_MS`), reset on `mousemove`/`mousedown`/`keydown`/`touchstart`/`scroll`, throttled to at most once/second (`RESET_THROTTLE_MS = 1000`) to avoid excessive timer churn. Also locks immediately if the tab was hidden ≥5 minutes (`visibilitychange`). Only active for `role === 'manager'`; staff are never subject to it. `useAuthStore.isLocked = true` — Firebase session stays fully alive; `SessionLockScreen` overlay requires password re-entry via `reauthenticateWithCredential` (**never compared client-side**). Progressive cooldown after **3 failed attempts**: `min(30000, attemptCount * 5000)` ms (3rd fail→15s, 4th→20s, 5th→25s, capped 30s); `auth/too-many-requests` forces a flat 30s cooldown. `assertUnlockedManagerMutation()` is called at the top of nearly every `useDataStore` manager mutation and throws while locked — a client-side belt-and-braces guard (Firestore rules themselves don't know about UI-level lock state).

**Device authentication (WebAuthn app lock, both roles, optional)**: `src/lib/deviceAuth.ts`. Local-only convenience lock using the platform authenticator (Face ID/Touch ID/Android fingerprint/device PIN) — **not verified server-side**; underlying Firebase session is unaffected. `registerDeviceAuth({uid,email,fullName})` → `navigator.credentials.create()` (`rp.id` = hostname, ES256/RS256, `authenticatorSelection: {attachment:'platform', userVerification:'required', residentKey:'preferred'}`, `attestation:'none'`, **60s timeout**) → persists only `{credentialId, createdAt}` to `localStorage['roster_device_auth_{uid}']` — no secret leaves the device. `verifyDeviceAuth(uid)` → `navigator.credentials.get()` with the stored credential id, **never throws**, returns boolean. Gate logic `shouldGateDeviceAuth({hasUser, enabled, verified}) = hasUser && enabled && !verified` — restored/persisted sessions start `verified:false` and are blocked by `DeviceAuthGate` (full-screen overlay in `App.tsx`) until verified; a **fresh manual login** sets `verified:true` immediately (no double-prompt). **Background re-lock threshold: 2 minutes** (`RELOCK_AFTER_MS`, `App.tsx`) — returning to a backgrounded PWA after ≥2 min hidden calls `markDeviceAuthVerified(false)`, re-showing the gate. Requires secure context + `PublicKeyCredential` + a configured platform authenticator; on iOS must be opened from the Home Screen PWA (not a Safari tab).

**Account lock/unlock** (manager toggles staff `status` between `active`/`locked`/`inactive`): both `locked` and `inactive` immediately sign the user out on any `onAuthStateChanged` event or login attempt.

**ATO-safe account deletion lifecycle** (recently added, `worker/handlers/accountDeletion.ts`): staff request → manager approve → **30-day grace lock** → cron Auth purge. States: `requested → approved (locked) → auth_purged | cancelled`. Staff self-request via `StaffAccount.tsx` "Request account deletion"; manager can also request/approve on a staff member's behalf from `EditStaffModal.tsx`. On **approve**: staff `status` set to `locked` immediately, an identity snapshot (`fullName, dob, address, tfn, employeeId, email, phone, startDate, employmentType`) is captured into `deletion.identitySnapshot`, notification tokens purged, `cancelDeadlineAt = now + 30 days`. Manager can **cancel** within the grace window (reinstates `status: active`, clears `deletion`). At day 30, `runAccountDeletionPurgeCron` (Worker cron) calls `purgeStaffAuthRetainRecords()`: deletes the Firebase Auth account + notification tokens, but **explicitly retains** `fullName, dob, address, tfn, employeeId, startDate, employmentType, phone` plus all timesheets/shifts/payslips — clears only `email` (blanked), `mustChangePassword`, `availability`, `weeklyAvailability`, `theme`, `notificationPreferences`. All four transitions write structured `writeAuditLog` entries. `firestore.rules`: the `deletion` field on `users/{uid}` is **never client-writable by anyone, including managers** — Worker-only via the Admin service account. **Manager/business-owner self-deletion is explicitly blocked** in this single-tenant build ("Manager accounts cannot be deleted here... Organisation closure will be handled by Super Admin when Rosterra becomes multi-tenant SaaS" — ties into §13).

**Legacy "Clear staff" (`/api/delete-staff-users`)**: no longer hard-deletes anything — internally delegates to the same ATO-safe approve path (lock + 30-day purge scheduling) for up to 100 staff IDs at once; a staff id with no Firestore profile at all (orphan Auth) gets a straight Auth-only delete.

**TFN (Tax File Number) handling**: manager-only field on `User.tfn`, 9-digit AU checksum-validated (`src/lib/tfn.ts`) — weights `[1,4,3,7,5,8,6,9,10]`, `sum % 11 === 0`, plus rejection of all-same-digit sequences (e.g. `111111111`) even if checksum coincidentally passes. Never staff-writable (Firestore rules), never shown to staff in the UI, stripped client-side from the staff's own `onSnapshot` payload before syncing into `authStore`. Payslip snapshots store only `tfnLast4`.

**Multi-tenant / org model**: **none in the shipped app** — single implicit business. See §13 for the draft multi-tenant Super Admin PRD, which is unimplemented design work, not a current feature.

---

## 3. Data Model

All Firestore documents use root-level collections; timestamps are ISO-8601 strings unless noted "Firestore timestamp" (server-authoritative `serverTimestamp()`). Types sourced from `src/types/index.ts`, `firestore.rules`, and `firestore.indexes.json`.

### `users/{uid}` (doc id = Auth UID, or `placeholder_{ts}_{rand}` for unmigrated Excel-import accounts)
`id, fullName, email, phone?, role: 'manager'|'staff', employmentType?: 'full_time'|'part_time'|'casual', mustChangePassword, status: 'active'|'inactive'|'locked', startDate?, dob?, address?, tfn?` (manager-only, 9-digit AU checksum), `emergencyContact?, notes?` (manager-only), `defaultLocation?, needsSetup?, theme?: 'light'|'dark', availability?: UserAvailability` (standing weekly default), `weeklyAvailability?: Record<weekStartKey, UserAvailability>` (per-week overrides, Worker-write-only), `hourlyRate?, superPercentage?` (default 12), `wageRateId?` (legacy single FK), `wageRateIds?: string[]` (multi-classification FK → `wages.id`), `profileUpdateRequired?` (manager-forced re-confirmation), `deletion?: AccountDeletionState`, `createdAt, updatedAt, lastLoginAt?`. `UserAvailability` = 7 `DayAvailability` keyed by lowercase weekday name; `DayAvailability = {available: bool, allDay: bool, start?: HH:mm, end?: HH:mm}`.

### `shifts/{id}`
`id, staffId (FK users), date: YYYY-MM-DD, rosteredStart/End: HH:mm, breakMinutes, scheduledHours, location?, department?, notes?, status: 'draft'|'published'|'completed'|'cancelled', createdBy (FK users), publishedAt?, submittableAfter: Firestore timestamp` (= shift end instant, Adelaide-tz-aware, overnight-safe; the rule boundary for staff timesheet writes), `shiftStartAt?: Firestore timestamp` (shift start instant, drives pre-shift reminder cron), `startReminder6hSent?/startReminder30mSent?: bool` (cron dedup flags, reset when start time changes), `importBatchId?` (FK importBatches), `createdAt, updatedAt`. *(iOS-only fields also live in the same doc but unused by PWA: `lastReminderHours` int is shared with the hours-submission-reminder cron; `lateNotificationSent`/`overtimeNotificationSent` bools are set by the iOS-only late/overtime sweep cron — see §11.)*

### `timesheets/{id}` (doc id === linked `shiftId` — enforced by rules, one timesheet per shift)
`id, shiftId, staffId, actualStart/End: HH:mm, actualBreakMinutes, workedHours, staffNotes?, status: 'draft'|'pending'|'approved'|'rejected'|'absent_reported'|'absent', managerNotes?, approvedBy?, approvedAt?, rejectedReason?, submittedAt: Firestore timestamp` (server-time on submit/resubmit), `updatedAt`. `absent_reported` = staff self-report (zero hours); `absent` = manager-confirmed. *(iOS-only: `rejectedAt`, `rejectionReminderCount` drive the escalating rejected-timesheet reminder cron — shared field, not written by the PWA client but read by the shared Worker cron regardless of which platform rejected it.)*

### `auditLogs/{id}` — immutable, `orderBy(createdAt desc) limit(100)` in manager listener
`id, actorUserId, action` (e.g. `CREATE_USER, APPROVE_TIMESHEET, PUBLISH_ROSTER, RESET_PASSWORD, CHANGE_PASSWORD, REJECT_TIMESHEET, UPDATE_SETTINGS, CREATE_WAGE, UPDATE_WAGE, DELETE_WAGE, ACCOUNT_DELETION_REQUESTED/APPROVED/DECLINED/CANCELLED/AUTH_PURGED, DELETE_STAFF_USER, RESET_PASSWORD_ATTEMPT, CHANGE_STAFF_EMAIL(_ATTEMPT)`), `entityType: 'user'|'shift'|'timesheet'|'password', entityId, oldValue?, newValue?, reason?, createdAt`.

### `importBatches/{id}` — Excel roster upload history (data model present; no import UI, see §2)
`id, filename, storeName, weekStart/End: YYYY-MM-DD, shiftIds: string[], shiftCount, importedBy, importedAt`.

### `settings/app` (singleton)
`companyName` (default `'Roster'`) — used throughout for branding, PDF headers, login page.

### `settings/availabilityLocks` (singleton)
`weeks: Record<weekStartKey, true>` — managers set via "Publish & Lock"; drives the availability-week-lock UI and the Worker's server-side lock enforcement.

### `wages/{id}` — AU Award (MA000089) + custom wage classifications
`id, label, baseRate (AUD/hr), employmentType: 'full_time'|'part_time'|'casual', rateType?: 'weekday'|'weekend'|'weekday_overtime'|'weekend_overtime', ageGroup` (e.g. "Age 20+"/"Age 19"/"Age 18"/"Age 17"/"Age 16"), `isSystem?` (true = seeded MA000089 rate, sorted first), `createdAt, updatedAt`. Manager-only read+write.

### `masterSheets/{id}` + `masterSheets/{id}/chunks/{index}` subcollection
`id, filename, uploadedBy, uploadedAt, fileData` — arbitrary uploaded spreadsheet storage (not the same as roster-import parsing). Large files chunked into **~500,000-char (500KB) pieces** as subcollection docs (parent doc's own `fileData` left `''`); reassembled client-side on download. Manager-only.

### `messages/{id}` — one-way manager→staff broadcast (Task type only; `announcement` reserved, unimplemented)
`id, senderId, senderName?, recipientId` (**one doc per recipient** even for a single broadcast), `body` (≤500 chars, enforced by rules), `type?: 'task'`, `sentAt, expiresAt` ("Never Expire" UI option actually caps at **+1 year**, not literal never), `read: bool`.

### `users/{uid}/notificationTokens/{tokenId}` subcollection (doc id = URL-encoded FCM token)
`token, platform: 'ios-pwa'|'android-pwa'|'macos-pwa'|'macos-browser'|'desktop'|'ios-native'|'android-native', userAgent, enabled, createdAt, updatedAt` (both Firestore server timestamps). Owner-only read/write; Worker reads via Admin SDK.

### iOS-only collections present in `firestore.rules` (no PWA reader/writer — listed for completeness / Android scoping decisions)
- **`payslips/{periodStart}_{staffId}`** — payroll data; `status: draft|under_review|approved|submitted|archived`; staff can only read once `submitted`/`archived`. Written exclusively by the native iOS app. (This collection was accidentally missing from `firestore.rules` between 2026-07-10 and 2026-07-15 — a default-deny gap silently broke every payslip read/write; fixed and covered by 5 new rules tests.)
- **`tasks/{id}`** — manager-defined recurring/one-off tasks, readable by all authenticated users, manager-write.
- **`task_completions/{id}`** — staff-submitted task completions (`completedBy`, optional `staffPhotoUrl`); staff can create/update their own, cannot overwrite another's; managers own review/redo/cleanup.
- **`daily_job_templates/{id}`** — manager-curated reusable job library.
- **`daily_job_assignments/{id}`** — shift-scoped job assignments; staff read own, can only toggle `completed`/`completedAt`/`completedBy`.
- **`shift_attendance/{shiftId}`** (doc id = shift id) — verified clock-in/out with server-timestamp-enforced `clockInAt`/`clockOutAt` (staff cannot forge times), optional GeoPoint/geofence fields. Drives the iOS-only "running late"/"overtime" manager-alert cron (§11) and is also consulted by the PWA's own Firestore rules (`hasVerifiedClockOut()`) as an *alternative* early-submission unlock path for timesheets.

### Indexes (`firestore.indexes.json`)
`shifts(staffId, status, date)`; `timesheets(staffId, submittedAt)`; `messages(recipientId, sentAt)`, `messages(recipientId, expiresAt)`, `messages(senderId, expiresAt)`, `messages(senderId, sentAt)`; `daily_job_assignments(staffId, date)`; `shift_attendance(staffId, date)`.

**No D1/R2/other Cloudflare storage is used today** — only the KV namespace `RATE_LIMITS` for rate limiting. (A proposed migration of `masterSheets.fileData` off base64-in-Firestore onto R2 is documented only in the draft Super Admin PRD, §13 — not implemented.)

---

## 4. Security Rules Summary (`firestore.rules`)

RBAC via two helpers: `isAuthenticated()` and `isManager()` (active Firestore profile with `role=='manager' && status=='active'`, re-checked via `get()` on every rule evaluation — no custom claims used).

| Collection | Staff | Manager |
|---|---|---|
| `users` | Read own doc only. Update only `fullName, phone, dob, address, emergencyContact, theme, profileUpdateRequired, updatedAt, lastLoginAt` (`isValidSelfUserUpdate()`) — **cannot** touch `role, email, status, tfn, weeklyAvailability, availability, deletion`. Cannot delete. | Read/create/update all (cannot touch `deletion` — `doesNotModifyDeletion()`); can delete only setup placeholders (`needsSetup==true && email==''`). |
| `users/{uid}/notificationTokens` | Owner-only CRUD with strict field allowlist (`token, platform, userAgent, createdAt, updatedAt, enabled`), `platform` enum-checked, timestamps must equal `request.time`. | No cross-user access via rules (Worker uses Admin SDK). |
| `shifts` | Read only own **published** shifts (query-shape enforced — an unfiltered query is rejected, not just result-filtered). No write. | Full read/write. |
| `timesheets` | Read own only (query-shape enforced). **Create**: doc id must equal `shiftId`; `staffId==self`; linked shift must be own+published+`request.time >= shift.submittableAfter` **or** have a `shift_attendance` doc with a server-verified `clockOutAt` (`hasVerifiedClockOut()`); status must be `pending`/`draft`/(`absent_reported` with `workedHours==0`); `submittedAt==request.time`; cannot include `approvedBy/approvedAt/managerNotes`. **Update**: same shift-linkage+timing gate; only from `pending/rejected/draft/absent_reported`; absence status always requires `workedHours==0`; cannot touch manager-approval fields; status can only be set to `pending/draft/absent_reported`; `rejectedReason` can only be cleared, never rewritten. **Delete**: only own `absent_reported` (the "undo" path) — never an `approved` or manager-confirmed `absent`. | Full read/write/delete, any active manager (not scoped to a per-shift "owner"). |
| `auditLogs` | Create only where `actorUserId==self` (no spoofing); no read/update/delete. | Full CRUD. |
| `importBatches`, `wages`, `masterSheets` (+`chunks`) | No access. | Full CRUD. |
| `payslips` | Read own only when `status in [submitted, archived]` (drafts/review/approved invisible). No write. | Full CRUD. |
| `settings` | Read any. | Write. |
| `messages` | Read own (`recipientId==self`) only; update limited to flipping `read` to `true` on own docs. | Full CRUD, body ≤500 chars validated. |
| `tasks` | Read all. No write. | Full CRUD. |
| `task_completions` | Create own (`completedBy==self`); update own or a manager-reopened (`completed==false`) doc, `staffPhotoUrl` type-checked. | Full CRUD (redo/review/cleanup). |
| `daily_job_templates` | No access. | Full CRUD. |
| `daily_job_assignments` | Read own; update **only** `completed/completedAt/completedBy` fields on own assignment. | Full CRUD. |
| `shift_attendance` | Read own. Create: own+published shift, `clockInAt==request.time`, no `clockOutAt` on create. Update: identity fields frozen, `clockInAt`/`clockOutAt` (if touched) must equal `request.time`. No delete (audit trail). | Full read; delete only. |

**Cross-cutting invariants**: staff can never self-approve/self-reject timesheets or write manager-only fields; `weeklyAvailability` is entirely excluded from the staff self-update allowlist (Worker-only write path, see §8); `tfn` and `deletion` are never staff-writable and `deletion` is never *manager*-client-writable either (Worker/Admin-SDK-only); real staff/manager profiles can never be hard-deleted through rules, only setup placeholders. `firestore.rules.test.ts` (48 emulator tests) is the canonical source of truth and covers exactly these boundaries — see §7 (test-driven business rules) for the exact matrix.

---

## 5. Staff Screens

Route guard `RequireStaff` (redirects non-staff to `/manager/dashboard`, redirects `mustChangePassword` to `/change-password`). All wrapped by `StaffLayout` → `ProfileCompletionGate` (blocks the entire app behind a "complete your profile" form if `dob`/`address`/`phone`/`email` are incomplete, or if a manager set `profileUpdateRequired: true`).

1. **`/staff/home` — `StaffHome`** (`src/pages/staff/StaffHome.tsx`): greeting header ("Good morning/afternoon/evening" by hour, first name), unread-message "Notifications" pill (badge caps at 99+), `EnableNotifications` opt-in card, PWA install banner (only if `beforeinstallprompt` captured, not standalone, not dismissed within 7 days), Today's Shift card(s) (`StaffShiftCard` variant `today`), 2×2 approved-hours summary (This week/month/year/All time), Upcoming Shifts (next 3, `variant="compact"`), and an in-app Notifications modal listing unread-first Task messages (marks read on open).
2. **`/staff/roster` — `StaffRoster`** (`src/pages/staff/StaffRoster.tsx`): weekly roster (`StaffWeekPicker`, week bounds **-4 weeks to +8 weeks** from `getStaffShiftWeekOffsetBounds()`), per-day sections with `StaffShiftCard`s, "N shift(s) need your attention" action banner (scroll-to-first), deep-link support for `?submit=<shiftId>` / `?absent=<shiftId>` (used by push-notification taps and StaffHome links; falls back to a network fetch if the shift isn't in the already-loaded window, shows a failure banner if still not found), ICS calendar export per shift, and hosts `SubmitHoursModal` + `ReportAbsenceModal`.
3. **`/staff/submit`** — legacy redirect shim (`StaffSubmitRedirect`) → `/staff/roster?submit=<shiftId>`.
4. **`/staff/history` — `StaffHistory`** (`src/pages/staff/StaffHistory.tsx`): timesheet history list, period filter (This week / This month [default] / This year / All time), status filter (all/approved/pending/rejected/absent_reported/absent), 2×2 approved-hours summary tiles, pending-hours banner, per-record card (rostered vs actual, staff notes, rejection reason, manager notes + approver, "Resubmit hours" shortcut for rejected records).
5. **`/staff/availability` — `StaffAvailability`** (`src/pages/staff/StaffAvailability.tsx`): per-week (Mon–Sun) editor, 7 day rows each with Available toggle / All-day toggle / custom start-end time, week navigation **-2 to +12 weeks**, current/past weeks always locked (server-enforced, see §8), manager-published weeks locked, "Set as Default/Recurring" (propagates to all following weeks out to +12), "Reset This Week"/"Reset Following Weeks" shortcuts.
6. **`/staff/account` — `StaffAccount`** (`src/pages/staff/StaffAccount.tsx`): profile summary (avatar, role/status/employment badges, member-since), stats (total approved hours, timesheet count, pending count), `EnableNotifications` (always visible here, `hideWhenEnabled={false}`), `DeviceAuthSettings`, Change password modal (with `PasswordRequirements` checklist), Account deletion request flow (state-dependent messaging for `requested`/`approved`/`auth_purged`), Sign out, `CopyrightFooter`.

**Shared staff components**: `StaffShiftCard` (shift card with submit/report-absence/undo-absence action footer, gated by `isShiftSubmittable`/`canStaffReportAbsenceForShift`), `StaffWeekPicker` (reusable week-nav + 7-day strip), `SubmitHoursModal` (actual start/end/break entry, live worked-hours diff warning at >0.5h), `ReportAbsenceModal` (optional reason text), `ProfileCompletionGate`, `StaffLayout` (header, 5-tab bottom nav: Home/Roster/History/Availability/Account, offline pill, timesheet-alert pill for rejected/pending counts, skip-to-content link).

**Auth pages**: `LoginPage` (email/password, show/hide password, theme toggle, link to `/home` marketing site, `autocomplete` hints for password managers), `ChangePasswordPage` (forced or voluntary password change with live requirement checklist).

---

## 6. Manager Screens

Route guard `RequireManager`. All wrapped by `ManagerLayout` (collapsible desktop sidebar / mobile bottom nav + drawer, offline pill, theme toggle).

1. **`/manager/dashboard` — `ManagerDashboard`** (`src/pages/manager/Dashboard.tsx`): greeting header, 5 KPI cards (Rostered Today, Rostered hrs this week, Approved hrs this week, Pending timesheets, Missing submissions — each computed via `computeDashboardStats()`), hours-this-week `AreaChart` (rostered vs approved series), Quick Actions list (Create shift, Review timesheets w/ badge, Deletion requests w/ badge, Add staff, Export report), Today's Roster table, Pending Timesheets preview (capped 8 rows, oldest-first).
2. **`/manager/roster` — `RosterPage`** (`src/pages/manager/RosterPage.tsx`, ~430 lines, orchestrates sub-components): Week/Day/Staff view toggle, shift create/edit/delete (`ShiftFormModal`), Publish workflow ("Publish Only" vs "Publish & Lock" which also locks staff availability for that week), drag-and-drop shift move/copy (manager+week-view only; published-current-week shifts protected from drag; same-staff overlap blocked), "Copy Last Week" (dedupe-guarded), availability week-lock toggle, Master Sheets modal, staff/status filters, desktop KPI row (Total Rostered Hours, Weekly Budget Forecast [gross/super/total labour cost via `computeRosterForecast`], Active Rostered Staff, missing-wage-rate warning).
3. **`/manager/timesheets` — `TimesheetsPage`** (`src/pages/manager/TimesheetsPage.tsx`): Pending (incl. `absent_reported`) / Approved / Rejected / Absent tabs, week nav, staff filter, deep-link support (`?review=<id>`, `?view=missing`), per-card Approve/Reject/Edit actions, Confirm Absent / Undo Confirmation, **bulk-approve** normal-variance timesheets (`|worked-scheduled| <= 0.5h`, tagged "Bulk approved"), Download Wage PDF for the displayed week.
4. **`/manager/staff` — `StaffPage`** (`src/pages/manager/StaffPage.tsx`): searchable/filterable staff directory (excludes managers), per-row this-week hours, Add/View/Edit/Complete-setup modals, Reset password, Lock/Unlock, Deactivate, Delete placeholder, pending-deletion-request banner with review shortcut.
5. **`/manager/availability` — `AvailabilityPage`** (`src/pages/manager/AvailabilityPage.tsx`): read-only weekly availability grid, week nav **-4 to +12**, search, Unavailable/All-day/Custom-hours cell states.
6. **`/manager/reports` — `ReportsPage`** (`src/pages/manager/ReportsPage.tsx`): Weekly/Monthly/Yearly period tabs (forward-capped at present), staff/status filters, KPI cards, approved-hours-by-staff bar chart, detail table, **3 export formats** (CSV 4-col, Excel 14-col, PDF wage report via `generatePayrollPdf`).
7. **`/manager/tenure` — `TenureSummary`** (`src/pages/manager/TenureSummary.tsx`, explicitly "Read only"): per-staff tenure/hours analytics (`getTenureString`, weekly run-rate clamped to a 1-week-minimum divisor), sortable table, drill-down modal (milestones, 12-month chart, last 5 shifts), "New" badge (started ≤30 days ago), Excel + branded PDF export.
8. **`/manager/wage` — `WagePage`** (`src/pages/manager/WagePage.tsx`): AU Award (MA000089) + custom wage-rate CRUD, search/type/source filters, single/bulk/clear-all delete each behind a typed confirm-phrase (`DELETE SYSTEM RATE` / `DELETE SELECTED WAGE RATES` / `DELETE ALL WAGE RATES`).
9. **`/manager/settings` — `SettingsPage`** (`src/pages/manager/SettingsPage.tsx`): own profile edit, company name, audit log (last 20), change-password, `EnableNotifications`, `DeviceAuthSettings`, app version/build info, and the **Danger Zone / Clear Data** flow (production-gated: staff-only clearing forced-on with shifts/timesheets clearing hard-blocked in prod; confirm phrase `DELETE PRODUCTION DATA` vs dev's `DELETE ALL DATA`; requires manager's current password; all-staff or selected-staff scope; staff clearing routes through the ATO-safe 30-day-purge path, never immediate hard delete).
10. **`/manager/messages` — `MessagesPage`** (`src/pages/manager/MessagesPage.tsx`): Compose (500-char body, expiry presets incl. "Never Expire" [actually +1 year], recipient multi-select) / Sent history (grouped by broadcast, 7-day date-strip nav, read-receipt tracking, inline edit for still-open broadcasts).

**Shared roster components**: `RosterToolbar`, `RosterWeekView` (7-col drag/drop grid), `RosterStaffView` (staff × day matrix with per-cell rate + labour cost), `RosterDayView` (day list), `ShiftFormModal` (Staff/Status, Date/Schedule with live availability-conflict banner, Location/Department/Notes), `MasterSheetsModal` (template download + arbitrary file store), `AddStaffModal`, `EditStaffModal` (setup-completion mode, TFN field, deletion-lifecycle actions, "Request profile update"), `ViewStaffModal` (read-only, shows loaded/casual rate).

---

## 7. Roster & Scheduling Features

- **Shift lifecycle**: `draft → published → completed/cancelled`. Staff only ever see `published` shifts. Creating/editing always recomputes `submittableAfter` (shift end instant) and `shiftStartAt` (shift start instant) server-relevant fields via `getShiftSubmittableAfter()`/`getShiftStartDateTime()` — both timezone-aware against **`Australia/Adelaide`** (fixed business timezone, `ROSTER_TIME_ZONE` constant), not device locale, using `Intl.DateTimeFormat`-offset math so it's correct whether evaluated in a browser or a UTC Cloudflare Worker isolate.
- **Scheduled hours** (`calcScheduledHours(start, end, breakMinutes)`, `scheduled-hours.test.ts`): handles overnight shifts (`end < start` ⇒ +24h, e.g. `22:00→02:00` with no break = 4h); never negative (break exceeding raw duration clamps to 0); **rounded to 2 decimal places** (`09:00→09:20` = 0.33h, i.e. nearest ~36 seconds). `08:15→16:45` minus 45-min break = 7.75h.
- **Break minutes**: `BREAK_MINUTES_MIN=0, MAX=90, STEP=5` — single source of truth in `src/lib/utils.ts`, consumed by the shared `BreakMinutesInput` stepper component everywhere. New shifts and Excel-imported shifts both default break to 0.
- **Drag-and-drop** (`roster-drag.test.ts`): manager + week-view only (`canUseRosterDragDrop`). **Move**: mutates `date` only, forces `status:'draft'`/`publishedAt:null` on the *existing* shift id (un-publishes it). **Copy**: original left untouched; a *new* shift doc created (`draft`, new id/timestamps, all content fields copied). **Cancel** (drop on same day): no-op. **Overlap guard**: blocked with "This staff member already has a shift at this time." if the same staff already has an overlapping (midnight-crossing-aware) shift that day. **Published-current-week lock**: a `published` shift dated in the *current* roster week cannot be dragged at all — draft shifts in the current week, or published shifts in *future* weeks, remain draggable.
- **Publishing**: batch-updates draft shifts to `published`, sets `publishedAt`; optional "Publish & Lock" simultaneously writes `settings/availabilityLocks.weeks[weekKey]=true`, blocking staff availability edits for that week (see §8) and firing an `availability`-category push notification suppression is not relevant here — it fires `roster-published` to the affected staff.
- **Copy Last Week**: clones every non-cancelled shift from the prior week +7 days as a new draft, **deduplicated** against any existing identical (date/staff/start/end, non-cancelled) shift.
- **Excel import** — data model (`importBatches`, placeholder accounts) exists and `parseRosterExcel()`/`parseTimeStr()` are fully implemented and unit-tested (`excelParser.test.ts`) against the "X Convenience"/"SURA Store" weekly format (title row → date-serial row Mon-Sun at cols 1,3,5,7,9,11,13 → header row → staff rows with `[timeRaw, hours]` pairs per day, "Break" rows skipped, **5MB file size cap**, graceful parse-error collection rather than throwing), **but no UI in this codebase calls it** — see §2 finding. `downloadRosterTemplate()` (also in `excelParser.ts`) *is* wired up (`MasterSheetsModal` → "Generate Standard Weekly Template" button) and produces a blank `.xlsx` in the exact layout the parser expects.
- **Forecasting / labour cost** (`roster-forecast.test.ts`, `computeRosterForecast()`): per-shift `loadedRate = resolveShiftLoadedRate(shift, staff, wages)` (see below) × `scheduledHours` summed into `totalGrossWages`; `totalSuper = Σ (grossWagesForStaff × (staff.superPercentage ?? defaultSuperPercentage)/100)` — **explicit `superPercentage: 0` is respected as a deliberate override**, not treated as "unset"; `totalLabourCost = totalGrossWages + totalSuper`; `rosteredStaffCount` = unique staff with ≥1 shift; `missingStaffNames` lists staff with no resolvable rate (by name) or "Unknown Staff" for a shift whose `staffId` has no matching user record.
- **Loaded rate resolution** (`resolveShiftLoadedRate()`, `loaded-rate.test.ts`) — the single most important payroll formula:
  1. If `staff.wageRateIds[]` set: filter to assigned rates, pick by the shift's actual day-of-week (Sat/Sun = weekend, else weekday) matching `rateType`, falling back to a `rateType`-prefix match, then the first assigned rate.
  2. Else fall back to legacy single `staff.wageRateId`.
  3. Else fall back to `staff.hourlyRate ?? 0`.
  4. **Casual loading = ×1.25**, applied only when `staff.employmentType==='casual'` **and** the resolved wage-table rate is not itself flagged as a casual-specific classification (no double-loading); a bare `hourlyRate` with no wage-table entry always gets the 1.25× if casual.
  5. Returns `0` for no staff or no resolvable rate at all.
- **Availability conflict indicator** (`resolveShiftAvailability()`, `shift-availability.test.ts`, used live in `ShiftFormModal` via `useShiftAvailability`): default = available all day if nothing configured; a day marked fully unavailable → `danger` conflict naming the weekday; a shift falling partially/fully outside a custom availability window → `warning` conflict (a lesser severity than a full day-off).
- **Payroll PDF** (`computePayrollSummary()`/`generatePayrollPdf()`, `pdfGenerator.test.ts`): per-staff weekday/weekend hour+wage split (Sat/Sun test on shift date), rejected/pending/absent counts tracked separately (excluded from wage totals), "edited" flag (manager notes present OR any actual time/break differs from rostered), superannuation at each staff's individual `superPercentage` (default 12%), `grandTotalCost = grandWages + grandSuper`, rows sorted alphabetically, staff with no matching user record silently excluded. Landscape A4 PDF, indigo (`#4f46e5`) theme, KPI summary block + detail table + per-page footer.
- **Sort order** (`sortShiftsForRoster`): date asc → rosteredStart asc → staff DOB asc (older staff first; missing DOB sorts last via a `'9999-99-99'` sentinel).

---

## 8. Availability & Leave/Absence Management

- **Weekly availability model**: `User.availability` (standing recurring default) + `User.weeklyAvailability[weekStartKey]` (per-week overrides, `weekStartKey` = Monday `yyyy-MM-dd`). Resolution priority for any given week: specific week override → standing default → "available all day" if nothing set.
- **Staff-side lock — server-time enforced, not device-time**: staff can edit availability only for **strictly future** roster weeks. Both the current week and all past weeks are locked (`weekKey <= currentWeekStart`, Monday-key string comparison). The lock decision is made by the **Worker's own clock** (`worker/handlers/availability.ts` `rosterWeekStartKey(now, 'Australia/Adelaide')`), not the browser — `POST /api/staff/availability` recomputes the current week server-side and rejects (HTTP 403) any changed week ≤ that boundary. If the Worker is unreachable, the client shows "Could not verify server time..." and does **not** fall back to a device-time decision (`availability-lock.test.ts` explicitly verifies a tampered/backdated device clock cannot bypass this). Firestore rules independently back this up by excluding `weeklyAvailability` from the staff self-update field allowlist entirely — the Worker (Admin SDK) is the *only* write path for that field.
- **Manager week-lock ("Publish & Lock")**: `settings/availabilityLocks.weeks[weekKey]=true`, additionally locks an otherwise-still-future week once its roster has been published — checked both client-side (`isAvailabilityWeekLockedForStaff`) and server-side inside the same Worker endpoint (`getManagerLockedWeeks()`), which rejects a save touching a manager-locked week even if it's chronologically future.
- **UI bounds**: `StaffAvailability.tsx` restricts navigation to **-2 weeks to +12 weeks**; `AvailabilityPage.tsx` (manager, read-only) allows **-4 to +12**. Note the general availability *feature* bound (`AVAILABILITY_MAX_WEEK_OFFSET=12`) is deliberately wider than the staff shift-visibility window (+8 weeks) — staff can pre-set availability further ahead than shifts are actually rostered.
- **"Set as Default/Recurring"**: applying the current week's form to this week *and* all following weeks out to the +12-week horizon (`buildRecurringWeekKeys`), skipping any manager-locked weeks. "Reset This Week" / "Reset Following Weeks" delete the corresponding override entries, reverting to the standing default.
- **Notification on availability change**: saving triggers an `availability-updated` push to all active managers (best-effort, awaited server-side since the Worker fetch handler has no `ExecutionContext.waitUntil`).
- **Availability reminder cron**: `runAvailabilityReminderCron` fires once, in the Friday **17:00–17:29 Adelaide** half-hour, nudging any active staff member who hasn't yet set availability for *next* week (dedup via a per-user `lastAvailabilityReminderWeek` field, optimistic-concurrency commit).
- **Absence reporting** (leave/no-show model — no separate "leave request" feature exists; absence reporting is the closest analogue and it's tightly coupled to shift attendance, not a forward leave-request):
  - Staff can report an absence (`status: 'absent_reported'`, `workedHours: 0`) only after the shift has ended (`canReportAbsence` = same submittable-after gate as hours submission) and only if there's no existing timesheet, or the existing one is `rejected` (`canStaffReportAbsenceForShift`).
  - Staff can **undo** their own un-confirmed absence report (delete it) at any time before manager confirmation.
  - Manager **confirms** (`absent_reported → absent`, sets `approvedBy/approvedAt`) or can **undo a confirmation** back to `absent_reported`.
  - Staff can never directly reach the manager-confirmed `absent` status, and cannot delete a confirmed absence.
  - Reporting absence fires a `timesheet-absent` push to all active managers.

---

## 9. Time & Attendance / Timesheets / Rounding

*(Clock-in/out with GPS geofencing is an **iOS-only** feature — `shift_attendance` collection has no PWA UI; the PWA's only "attendance" concept is the after-the-fact hours-submission flow below.)*

- **Submission window**: a shift becomes submittable only once `now >= submittableAfter` (= shift's rostered end instant, Adelaide-tz-aware, overnight-safe) **or** (iOS-only alternate path) there's a verified `shift_attendance` doc with a server-stamped `clockOutAt`. `isShiftSubmittable()`/`canReportAbsence()` mirror this client-side so the UI disables early actions before Firestore rules would reject them; the rules themselves are authoritative (`isSubmittableStaffShift()`).
- **Submit Hours flow** (`SubmitHoursModal`): pre-fills actual start/end from rostered times (or the existing rejected timesheet); live worked-hours computation via `calcScheduledHours`; a diff banner appears once `|worked - scheduled| > 0.5h` ("Your manager will review this"); a rejected timesheet resubmission clears `rejectedReason` and re-fires the manager `timesheet-submitted` notification (the generic `updateTimesheet` store action doesn't auto-notify, so the modal fires it explicitly).
- **Timesheet statuses**: `draft, pending, approved, rejected, absent_reported, absent`. Staff-editable statuses: `draft, pending, rejected, absent_reported` (`isStaffEditableTimesheetStatus`/`STAFF_WRITABLE_TIMESHEET_STATUSES`). `approved`/manager-confirmed `absent` are terminal from the staff side.
- **Manager review** (`TimesheetsPage`): Approve (`approveTimesheet`, optional notes) / Reject (**requires a non-empty reason**, `rejectTimesheet` also resets `rejectedAt`+`rejectionReminderCount:0` to restart the escalating-reminder cron cycle) / Edit-while-pending / **Edit an already-approved timesheet** (`editApprovedTimesheet` — corrects hours without changing status or re-triggering approval notifications) / Confirm Absent / Undo Absence Confirmation.
- **Bulk approve**: approves every pending timesheet in view where `|workedHours - scheduledHours| <= 0.5h` in one action (tagged with manager note "Bulk approved"); anomalous ones are left for individual review.
- **Missing-submission detection** (`getMissingSubmissionShifts()`, `dashboard-stats.test.ts`): a published shift counts as "missing" once its submission window has opened (`isShiftSubmittable`) if there's no "actioned" timesheet — actioned = `pending|approved|absent_reported|absent`; a `draft` or `rejected` timesheet **still counts the shift as missing**. Excludes shifts belonging to `inactive`/`locked` staff.
- **Dashboard/report week convention**: strictly Mon–Sun bounded on both ends (`getRosterWeekRange`); a shift/timesheet from the adjacent week never leaks into "this week" totals, verified with 100-hour edge-case shifts just outside the boundary. **Approved hours are attributed to the shift's date**, not the timesheet's `approvedAt` date — an approval processed this week for a shift worked last week does not count toward this week's approved-hours KPI.
- **Escalating reminder crons** (see §11 for full cron detail) drive staff to submit hours (every 2h after shift end) and to resubmit after rejection (24h cadence, capped at 3 reminders).
- **No separate "clock in/out" or explicit break-taken timer UI in the PWA** — break is entered as a single number of minutes (`actualBreakMinutes`) alongside actual start/end at submission time, mirroring the rostered-shift model (`breakMinutes`) rather than a live timer.
- **Payroll-adjacent rounding recap**: hours rounded to 2 decimals (nearest ~36 seconds); break minutes rounded to nearest whole minute, clamped 0–90, step 5; casual loading 1.25×; default super 12% (staff-overridable, including an explicit 0% override).

---

## 10. Payroll Features

**No payslip generation, payslip viewing, or payroll-run UI exists in the PWA.** The `payslips` Firestore collection and its rules (staff can read only `submitted`/`archived` payslips) exist purely to support the **native iOS app**, which is the exclusive writer. What the PWA *does* provide that is payroll-adjacent:

- **Wage rate management** (`WagePage`) — AU Award MA000089 system rates + custom classifications, assignable per staff (§6, §7).
- **Loaded-rate/labour-cost computation** for rostering and forecasting (§7) — this is cost *planning*, not a payslip.
- **Payroll PDF report** (`generatePayrollPdf`/`computePayrollSummary`, §7, §9) — a **wage & timesheet summary report** (hours worked × rate + super, by staff, for a date range), explicitly distinct from an individual payslip. This is the closest thing to "payroll output" the PWA produces, and it is a manager-facing export, not a staff-visible payslip.
- **TFN capture** (manager-only field, §2) exists purely as payroll/ATO record-keeping metadata, feeding into the (iOS-only) payslip's `tfnLast4` snapshot.
- **Superannuation**: per-staff `superPercentage` (default 12%) is used only inside the forecast/PDF calculations above, never disbursed or tracked as a separate ledger.

**Android parity implication**: if the Android app is meant to mirror the *PWA* specifically (not the iOS app), payslip viewing/generation is **out of scope** unless the product decision is to also bring the iOS-only payroll feature set to Android — worth an explicit product decision rather than inferring parity from the shared backend schema.

---

## 11. Notifications

### Push setup (FCM)
- Web Push via Firebase Cloud Messaging, VAPID key embedded at build time (`vite.config.ts` fallback constant, overridable via `VITE_FIREBASE_VAPID_KEY`).
- Background delivery: `public/firebase-messaging-sw.js` (separate scope `/firebase-cloud-messaging-push-scope`, distinct from the Workbox SW at `/`), `onBackgroundMessage` builds and shows the notification from the **data-only** payload (title/body/url), and a `notificationclick` handler either focuses an existing window (postMessage `{type:'NOTIFICATION_CLICK', url}` since Safari can't `client.navigate()` from a SW) or opens a new one.
- Foreground delivery: `initForegroundNotifications()` (`src/lib/notifications.ts`) subscribes to `onMessage` and manually calls `registration.showNotification(...)` via the FCM SW registration (never falls back to the Workbox SW).
- Environment/support detection (`evaluateNotificationEnvironment()`, `notification-environment.test.ts`): distinguishes `ios-pwa` (supported, only if installed standalone), `ios-not-installed` (blocked, Safari-tab iOS — shows Add-to-Home-Screen instructions), `android-pwa`, `macos-pwa`, `macos-browser`, generic `desktop`; `canRequestPermission` only when `Notification.permission === 'default'`.
- Token lifecycle: `getNotificationToken()` (registers/reuses the FCM SW, 10s activation wait) → `saveNotificationToken()` upserts `users/{uid}/notificationTokens/{urlEncodedToken}` → `ensureNotificationRegistration()` (best-effort re-heal, called on every login and PWA update) → `ensureNotificationRegistrationThrottled()` (**12-hour throttle**, `localStorage['roster_push_heal_at']`, bypassed with `force=true` after a service-worker update) → `disableNotifications()` deletes both the FCM token and the Firestore doc.
- `EnableNotifications` UI component: compact auto-hiding card on StaffHome, always-visible card (`hideWhenEnabled={false}`) with an explicit "Re-register device" action on StaffAccount/manager Settings.

### Client-triggered events (`src/lib/notificationTriggers.ts` → `POST /api/send-notification`)
Fire-and-forget, deduped in-flight by event+payload key, never throw to the caller. Worker resolves recipients **server-side** (never trusts client-supplied recipient identity beyond explicit manager-only `recipientIds` for message/job/payslip/cancel events) and sends via FCM HTTP v1, using a **data-only** message plus an explicit `apns.payload.aps.alert` block (a prior production bug: without this, iOS received a silent push with nothing to display — there's no PWA-service-worker equivalent on iOS to synthesize a banner from bare `data`).

| Event | Trigger (who fires it) | Recipients | PWA-visible |
|---|---|---|---|
| `roster-published` | manager publishes shifts | assigned staff (resolved from shift docs) | ✅ |
| `shift-changed` | manager edits a published shift's date/time | assigned staff | ✅ |
| `shift-cancelled` | manager deletes a published shift | the shift's (pre-deletion) staffId | ✅ |
| `timesheet-submitted` | staff submits/resubmits hours | all active managers (body enriched with incomplete-jobs count if any — iOS jobs feature) | ✅ |
| `timesheet-absent` | staff reports absence | all active managers | ✅ |
| `timesheet-approved` | manager approves | the owning staff member | ✅ |
| `timesheet-rejected` | manager rejects | the owning staff member | ✅ |
| `message-task` | manager sends a Task message | explicit recipient list | ✅ |
| `shift-started` / `shift-ended` | iOS clock-in/out | all active managers | ❌ iOS-only trigger, PWA never fires it |
| `task-completed` / `jobs-all-completed` | iOS task/job completion | all active managers | ❌ iOS-only |
| `job-assigned` / `payslip-generated` | manager (iOS flows) | explicit staff | ❌ iOS-only trigger; PWA has no UI to fire these |
| `availability-updated` | staff saves availability (fired **server-side** inside the availability Worker endpoint, not a client trigger) | all active managers | ✅ |

Every event: the acting caller is always excluded from their own notification list; recipient lists are de-duplicated; manager-only events reject non-manager callers with `403 "Only managers can send this notification"`; unknown event name → `400`. Notification-preference gating (`notifyUsers()` in the Worker) checks `users/{uid}.notificationPreferences.{category}` per-recipient — **fail-open**: missing field, missing doc, or a failed lookup all default to *enabled*; only an explicit `false` skips (no UI to set these preferences exists yet in the PWA — this is forward-compatible plumbing). Rate-limited: **20 notification-send calls/hour per manager** (`worker/lib/rateLimit.ts`).

### Scheduled reminders — Worker cron (`*/30 * * * *`, `wrangler.jsonc`)
`scheduled()` runs 6 jobs in parallel via `Promise.allSettled` (one job's failure never blocks another):

1. **`runReminderCron`** (hours-submission nudge) — published shifts whose `submittableAfter` is 2–48h in the past with no actioned timesheet get a `timesheet-reminder` push, **repeating every 2 hours** (tier = `floor(hoursElapsed/2)*2`, tracked in `shifts.lastReminderHours`, only advances the tier once past it — no duplicate sends within the same 2h window).
2. **`runShiftStartReminderCron`** — exactly two pre-shift pushes per published shift: **6 hours before** (`shift-start-6h`, ±30-min window tolerance, dedup `startReminder6hSent`) and **30 minutes before** (`shift-start-30m`, dedup `startReminder30mSent`). If a shift is published <6h before it starts, the 6h reminder is skipped and only the 30-min one fires. Optimistic-concurrency Firestore commit (`currentDocument.updateTime`) prevents double-send from overlapping cron ticks.
3. **`runRejectedTimesheetReminderCron`** — escalating nag every **24 hours**, **capped at 3 reminders**, dedup via `rejectionReminderCount` + optimistic-concurrency commit; skips timesheets with no `rejectedAt` (predates the field); stops automatically once the timesheet leaves `rejected` status (falls out of the query).
4. **`runAvailabilityReminderCron`** — fires only inside the **Friday 17:00–17:29 Adelaide** half-hour window, one `availability-reminder` push per active staff member who hasn't set next week's availability yet, dedup via `lastAvailabilityReminderWeek`.
5. **`runLateOrOvertimeSweepCron`** — **iOS-only signal** (depends on `shift_attendance`, "zero references to `shift_attendance` anywhere in `src/`" per the Worker's own code comment): "Running late" (rostered start ≥15 min past with no clock-in, within a 2h window) and "Overtime started" (rostered end passed by up to 4h with a clock-in but no clock-out) manager alerts. **PWA-only staff will never trigger either of these** since they don't clock in/out.
6. **`runAccountDeletionPurgeCron`** — day-30 Auth purge for any `deletion.status==='approved'` account whose `cancelDeadlineAt` has passed (§2).

### In-app notifications
- The Task-message inbox on `StaffHome` (bell icon with unread badge) is the only in-app (non-push) notification surface. No general "notification center" beyond this.
- `Message.type` union in code is `'task'` only; `announcement` is a reserved-but-unimplemented type (no compose UI, no listener support, no push event).

---

## 12. Offline Behavior, Caching, Service Worker / PWA Update Mechanism

- **Firestore offline**: `persistentLocalCache({tabManager: persistentMultipleTabManager()})` — previously loaded shifts/timesheets/users/wages remain available offline and sync automatically on reconnect; multi-tab-safe.
- **Offline indicators**: `useOnlineStatus()` hook (`window` `online`/`offline` events). Manager layout: floating solid amber pill, positioned above the mobile bottom nav or bottom-left on desktop, "Offline Mode — Changes sync automatically". Staff layout: sticky amber banner below the header, with page-specific copy (roster cached but availability needs a connection, vs. roster & timesheets cached + sync-when-online elsewhere). Excel-import/rollback controls (where present) are network-gated.
- **PWA config**: `vite-plugin-pwa`, `registerType: 'prompt'` — a new SW enters a **waiting** state; the user is prompted, never force-updated silently. Manifest: name "Roster", `display: standalone`, `orientation: portrait`, theme `#0f172a`, icons at 64/192/512 + a 512 maskable variant.
- **Update detection** (`ReloadPrompt.tsx`): two triggers — a **5-minute poll** (`setInterval`) and an immediate check on `visibilitychange → visible`. Each check does a cache-busted `fetch(swUrl, {cache:'no-store'})`; a 200 response triggers `registration.update()`.
- **Update banner**: floating, bottom-right desktop / full-width-above-bottom-nav mobile; "Refresh App" calls `updateServiceWorker(true)` **and** re-runs push-registration healing (`ensureNotificationRegistrationThrottled(true)`, bypassing the 12h throttle) **and** a hard `setTimeout(() => window.location.reload(), 250)` fallback (iOS standalone PWAs sometimes don't fire `controllerchange` reliably). Dismiss/×/Escape all just hide the banner (update stays pending, applied next check).
- **Critical cache-header requirement**: `/sw.js` and `/firebase-messaging-sw.js` **must** be served `Cache-Control: no-store, no-cache, must-revalidate, max-age=0` (`public/_headers`) — a stale `sw.js` stalls updates forever; a stale `firebase-messaging-sw.js` silently breaks push delivery and, on Safari/macOS, an "invisible" out-of-date push can cause the browser to **revoke notification permission entirely**. `firebase-messaging-sw.js` is also excluded from the Workbox precache manifest (`globIgnores`) so the app-shell SW can never itself serve a stale copy.
- **Workbox precache**: all static build assets (`**/*.{js,css,html,ico,png,svg}`); Google Fonts cached `CacheFirst` for 1 year. Marketing pages (`/home`, `/contact`, `/privacy`, `/terms`) are explicitly excluded from precache and from the SPA `navigateFallback` (`navigateFallbackDenylist`) — otherwise the SW would serve the app shell for those static pages after the first visit, a regression that shipped once and was fixed.
- **Interaction with other visibility-driven timers**: PWA update check, manager session lock (5 min), and device-auth re-lock (2 min) all independently listen to `visibilitychange` — they don't interfere but can all fire on the same tab-foreground event; lock overlays block interaction with the update banner underneath until dismissed.

---

## 13. Admin / Super-Admin Portal

**Not implemented in the shipped PWA.** `docs/SUPER_ADMIN_PORTAL_PRD.md` (root `Roster/docs/`, dated July 2026, "Draft for engineering review") is a comprehensive design document proposing to convert Rosterra into a multi-tenant SaaS platform, triggered specifically by the gap that **a manager/business-owner cannot delete their own account** in the current single-tenant model (there's no role above manager to approve it). Key proposed elements, for context only — none of this exists in `src/` or `worker/` today:

- A `businessId`-per-document tenancy model enforced via Firebase custom claims + Firestore rules + Worker-side assertion (three-layer isolation).
- A separate React SPA at `admin.sura-roster.com` talking to a new `roster-admin` Worker / `/admin/api/*` route group.
- Six platform-admin roles (Super Admin, Platform Administrator, Finance Admin, Support Admin, Operations Admin, Read-only Admin) with a detailed permission matrix.
- Full SaaS scaffolding: subscription plans (Trial/Starter/Growth/Business), Stripe billing, feature flags, audit logs, impersonation-with-audit, backup/recovery, system monitoring, 20+ new Firestore collections (`businesses`, `plans`, `platform_admins`, `subscription_events`, `platform_audit_logs`, etc.).
- Explicitly **out of scope for this PRD's phase 1**: any change to staff-facing app features.

**Relevance to Android parity**: none directly — the Android app should mirror the *current single-tenant PWA*, not this future-state design. The one concrete artifact already shipped from this line of thinking is the ATO-safe account-deletion lifecycle (§2), whose 30-day-grace/staff-only-deletion design explicitly exists *because* manager self-deletion is deferred to this future Super Admin console.

---

## 14. Other Notable Business Rules, Integrations, Permission Checks, Animations/Haptics, Edge Cases

**Haptics** (`src/lib/haptics.ts`, `haptics.test.ts`): `triggerHaptic(pattern)` is a **deliberate no-op on iOS** (`isIOSLike()` — UA contains iPhone/iPad, or `platform==='MacIntel' && maxTouchPoints>0` for iPadOS-as-Mac) even when `navigator.vibrate` exists, because iOS Safari doesn't honor it reliably; safe no-op if `navigator.vibrate` is entirely absent; named semantic patterns `light=10ms, medium=20ms, heavy=35ms, success=[15,40,20], warning=[20,50,20], error=[30,40,30], selection=8ms`; used pervasively on nav taps, toggles, form submits, week navigation, lock/unlock.

**Accessibility**: `Modal` component provides a real focus trap (Tab/Shift+Tab cycling), Escape-to-close, focus restoration to the previously-focused element, `role="dialog" aria-modal aria-labelledby`; skip-to-content links in both layouts; `aria-current="date"` / `aria-label` on week-picker days; `sr-only` labels on filter selects; 44px minimum touch targets on primary mobile actions (explicit `min-h-[44px]`); keyboard-activatable roster cards (`role="button"` + Enter/Space via `handleActivateKey`).

**Toast positioning**: responsive — mobile `bottom-center` (above bottom nav, safe-area aware), desktop `top-center` (below safe-area inset), 3s duration, custom success/error icon colors.

**Theme**: light/dark via `.dark` class, persisted to `localStorage['sura_theme']` + synced to the user's Firestore doc; a blocking inline `<script>` in `index.html` reads localStorage before first paint to prevent flash-of-wrong-theme; `<meta name="theme-color">` updated to match.

**Address autocomplete**: Google Places `AutocompleteService` (AU-restricted) if `VITE_GOOGLE_MAPS_API_KEY` set, else free OpenStreetMap Nominatim fallback automatically — no app behavior difference, just different geocoding quality; 300ms debounce, 3-char minimum; `preserveHouseNumber()` heuristic keeps a user-typed house/unit number the geocoder's suggestion might drop.

**ICS calendar export** (`downloadICS`/`generateShiftICS`): RFC5545 `VCALENDAR`, one `VALARM` **1 hour before** shift start, delivery via Web Share API (native share sheet) → iOS `data:` URL fallback → Blob download link, in that priority order.

**Cross-tab logout**: `authStore` broadcasts logout via a `localStorage` key (`roster_auth_logout`) so all open tabs sign out together.

**Analytics**: Firebase Analytics (GA4) used only in the signed-in web app (per the privacy policy, explicitly **not** used by the native iOS app) — page views, login/logout, timesheet submit/approve/reject, shifts-published-count. All calls wrapped try/catch so ad-blockers can't break navigation.

**Data-loading windows** (Firestore read-cost/perf guardrails): manager listens to shifts ±(3 months historical / 2 months future, dynamically grown by `ensureHistoryLoaded`), timesheets 6 months historical; staff listens to shifts **-28 to +56 days**, timesheets with a **5-year** `submittedAt` cutoff; messages bounded to the last **30 days**.

**Legacy shift backfill**: shifts created before the `submittableAfter`/`shiftStartAt` fields existed are backfilled up to **50 per snapshot** on a manager's `onSnapshot` load, gated to run **once per device** via a `localStorage` flag.

**Firestore batch limits honored throughout**: 500-doc batches for placeholder-migration shift reassignment, cascade-delete of a shift's timesheets, and message fan-out.

**Casual/permanent wage display**: staff list and Wage/View-Staff modals consistently show "(+25% Casual)" / "(incl. +25% loading)" suffixes wherever a casual loaded rate is displayed, matching the 1.25× formula.

**Known code discrepancies worth flagging before Android build** (surfaced by direct code reading, not assumption):
1. "Never Expire" message option actually sets `expiresAt` to **+1 year**, not a literal far-future sentinel — yet the Edit/badge UI gate checks for an `expiresAt` starting with `'9999-'`, which no current send can produce. The edit affordance for "never-expiring" messages may be effectively unreachable; confirm intended behavior before porting.
2. `parseRosterExcel()` (Excel→shifts import) is fully implemented and tested but has no UI caller anywhere in `src/` — treat bulk roster import as unimplemented, not "existing but hard to find."
3. Two coexisting wage-rate resolution paths on `User` (legacy single `wageRateId`/`hourlyRate` vs. newer `wageRateIds[]`) — Android should implement the newer multi-rate path as source of truth; the legacy path exists only for backward compatibility with older data.

---

## 15. Flat Feature List — Android Parity Checklist

### Auth & Session
- [ ] Email/password sign-in (Firebase Auth) with friendly error mapping (wrong password, too-many-requests, disabled account)
- [ ] Manager bootstrap gate (active Firestore profile required, not just Firebase Auth)
- [ ] Forced first-login password change (`mustChangePassword`) with live requirement checklist (8+ chars, uppercase, number)
- [ ] Manager: Add staff (creates real Firebase Auth account via trusted server endpoint)
- [ ] Manager: Complete-setup flow for placeholder/import-created accounts
- [ ] Manager: Reset staff password (rate-limited, temp password, forces next-login change)
- [ ] Manager: Change staff email (requires manager's own password re-verification, auto-rollback on failure)
- [ ] Manager: Lock / Unlock / Deactivate staff account
- [ ] Staff & manager: voluntary change-password flow
- [ ] Manager session inactivity lock (5 min idle or ≥5 min backgrounded, password re-entry, progressive cooldown after 3 fails, manager-only)
- [ ] Optional device biometric/PIN app lock (both roles; 2-minute background re-lock threshold; local-only, not server-verified)
- [ ] Cross-tab/cross-device logout broadcast
- [ ] ATO-safe staff account deletion: staff request → manager approve/decline → 30-day locked grace period with manager-cancel → scheduled Auth purge retaining identity/payroll records
- [ ] Manager-only TFN field with AU checksum validation, masked display, never staff-visible

### Data & Sync
- [ ] Firestore offline persistence with automatic sync-on-reconnect
- [ ] Real-time listeners scoped by role (manager: broad; staff: own docs only) with sensible time-window bounds
- [ ] Offline indicator banners (role-specific copy)
- [ ] Server-authoritative time checks for security-sensitive windows (availability lock, timesheet submittability) — never trust device clock

### Roster / Scheduling (Manager)
- [ ] Create/edit/delete shifts (staff, date, time, break, location, department, notes, status)
- [ ] Week / Day / Staff roster views with staff & status filters
- [ ] Draft → Published → Completed/Cancelled shift lifecycle
- [ ] Publish workflow with optional simultaneous availability-week-lock ("Publish & Lock")
- [ ] Drag-and-drop shift Move (un-publishes) and Copy (creates new draft), with same-staff overlap guard and published-current-week drag protection
- [ ] "Copy Last Week" with duplicate-shift dedup
- [ ] Live availability-conflict indicator when assigning/editing a shift
- [ ] Rostered-hours / labour-cost / superannuation forecast KPIs (casual 1.25× loading, per-staff super %, weekday/weekend wage-rate selection)
- [ ] Missing-wage-rate warning list
- [ ] Roster Excel template download (blank pre-formatted weekly template)
- [ ] (Decision needed) Bulk Excel roster import — parser exists, no UI in PWA to copy from

### Timesheets / Attendance (both roles)
- [ ] Staff: Submit worked hours (actual start/end/break, notes) only after shift end, with rostered-vs-worked diff warning
- [ ] Staff: Resubmit after rejection (clears rejection reason)
- [ ] Staff: Report absence (optional reason) only after shift end; undo before manager confirmation
- [ ] Manager: Approve / Reject (reason required) / Edit-while-pending / Edit-after-approval / Confirm absence / Undo absence confirmation
- [ ] Manager: Bulk-approve near-exact-match timesheets (≤0.5h variance)
- [ ] Manager: Missing-submissions view (published+ended shifts with no actioned timesheet, active staff only)
- [ ] Exact hours rounding (2 decimals), overnight-shift handling, break clamping (0–90, step 5)

### Availability
- [ ] Staff weekly availability editor (available/unavailable, all-day vs custom hours, per weekday)
- [ ] Server-time-enforced lock on current/past weeks (client never decides this alone)
- [ ] Manager-publish-triggered lock on a specific future week
- [ ] "Set as default/recurring" propagation to future weeks; reset-this-week / reset-following-weeks
- [ ] Manager read-only availability grid across all staff

### Staff Management (Manager)
- [ ] Searchable/filterable staff directory with this-week-hours column
- [ ] Add/View/Edit staff profile (name, contact, DOB, address, employment type, wage classifications, hourly rate, super %, TFN, notes, emergency contact)
- [ ] Manager "Request profile update" (forces staff re-confirmation gate)
- [ ] Staff-side mandatory profile-completion gate (blocks app until DOB/address/phone/email present)

### Wage Management
- [ ] AU Award (MA000089) system rate seed + custom rate CRUD (label, base rate, employment type, rate type, age group)
- [ ] Multi-classification assignment per staff member
- [ ] Single/bulk/clear-all delete with typed confirmation phrases

### Reporting & Analytics
- [ ] Manager dashboard KPIs (rostered today, rostered/approved hours this week, pending count, missing count) + hours trend chart
- [ ] Weekly/Monthly/Yearly timesheet reports with staff/status filters, chart, CSV/Excel/PDF export
- [ ] Wage & Timesheet PDF report (weekday/weekend split, edited/rejected/pending/absent counts, super, grand totals)
- [ ] Staff tenure & hours analytics (service length, weekly run-rate, drill-down history, Excel/PDF export)

### Messaging
- [ ] Manager compose Task message (500-char cap, expiry presets incl. long-lived "never"), multi-recipient
- [ ] Staff inbox (unread badge, mark-as-read, expiry filtering)
- [ ] Manager sent-history with read-receipt tracking and inline edit for open broadcasts

### Notifications
- [ ] FCM push opt-in with platform/support detection (Android-specific: standard FCM, no iOS-style install-first gate needed)
- [ ] Token registration/healing lifecycle (re-heal on login, on app foreground with throttle, on app update)
- [ ] Server-resolved recipients for: roster published, shift changed/cancelled, timesheet submitted/absent/approved/rejected, task message, availability updated
- [ ] Cron-driven reminders: hours-submission nag (every 2h after shift end), pre-shift reminders (6h and 30m before start, each exactly once), rejected-timesheet escalation (24h cadence, max 3), Friday-5pm availability nag
- [ ] Notification-category preference gating (fail-open; no settings UI yet exists but the backend already supports it — good candidate for Android to be first to expose)
- [ ] Tap-to-navigate deep linking into the correct in-app screen

### PWA-equivalent App Behaviors
- [ ] In-app update/refresh prompt equivalent (Android: standard Play Store auto-update, but consider an in-app "what's new"/force-update gate for parity with the deliberate non-silent update UX)
- [ ] Light/dark theme with persisted preference synced across devices
- [ ] Install/onboarding prompt (Android: less relevant, native install)

### Cross-Cutting
- [ ] Audit log (manager-visible, last 100/20 depending on screen) covering account, roster, timesheet, wage, and password actions
- [ ] Danger-zone data clearing (production-gated, typed confirmation + manager password, staff-clearing always routes through the 30-day ATO-safe purge, never a hard delete)
- [ ] Haptic feedback on key interactions (respect platform conventions — Android doesn't need the iOS no-op)
- [ ] Accessibility: focus management in dialogs, keyboard activation on interactive cards, adequate touch targets, screen-reader labels
- [ ] ICS calendar export per shift (1-hour-before reminder)
- [ ] Address autocomplete for staff profiles (Google Places or equivalent, AU-scoped)
