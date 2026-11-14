# CORE: Concurrency-Safe Reservation Engine

A BTech Computer Science and Engineering project focused on building a concurrency-safe seat reservation system using Java, Spring Boot, MySQL, and a custom multithreaded booking engine.

## 1. Project Overview

CORE (Concurrency-Safe Reservation Engine) is a backend reservation system designed to manage seat bookings while preventing duplicate reservations during concurrent requests.

The system uses a bounded FIFO request queue, multiple worker threads, database transactions, pessimistic locking, and database-level constraints to protect booking consistency.

It also includes a seat availability cache with LRU eviction and generation-based invalidation to prevent stale database results from repopulating the cache after booking operations.

## 2. Phase 3 — What Was Added

Phase 3 completes the reservation journey and the reliability
evidence, on branch `feature/phase3-completion`:

- A real login and registration interface in the frontend, replacing
  the old browser-prompt login. Credentials are kept in memory only.
- Show selection: `GET /api/events/{eventId}/shows` lists the shows
  for an event, so the user picks a show from a list instead of
  typing a show ID.
- Booking history: `GET /api/bookings/my` returns the signed-in
  user's bookings, newest first, with a cancel button per booking.
  Seat numbers are now shown in the seat grid.
- Database schema and demo seed data, in
  `backend/src/main/resources/db/schema.sql` and `seed.sql`.
  The schema is derived from the JPA entities and the integration
  tests. Duplicate active bookings are prevented by the pessimistic
  row lock inside a transaction, not by a unique index — see
  `docs/ARCHITECTURE.md` and the comment in `schema.sql`.
- New tests: engine shutdown, 100-request stress, queue
  saturation, transaction rollback, cache consistency after
  booking and cancellation, and API tests for authentication,
  validation and booking history.
- Documentation: `docs/ARCHITECTURE.md`, `docs/TEST_EVIDENCE.md`,
  `docs/DEMO.md`, `docs/VIVA.md`.

## 3. Technology Stack

| Technology                | Purpose |

| Java 25                   | Backend programming language |
| Spring Boot 4.1.1         | Application framework |
| Spring Data JPA           | Database persistence |
| Hibernate                 | ORM |
| MySQL                     | Relational database |
| MariaDB Connector/J 4.0.0 | Runtime JDBC driver used only in the Phase 3 test VM to reach local MySQL over its Unix socket (see Section 6); the default remains the MySQL driver over TCP |
| Maven                     | Build and dependency management |
| JUnit 5                   | Automated testing |
| Spring Security           | Authentication and authorization |
| BCrypt                    | Password hashing |

## 3. Project Structure

```text
core-reservation-engine/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   ├── com/core/backend/
│   │   │   │   │   ├── cache/
│   │   │   │   │   ├── config/
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── exception/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── service/
│   │   │   │   │
│   │   │   │   └── core/reservation/engine/
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── static/
│   │   │
│   │   └── test/
│   │       └── java/
│   │
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
│
├── src/
│   └── core/reservation/engine/
│
├── .gitignore
└── README.md
```

## 4. System Architecture

The booking workflow follows these steps:

1. A user submits a booking request through the REST API.
2. The controller identifies the authenticated user.
3. The request is submitted to the booking engine.
4. The engine places the request in its bounded FIFO queue.
5. An available worker processes the request.
6. The booking service obtains a pessimistic database lock on the requested show seat.
7. The service checks whether the seat is available.
8. If available, the seat is marked BOOKED and a booking record is created within the transaction.
9. If already booked, the request is rejected.
10. The result is returned to the HTTP controller.

FIFO ordering applies to queue dequeue order. Since multiple workers execute concurrently, completion order is not guaranteed to be FIFO.

Requests submitted when the queue is full are rejected immediately.

## 5. Concurrency Protection

The project uses multiple layers of protection against duplicate active bookings:

- A bounded FIFO request queue controls incoming work.
- Multiple worker threads process requests concurrently.
- Pessimistic write locking protects the show-seat database row.
- Transactional booking operations ensure related database updates are handled together.
- There is deliberately **no** unique index on `bookings`: a unique
  index on `show_seat_id` alone would forbid rebooking a seat after
  a cancellation, because the cancelled booking row stays recorded.
  Duplicate active bookings are prevented by the pessimistic row
  lock inside a transaction (`BookingService.bookSeat`).

The concurrency integration test submits 20 concurrent requests for the same available show seat.

The expected result is:

- One successful booking.
- Nineteen duplicate booking rejections.
- Exactly one active confirmed booking.
- Final seat status of BOOKED.

### Seat Availability Cache

The project includes a thread-safe seat availability cache.

Its main characteristics are:

