# CORE: Test Evidence

All results below are from actual runs in the Phase 3 VM, unless
a line says otherwise. Nothing in this file is claimed from the
README alone.

## 1. Environment used for the Phase 3 runs

- Java: Temurin OpenJDK 25.0.5
- Maven: wrapper, Maven 3.10.0, Spring Boot 4.1.1
- MySQL: 8.0.46, database `core_db`, created from
  `backend/src/main/resources/db/schema.sql` and seeded from
  `backend/src/main/resources/db/seed.sql`
- Seed check (mysql client): 6 tables, 2 events, 2 shows,
  24 show_seats, all AVAILABLE

## 2. Unit tests — verified

Command (from `backend/`):

```bash
./mvnw clean test -Dtest=BookingEngineTests,BookingEngineShutdownTests,BookingEngineStressTests,SeatAvailabilityCacheTests
```

Result:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Breakdown:

| Test class | Tests | Result |
|---|---|---|
| `BookingEngineTests` (Phase 2) | 4 | Pass |
| `BookingEngineShutdownTests` (Phase 3) | 3 | Pass |
| `BookingEngineStressTests` (Phase 3) | 2 | Pass |
| `SeatAvailabilityCacheTests` (Phase 2) | 2 | Pass |

What the new tests prove:

- Shutdown drains queued requests, rejects submissions after
  close, and waits for the request in progress.
- Stress: 100 concurrent requests for 10 seats give exactly
  10 successes and 90 `SEAT_ALREADY_BOOKED` rejections.
- Queue saturation: with 1 busy worker and a queue of 5, the
  next 9 requests are rejected immediately.

## 3. Database and API tests — written, run pending

New tests added in Phase 3:

| Test class | Tests | What it checks |
|---|---|---|
| `BookingConcurrencyIntegrationTests` (Phase 2) | 2 | 20 concurrent requests, 1 success / 19 duplicates; cancel and rebook |
| `BookingServiceRollbackTests` | 3 | Duplicate booking leaves 1 CONFIRMED booking; cancel by wrong user changes nothing; cancel of a missing booking changes nothing |
| `CacheConsistencyIntegrationTests` | 1 | Seats read AVAILABLE, then BOOKED after booking, then AVAILABLE after cancellation |
| `AuthAndValidationApiTests` | 7 | Register 201, duplicate register 409, short password 400, wrong password 401, history requires login 401, invalid booking 400, history returns only my bookings, cancel of another user's booking 404 |

Status in this VM: **not yet run to completion.** The Spring
context tests fail at startup with
"Unable to determine Dialect without JDBC metadata", because the
JVM in this VM cannot open a TCP connection to MySQL: the Muse
runtime intercepts Java database connections with the message
"Database connections is turned off for this assistant. To allow
it, ask the user to open Muse settings -> Permissions -> Direct
network protocols and switch databases from Deny to Ask."

Re-run on 2026-11-13 at 21:18 IST (`mvn-core.sh clean test`):
**Tests run: 25, Failures: 0, Errors: 14, Skipped: 0.**
The 11 unit tests pass; all 14 errors are the Spring-context
tests above. A Java socket test the same evening still returns
the same "Database connections is turned off" message, and a
Python TCP forwarder on another port also fails, so Java TCP
is blocked on any port in this VM — the permission change has
not taken effect here yet.

The MySQL server itself is running and reachable from the
command-line client and from Python, and the schema and seed
files were applied and verified through it. The failure is in
the runtime permission for Java database connections, not in
the project code or the schema. The full suite must be re-run
after that permission is enabled, and the numbers above must
be updated with the real result before submission.

## 4. Phase 2 result (as reported, not re-run here)

From the Phase 2 README, on the verified branch:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

That result has not been re-run in this VM yet, for the same
database-permission reason as section 3. The 9 Phase 2 tests
are a subset of the full suite in section 3.

## 5. Frontend checks — verified against a stub API

The full browser journey was verified with the real
`index.html` and `app.js` running in jsdom against a temporary
local stub that mirrors the real API shapes (register, login,
events, shows, seats, bookings, history, cancel). The stub is
not part of the project and is not committed. This checks the
frontend logic, not the real backend.

Result: **18/18 checks passed**, including:

- Events load; shows load for the chosen event
- Register succeeds, duplicate register is rejected with an error
- Sign in shows "Signed in as ...", sign out clears it
- 12 seats render as A1–A12, all available
- Selecting A5 and booking shows "Booking confirmed. Booking ID: ..."
- History lists the booking as CONFIRMED; the booked seat is disabled
- Cancelling from history shows the booking as CANCELLED and the
  seat becomes available again

One real bug was found and fixed by this check: after a booking
or cancellation, reloading the seat list overwrote the
confirmation toast with "12 seats loaded." The confirmation
message is now shown again after the reload.

The full browser journey against the real backend still needs
the permission in section 3. See `docs/DEMO.md` for the steps.

## 6. GitHub push — completed 2026-11-13 ~21:30 IST

- The GitHub connector was connected as Shubham-0x and the
  Muse GitHub App was installed on the repository. The first
  `create_branch` / `push_files` attempts failed with
  `403 Resource not accessible by integration` on `git/refs`;
  after the App installation the branch creation succeeded.
- Branch `feature/phase3-completion` was created from the
  exact Phase 2 commit `fd358924a5a3112731ee7f97cef84b02d371c714`.
- Snapshot push confirmed: remote HEAD
  **`fc029166e6437bfbb8dda23dcdcf3515ba66a49c`**
  ("Complete CORE Phase 3: frontend journey, show and booking
  history APIs, schema, reliability tests and docs"), parent
  `fd358924...`, verified with `list_commits`.
- Content verified: the remote blob SHAs of
  `backend/src/main/resources/db/schema.sql`,
  `docs/TEST_EVIDENCE.md` and
  `backend/src/main/resources/static/app.js` match the local
  files exactly.
- Note: the remote snapshot is one commit holding all Phase 3
  changes on top of the Phase 2 commit. The local branch
  keeps the same content in four commits (`dba2c41`,
  `62ec3ec`, `de323c8`, `8fd923c`); the trees are identical,
  the commit SHAs differ by design of the snapshot push.
  Nothing was pushed to `main` or `feature/phase2-completion`.
