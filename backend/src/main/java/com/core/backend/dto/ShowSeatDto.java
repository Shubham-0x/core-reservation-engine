
package com.core.backend.dto;

import com.core.backend.entity.SeatStatus;

public record ShowSeatDto(
        Long id,
        Long showId,
        Long seatId,
        SeatStatus status
) {}
