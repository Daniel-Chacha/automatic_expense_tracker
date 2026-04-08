-- ============================================================
-- Expense Tracker — Initial Schema
-- Space-optimized for Supabase free tier (500 MB)
-- ============================================================

-- ---------------------- ENUM TYPES --------------------------

CREATE TYPE transaction_type AS ENUM ('income', 'expense');
CREATE TYPE transaction_status AS ENUM ('confirmed', 'uncategorized', 'pending_review');
CREATE TYPE debt_direction AS ENUM ('lent', 'borrowed');

-- ---------------------- LOOKUP TABLES -----------------------

-- Expense/income categories (Food, Transport, Salary, etc.)
CREATE TABLE categories (
    id          smallserial PRIMARY KEY,
    name        varchar(50)  NOT NULL UNIQUE,
    icon        varchar(30),                    -- emoji or icon name
    color       char(7),                        -- hex color e.g. #FF5733
    is_income   boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- Financial accounts (M-Pesa, KCB, Cash, etc.)
CREATE TABLE accounts (
    id          smallserial PRIMARY KEY,
    name        varchar(50)  NOT NULL UNIQUE,
    icon        varchar(30),
    balance     integer      NOT NULL DEFAULT 0, -- cents
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- Whitelisted SMS senders to monitor
CREATE TABLE sms_sources (
    id          smallserial PRIMARY KEY,
    sender      varchar(30)  NOT NULL UNIQUE,    -- e.g. 'MPESA', 'KCB'
    label       varchar(50),                     -- friendly name
    account_id  smallint     REFERENCES accounts(id) ON DELETE SET NULL,
    is_active   boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------- CORE TABLE --------------------------

-- The main transaction log — largest table, every byte counts
CREATE TABLE transactions (
    id              serial          PRIMARY KEY,
    type            transaction_type   NOT NULL,
    status          transaction_status NOT NULL DEFAULT 'uncategorized',
    amount          integer            NOT NULL, -- cents (KES * 100)
    category_id     smallint           REFERENCES categories(id) ON DELETE SET NULL,
    account_id      smallint           REFERENCES accounts(id) ON DELETE SET NULL,
    description     varchar(150),
    transacted_at   timestamptz        NOT NULL DEFAULT now(),
    meta            jsonb,                       -- {ref, balance, raw_sms, counterparty}
    created_at      timestamptz        NOT NULL DEFAULT now()
);

-- Only the indexes we actually need
CREATE INDEX idx_txn_transacted_at ON transactions (transacted_at);
CREATE INDEX idx_txn_category_id  ON transactions (category_id);

-- ---------------------- BUDGETS -----------------------------

-- Monthly spending limits per category
CREATE TABLE budgets (
    id          smallserial PRIMARY KEY,
    category_id smallint    NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    month       date        NOT NULL, -- first of month, e.g. '2026-04-01'
    amount      integer     NOT NULL, -- cents
    UNIQUE (category_id, month)
);

-- ---------------------- SAVINGS & INVESTMENTS ---------------

-- Savings goals with target and progress
CREATE TABLE savings_goals (
    id              smallserial PRIMARY KEY,
    name            varchar(80)  NOT NULL,
    target_amount   integer      NOT NULL, -- cents
    current_amount  integer      NOT NULL DEFAULT 0, -- cents
    deadline        date,
    is_completed    boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

-- Investment portfolio entries
CREATE TABLE investments (
    id              smallserial PRIMARY KEY,
    name            varchar(80)  NOT NULL,       -- e.g. 'MMF - CIC', 'SACCO'
    type            varchar(30),                 -- e.g. 'mmf', 'sacco', 'stock'
    buy_in_amount   integer      NOT NULL,       -- cents, total invested
    current_value   integer      NOT NULL,       -- cents, current worth
    notes           varchar(200),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------- DEBT TRACKER ------------------------

-- Lend/borrow tracker
CREATE TABLE debts (
    id          serial         PRIMARY KEY,
    direction   debt_direction NOT NULL,
    person      varchar(80)    NOT NULL,
    amount      integer        NOT NULL, -- cents
    description varchar(150),
    is_settled  boolean        NOT NULL DEFAULT false,
    due_date    date,
    created_at  timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_debts_active ON debts (is_settled) WHERE is_settled = false;

-- ---------------------- SEED CATEGORIES ---------------------

INSERT INTO categories (name, icon, color, is_income) VALUES
    -- Expense categories
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
    -- Income categories
    ('Salary',           '💰', '#2ECC71', true),
    ('Freelance',        '💻', '#1ABC9C', true),
    ('Received',         '📥', '#3498DB', true),
    ('Interest',         '🏦', '#27AE60', true),
    ('Other Income',     '💵', '#82E0AA', true);

-- ---------------------- ROW LEVEL SECURITY ------------------
-- Single-user app: RLS ensures only your auth UID can access data.
-- After sign-in, replace '<YOUR_AUTH_UID>' with your actual Supabase auth.uid().

ALTER TABLE categories     ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE sms_sources    ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets        ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_goals  ENABLE ROW LEVEL SECURITY;
ALTER TABLE investments    ENABLE ROW LEVEL SECURITY;
ALTER TABLE debts          ENABLE ROW LEVEL SECURITY;

-- Placeholder policies — update '<YOUR_AUTH_UID>' after first sign-in
DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'categories', 'accounts', 'sms_sources', 'transactions',
        'budgets', 'savings_goals', 'investments', 'debts'
    ] LOOP
        EXECUTE format(
            'CREATE POLICY owner_all ON %I FOR ALL USING (true) WITH CHECK (true)',
            tbl
        );
    END LOOP;
END $$;

-- NOTE: The above policies allow all operations while developing.
-- Once you have your auth UID, replace with:
--   CREATE POLICY owner_all ON <table> FOR ALL
--     USING (auth.uid() = '<YOUR_AUTH_UID>')
--     WITH CHECK (auth.uid() = '<YOUR_AUTH_UID>');
