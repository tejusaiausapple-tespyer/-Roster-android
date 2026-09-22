# Rosterra Android Staff Experience — Product Redesign Plan

Status: working baseline  
Scope: Android, Staff role only  
Reference: current iOS Staff implementation, reviewed 2026-09-16  
Design target: Android-native, production-ready; not a visual port of SwiftUI

## 1. Product boundary

The Android application is a Staff product. It must not contain a Manager shell, Manager routes,
Manager screens, Manager repositories, Manager-only API operations, or Manager permissions in the
compiled artifact. Manager-created information that Staff legitimately consumes remains in scope:
published shifts, availability locks, task instructions, review notes, messages, and issued
payslips.

The boundary is enforced at five levels:

1. Authentication rejects any profile whose role is not `staff` and signs it out.
2. Root routing has no Manager destination.
3. Navigation exposes only Staff destinations and deep links.
4. Manager source sets and manager-only repositories are excluded from Android builds, then
   removed after migration verification.
5. Release tests inspect the APK/AAB for manager routes, screens, strings, and endpoints.

The iOS app defines product behaviour and business rules. Material 3, Android system conventions,
adaptive layouts, predictive back, permission patterns, and accessibility define the Android UX.

## 2. Current iOS Staff experience — verified inventory

The current iOS implementation has moved beyond older documentation. Its active five-tab Staff
shell is:

| Tab | Purpose | Secondary destinations |
|---|---|---|
| Home | Today, clocking, jobs, tasks, hours, upcoming shifts | Daily Jobs, Tasks, messages/alerts |
| Roster | Week schedule and staff actions | Submit/Update/Resubmit Hours, Report/Undo Absence, History |
| Payslips | Month-scoped issued payslips | Payslip detail, PDF preview/share/print |
| Availability | Weekly availability planning | Day editor, recurring/reset actions |
| Account | Profile, preferences, security, support | Edit profile/email/password, notifications, legal, deletion |

Important audit correction: older Android documents describe Tasks as a tab and Payslips as an
Account child. The current iOS source uses Payslips as tab three and opens Tasks from Home. The
redesign follows the current implementation unless usability testing disproves it.

Core Staff journeys:

- Sign in → required password/profile gates → optional device authentication → Home.
- See today's shift → clock in/out with location feedback → submit actual hours.
- Review a week → add shift to calendar → submit/resubmit hours or report/undo absence.
- Complete shift-linked Daily Jobs and scheduled Tasks, including camera-only evidence.
- Set availability for future editable weeks; understand past or employer-locked weeks.
- Review approved/pending/rejected/absent history and employer notes.
- Review issued payslips by month and open/share/print the PDF.
- Maintain profile, notification channels, appearance, biometrics, password, and deletion request.

## 3. Target Android information architecture

### Compact width

Use a Material 3 `NavigationBar` with five destinations: Home, Roster, Payslips, Availability,
Account. Each destination owns its back stack and restores scroll/filter state. Full-screen
destinations push within the selected tab; short focused edits use modal bottom sheets; critical
confirmation uses dialogs.

Tasks and Daily Jobs are contextual work launched from Home. History is launched from Roster.
Notification deep links land on the owning destination and open the pending action exactly once.

### Medium and expanded width

Use `NavigationRail` from 600dp and a persistent rail/supporting pane where useful from 840dp.
Do not stretch phone cards edge-to-edge across tablets. Constrain readable content to 720dp and
use list-detail layouts for Roster/History/Payslips where space permits.

### Navigation rules

- System back dismisses sheet/dialog, then pops detail, then follows tab history, then exits.
- Reselecting a current tab returns it to its root and scrolls to top.
- A visible title and destination icon remain stable; avoid decorative floating title pills where
  they fight Android app-bar behaviour.
- Preserve destination and filters across rotation and process recreation with saved state.
- Deep links never route to Manager or generic placeholder destinations.

## 4. Screen and state specification

Every screen ships with loading, content, empty, recoverable-error, offline/stale, success, and
permission-denied states where applicable.

### Authentication

