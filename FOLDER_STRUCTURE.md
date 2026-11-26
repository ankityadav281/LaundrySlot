# 📁 LaundrySlot Project Folder Structure & Architecture Guide

This document outlines the complete folder structure and architectural organization for the **LaundrySlot** Android project.

---

## 🏛️ High-Level Architectural Pattern (MVVM + Clean Architecture)

LaundrySlot follows the **Model-View-ViewModel (MVVM)** pattern combined with principles of **Clean Architecture**:

```text
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│   • Jetpack Compose Screens (`ui/screens/`)            │
│   • Reusable Components (`ui/components/`)             │
│   • Navigation & Routes (`ui/navigation/`)             │
│   • UI State & ViewModels (`viewmodel/`)               │
└───────────────────────────▲────────────────────────────┘
                            │ observes / triggers
┌───────────────────────────┴────────────────────────────┐
│                      Domain Layer                      │
│   • Domain Models & Helpers (`domain/model/`)          │
└───────────────────────────▲────────────────────────────┘
                            │ used by
┌───────────────────────────┴────────────────────────────┐
│                       Data Layer                       │
│   • Local Storage: Room & Encrypted Prefs (`local/`)   │
│   • Remote Backend: Firebase Auth & Firestore          │
│   • Repository Implementations (`repository/`)         │
│   • Background Sync & Workers (`worker/`, `sync/`)     │
└────────────────────────────────────────────────────────┘
```

---

## 🌳 Complete Directory Tree

