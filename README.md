<<<<<<< HEAD
# LaundrySlot — Smart Hostel Laundry Booking Application

**LaundrySlot** is a production-ready Android application built for hostel residents and students to check real-time washing machine and dryer availability, start instant wash cycles, reserve upcoming time slots with cross-device conflict protection, track active wash countdowns, and report machine faults—eliminating queues and laundry room clashes.

---

## Key Features

### Live Machine Availability Dashboard
- **Status Summary Cards**: Interactive overview cards (`Available`, `In Use`, `Maintenance`) with tap-to-filter toggles and live counts.
- **Search & Multi-Criteria Filters**: Debounced search by machine name, ID, or block alongside dynamic Block, Floor, Machine Type (`Washers` / `Dryers`), and `Available Only` filter chips.
- **Machine Cards**: Material 3 cards showing status badges, capacity, queue/wait info, primary **Reserve Slot** action (pre-selecting the machine in the booking flow), secondary **Start Now** action, and an overflow menu (`View Details`, `Report Fault`).
- **Offline & Loading Awareness**: Live connectivity monitoring (`NetworkMonitor`) with offline banner, shimmer loading placeholders, pull-to-refresh, and retry/empty states.
- **Hero Active Wash Tracker**: Persistent animated progress card with countdown timer and stage indicator whenever the hosteller has an active wash.

### Interactive Machine & Quick Start Sheets
- **Machine Details Bottom Sheet**: Displays machine specs, live vs. cached availability indicator, queue length, next available slot, supported wash/dry cycles, and an expandable chip-based fault reporting flow.
- **Quick Start Bottom Sheet**: Cycle picker supporting **Quick Wash (30m)**, **Standard Wash (45m)**, **Heavy Bedding (60m)**, and **Turbo Dry (40m)** with machine-type cycle compatibility checks and atomic Firestore + Room transaction validation to prevent double-starts across devices.

### Smart Slot Reservation & Multi-User Conflict Protection
- **Date & Machine Selection**: Reserve slots across Today, Tomorrow, and upcoming days with machine preselection from the dashboard.
- **Visual Time Slot Grid**: 1-hour time slots (`06:00 AM` – `11:00 PM`) with overlap checking and real-time `machine_slot_locks` conflict prevention via Firestore transactions.
- **Cycle Customization & Digital Pass**: Supports cycle selection and generates unique booking pass tokens (e.g., `#LS-8941`).

### My Slots & Cross-Device Recovery
- **Active Wash Controls**: Monitor remaining wash time, mark laundry as collected, or cancel upcoming bookings.
- **Device Replacement / Reinstall Recovery**: Signing into an existing account on a new device pulls the hosteller's remote bookings (`hostellers/{studentId}/bookings`), live machine states (`machines`), and today's slot locks (`machine_slot_locks`) into Room and reschedules upcoming reminders.

### Machine Breakdown & Issue Reporting
- **Hosteller Fault Reporting**: Report specific faults (e.g., drum stuck, drain blocked, power issue) from either the machine sheet overflow/details or the dedicated issue screen.
- **Protected Maintenance Transitions**: Submitting a fault report flags the machine as `OUT_OF_ORDER` and cancels interrupted active local cycles; `firestore.rules` prevents non-admin clients from clearing `OUT_OF_ORDER` back to `AVAILABLE`.

### Smart Reminders & Background Sync
- **Slot Reminder (`SlotReminderWorker`)**: WorkManager notification 15 minutes before a reserved slot begins.
- **Cycle Complete Alert (`LaundryCycleReminderWorker`)**: Notifies the hosteller when a wash or dry cycle finishes.
- **Offline Sync & Reboot Resilience**: `SyncWorker` synchronizes hosteller-scoped pending Room changes with Cloud Firestore using retry classification (`SyncErrorClassifier`), and `BootReceiver` restores scheduled reminders after device reboot.

### Security, Privacy & Localization
- **Firebase Auth + Encrypted Local Credentials**: `SessionManager` stores credential hashes and 4-digit MPIN using `EncryptedSharedPreferences` (with automatic corrupted-keystore recovery and no plaintext fallback) plus DataStore preferences (`android:allowBackup="false"`, excluded from cloud/device backup rules).
- **Hardened Firestore Rules (`firestore.rules`)**: Default-deny rules with `authUid`-owned `hostellers/{studentId}` profiles, strict schema validation, and machine spec protection.
- **Privacy-Safe Diagnostics**: Logs avoid sensitive fields (no passwords, MPINs, emails, room numbers, or tokens) and release builds strip `Log.v` / `Log.d` via R8 (`proguard-rules.pro`).
- **Teal Design System (`LaundrySlotTheme`)**: Material 3 palette anchored in Brand Teal (`#008080`) and supporting Mint, full Light & Dark mode support, adaptive launcher icon, and Android 12+ (`values-v31`) splash theme.
- **Multilingual Support**: Localized in English, Hindi (हिन्दी), Bengali (বাংলা), and Marathi (मराठी).

