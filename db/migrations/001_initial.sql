-- ============================================================
-- Finance Tracker — Initial Schema (Neon Postgres)
-- Single-user personal app, no auth, no RLS.
-- Apply with: psql "$NEON_JDBC_URL" -f db/migrations/001_initial.sql
-- ============================================================

-- ---------------------- ENUM TYPES --------------------------

CREATE TYPE transaction_type AS ENUM ('INCOME', 'EXPENSE');
CREATE TYPE transaction_status AS ENUM ('CONFIRMED', 'UNCATEGORIZED', 'PENDING_REVIEW');
CREATE TYPE debt_direction AS ENUM ('LENT', 'BORROWED');

-- ---------------------- LOOKUP TABLES -----------------------

CREATE TABLE categories (
    id          smallserial PRIMARY KEY,
    name        varchar(50)  NOT NULL UNIQUE,
    icon        varchar(30),
    color       char(7),
    is_income   boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id          smallserial PRIMARY KEY,
    name        varchar(50)  NOT NULL UNIQUE,
    icon        varchar(30),
    balance     integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE sms_sources (
    id          smallserial PRIMARY KEY,
    sender      varchar(30)  NOT NULL UNIQUE,
    label       varchar(50),
    account_id  smallint     REFERENCES accounts(id) ON DELETE SET NULL,
    is_active   boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------- CORE TABLE --------------------------

CREATE TABLE transactions (
    id                      serial             PRIMARY KEY,
    type                    transaction_type   NOT NULL,
    status                  transaction_status NOT NULL DEFAULT 'UNCATEGORIZED',
    amount                  integer            NOT NULL,
    category_id             smallint           REFERENCES categories(id) ON DELETE SET NULL,
    account_id              smallint           REFERENCES accounts(id) ON DELETE SET NULL,
    description             varchar(150),
    transacted_at           timestamptz        NOT NULL DEFAULT now(),
    meta                    jsonb,
    reference               varchar(60),
    counterparty            varchar(120),
    dedup_hash              varchar(80) UNIQUE,
    created_at              timestamptz        NOT NULL DEFAULT now(),
    last_sync_attempt_at    timestamptz,
    sync_failures           integer            NOT NULL DEFAULT 0
);

CREATE INDEX idx_txn_transacted_at ON transactions (transacted_at);
CREATE INDEX idx_txn_category_id   ON transactions (category_id);
CREATE INDEX idx_txn_counterparty  ON transactions (counterparty);
CREATE INDEX idx_txn_reference     ON transactions (reference);

-- Reference uniqueness only enforced when reference is provided.
CREATE UNIQUE INDEX idx_txn_ref_time
    ON transactions (reference, transacted_at)
    WHERE reference IS NOT NULL;

-- ---------------------- BUDGETS -----------------------------

CREATE TABLE budgets (
    id          smallserial PRIMARY KEY,
    category_id smallint    NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    month       date        NOT NULL,
    amount      integer     NOT NULL,
    UNIQUE (category_id, month)
);

-- ---------------------- SAVINGS & INVESTMENTS ---------------

CREATE TABLE savings_goals (
    id              smallserial PRIMARY KEY,
    name            varchar(80)  NOT NULL,
    target_amount   integer      NOT NULL,
    current_amount  integer      NOT NULL DEFAULT 0,
    deadline        date,
    is_completed    boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE investments (
    id              smallserial PRIMARY KEY,
    name            varchar(80)  NOT NULL,
    type            varchar(30),
    buy_in_amount   integer      NOT NULL,
    current_value   integer      NOT NULL,
    notes           varchar(200),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------- DEBT TRACKER ------------------------

CREATE TABLE debts (
    id          serial         PRIMARY KEY,
    direction   debt_direction NOT NULL,
    person      varchar(80)    NOT NULL,
    amount      integer        NOT NULL,
    description varchar(150),
    is_settled  boolean        NOT NULL DEFAULT false,
    due_date    date,
    created_at  timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_debts_active ON debts (is_settled) WHERE is_settled = false;

-- ---------------------- SEED CATEGORIES ---------------------

INSERT INTO categories (name, icon, color, is_income) VALUES
    ('Food & Dining',    '🍔', '#FF6B35', false),
    ('Transport',        '🚌', '#4ECDC4', false),
    ('Rent & Housing',   '🏠', '#45B7D1', false),
    ('Utilities',        '💡', '#96CEB4', false),
    ('Shopping',         '🛍️', '#DDA0DD', false),
    ('Health',           '💊', '#FF6B6B', false),
    ('Entertainment',    '🎬', '#C44DFF', false),
    ('Education',        '📚', '#4A90D9', false),
    ('Airtime & Data',   '📱', '#F7DC6F', false),
    ('Transfers Out',    '📤', '#E67E22', false),
    ('Other Expense',    '📦', '#BDC3C7', false),
    ('Salary',           '💰', '#2ECC71', true),
    ('Freelance',        '💻', '#1ABC9C', true),
    ('Received',         '📥', '#3498DB', true),
    ('Interest',         '🏦', '#27AE60', true),
    ('Other Income',     '💵', '#82E0AA', true);

INSERT INTO sms_sources (sender, label, is_active) VALUES
    ('MPESA',       'M-Pesa',       true),
    ('AIRTELMONEY', 'Airtel Money', true);
