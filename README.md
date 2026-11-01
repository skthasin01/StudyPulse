# StudyPulse – Smart Study Load & Burnout Prevention System

StudyPulse is a Java desktop application that helps university students plan their study work
and notice early when the workload becomes too much. Students enter their tasks and free time;
StudyPulse builds a study plan, calculates a **Study Load Index** and a **Burnout Risk Score**
from the workload, deadlines, study sessions and an optional daily mood / energy / sleep
check-in, explains every point of the score and gives short, practical recommendations.

> StudyPulse is a productivity and early-warning tool, not a medical diagnosis system.
> All data stays on the computer in `data/studypulse.db`; nothing is sent online.

Built with Java 8, Swing (NetBeans JFrame Forms, GroupLayout), SQLite, FlatLaf, JFreeChart and
JCalendar, as a NetBeans "Java with Ant" project.

## Features

- **Accounts** – sign-up / login, SHA-256 password hashes with a random salt.
- **Smart task entry** – type, difficulty, estimate, deadline with time, priority; search and
  filters; overdue tasks become *Missed* automatically; colour-coded rows; estimate hints from
  your own history ("Your Hard assignments usually take 25% longer than estimated").
- **Automatic study plan** – rule-based generator that spreads remaining work over your free time
  before each deadline (max. 2 h per task per day, weekly rest day kept free, Hard tasks first),
  warns when a deadline cannot be met; mark blocks done (creates a study log) or skip them.
- **Daily check-in** – stress, energy, mood, sleep and a note in about one minute; 14-day
  history, streak, once-a-day reminder; fully optional (can be switched off).
- **Analytics engine** – Study Load Index (0–150) and Burnout Risk Score (0–100) with a reason
  and points for every rule, daily snapshots, recommendations.
- **Dashboard** – summary cards, today's plan, upcoming deadlines, 7-day risk chart.
- **Analytics page** – four JFreeChart charts (7 days / 4 weeks), insight sentences, PNG export.
- **Notifications** – workload, burnout, deadline, break and info messages; desktop toasts and
  system tray messages every 15 minutes.
- **Pomodoro timer** – focus / short break / long break, rounds are saved as study sessions,
  break reminder after more than 120 minutes without a break.
- **Settings & privacy** – profile, password, study preferences, light / dark mode, CSV export of
  all your data, delete well-being data, delete account.
- **Demo data** – one click creates a demo account with three weeks of realistic data.

## Screens

| Screen | Form | What it shows |
|---|---|---|
| Login | `LoginFrame` | login, *Create an account*, *Load demo data* |
| Sign up | `SignupFrame` | new account, optional well-being tracking |
| Dashboard | `DashboardFrame` | cards (load, risk, due, hours), today's plan, deadlines, risk chart, bell |
| Tasks | `TaskFrame` | add / edit / delete / search / filter tasks, mark done |
| Study Plan | `StudyPlanFrame` (+ `AvailabilityFrame`) | week grid, generate plan, mark / skip blocks, free time |
| Daily Check-in | `CheckinFrame` | today's check-in, 14-day history, streak |
| Risk Report | `RiskReportFrame` | two gauges, "Why this score?", recommendations, 14-day history |
| Analytics | `AnalyticsFrame` | four charts, insights, PNG export |
| Notifications | `NotificationFrame` (+ `ToastFrame`) | list, filter, mark read, delete |
| Pomodoro | `PomodoroFrame` | timer, rounds, task, settings |
| Settings | `SettingsFrame` | Profile, Study preferences, Privacy, Appearance, About |

## Demo login

On the login screen click **Load demo data**, then log in with

```
demo@studypulse.com
demo123
```

The demo student has 12 CSE tasks (5 done, 2 missed, 5 upcoming), 21 days of check-ins
(balanced week 1, rising stress in week 2, sleep below 6 h in week 3), study sessions, a study
plan, 21 risk snapshots calculated by the real engine and some read / unread notifications.

## Requirements

