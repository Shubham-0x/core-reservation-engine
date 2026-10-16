
package com.core.backend.config;

import core.reservation.engine.BookingEngine;
import core.reservation.engine.BookingOperation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingEngineConfig {

    @Bean(destroyMethod = "close")
    public BookingEngine bookingEngine(
            BookingOperation bookingOperation) {

        return new BookingEngine(
                bookingOperation,
                4,
                200
        );
    }
}