- LRU (Least Recently Used) eviction policy.
- Maximum capacity of 100 show entries.
- Synchronized cache access and updates.
- Cache hit and miss statistics.
- Immutable cached seat lists.
- Invalidation after successful booking and cancellation transactions.

#### Generation-Based Cache Invalidation

A generation counter protects against stale database results being inserted into the cache after an invalidation.

The mechanism works as follows:

1. A cache miss occurs.
2. The service records the current cache generation.
3. The service retrieves seat information from the database.
4. A booking or cancellation commits and invalidates the cache, incrementing the generation.
5. The original database read attempts to insert its result.
6. If the generation has changed, the cache rejects that result.

This prevents an older database read from repopulating the cache after an invalidation has occurred.

The cache provides a performance optimization. Database transactions and locking remain authoritative for booking correctness.

## 6. Database Configuration

The backend connects to a MySQL database named `core_db`.

Database credentials are supplied through environment variables.

### Environment Variables

Set the following PowerShell environment variables before starting the backend or running integration tests:

```powershell
$env:DB_USERNAME = "root"
$secure = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
```

These variables apply to the current PowerShell session. Set them again when opening a new session.

### Application Configuration

The relevant database settings in `application.properties` are:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/core_db}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=${DB_DRIVER:com.mysql.cj.jdbc.Driver}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
```

The defaults are MySQL over TCP on `localhost:3306`, which is what
a normal Windows setup uses. The URL and driver can be overridden
with `DB_URL` / `DB_DRIVER` environment variables without changing
the code. This override is only needed in the Phase 3 test VM,
where the runtime intercepts Java TCP connections to MySQL: there
the app and the tests run with the MariaDB driver over MySQL's
Unix socket
(`DB_URL=jdbc:mariadb://address=(localSocket=/var/run/mysqld/mysqld.sock)/core_db`,
`DB_DRIVER=org.mariadb.jdbc.Driver`). The MariaDB Connector/J
4.0.0 runtime dependency in `pom.xml` exists only to make that
VM setup work; the Windows default is still the MySQL driver over
TCP. Full detail is in `docs/TEST_EVIDENCE.md`.

The application uses `ddl-auto=validate`.

This validates the database schema rather than automatically creating or modifying it.

**Important:** The database schema is included in this repository
at `backend/src/main/resources/db/schema.sql`. Run it once on a
fresh MySQL install, then load demo data with `seed.sql`:

```powershell
mysql -u root -p < backend\src\main\resources\db\schema.sql
mysql -u root -p core_db < backend\src\main\resources\db\seed.sql
```

The database includes the following main entities:

- `users`
- `events`
- `shows`
- `seats`
- `show_seats`
- `bookings`

Duplicate active bookings are prevented by the pessimistic write
lock on the `show_seats` row inside a database transaction
(`BookingService.bookSeat`). There is no unique index on
`bookings`: a unique index on `show_seat_id` alone would forbid
rebooking a seat after a cancellation, because the cancelled
booking row stays recorded. See `docs/ARCHITECTURE.md`.

## 7. Running the Backend

### Prerequisites

Install the following:

- Java 25
- MySQL Server
- Git

The Maven wrapper is included in the backend directory.

### Step 1: Clone the Repository

```powershell
git clone https://github.com/Shubham-0x/core-reservation-engine.git
```

### Step 2: Open the Backend Directory

```powershell
cd core-reservation-engine/backend
```

### Step 3: Configure Database Credentials

Set the `DB_USERNAME` and `DB_PASSWORD` environment variables using the instructions in Section 6.

Ensure that MySQL is running and that the `core_db` database has the required schema.

### Step 4: Start the Application

```powershell
.\mvnw.cmd spring-boot:run
```

The application uses the default Spring Boot HTTP port unless otherwise configured.

## 8. API Endpoints

The backend exposes the following REST endpoints:

| Method | Endpoint | Description | Authentication |
|---|---|---|---|
| POST | `/api/auth/register` | Register a user | Public |
| POST | `/api/auth/login` | Authenticate a user | Public |
| GET | `/api/health` | Application health (`{"status":"UP"}`) | Public |
| GET | `/api/events` | Retrieve events | Public |
| GET | `/api/events/{eventId}/shows` | Retrieve shows for an event | Public |
| GET | `/api/shows/{showId}/seats` | Retrieve seats for a show | Public |
| GET | `/api/shows/cache/stats` | Retrieve seat cache statistics | Public |
| POST | `/api/bookings` | Book a show seat | Required |
| GET | `/api/bookings/my` | Retrieve my bookings, newest first | Required |
| PUT | `/api/bookings/{bookingId}/cancel` | Cancel an existing booking | Required |

### User Registration

Endpoint:

```http
POST /api/auth/register
```