- Branded launch/loading surface using the current icon and wordmark.
- Email and password sign-in; no registration.
- Inline validation, password visibility control, IME Next/Done, forgot-password confirmation.
- Staff-only rejection message for Manager credentials.
- Forced password change, profile completion, biometric quick login, two-minute app relock.
- Avoid loading dead ends: missing/unreadable profiles surface a timed retry and sign-out action.

### Home

- Greeting/date and notification/job status.
- Today's shift hero with location, time, status, calendar action, and clock controls.
- Primary `Action required` section before informational content.
- Daily Jobs and Tasks progress cards with direct continuation.
- Approved-hours summary with clear period labels.
- Next three shifts and `View roster` action.
- Day-off and no-upcoming-shifts variants.

### Roster and History

- Monday-first week control with Today shortcut and locked-week state.
- Group shifts by day; cards show time, duration, location, notes, and status.
- Prefer explicit overflow/action affordances; swipe may accelerate but must not be the only path.
- Submit form shows rostered versus actual time, break, calculated paid hours, warnings, notes,
  validation, and review/resubmission context.
- Absence flow explains timing and reversibility before confirmation.
- History supports status/time filters and search, with active-filter count and clear-all.

### Tasks and Daily Jobs

- Separate mental models: Tasks are scheduled instructions; Daily Jobs belong to a shift/date.
- Today-first progress, priority/due time, assignee applicability, redo reason, and completion owner.
- Camera-only evidence, up to four images, compression/upload progress, retry, optional note.
- Completion is idempotent; offline behaviour is explicit and never implies an uploaded photo when
  upload has failed.

### Payslips

- Month selector with cached/stale status and pull-to-refresh.
- Only submitted/archived Staff-owned records.
- Strong pay-period, issued-date, net/gross/status hierarchy.
- Native PDF preview, share, print, download failure and retry states.

### Availability

- Week range −2 to +12; current/past read-only and employer-locked future weeks explained.
- Seven-day summary with availability, all-day, or time range.
- Day editor bottom sheet: available → all day → start/end.
- Unsaved-change protection, save progress, success feedback, network-required error.
- Recurring/default and reset actions use scoped confirmation copy listing affected weeks.

### Account

- Staff identity/profile only; no Business or Management sections.
- Profile and employment fields distinguish editable from employer-provided values.
- Payslips removed from Account after becoming a primary tab.
- Notification channel settings deep-link to Android system controls.
- Appearance offers System, Light, Dark—not a misleading single dark-mode switch.
- Biometrics, password, legal/support, account-deletion lifecycle, and sign out.

## 5. Design system

Use semantic tokens, never screen-local brand hex values.

### Foundations

- Colour: brand indigo primary; emerald success; amber attention; red destructive/error; neutral
  surfaces with AA contrast in light and dark themes.
- Type: Android system font and Material type scale; minimum 12sp supporting text, 16sp body/form
  input, no fixed-height text containers that clip at 200% font scale.
- Spacing: 4dp base; common steps 4/8/12/16/24/32; 16dp compact gutters, 24dp expanded gutters.
- Shape: 10dp controls, 14dp cards, 20dp prominent containers, full-pill status chips only.
- Touch: minimum 48×48dp; destructive and primary actions never adjacent without separation.
- Icons: Material Symbols semantics; labelled when meaning is not universal.
- Motion: 150–300ms purposeful transitions; respect animator/reduce-motion settings.

### Component set

App bar, adaptive navigation, primary/secondary/tonal/destructive buttons, text fields, password
field, search, filter chips, status chip, shift card, action-required card, task/job row, payslip
row, availability day row, metric tile, banner, snackbar, skeleton, empty state, error state,
permission rationale, confirmation dialog, bottom sheet, date/time picker, offline indicator.

Component documentation must include anatomy, states, tokens, accessibility semantics, and examples
of correct/incorrect use.

## 6. Branding and icon rollout

The current iOS “R” mark is the approved canonical identity. Android now uses its 1024px master
for the in-app mark and launcher artwork. The branding phase completes the remaining generated
and validation work:

- adaptive foreground/background layers with safe-zone verification;
- monochrome themed icon;
- round and legacy launchers;
- splash icon and in-app wordmark treatment;
- light/dark previews on common OEM masks.