```text
LaundrySlot/
├── .gitignore                             # Excludes build outputs, keystores (*.jks, *.keystore), local.properties
├── firestore.rules                        # Production Cloud Firestore security & validation rules
├── app/                                   # Main Android application module
│   ├── build.gradle.kts                   # Module-level Gradle build & R8 release minification config
│   ├── proguard-rules.pro                 # R8 / ProGuard keep rules & debug log stripping
│   └── src/
│       ├── androidTest/                   # Instrumented Android UI tests
│       │   └── java/com/example/aasha/
│       │       └── ExampleInstrumentedTest.kt
│       ├── test/                          # Local JVM Unit tests
│       │   └── java/com/example/aasha/
│       │       ├── BookingAndCycleStateTest.kt
│       │       ├── ExampleUnitTest.kt
│       │       ├── SlotTimeHelperTest.kt
│       │       ├── SyncAndSecurityRulesTest.kt
│       │       └── WashingMachineDomainTest.kt
│       └── main/
│           ├── AndroidManifest.xml        # App declaration, permissions, adjustResize & allowBackup=false
│           ├── ic_launcher-playstore.png  # App store graphic asset
│           ├── java/com/example/aasha/    # Kotlin source code root
│           │   ├── LaundrySlotApplication.kt # @HiltAndroidApp, WorkManager config, PII-safe crash handler
│           │   ├── MainActivity.kt        # Single activity host with edge-to-edge & locale handling
│           │   │
│           │   ├── data/                  # Data Layer (Single Source of Truth)
│           │   │   ├── local/             # Room SQLite DB (v6), DAOs, Converters, Encrypted Prefs
│           │   │   │   ├── AppDatabase.kt        # Room DB (v6, MIGRATION_5_6) & hostel machine seeding
│           │   │   │   ├── Converters.kt         # TypeConverters (Enums)
│           │   │   │   ├── MachineIssueDao.kt    # DAO for breakdown & maintenance reports
│           │   │   │   ├── SessionManager.kt     # EncryptedSharedPreferences MPIN/credentials & DataStore
│           │   │   │   ├── SlotBookingDao.kt     # DAO for time slot reservations & overlap queries
│           │   │   │   └── WashingMachineDao.kt  # DAO for washing machines, dryers & atomic transitions
│           │   │   ├── repository/        # Repository implementations
│           │   │   │   ├── AuthRepository.kt     # Firebase Authentication wrapper
│           │   │   │   ├── FirestoreRepository.kt# Hosteller profile queries & transactions
│           │   │   │   └── LaundryRepository.kt  # Unified repo for machines, slot locks, sync & recovery
│           │   │   ├── sync/              # Data synchronization
│           │   │   │   └── SyncWorker.kt         # WorkManager offline-to-cloud sync worker
│           │   │   └── worker/            # Background alert & reminder tasks
│           │   │       ├── BootReceiver.kt               # Re-schedules reminders on device reboot
│           │   │       ├── LaundryCycleReminderWorker.kt # Alerts when wash/dry cycle is complete
│           │   │       └── SlotReminderWorker.kt         # Alerts 15m before reserved slot begins
│           │   │
│           │   ├── di/                    # Dependency Injection (Dagger Hilt)
│           │   │   ├── DatabaseModule.kt  # Provides AppDatabase (with MIGRATION_5_6), DAOs, SessionManager
│           │   │   └── FirebaseModule.kt  # Provides FirebaseAuth & FirebaseFirestore
│           │   │
│           │   ├── domain/                # Domain Layer
│           │   │   └── model/             # Pure Kotlin data classes, enums & time helpers
│           │   │       ├── LaundryNotice.kt       # Broadcast announcements / notices
│           │   │       ├── MachineIssueReport.kt  # Breakdown report domain model
│           │   │       ├── SlotBooking.kt         # Reservation model & BookingStatus enum
│           │   │       ├── SlotTimeHelper.kt      # Deterministic slot windows, lock IDs & day bounds
│           │   │       ├── SyncStatus.kt          # SyncStatus enum (SYNCED, PENDING, ERROR)
│           │   │       ├── User.kt                # Hosteller profile (studentId, authUid, room, block, floor)
│           │   │       └── WashingMachine.kt      # Machine, MachineStatus, MachineType, CycleType
│           │   │
│           │   ├── ui/                    # Presentation Layer (Jetpack Compose)
│           │   │   ├── components/        # Reusable Design System UI Components
│           │   │   │   ├── ActiveWashProgressCard.kt    # Animated hero wash cycle tracker
│           │   │   │   ├── LaundryButton.kt             # Branded primary/secondary action buttons
│           │   │   │   ├── LaundryCard.kt               # Elevated surface cards (12dp radius)
│           │   │   │   ├── LaundrySlotCard.kt           # Interactive time slot selection item
│           │   │   │   ├── LaundryTextField.kt          # Input fields with validation & styling
│           │   │   │   ├── MachineCard.kt               # Machine list item card with quick actions
│           │   │   │   ├── MachineDetailsBottomSheet.kt # Specs, queue, next slot & fault report sheet
│           │   │   │   ├── MachineFilterSection.kt      # Search bar & Block/Floor/Type filter chips
│           │   │   │   ├── MachineShimmer.kt            # Shimmer loading placeholders
│           │   │   │   ├── MachineStatusBadge.kt        # Status badges (Available, In Use, Maintenance)
│           │   │   │   ├── MachineSummaryCard.kt        # Summary counts card with tap-to-filter
│           │   │   │   ├── QuickStartBottomSheet.kt     # Cycle picker & Start Now confirmation sheet
│           │   │   │   └── StateComponents.kt           # Offline banner, empty & error state cards
│           │   │   ├── navigation/        # Compose Navigation Routing
│           │   │   │   ├── BottomNavItem.kt       # Bottom navigation bar items & icons
│           │   │   └── Screen.kt              # App route definitions & argument passing
│           │   │   ├── screens/           # Feature-based Compose UI Screens
│           │   │   │   ├── MainScreen.kt          # Root Scaffold, NavHost & Bottom Navigation
│           │   │   │   ├── activity/              # My Laundry Activity tab
│           │   │   │   │   └── MySlotsScreen.kt       # Active countdowns & historical washes
│           │   │   │   ├── booking/               # Slot Reservation tab
│           │   │   │   │   └── BookingScreen.kt       # Date picker, slot grid & booking pass
│           │   │   │   ├── dashboard/             # Machine Availability tab
│           │   │   │   │   └── DashboardScreen.kt     # Filterable machine list, pull-to-refresh & sheets
│           │   │   │   ├── issues/                # Maintenance Breakdown Reporting
│           │   │   │   │   └── ReportIssueScreen.kt   # Issue reporting form & auto-flagging
│           │   │   │   ├── login/                 # Authentication Flow
│           │   │   │   │   └── LoginScreen.kt         # Student ID/Email login, signup & MPIN unlock
│           │   │   │   ├── profile/               # Hosteller Profile tab
│           │   │   │   │   └── ProfileScreen.kt       # Hosteller room info, stats, sync & preferences
│           │   │   │   └── splash/                # Startup Flow
│           │   │   │       └── SplashScreen.kt        # Animated splash & auth routing
│           │   │   └── theme/             # Material 3 Design System Styling
│           │   │       ├── Color.kt               # Brand Teal (#008080), Mint & Status palette
│           │   │       ├── Theme.kt               # Light/Dark LaundrySlotTheme configuration
│           │   │       └── Type.kt                # Typography scale
│           │   │
│           │   ├── util/                  # Utilities & Helpers
│           │   │   ├── LocaleHelper.kt    # Multi-language locale switching runtime helper
│           │   │   └── NetworkMonitor.kt  # Reactive ConnectivityManager online/offline monitor
│           │   │
│           │   └── viewmodel/             # Jetpack ViewModels (State Management)
│           │       ├── BookingViewModel.kt        # Slot selection, date filters & atomic confirmation
│           │       ├── DashboardViewModel.kt      # Machine status feeds, filters & active wash tracker
│           │       ├── LoginViewModel.kt          # Firebase Auth, Student ID/Email & MPIN validation
│           │       ├── MySlotsViewModel.kt        # Active washes & past booking history state
│           │       ├── ProfileViewModel.kt        # Hosteller room details, manual sync & counters
│           │       ├── SessionViewModel.kt        # App-wide session & locale state
│           │       └── SplashViewModel.kt         # Initial routing logic
│           │
│           └── res/                       # Android Resource Directory
│               ├── drawable/              # Vector drawables & adaptive icon foreground
│               ├── mipmap-*/              # App launcher icons for multiple screen densities
│               ├── values/                # Default values (colors.xml, strings.xml, themes.xml)
│               ├── values-v31/            # Android 12+ SplashScreen theme attributes
│               ├── values-hi/             # Hindi translations (strings.xml)
│               ├── values-bn/             # Bengali translations (strings.xml)
│               ├── values-mr/             # Marathi translations (strings.xml)
│               └── xml/                   # backup_rules.xml & data_extraction_rules.xml
│
├── gradle/                                # Gradle wrapper & version catalog (libs.versions.toml)
├── build.gradle.kts                       # Root project build script
├── settings.gradle.kts                    # Project subproject declarations & repositories
├── FOLDER_STRUCTURE.md                    # This directory structure specification
├── PROGRESS.md                            # Development progress & milestone tracker
└── README.md                              # Project overview, setup & deployment guide
```