---

## Technical Architecture

LaundrySlot follows **MVVM + Clean Architecture** with a local-first single source of truth (`LaundryRepository`) backed by Room and synchronized with Firebase Firestore.

### Tech Stack
- **Language & Build**: Kotlin `2.1.0`, AGP `8.13.2`, `compileSdk 36`, `minSdk 24`, `targetSdk 36`, JVM 17
- **UI**: Jetpack Compose (`compose-bom 2024.09.00`), Material 3, Adaptive Edge-to-Edge (`adjustResize`)
- **Architecture & DI**: MVVM, Clean Architecture, Hilt `2.55`, KSP
- **Async & State**: Kotlin Coroutines, `StateFlow` / `SharedFlow`, lifecycle-aware `collectAsStateWithLifecycle`
- **Local Storage**: Room `2.7.0-alpha01` (v6 schema with `MIGRATION_5_6` indices and pre-populated hostel machines), DataStore Preferences, `androidx.security:security-crypto`
- **Remote Backend**: Firebase BOM `34.12.0` (Firebase Auth, Cloud Firestore, Analytics)
- **Navigation & Background Work**: Navigation Compose `2.8.5`, WorkManager `2.10.0`

### Project Structure
```text
D:/Sem 7/LaundrySlot/
├── firestore.rules     # Production Cloud Firestore security rules
├── app/
│   ├── build.gradle.kts # Debug & R8-minified release build configuration
│   ├── proguard-rules.pro
│   └── src/main/java/com/example/aasha/
│       ├── data/
│       │   ├── local/          # AppDatabase (v6), DAOs, Converters, SessionManager
│       │   ├── repository/     # LaundryRepository, AuthRepository, FirestoreRepository
│       │   ├── sync/           # SyncWorker (Room <-> Firestore)
│       │   └── worker/         # SlotReminderWorker, LaundryCycleReminderWorker, BootReceiver
│       ├── di/                 # DatabaseModule, FirebaseModule
│       ├── domain/model/       # WashingMachine, SlotBooking, SlotTimeHelper, MachineIssueReport, User, SyncStatus, LaundryNotice
│       ├── ui/
│       │   ├── components/     # LaundryButton, LaundryCard, MachineCard, MachineStatusBadge,
│       │   │                   # MachineSummaryCard, MachineFilterSection, MachineShimmer,
│       │   │                   # StateComponents, MachineDetailsBottomSheet, QuickStartBottomSheet
│       │   ├── navigation/     # Screen routes & BottomNavItem
│       │   ├── screens/        # MainScreen, dashboard/, booking/, activity/, issues/, profile/, login/, splash/
│       │   └── theme/          # Color.kt (#008080 Teal & Mint), Type.kt, Theme.kt (LaundrySlotTheme)
│       ├── util/               # NetworkMonitor, LocaleHelper
│       └── viewmodel/          # DashboardViewModel, BookingViewModel, MySlotsViewModel,
│                               # ProfileViewModel, LoginViewModel, SplashViewModel, SessionViewModel
```

---

## Build, Test & Production Deployment

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- JDK 17+
- Android SDK 36 (`minSdk 24`)
- Firebase `google-services.json` placed in `app/` (kept out of version control or scoped per environment)

### Build & Test Commands
1. Run local JVM unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
2. Build the debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
3. Build the R8-minified release APK (and configure signing via local `keystore.properties` — never commit `.jks`/`.keystore` files):
   ```bash
   ./gradlew :app:assembleRelease
   ```

### Firebase Production Checklist
Before rolling out to a university hostel:
1. **Deploy Security Rules**: Publish `firestore.rules` via Firebase CLI (`firebase deploy --only firestore:rules`).
2. **Seed `/machines` Collection**: Because `firestore.rules` restricts `/machines` creation and `OUT_OF_ORDER -> AVAILABLE` maintenance clearance to `admin == true` custom claims, run an admin script to populate `/machines/{machineId}` documents matching `AppDatabase.createSeedMachines()`.
3. **Enable Firebase App Check**: Enforce Play Integrity in the Firebase Console for Firestore and Authentication to prevent non-app API abuse.
4. **Sign-In on New Devices**: Hostellers who registered with a custom email (different from `{studentId}@hostel.edu`) should enter their **registered email address** when signing in on a brand-new device, unless a backend lookup Cloud Function (e.g., `resolveStudentEmail`) is added.
=======
# LaundrySlot
>>>>>>> 23a9f19ab0b23fd13fcb078a2736d1450a162f9d
