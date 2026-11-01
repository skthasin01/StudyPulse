-- StudyPulse database schema (SQLite)
-- Executed at every start by DBConnection.initDatabase().
-- Rules:
--   * first column of every table: id INTEGER PRIMARY KEY AUTOINCREMENT
--   * every foreign key column has its own index
--   * dates are TEXT yyyy-MM-dd, date-times are TEXT yyyy-MM-dd HH:mm:ss
--   * day_of_week / rest_day: 1 = Monday ... 7 = Sunday (java.time.DayOfWeek)

CREATE TABLE IF NOT EXISTS users (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name         TEXT    NOT NULL,
    email             TEXT    NOT NULL UNIQUE,
    password_hash     TEXT    NOT NULL,
    salt              TEXT    NOT NULL,
    created_at        TEXT    NOT NULL,
    wellbeing_enabled INTEGER NOT NULL DEFAULT 1 CHECK (wellbeing_enabled IN (0, 1)),
    rest_day          INTEGER NOT NULL DEFAULT 5 CHECK (rest_day BETWEEN 1 AND 7)
);

CREATE TABLE IF NOT EXISTS availability (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    day_of_week  INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    free_minutes INTEGER NOT NULL DEFAULT 0 CHECK (free_minutes BETWEEN 0 AND 1440),
    UNIQUE (user_id, day_of_week),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_availability_user_id ON availability (user_id);

CREATE TABLE IF NOT EXISTS tasks (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    title             TEXT    NOT NULL,
    description       TEXT,
    task_type         TEXT    NOT NULL CHECK (task_type IN ('ASSIGNMENT', 'EXAM', 'PROJECT', 'PREPARATION')),
    course_name       TEXT,
    difficulty        TEXT    NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    estimated_minutes INTEGER NOT NULL CHECK (estimated_minutes > 0),
    actual_minutes    INTEGER NOT NULL DEFAULT 0 CHECK (actual_minutes >= 0),
    deadline          TEXT    NOT NULL,
    priority          TEXT    NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status            TEXT    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'MISSED')),
    created_at        TEXT    NOT NULL,
    completed_at      TEXT,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks (user_id);

CREATE TABLE IF NOT EXISTS plan_blocks (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    task_id         INTEGER NOT NULL,
    plan_date       TEXT    NOT NULL,
    planned_minutes INTEGER NOT NULL CHECK (planned_minutes > 0),
    status          TEXT    NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'DONE', 'SKIPPED')),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_plan_blocks_user_id ON plan_blocks (user_id);
CREATE INDEX IF NOT EXISTS idx_plan_blocks_task_id ON plan_blocks (task_id);

CREATE TABLE IF NOT EXISTS study_logs (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL,
    task_id          INTEGER,
    start_time       TEXT    NOT NULL,
    end_time         TEXT    NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes >= 0),
    breaks_taken     INTEGER NOT NULL DEFAULT 0 CHECK (breaks_taken >= 0),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_study_logs_user_id ON study_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_study_logs_task_id ON study_logs (task_id);

CREATE TABLE IF NOT EXISTS checkins (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    checkin_date TEXT    NOT NULL,
    stress_level INTEGER NOT NULL CHECK (stress_level BETWEEN 1 AND 5),
    energy_level INTEGER NOT NULL CHECK (energy_level BETWEEN 1 AND 5),
    mood_level   INTEGER NOT NULL CHECK (mood_level BETWEEN 1 AND 5),
    sleep_hours  REAL    NOT NULL CHECK (sleep_hours BETWEEN 0 AND 24),
    note         TEXT,
    UNIQUE (user_id, checkin_date),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_checkins_user_id ON checkins (user_id);

CREATE TABLE IF NOT EXISTS risk_snapshots (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INTEGER NOT NULL,
    snapshot_date  TEXT    NOT NULL,
    workload_index REAL    NOT NULL,
    burnout_score  REAL    NOT NULL,
    risk_level     TEXT    NOT NULL CHECK (risk_level IN ('LOW', 'MODERATE', 'HIGH', 'SEVERE')),
    reasons        TEXT,
    UNIQUE (user_id, snapshot_date),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_risk_snapshots_user_id ON risk_snapshots (user_id);

CREATE TABLE IF NOT EXISTS notifications (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL,
    type       TEXT    NOT NULL CHECK (type IN ('WORKLOAD', 'BURNOUT', 'BREAK', 'DEADLINE', 'INFO')),
    message    TEXT    NOT NULL,
    created_at TEXT    NOT NULL,
    is_read    INTEGER NOT NULL DEFAULT 0 CHECK (is_read IN (0, 1)),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id);

CREATE TABLE IF NOT EXISTS user_settings (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL UNIQUE,
    focus_minutes    INTEGER NOT NULL DEFAULT 25 CHECK (focus_minutes BETWEEN 1 AND 90),
    short_break      INTEGER NOT NULL DEFAULT 5 CHECK (short_break BETWEEN 1 AND 30),
    long_break       INTEGER NOT NULL DEFAULT 15 CHECK (long_break BETWEEN 5 AND 60),
    notifications_on INTEGER NOT NULL DEFAULT 1 CHECK (notifications_on IN (0, 1)),
    tray_on          INTEGER NOT NULL DEFAULT 1 CHECK (tray_on IN (0, 1)),
    theme            TEXT    NOT NULL DEFAULT 'LIGHT' CHECK (theme IN ('LIGHT', 'DARK')),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_user_settings_user_id ON user_settings (user_id);
