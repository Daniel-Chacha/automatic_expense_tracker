-- ============================================================
-- Finance Tracker — One-time Neon reset
--
-- DESTRUCTIVE: drops every Finance Tracker table and enum type
-- in the current database. Only run this when you want a clean
-- slate (e.g. before applying a refreshed 001_initial.sql).
--
--   psql "$NEON_JDBC_URL" -f db/migrations/000_reset.sql
--   psql "$NEON_JDBC_URL" -f db/migrations/001_initial.sql
--
-- Safe to run multiple times — every drop uses IF EXISTS and
-- CASCADE handles FK dependencies in the right order.
-- ============================================================

DROP TABLE IF EXISTS monthly_snapshots CASCADE;
DROP TABLE IF EXISTS debts CASCADE;
DROP TABLE IF EXISTS investments CASCADE;
DROP TABLE IF EXISTS savings_goals CASCADE;
DROP TABLE IF EXISTS budgets CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS sms_sources CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;      -- only present from the old schema
DROP TABLE IF EXISTS categories CASCADE;

DROP TYPE IF EXISTS snapshot_kind CASCADE;
DROP TYPE IF EXISTS debt_direction CASCADE;
DROP TYPE IF EXISTS transaction_status CASCADE;
DROP TYPE IF EXISTS transaction_type CASCADE;
