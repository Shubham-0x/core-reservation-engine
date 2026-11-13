# CORE: Viva Questions and Answers

Short, honest answers. Do not claim anything that is not in the
code or in `docs/TEST_EVIDENCE.md`.

## Concurrency

**Q: How do you prevent two users booking the same seat?**
A: Three layers. The booking goes through a bounded queue with
worker threads, then `BookingService.bookSeat` runs in one
transaction that locks the `show_seats` row with
`PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`). The first request
marks the seat BOOKED and commits; the second waits for the lock,
sees BOOKED, and is rejected with `SEAT_ALREADY_BOOKED`. Nothing
is written for the second request.

**Q: Why pessimistic locking instead of optimistic locking?**
A: Contention is the normal case here, not the exception: many
users want the same popular seat. Pessimistic locking makes the
second request wait and then fail cleanly, instead of letting both
do the work and failing one at commit time. It is also simpler to
explain and to test.

**Q: What does the bounded queue do when it is full?**
A: `ArrayBlockingQueue.offer` returns false immediately and the
request is rejected with `BOOKING_FAILED` and the message
"Request queue is full". The caller is not made to wait, and the
request is never silently dropped. This is covered by a unit test.

**Q: Is the booking order FIFO?**
A: Dequeue order is FIFO, because the queue is fair. Completion
order is not guaranteed FIFO, because four workers process
requests at the same time. The README and the code comment say
exactly that.

**Q: What happens on shutdown?**
A: `BookingEngine.close` stops accepting new requests, lets the
workers finish everything already queued, and only then returns.
Requests submitted after close are rejected. This is covered by
`BookingEngineShutdownTests`.

## Database

**Q: What happens if booking fails halfway through?**
A: `bookSeat` is `@Transactional`. If any step throws, the
transaction rolls back: the seat stays AVAILABLE and no booking
row is created. The duplicate-booking test checks this: after a
failed second booking there is still exactly one CONFIRMED booking
and the seat is BOOKED.

**Q: Is there a unique constraint stopping duplicate bookings?**
A: No, and that is deliberate. A unique index on `show_seat_id`
alone would forbid rebooking a seat after a cancellation, because
the cancelled booking row stays in the table. Duplicate active
bookings are prevented by the row lock plus the transaction, and
the 20-request concurrency test proves it: 1 success, 19
rejections, 1 active booking.

**Q: Why is `ddl-auto` set to `validate`?**
A: So Hibernate never creates or changes tables by itself. The
schema lives in `db/schema.sql`, it is reviewed like code, and
running the app cannot accidentally change production data.

**Q: How does cancellation keep the seat and the booking consistent?**
A: Cancellation is one transaction too: it locks the seat row,
sets the booking to CANCELLED and the seat to AVAILABLE, commits,
and only then invalidates the cache. If it fails, neither change
is saved.

## Cache

**Q: Why have a cache at all?**
A: Seat lists are read far more often than they are booked. The
cache avoids a database read for every seat-grid refresh.

**Q: What if the cache shows a seat as available after it was booked?**
A: It cannot after a committed booking, because the cache is
invalidated in `afterCommit`, not before. The generation check
adds a second guard: if a slow database read was in progress
while a booking committed, its result is not cached.
`CacheConsistencyIntegrationTests` checks this end to end:
AVAILABLE, then BOOKED after booking, then AVAILABLE after
cancellation.

**Q: Why LRU with capacity 100?**
A: Seat lists are per show, and only recent shows are looked at.
LRU keeps the working set small and bounded, so memory use cannot
grow without limit.

## API and frontend

**Q: How does the frontend know which seats are available?**
A: `GET /api/shows/{showId}/seats` returns every seat for the
show with its status and seat number. The frontend disables
booked seats so they cannot be clicked, and the backend still
re-checks under the lock, so a stale page cannot create a
duplicate booking.

**Q: How does booking history work?**
A: `GET /api/bookings/my` returns the signed-in user's bookings,
newest first. The controller looks the user up from the
authentication, so a user can never ask for another user's
history by changing an ID in the URL.

**Q: How is a user authenticated?**
A: HTTP Basic authentication against the users table, with
BCrypt-hashed passwords. Registration validates name, email and
a password of at least 8 characters. The frontend keeps the
credentials in memory only, never in localStorage.

## Testing

**Q: What did you actually test?**
A: See `docs/TEST_EVIDENCE.md`. Unit tests for the engine
(FIFO, queue-full, invalid requests, statistics, shutdown,
stress) and the cache. Database integration tests for the
20-request concurrency case, cancellation and rebooking,
rollback behaviour, and cache consistency. API tests for
registration, duplicate registration, validation, wrong
password, unauthenticated access, booking history, and
cancelling another user's booking.

**Q: What is the stress test?**
A: 100 concurrent requests for 10 seats through the engine:
exactly 10 succeed and 90 are rejected as duplicates.
Separately, a queue-saturation test blocks one worker, fills
a queue of 5, and checks that the next 9 requests are rejected
immediately instead of waiting.
