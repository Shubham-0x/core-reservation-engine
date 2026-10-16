package core.reservation.engine;

public record BookingStats(long totalRequests, long successfulBookings,
                           long rejectedBookings, long duplicateBookings) {}
