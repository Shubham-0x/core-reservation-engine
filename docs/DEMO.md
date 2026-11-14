# CORE: Demo Script

Run the demo in this order. Each step shows the feature it proves.

Every step below was verified end to end in a real browser
against the running backend on 2026-11-14: 23/23 checks passed
(see `docs/TEST_EVIDENCE.md`, section 5).

## Setup (once)

```powershell
cd core-reservation-engine\backend
$env:DB_USERNAME = "root"
$secure = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
```

Create the database on a fresh MySQL install only:

```powershell
mysql -u root -p < src\main\resources\db\schema.sql
mysql -u root -p core_db < src\main\resources\db\seed.sql
```

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080 in a browser. Check the backend is up
first: `GET /api/health` returns `{"status":"UP"}` without signing
in.

## The demo

### 1. Register and sign in (30 seconds)

- In the account panel, create an account with a name, email and a
  password of at least 8 characters.
- Sign in with the same email and password.
- Point out: the top bar now shows "Signed in as ...", and the
  booking history panel becomes available.

### 2. Pick an event and a show (20 seconds)

- Choose "Live Concert - Demo Night" from the event list.
- Choose the show from the show list. Say: shows come from
  `GET /api/events/{eventId}/shows`, the user never types an ID.
- Click "Find seats". Twelve seats, A1-A12, appear.

### 3. Book a seat (20 seconds)

- Click an available seat, for example A5. It is highlighted.
- Click "Confirm booking".
- Point out: the toast says "Booking confirmed. Booking ID: ...",
  the seat turns booked, and the booking appears in "Your bookings".

### 4. Show the duplicate protection (30 seconds)

- In a second browser window, sign in as a second user.
- Load the same show and try to book A5. It is disabled in the grid.
- Say: the seat was locked and marked BOOKED in one transaction,
  and the cache was invalidated, so the second window sees it as
  booked. The automated version of this is the 20-request
  concurrency test: 1 success, 19 duplicate rejections.

### 5. Cancel and rebook (30 seconds)

- Back in the first window, click "Cancel" next to the booking in
  "Your bookings".
- Point out: the booking stays in history with status CANCELLED,
  and seat A5 becomes available again.
- Book A5 again to show rebooking works.

### 6. Show the evidence (30 seconds)

- Run the tests: `.\mvnw.cmd clean test` in `backend\`.
- Show `docs/TEST_EVIDENCE.md` for the recorded results.
- If asked, show `GET /api/shows/cache/stats` in the browser for
  cache hits, misses and capacity.

## If something goes wrong live

- "Could not load events": MySQL is not running, or the seed data
  was not loaded. Check `mysql -u root -p -e "USE core_db; SELECT * FROM events;"`.
- "Login failed": the account was not created, or the password is
  wrong. Register again with a new email.
- "Booking failed: Seat is already booked": that is the system
  working. Pick another available seat.