Example request:

```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "password123"
}
```

Passwords are stored using BCrypt hashing.

### User Authentication

The application uses HTTP Basic authentication for protected endpoints.

Users must authenticate before accessing booking and cancellation operations.

### Retrieve Events

```http
GET /api/events
```

Returns available events.

### Retrieve Show Seats

```http
GET /api/shows/{showId}/seats
```

Returns seat availability information for the requested show.

### Retrieve Cache Statistics

```http
GET /api/shows/cache/stats
```

Returns cache statistics, including:

- Hits
- Misses
- Current entries
- Maximum capacity

### Book a Seat

```http
POST /api/bookings
```

Requires authentication.

The booking operation is processed through the booking engine and its worker queue.

### Cancel a Booking

```http
PUT /api/bookings/{bookingId}/cancel
```

Requires authentication.

A user can cancel their own booking. Successful cancellation makes the seat available again, allowing it to be booked by another request.

## 9. Running Tests

Run all backend tests from the `backend` directory.

Set the MySQL environment variables first because the integration tests require a working database connection.

Execute:

```powershell
.\mvnw.cmd clean test
```

The test suite includes:

- FIFO request processing.
- Queue-full request rejection.
- Invalid booking request rejection.
- Booking engine statistics.
- Spring application context loading.
- Database-backed concurrent booking protection.
- Cancellation and subsequent rebooking.
- Rejection of stale cache results after invalidation.
- Successful cache insertion when the generation remains unchanged.

The database integration tests require a working MySQL connection and a correctly prepared `core_db` schema.

## 10. Current Test Evidence

The final verified Phase 3 result, run 2026-11-14 against real
MySQL 8.0.46 (full suite, `clean test`):

```text
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

That is 11 unit tests (engine and cache) plus 14 database and API
tests (concurrency, rollback, cache consistency, auth and
validation, application context), all passing. The 9 Phase 2 tests
are a subset of these 25 and pass as part of the same run.

The real browser end-to-end journey against the running backend
also passed **23/23 checks** on 2026-11-14 (register, login and
logout, event and show selection, 12 seats, booking A5, duplicate
rejection with 409, cancel and rebook, history, responsive layout
at 390px). Full detail, including the per-test table and the
evidence for every number here, is in `docs/TEST_EVIDENCE.md`.
That file records only results from actual runs.

### Concurrent Booking Verification

The database concurrency integration test verified:

```text
Concurrent requests: 20
Successful bookings: 1
Duplicate rejections: 19
Active confirmed bookings: 1
Final seat status: BOOKED
```

### Cancellation and Rebooking Verification

The integration test verified:

- An initial booking succeeds.
- The booking can be cancelled by its owner.
- The seat becomes AVAILABLE.
- The same seat can subsequently be booked again.
- The cancelled booking remains recorded.
- Exactly one active confirmed booking exists after rebooking.

### Cache Regression Verification

The cache regression tests verified:

- A stale database result is rejected after cache invalidation.
- A result can be cached when the generation remains unchanged.

## 11. Current Implementation Scope

The current implementation covers:

- REST-based booking operations.
- User registration and authentication.
- Event and show-seat retrieval.
- Bounded FIFO request processing.
- Multiple worker threads.
- Transactional seat booking.
- Booking cancellation.
- Database pessimistic locking.
- Duplicate active booking protection.
- LRU seat availability caching.
- Generation-based cache invalidation.
- Cache hit and miss statistics.
- Automated unit and database integration tests.

The current implementation does not claim completion of write-ahead logging (WAL), checkpointing, crash recovery, or disk-based scheduling.

## 12. Git Branch and Development Status

Phase 2 is maintained on `feature/phase2-completion` and is
unchanged by Phase 3. Phase 3 work is on:

```text
feature/phase3-completion
```

The Phase 3 branch contains the completed frontend reservation
journey, the show and booking-history APIs, the database schema
and seed data, the additional reliability tests, and the final
documentation.

The development branches are maintained separately from `main`.

## 13. Future Scope

Potential future improvements include:

- Database schema migration management.
- More extensive cache concurrency testing.
- Additional API and security integration tests.
- Persistent request logging.
- Write-ahead logging and recovery mechanisms.
- Checkpointing and crash recovery.
- Performance benchmarking under higher concurrent loads.

These are potential future improvements and are not claimed as completed features.

---

**Project:** CORE, Concurrency-Safe Reservation Engine
**Project Type:** BTech Computer Science and Engineering
**Backend:** Java, Spring Boot, MySQL
**Build Tool:** Maven
**Testing:** JUnit 5 and Spring Boot integration tests

**GitHub Repository:**
https://github.com/Shubham-0x/core-reservation-engine