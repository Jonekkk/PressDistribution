# Heroku Deployment Guide

Deploying the Press Distribution application to Heroku using the container stack
with an external managed MySQL database.

---

## 1. Prerequisites

- **Heroku account** with billing verification (required for any paid add-on)
- **Heroku CLI** installed and authenticated (`heroku login`)
- **Docker** installed and running locally
- **Git** repository initialized and committed
- **Java 25** and **Maven** (for local testing via `./mvnw`)

---

## 2. Local Validation

Before deploying, confirm the application builds and tests pass locally.

### Run tests

```bash
./mvnw test
```

### Build the Docker image locally

```bash
docker build -t pressdistribution:local .
```

### Verify with Docker Compose (optional, local only)

```bash
docker compose up --build
```

> Do not delete Docker volumes during verification. This step uses the local
> MySQL container and is not part of the Heroku deployment.

---

## 3. Heroku App Creation

```bash
heroku create <your-app-name>
heroku stack:set container -a <your-app-name>
```

Replace `<your-app-name>` with your chosen Heroku app name. Do not hard-code
the app name in configuration files.

---

## 4. Database Provisioning

This application requires an **external managed MySQL database**. Do not deploy
MySQL inside the application container. Do not use Docker Compose on Heroku.

### Choose a MySQL add-on

JawsDB MySQL is one available option. Inspect available plans before selecting:

```bash
heroku addons:plans jawsdb
```

Provision the add-on manually after reviewing pricing:

```bash
heroku addons:create jawsdb:<plan-name> -a <your-app-name>
```

> Check current marketplace pricing before provisioning. Prices change over
> time and are not documented here.

### Read the connection URL

After provisioning, the add-on sets a config var (typically `JAWSDB_URL`):

```bash
heroku config:get JAWSDB_URL -a <your-app-name>
```

This returns a URL in the format:

```
mysql://<username>:<password>@<host>:<port>/<database>
```

**This is not a JDBC URL.** You must convert it to JDBC format for Spring Boot.

---

## 5. Heroku Config Vars

### Converting the add-on URL to JDBC format

Given an add-on URL like:

```
mysql://user123:pass456@some-host.jawsdb.com:3306/dbname
```

Convert it to:

```
jdbc:mysql://some-host.jawsdb.com:3306/dbname?useSSL=true&requireSSL=true&serverTimezone=UTC
```

Use the username and password from the URL separately in
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

### Required Config Vars

Set all of the following via Heroku Dashboard or CLI. Never commit these values
to source control.

```bash
heroku config:set \
  SPRING_DATASOURCE_URL="jdbc:mysql://<host>:<port>/<database>?useSSL=true&requireSSL=true&serverTimezone=UTC" \
  SPRING_DATASOURCE_USERNAME="<username>" \
  SPRING_DATASOURCE_PASSWORD="<password>" \
  APP_SEED_ENABLED="false" \
  APP_BOOTSTRAP_ADMIN_FULL_NAME="<admin full name>" \
  APP_BOOTSTRAP_ADMIN_EMAIL="<admin email>" \
  APP_BOOTSTRAP_ADMIN_PASSWORD="<strong generated password>" \
  JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC" \
  -a <your-app-name>
```

| Config Var | Purpose | Production Value |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC MySQL connection URL | `jdbc:mysql://...` (converted from add-on) |
| `SPRING_DATASOURCE_USERNAME` | Database username | From add-on URL |
| `SPRING_DATASOURCE_PASSWORD` | Database password | From add-on URL |
| `APP_SEED_ENABLED` | Load sample data | `false` |
| `APP_BOOTSTRAP_ADMIN_FULL_NAME` | Initial admin name | Set securely |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | Initial admin email | Set securely |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | Initial admin password | Strong, set securely |
| `JAVA_TOOL_OPTIONS` | JVM memory tuning | `-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC` |

### JVM Memory Configuration

The suggested `JAVA_TOOL_OPTIONS` value is conservative for a 512 MB Eco dyno:

- `-XX:MaxRAMPercentage=75.0` — limits heap to 75% of available RAM (~384 MB)
- `-XX:+UseSerialGC` — low-overhead garbage collector for single-core dynos

Set this as a Config Var. Do not hard-code it in the Dockerfile or application
configuration.

### Important notes

- **`APP_SEED_ENABLED=false`** — never enable sample seed data in production
  unless deliberately required for a demo.
- **Bootstrap admin** — the application creates an initial administrator on
  first startup. Set credentials via Config Vars only. The password is shown
  only once (in the recovery code flow); use a strong generated password.
- **Do not commit credentials** — all secrets must be provided exclusively
  through Heroku Config Vars or the Dashboard.

---

## 6. Deployment

### Set the container stack (if not already done)

```bash
heroku stack:set container -a <your-app-name>
```

### Deploy via git push

```bash
git push heroku main
```

Heroku reads `heroku.yml`, builds the Docker image from the `Dockerfile`, and
starts the `web` process.

### Alternative: Heroku container registry workflow

```bash
heroku container:push web -a <your-app-name>
heroku container:release web -a <your-app-name>
```

### Scale to one Eco dyno

```bash
heroku ps:scale web=1 -a <your-app-name>
```

This uses the cheapest Eco dyno for a low-cost prototype.

### Switching to a Basic dyno later