- JDK 8 (tested with 1.8.0_111); the app also runs on newer Java versions.
- NetBeans 8.2 or newer with Java SE support (for development).
- Libraries (already in `lib/`): `sqlite-jdbc-3.54.1.0.jar`, `jfreechart-1.5.6.jar`,
  `jcalendar-1.4.jar`, `flatlaf-3.7.2.jar`; for tests only `junit-4.13.2.jar`, `hamcrest-core-1.3.jar`.

## How to run

### In NetBeans
1. **File > Open Project…** and choose the `StudyPulse` folder.
2. If NetBeans asks for a Java platform: **Project Properties > Libraries** → a JDK 8 platform.
3. **Clean and Build** (Shift+F11), then **Run** (F6). The console shows
   `Schema check: 9/9 tables use id INTEGER PRIMARY KEY AUTOINCREMENT`.
4. **Test** (Alt+F6) runs all JUnit tests.

Every screen is a JFrame Form: open it in `src/studypulse/forms/` and click **Design**.

### The built JAR
Clean and Build creates `dist/StudyPulse.jar` and `dist/lib/` (Main-Class `studypulse.Main`):

```
java -jar dist/StudyPulse.jar
```

### One file: StudyPulse-all.jar
In NetBeans: **Files** window → right-click `build.xml` → **Run Target** → `package-fat`
(or `ant package-fat` in a terminal). This creates `dist/StudyPulse-all.jar` with all libraries
inside and copies `run.bat` next to it. Double-click `dist/run.bat`, or:

```
java -jar StudyPulse-all.jar
```

The `data` folder (database and `error.log`) is created next to the jar.

> Building from a terminal without NetBeans: `dist/lib` is filled by NetBeans' CopyLibs task.
> Pass its location once, e.g. in a properties file
> `libs.CopyLibs.classpath=C:/Program Files/NetBeans 8.2/java/ant/extra/org-netbeans-modules-java-j2seproject-copylibstask.jar`
> and run `ant -propertyfile that.properties clean jar package-fat`.