---

## 📦 Layer-by-Layer Responsibilities

### 1. Presentation Layer (`com.example.aasha.ui` & `viewmodel`)
- **`ui/screens/`**: Divided by user features (`dashboard`, `booking`, `activity`, `issues`, `profile`, `login`, `splash`).
- **`ui/components/`**: Reusable Material 3 composables styled specifically for laundry cards, machine status badges, shimmer placeholders, and interactive bottom sheets.
- **`ui/navigation/`**: Centralized routes and navigation definitions (`Screen`, `BottomNavItem`).
- **`viewmodel/`**: Holds `StateFlow` and `SharedFlow` objects exposing state to Compose screens, surviving configuration changes, and handling user intents.

### 2. Domain Layer (`com.example.aasha.domain`)
- **`domain/model/`**: Core business entities and deterministic slot-time utilities (`WashingMachine`, `SlotBooking`, `SlotTimeHelper`, `MachineIssueReport`, `User`, `SyncStatus`).

### 3. Data Layer (`com.example.aasha.data`)
- **`data/local/`**: Room SQLite database (`AppDatabase` v6), entity DAOs, TypeConverters, and `SessionManager` (`EncryptedSharedPreferences` + DataStore).
- **`data/repository/`**: Exposes Clean Architecture repository APIs to ViewModels (`LaundryRepository`, `AuthRepository`, `FirestoreRepository`).
- **`data/worker/`**: Background jobs executed via AndroidX WorkManager for scheduled reminders and boot recovery.
- **`data/sync/`**: `SyncWorker` keeping local Room SQLite synchronized with Firebase Firestore.

### 4. Dependency Injection (`com.example.aasha.di`)
- Hilt modules (`DatabaseModule`, `FirebaseModule`) providing application-scoped singletons (`AppDatabase`, DAOs, `FirebaseAuth`, `FirebaseFirestore`).

---

## 💡 Guidelines for Adding New Features
1. **Model First**: Add new business models to [`domain/model/`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/domain/model).
2. **Database & DAO**: If persisting locally, add Room entities/DAOs in [`data/local/`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/data/local) and add a Room migration in [`AppDatabase.kt`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/data/local/AppDatabase.kt).
3. **Repository**: Expose operations through [`LaundryRepository.kt`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/data/repository/LaundryRepository.kt).
4. **ViewModel**: Implement a Hilt ViewModel in [`viewmodel/`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/viewmodel) exposing `StateFlow<UiState>`.
5. **UI Screen & Navigation**: Create a Composable screen in [`ui/screens/<feature>/`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/ui/screens) and register its route in [`Screen.kt`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/ui/navigation/Screen.kt) and [`MainScreen.kt`](file:///D:/Sem%207/LaundrySlot/app/src/main/java/com/example/aasha/ui/screens/MainScreen.kt).
