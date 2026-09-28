# Running ShopFlow without Docker

Docker Desktop needs a working hypervisor (WSL 2 / Virtual Machine Platform). If it
won't start on your machine, you can run the whole backend natively instead. You need
three things on your PC, then one script does the rest.

The service **jars are already built** (`services/*/target/*-0.0.1-SNAPSHOT.jar`), so
there is no Maven step unless you change code.

---

## 1. Install PostgreSQL 16

* Download: <https://www.postgresql.org/download/windows/> (EDB installer)
* During setup: set a password for the `postgres` superuser — **remember it**.
* Keep the default port **5432**.
* You do **not** need Stack Builder.
* Default install path is `C:\Program Files\PostgreSQL\16` — the script assumes this.

The script creates the role `shopflow` / password `shopflow` and the six databases
(`shopflow_user`, `shopflow_product`, `shopflow_order`, `shopflow_inventory`,
`shopflow_payment`, `shopflow_notification`). You don't do this by hand.

## 2. Install Redis (Memurai)

Windows has no official Redis build; **Memurai** is a drop-in, runs as a Windows service.

* Download: <https://www.memurai.com/get-memurai> (Developer edition is free)
* After install it listens on **localhost:6379** automatically. Nothing to configure.

## 3. Use a supported JDK

This project targets **Java 17**; Spring Boot 3.4 supports 17–23. You have **JDK 21**
at `C:\Program Files\Java\jdk-21` — the script uses that. (JDK 24 is **not** supported
and will fail at boot, so don't point it there.)

---

## 4. Run it

From the repo root (`C:\Users\yashh\shopflow`) in **PowerShell**:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
```

It will:

1. check Java, Redis (6379) and Postgres (5432),
2. create the `shopflow` role + 6 databases (prompts once for the `postgres` password),
3. start all 8 services in the right order, logging to `logs\<service>.log`,
4. poll each `/actuator/health` and print a summary.

Flags:

| Flag | Use |
|---|---|
| `-SkipDbSetup` | databases already exist, skip step 2 |
| `-PgBin "C:\Program Files\PostgreSQL\17\bin"` | non-default Postgres version/path |
| `-JavaHome "C:\Program Files\Java\jdk-17"` | use a different JDK |
| `-PgSuperPassword "…"` | don't prompt for the postgres password |
| `-WithKafka` | only if you have a broker on `localhost:29092` (see note below) |

Then start the storefront:

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Seed a product: register `admin@shopflow.local` in the UI
(that email is auto-granted ADMIN), sign in, and use **Admin → Products / Inventory** —
or run `bash scripts/smoke.sh` from Git Bash.

Stop everything:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
```

---

## What works without Kafka

Everything except the **automatic order saga**. Without a broker:

* register / login / profile, browse & search catalog, product detail, cart, admin
  product & stock management — all fine.
* placing an order creates it and reserves inventory, but it stays at
  `INVENTORY_PENDING` because there's no broker to carry the events between services.

To get the full checkout → `CONFIRMED` flow you need Kafka. The easy way is still Docker
(`docker compose up -d kafka`). Running Kafka natively on Windows is possible (KRaft mode,
`localhost:9092`) but fiddly and not covered here.

---

## Rebuilding the jars (only if you change backend code)

There's no `mvnw` in the repo and no system Maven. A copy of Maven 3.9.9 was downloaded
to this session's scratchpad; the dependency cache in `~/.m2` (1.2 GB) is already
populated, so builds work offline:

```powershell
$m2 = "$env:USERPROFILE\.m2\repository"
& "<path-to>\apache-maven-3.9.9\bin\mvn" -o -DskipTests package
```

`-o` (offline) sidesteps the AVG TLS interception that breaks fresh downloads. If you
need to fetch a *new* dependency, drop `-o` and add:
`-Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true`
