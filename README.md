# CORE Reservation Engine

A concurrency-safe ticket reservation system built with Java, Spring Boot, and MySQL. The project demonstrates concurrent request processing, bounded FIFO queues, worker threads, database transactions, pessimistic locking, and protection against duplicate active seat reservations.

## 1. Project Overview

CORE is a ticket reservation backend designed to handle multiple users attempting to reserve the same seat concurrently.

The system combines a Java-based booking request engine with a Spring Boot REST API and a relational MySQL database.

Its main objectives are:

- Process booking requests through a bounded FIFO queue.
- Use a fixed worker pool for concurrent request processing.
- Prevent multiple users from successfully booking the same show seat.
- Use database transactions and pessimistic locking to protect booking operations.
- Support booking cancellation and subsequent seat availability.
- Provide event and seat availability endpoints.

## 2. Technology Stack

- Java 25
- Spring Boot
- Spring Data JPA
- Spring Security
- MySQL
- Maven
- JUnit 5

## 3. Project Structure

```text
core-reservation-engine/
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
│   │   │   │   └── core/reservation/engine/
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── static/
│   │   └── test/
│   │       └── java/
│   └── pom.xml
├── src/
├── .gitignore
└── README.md
```

## 4. Booking Engine Architecture

The booking engine uses a bounded FIFO queue and a fixed worker pool.

Current configuration:

| Component | Configuration |
|---|---:|
| Worker threads | 4 |
| Queue capacity | 200 |
| Queue type | Fair ArrayBlockingQueue |
| Request timeout at HTTP layer | 10 seconds |

### Request Processing

1. An authenticated user submits a booking request.
2. The request is validated and submitted to the booking engine.
3. The engine places the request in its bounded FIFO queue.
4. An available worker processes the request.
5. The booking service obtains a pessimistic database lock on the requested show seat.
6. The service checks whether the seat is available.
7. If available, the seat is marked BOOKED and a booking record is created within the transaction.
8. If already booked, the request is rejected.
9. The result is returned to the HTTP controller.

FIFO ordering applies to queue dequeue order. Since multiple workers execute concurrently, completion order is not guaranteed to be FIFO.

Requests submitted when the queue is full are rejected immediately.

## 5. Concurrency Protection

The project uses multiple layers of protection against duplicate active bookings:

- A bounded FIFO request queue controls incoming work.
- Multiple worker threads process requests concurrently.
- Pessimistic write locking protects the show-seat database row.
- Transactional booking operations ensure related database updates are handled together.
- A database-level unique constraint on active bookings provides an additional safeguard.

The integration test submits 20 concurrent requests for the same available show seat. The expected result is one successful booking and 19 duplicate booking rejections.

## 6. Database Configuration

The backend connects to a MySQL database named `core_db`.

The application configuration uses environment variables for database credentials.

Set the following PowerShell environment variables before starting the backend:

```powershell
$env:DB_USERNAME = "root"
$secure = Read-Host "Enter your MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
```

The database must already exist and contain the tables and relationships expected by the JPA entities.

The application uses:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

This validates the database schema rather than automatically creating or modifying it.

**Important:** A database creation or migration SQL script is not currently included in this repository. The database must be prepared separately before running the application and integration tests.

## 7. Running the Backend

Prerequisites:

- Java 25
- MySQL
- A configured `core_db` database
- Database credentials supplied through environment variables

Open PowerShell in the `backend` directory.

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The Spring Boot application uses its configured default port unless overridden.

The frontend static files are located in:

```text
backend/src/main/resources/static/
```

## 8. REST API Endpoints

| Method | Endpoint | Description | Authentication |
|---|---|---|---|
| POST | `/api/auth/register` | Register a user | Public |
| POST | `/api/auth/login` | Authenticate a user | Public |
| GET | `/api/events` | Retrieve events | Public |
| GET | `/api/shows/{showId}/seats` | Retrieve seats for a show | Public |
| GET | `/api/shows/cache/stats` | Retrieve seat cache statistics | Public |
| POST | `/api/bookings` | Book a show seat | Required |
| PUT | `/api/bookings/{bookingId}/cancel` | Cancel an existing booking | Required |

### User Registration

Request:

```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "password123"
}
```

Endpoint:

```text
POST /api/auth/register
```

Passwords are encoded using BCrypt.

### User Login

Endpoint:

```text
POST /api/auth/login
```

Authentication is handled through HTTP Basic authentication. The login endpoint returns a confirmation response after successful authentication.

### Book a Seat

Request:

```json
{
  "showSeatId": 1
}
```

Endpoint:

```text
POST /api/bookings
```

Requires HTTP Basic authentication.

A successful booking returns HTTP 201. A seat that has already been booked returns HTTP 409.

### Cancel a Booking

Endpoint:

```text
PUT /api/bookings/{bookingId}/cancel
```

Requires authentication. A user can cancel their own booking.

## 9. Running Tests

Run all backend tests from the `backend` directory:

```powershell
.\mvnw.cmd test
```

The test suite includes:

- FIFO request processing.
- Queue-full request rejection.
- Invalid booking request rejection.
- Booking engine statistics.
- Spring application context loading.
- Database-backed concurrent booking protection.

The concurrency integration test requires a working MySQL connection and a correctly prepared `core_db` schema.

## 10. Current Test Evidence

The current verified test result:

```text
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0
```

The database concurrency integration test verified:

```text
Concurrent requests: 20
Successful bookings: 1
Duplicate rejections: 19
Active confirmed bookings: 1
Final seat status: BOOKED
```

## 11. Current Implementation Scope

The current implementation covers the REST booking workflow, bounded FIFO request processing, worker threads, transactional seat booking, cancellation, database locking, and duplicate booking protection.

Features such as write-ahead logging, checkpointing, crash recovery, and disk scheduling are not claimed as completed backend functionality.

## 12. Repository

GitHub:

https://github.com/Shubham-0x/core-reservation-engine.git