No asset is promoted merely because it is newest by filename. Product approval selects the
canonical master first; generated assets receive pixel/safe-area QA.

## 7. Phased execution

### Phase 0 — Staff-only boundary

Remove Manager routes from the root state machine, reject non-Staff login/restored sessions,
remove Manager account sections, exclude Manager UI/repositories from build variants, and add
artifact inspection tests. Exit: a Manager account cannot enter an authenticated Android surface
and no Manager class is packaged.

### Phase 1 — Audit and journey baseline

Complete iOS source inventory, reconcile stale docs, map Android parity/gaps, catalogue backend
contracts and permissions, and record analytics-free task success benchmarks. Exit: signed-off
screen/state matrix and no unresolved role ambiguity.

### Phase 2 — IA and low-fidelity prototype

Prototype compact and expanded navigation plus the three riskiest flows: clock-to-submit,
photo-task completion, and availability save/lock. Test with 5–7 Staff participants. Exit: ≥80%
unassisted completion for each critical flow and resolved navigation model.

### Phase 3 — Brand and design-system foundation

Approve icon master, implement tokens and core components, add preview catalog and accessibility
checks. Exit: all new screens use tokens/components; light/dark and 200% font-scale pass.

### Phase 4 — Authentication and shell

Build launch/login/gates, adaptive navigation, independent tab state, deep links, offline banner,
and predictive back. Exit: auth and navigation test matrix passes on compact/medium/expanded.

### Phase 5 — Daily work

Redesign Home, Roster, clocking, hours, absence, History, Tasks, and Daily Jobs. Exit: a Staff
tester completes an entire shift lifecycle with clear recovery from GPS/network/camera failures.

### Phase 6 — Planning and pay

Redesign Availability and Payslips, including cache/offline clarity and PDF actions. Exit: locked
weeks cannot be edited; issued payslips work offline after download.

### Phase 7 — Account and settings

Redesign profile, appearance, notification channels, biometrics, credentials, legal/support,
deletion, and sign out. Exit: all writes match the Staff allow-list and Manager UI is absent.

### Phase 8 — Edge cases and accessibility

Cover empty/first-use/loading/error/offline/permission/retry/success states, overnight shifts,
DST, large text, TalkBack, switch access, RTL resilience, and interrupted uploads. Exit: WCAG AA
contrast and the accessibility/manual edge-case matrix pass.

### Phase 9 — Validation and release specification

Usability validation, screenshot regression, Compose UI tests, Firebase-rule/emulator tests,
performance/battery review, Crashlytics verification, design QA, and signed handoff specs. Exit:
zero critical/high defects and product/design/engineering approval.

## 8. Delivery artifacts

- Verified iOS Staff audit and Android gap matrix.
- Staff-only sitemap, journey maps, and state matrix.
- Compact/medium/expanded wireframes and interactive prototype.
- Brand package and Compose design-system catalog.
- Final screen designs for light/dark modes and all operational states.
- Redlines/tokens/component contracts and interaction/deep-link specifications.
- Accessibility annotations and content/copy inventory.
- QA matrices: role boundary, device sizes, permissions, offline, notifications, DST, and release.

## 9. Definition of done

The redesign is complete only when:

- Manager login is rejected and Manager code is absent from the release artifact.
- Every Staff journey above is implemented; no placeholder or `Coming soon` remains.
- All screens have intentional loading/empty/error/offline/success behaviour.
- Compact, foldable/medium, and tablet layouts are verified.
- TalkBack order/actions, 200% font scale, contrast, touch targets, and motion settings pass.
- Business-rule and Firestore security tests pass, including Staff ownership boundaries.
- Critical flows pass usability validation and release QA on at least one low/mid/high Android
  device profile.
- Branding uses one approved master across launcher, splash, login, and store collateral.

## 10. Immediate implementation decisions

The first implementation slice is Phase 0 because role leakage invalidates every later design
review. Next is an executable IA migration from Tasks-tab/Payslips-child to the current iOS model:
Payslips as the third primary destination, Tasks and Daily Jobs from Home. That migration must
preserve notification deep links and give Tasks a visible Home continuation before removing its
tab.