When you need a dyno that does not sleep:

```bash
heroku ps:type web=basic -a <your-app-name>
```

No code changes are required.

---

## 7. Post-Deployment Verification

### Check logs

```bash
heroku logs --tail -a <your-app-name>
```

Verify:
- Flyway migrations run successfully (look for `Successfully applied X migrations`)
- Hibernate validation passes (no schema mismatch errors)
- Application binds to the correct `$PORT`
- No secrets (passwords, JDBC URL with credentials) appear in logs

### Open the application

```bash
heroku open -a <your-app-name>
```

### Verification checklist

1. Application URL loads the login page
2. Log in as the bootstrap administrator (email + password from Config Vars)
3. Verify access to an admin-protected page (e.g., user management)
4. Log out successfully
5. Verify an unauthenticated request to a protected page redirects to login
6. Check `heroku logs` for any leaked secrets — there should be none

---

## 8. Cost Controls

### Eco dyno behavior

- Eco dynos **sleep after 30 minutes of inactivity**
- The first request after sleep has a **cold-start delay** (10–30 seconds)
- Eco dynos share a monthly pool of dyno hours across all Eco apps in your
  account
- This is suitable for a low-traffic prototype

### MySQL add-on costs

- MySQL add-on pricing is **separate** from dyno costs
- Check current pricing on the Heroku Elements marketplace before provisioning
- Free-tier MySQL plans (if available) have row/connection limits

### Reducing costs when not in use

Scale the web dyno to zero:

```bash
heroku ps:scale web=0 -a <your-app-name>
```

Or destroy the app entirely (irreversible):

```bash
heroku apps:destroy <your-app-name> --confirm <your-app-name>
```

> Destroying the app also removes associated add-ons and their data.
> Export or back up the database before destroying.

---

## 9. Troubleshooting

### Wrong JDBC URL format

**Symptom:** `Communications link failure` or `No suitable driver`

**Cause:** Using the raw `mysql://` URL from the add-on directly.

**Fix:** Convert to `jdbc:mysql://host:port/database?useSSL=true&requireSSL=true&serverTimezone=UTC`
and set as `SPRING_DATASOURCE_URL`.

### Invalid SSL or MySQL connection parameters

**Symptom:** `SSL connection error` or `Access denied`

**Fix:** Ensure `useSSL=true&requireSSL=true` are in the JDBC URL. Some add-ons
require SSL. If the add-on does not support SSL, use `useSSL=false`.

### Flyway migration failure

**Symptom:** `FlywayException` in logs at startup.

**Cause:** Schema drift, missing migration, or incompatible SQL.

**Fix:**
- First deploy against an **empty** database
- Never modify already-executed migrations
- If the remote database has prior state, back up and recreate it cleanly

### Hibernate validation failure

**Symptom:** `SchemaManagementException` — entity mapping does not match schema.

**Cause:** Flyway migrations out of sync with JPA entities.

**Fix:** Ensure all migrations are applied. Do not change `ddl-auto` from
`validate`.

### App fails to bind $PORT

**Symptom:** `Error R10 (Boot timeout)` in Heroku logs.

**Cause:** Application not listening on the Heroku-assigned `$PORT`.

**Fix:** Verify `server.port=${PORT:8080}` in `application.yaml` and that the
`heroku.yml` run command passes `--server.port=$PORT`.

### Boot memory failure (R14/R15)

**Symptom:** `Error R14 (Memory quota exceeded)` or the app is killed.

**Cause:** JVM using more memory than available on the 512 MB dyno.

**Fix:** Set `JAVA_TOOL_OPTIONS` config var:
```
-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC
```

If the issue persists, reduce to `-XX:MaxRAMPercentage=65.0` or upgrade to a
Basic dyno with more memory.

### Missing Config Var

**Symptom:** Application fails to start with `null` or empty datasource URL.

**Fix:** Verify all required Config Vars are set:
```bash
heroku config -a <your-app-name>
```

### Database connection timeout

**Symptom:** `CommunicationsException: Communications link failure` after
successful startup.

**Cause:** Connection pool exhausted or database unreachable.

**Fix:**
- Check the add-on's connection limit (free tiers often allow only 5–10)
- Add connection pool parameters to the JDBC URL if needed:
  `&connectionTimeout=30000&socketTimeout=60000`
- Verify the database add-on is active and not suspended

---

## Architecture Summary

```
┌──────────────────────────────────┐
│  Heroku Container Stack          │
│                                  │
│  ┌────────────────────────────┐  │
│  │  Web Dyno (Eco)            │  │
│  │  eclipse-temurin:25-jre    │  │
│  │  Spring Boot 4.1.0         │  │
│  │  Flyway + Hibernate        │  │
│  └─────────────┬──────────────┘  │
│                │                  │
└────────────────┼──────────────────┘
                 │ JDBC (SSL)
                 ▼
┌──────────────────────────────────┐
│  Managed MySQL Add-on            │
│  (e.g., JawsDB MySQL)           │
│  External to dyno                │
└──────────────────────────────────┘
```

- One web dyno, no worker dynos
- No MySQL container in the Docker image
- No Docker Compose on Heroku
- Flyway manages schema migrations at application startup
- Hibernate validates schema only (`ddl-auto=validate`)
- All secrets provided via Heroku Config Vars
