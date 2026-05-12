# Neon Postgres setup

End-to-end steps to spin up a Neon database and point the app at it.

---

## 1. Create a Neon project

1. Sign up at <https://neon.tech> (free tier is fine — 0.5 GB storage).
2. Click **New Project**.
3. Pick a name (e.g. `expense-tracker`), the closest region, and the **latest Postgres version Neon offers** (17 or 18 at time of writing). The schema only uses long-stable features (`serial`, `jsonb`, `ENUM`, partial indices, `ON CONFLICT`), so any 14+ works — but newer is free.
4. Leave the default branch (`main`) and database (`neondb`) — the schema works either way.

Neon will land you on the project dashboard with a connection string visible.

---

## 2. Grab the connection string

In **Connection Details** Neon shows a string like:

```
postgresql://danielc:AbCdEf123@ep-cool-snow-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
```

Two endpoints exist per project:

| Endpoint                              | When to use                                                                  |
| ------------------------------------- | ---------------------------------------------------------------------------- |
| Direct (`ep-cool-snow-12345…`)        | **Use this.** Single device, low concurrency.                                |
| Pooler (`ep-cool-snow-12345-pooler…`) | Only matters if you ever fan out to many concurrent clients. Skip for now.   |

Copy the **direct** string. You'll use it in two places: `psql` (step 4) and the Android app (step 5).

> ⚠️ Treat it like a password — it grants full read/write to your data. Never commit it.

---

## 3. Convert it to JDBC form

The Android app uses the JDBC driver, which expects a different shape. Take the parts of your URL and rewrite them:

```
jdbc:postgresql://<host>/<db>?user=<user>&password=<pass>&sslmode=require&channelBinding=require
```

So this:

```
postgresql://danielc:AbCdEf123@ep-cool-snow-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
```

becomes:

```
jdbc:postgresql://ep-cool-snow-12345.us-east-2.aws.neon.tech/neondb?user=danielc&password=AbCdEf123&sslmode=require&channelBinding=require
```

`channelBinding=require` adds a small extra binding check on the TLS handshake — Neon supports it and the JDBC driver does too, so leave it on.

If your password contains URL-special characters (`@`, `&`, `?`, `#`, `+`, etc.), URL-encode them. Neon-generated passwords usually don't.

---

## 4. Apply the schema

From the repo root, run:

```bash


psql "postgresql://danielc:AbCdEf123@ep-cool-snow-12345.us-east-2.aws.neon.tech/neondb?sslmode=require" \
     -f db/migrations/001_initial.sql
```

(Use the original `postgresql://...` form for `psql` — JDBC form is Java-only.)

You should see a stream of `CREATE TYPE`, `CREATE TABLE`, `CREATE INDEX`, `INSERT` lines and no errors. Verify with:

```bash
psql "$NEON_URL" -c "\dt"          # list tables
psql "$NEON_URL" -c "SELECT * FROM categories;"  # 16 seed rows
psql "$NEON_URL" -c "SELECT * FROM sms_sources;" # MPESA + AIRTELMONEY
```

If you ever need to reset:

```bash
psql "$NEON_URL" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
psql "$NEON_URL" -f db/migrations/001_initial.sql
```

---

## 5. Configure the app

```bash
cp local.properties.example local.properties
```

Open `local.properties` and fill in:

```properties
sdk.dir=/home/<you>/Android/Sdk
NEON_JDBC_URL=jdbc:postgresql://ep-cool-snow-12345.us-east-2.aws.neon.tech/neondb?user=danielc&password=AbCdEf123&sslmode=require&channelBinding=require
```

`local.properties` is gitignored. The build reads `NEON_JDBC_URL` and bakes it into `BuildConfig` of the APK — anyone with the APK can extract it, so keep that APK on your device only.

---

## 6. Build and install

The repo doesn't ship a Gradle wrapper. Generate one once:

```bash
gradle wrapper --gradle-version 8.11.1
# or just open the project in Android Studio and let it sync
```

Then:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug    # with a phone/emulator connected
```

---

## 7. Verify the round-trip

1. Open the app, grant SMS / Notification / Overlay permissions.
2. Send a test SMS to the device:
   ```bash
   adb emu sms send MPESA "QGH3X4Y2 Confirmed. You have received Ksh1,500.00 from JOHN DOE 0712345678 on 1/4/26 at 10:00 AM. New M-PESA balance is Ksh10,200.50."
   ```
3. The categorization overlay should appear. Confirm a category.
4. Hit **Sync Now** in Settings (or wait up to 15 min for the periodic worker).
5. From your laptop:
   ```bash
   psql "$NEON_URL" -c "SELECT id, type, amount, counterparty FROM transactions ORDER BY id DESC LIMIT 5;"
   ```
   You should see the row you just created.

---

## 8. Things that will trip you up

| Symptom                                                                               | Fix                                                                                                                                                  |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `org.postgresql.util.PSQLException: FATAL: password authentication failed`            | The password in your JDBC URL is wrong or contains unencoded special characters. Reset the role password in Neon and re-paste.                       |
| `SSLHandshakeException` / `connection refused`                                        | Make sure `sslmode=require` is in the URL. Neon refuses plain TCP.                                                                                   |
| `relation "transactions" does not exist`                                              | You skipped step 4. Run `psql -f db/migrations/001_initial.sql`.                                                                                     |
| First sync after a long pause takes ~500 ms longer                                    | Neon **auto-suspends** the compute after ~5 min idle. The worker tolerates this — it just looks like a slow first attempt. Subsequent syncs are fast. |
| `BuildConfig.NEON_JDBC_URL` is empty in the running app                               | `local.properties` was missing or you forgot to rebuild after editing it. Re-run `:app:assembleDebug`.                                               |
| `SyncWorker` logs `PermanentFailure(NEON_JDBC_URL not configured)`                    | Same as above — the URL didn't make it into the build.                                                                                               |
| `R8` complains about `waffle-jna` / SSPI                                              | Already excluded in [`build.gradle.kts`](../app/build.gradle.kts) under `configurations.all { exclude … }`. If you replaced that file, restore it.   |
| Method count over 64 K when packaging                                                 | `multiDexEnabled = true` is set already; if you removed it, put it back.                                                                             |
| You changed schema in `Entities.kt` — app crashes on launch with migration error      | We use `fallbackToDestructiveMigration()` once. After that, **stop using it** and write a real `Migration(2, 3)`. Bump the version in `AppDatabase`. |

---

## 9. Future migrations

After this initial schema, treat `db/migrations/` as append-only:

```
db/migrations/
├── 001_initial.sql
├── 002_<change>.sql
├── 003_<change>.sql
```

For Room, remove the one-time `fallbackToDestructiveMigration()` and add a `Migration(N, N+1)` block in `AppDatabase.kt` that mirrors the SQL changes. Commit the new `app/schemas/<package>.AppDatabase/N+1.json` that KSP generates.

---

## 10. Backups

Free Neon keeps point-in-time history for 24 hours by default. For a personal money-tracker, that's tight. Either:

- Upgrade to a paid tier (extends history to 7 days+).
- Or run `pg_dump "$NEON_URL" > backup-$(date +%F).sql` on a cron from your laptop.

CSV export from the app's Settings screen is the quick-and-dirty fallback.