### Windows .exe with Launch4j
1. Run the `package-fat` target so that `dist/StudyPulse-all.jar` exists.
2. Download and start **Launch4j** (https://launch4j.sourceforge.net, version 3.x).
3. **Basic** tab
   - *Output file*: `...\StudyPulse\dist\StudyPulse.exe`
   - *Jar*: `...\StudyPulse\dist\StudyPulse-all.jar` (leave "Don't wrap the jar" unchecked)
   - *Icon*: optional `.ico` file
   - *Change dir*: `.` (so the `data` folder is created next to the .exe)
4. **Header** tab: *Header type* = **GUI**.
5. **JRE** tab: *Min JRE version* = **1.8.0** (leave *Max JRE version* empty);
   *Message when JRE not found*: "StudyPulse needs Java 8 or newer."
6. **Version Info** tab (optional): file version `1.0.0.0`, product name `StudyPulse`.
7. Click the gear icon (**Build wrapper**) and save the configuration as `studypulse-launch4j.xml`
   in the project folder, so you can build the .exe again later.
8. Double-click `dist\StudyPulse.exe`. Java 8+ must be installed on the computer.

## Folder structure

```
StudyPulse/
├── build.xml                 Ant build (NetBeans) + package-fat target
├── manifest.mf
├── run.bat                   starts StudyPulse-all.jar (copied to dist/)
├── nbproject/                NetBeans project files (project.properties, build-impl.xml …)
├── lib/                      library jars (+ JUnit for tests)
├── src/studypulse/
│   ├── Main.java             look and feel, error handler, database + schema check, LoginFrame
│   ├── forms/                13 JFrame Forms (.java + .form)
│   ├── model/                POJOs and enums
│   ├── dao/                  JDBC data access, one DAO per table (+ SchemaDAO)
│   ├── service/              business logic used by the forms
│   ├── engine/               PlanGenerator, WorkloadCalculator, BurnoutRiskCalculator,
│   │                         RecommendationEngine, PomodoroCycle (pure logic, unit-tested)
│   ├── util/                 DBConnection, Session, DateUtil, UITheme, TableUtil, renderers,
│   │                         ChartUtil, DesktopAlerts, CsvUtil, ErrorLog, DemoDataSeeder …
│   └── resources/schema.sql  database schema (CREATE TABLE IF NOT EXISTS)
├── test/studypulse/          JUnit 4 tests (engine, service, util, DAO with a temporary database)
├── data/                     created at runtime: studypulse.db, error.log
└── dist/                     created by Clean and Build / package-fat
```

## Database (SQLite, `data/studypulse.db`)

The first column of every table is `id INTEGER PRIMARY KEY AUTOINCREMENT` (ids 1, 2, 3 …, never
reused); every `user_id` / `task_id` has an index; deleting a user or task deletes its rows
(`ON DELETE CASCADE`). Dates are `yyyy-MM-dd`, date-times `yyyy-MM-dd HH:mm:ss`.

| # | Table | Columns |
|---|---|---|
| 1 | `users` | id, full_name, email (UNIQUE), password_hash, salt, created_at, wellbeing_enabled (0/1, default 1), rest_day (1 Mon … 7 Sun, default 5) |
| 2 | `availability` | id, user_id, day_of_week (1-7), free_minutes (0-1440); UNIQUE(user_id, day_of_week) |
| 3 | `tasks` | id, user_id, title, description, task_type (ASSIGNMENT/EXAM/PROJECT/PREPARATION), course_name, difficulty (EASY/MEDIUM/HARD), estimated_minutes, actual_minutes, deadline, priority (LOW/MEDIUM/HIGH), status (PENDING/IN_PROGRESS/DONE/MISSED), created_at, completed_at |
| 4 | `plan_blocks` | id, user_id, task_id, plan_date, planned_minutes, status (PLANNED/DONE/SKIPPED) |
| 5 | `study_logs` | id, user_id, task_id (nullable), start_time, end_time, duration_minutes, breaks_taken |
| 6 | `checkins` | id, user_id, checkin_date, stress_level (1-5), energy_level (1-5), mood_level (1-5), sleep_hours, note; UNIQUE(user_id, checkin_date) |
| 7 | `risk_snapshots` | id, user_id, snapshot_date, workload_index, burnout_score, risk_level (LOW/MODERATE/HIGH/SEVERE), reasons; UNIQUE(user_id, snapshot_date) |
| 8 | `notifications` | id, user_id, type (WORKLOAD/BURNOUT/BREAK/DEADLINE/INFO), message, created_at, is_read (0/1) |
| 9 | `user_settings` | id, user_id (UNIQUE), focus_minutes (25), short_break (5), long_break (15), notifications_on (1), tray_on (1), theme (LIGHT/DARK) |

Delete `data/studypulse.db` to start with an empty database (the tables are created again).

## How the scores work (short)

- **Study Load Index** = Σ (remaining hours × difficulty weight × deadline factor) of open tasks
  due within 7 days ÷ free hours in the next 7 days × 100, max 150.
  Weights: Easy 1.0, Medium 1.5, Hard 2.0; deadline factor 2.0 (≤1 day), 1.5 (2-3), 1.2 (4-7), 1.0.
  Light < 50, Balanced < 80, Heavy ≤ 100, Overloaded > 100.
- **Burnout Risk Score** (0-100): +25/+15 low sleep, +20 high stress, +15 low energy, +10 low mood
  (average of the last 3 check-ins), +10 study session > 120 min without a break, +10 two or more
  missed tasks in 7 days, +15 load index ≥ 80 (+5 if it rose 3 days in a row).
  Low < 30, Moderate < 55, High < 75, Severe ≥ 75.

## Troubleshooting

- Something went wrong? The app shows a short message; technical details are in `data/error.log`.
- "Could not open its database": the folder must be writable (do not run from inside a ZIP).
