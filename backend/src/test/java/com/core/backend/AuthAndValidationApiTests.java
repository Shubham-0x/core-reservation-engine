package com.core.backend;

import com.core.backend.entity.User;
import com.core.backend.entity.UserRole;
import com.core.backend.repository.UserRepository;
import core.reservation.engine.BookingEngine;
import core.reservation.engine.BookingResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAndValidationApiTests {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BookingEngine bookingEngine;

    @Test
    void registerReturnsCreatedAndDuplicateReturnsConflict() throws Exception {
        String email = uniqueEmail("register");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Api Test", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Api Test", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isConflict());

        userRepository.findByEmail(email)
                .ifPresent(user -> userRepository.deleteById(user.getId()));
    }

    @Test
    void registerWithShortPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Api Test", "email": "%s", "password": "short"}
                                """.formatted(uniqueEmail("short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        User user = createUser(uniqueEmail("login"));

        try {
            mockMvc.perform(post("/api/auth/login")
                            .header("Authorization", basic(user.getEmail(), "wrong-password")))
                    .andExpect(status().isUnauthorized());
        } finally {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void myBookingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookSeatWithInvalidShowSeatIdReturnsBadRequest() throws Exception {
        User user = createUser(uniqueEmail("booking"));

        try {
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", basic(user.getEmail(), PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"showSeatId\": 0}"))
                    .andExpect(status().isBadRequest());
        } finally {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void myBookingsReturnsOnlyMyBookings() throws Exception {
        User user = createUser(uniqueEmail("history"));
        TestData data = createTestData(user.getId());

        try {
            BookingResult result = bookingEngine
                    .submitRequest(user.getId(), data.showSeatId())
                    .get(20, TimeUnit.SECONDS);

            assert result.success();

            mockMvc.perform(get("/api/bookings/my")
                            .header("Authorization", basic(user.getEmail(), PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(result.bookingId()))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        } finally {
            cleanup(data);
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void cancelOtherUsersBookingReturnsNotFound() throws Exception {
        User owner = createUser(uniqueEmail("owner"));
        User other = createUser(uniqueEmail("other"));
        TestData data = createTestData(owner.getId());

        try {
            BookingResult result = bookingEngine
                    .submitRequest(owner.getId(), data.showSeatId())
                    .get(20, TimeUnit.SECONDS);

            assert result.success();

            mockMvc.perform(put("/api/bookings/" + result.bookingId() + "/cancel")
                            .header("Authorization", basic(other.getEmail(), PASSWORD)))
                    .andExpect(status().isNotFound());
        } finally {
            cleanup(data);
            userRepository.deleteById(owner.getId());
            userRepository.deleteById(other.getId());
        }
    }

    private User createUser(String email) {
        User user = new User();
        user.setName("Api Test");
        user.setEmail(email);
        user.setRole(UserRole.USER);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));

        return userRepository.save(user);
    }

    private String basic(String email, String password) {
        String token = Base64.getEncoder().encodeToString(
                (email + ":" + password).getBytes(StandardCharsets.UTF_8));

        return "Basic " + token;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8) +
                "@example.com";
    }

    private TestData createTestData(Long userId) {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        Long eventId = insertAndGetId(
                "INSERT INTO events (name, description) VALUES (?, ?)",
                "Test Event " + unique,
                "Temporary API test event"
        );

        Long showId = insertAndGetId(
                "INSERT INTO shows (event_id, show_time) VALUES (?, ?)",
                eventId,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        );

        Long seatId = insertAndGetId(
                "INSERT INTO seats (seat_number) VALUES (?)",
                "A" + unique
        );

        Long showSeatId = insertAndGetId(
                "INSERT INTO show_seats (show_id, seat_id, status) " +
                "VALUES (?, ?, 'AVAILABLE')",
                showId,
                seatId
        );

        return new TestData(userId, eventId, showId, seatId, showSeatId);
    }

    private Long insertAndGetId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql, Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }

            return statement;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private void cleanup(TestData data) {
        if (data.showSeatId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM bookings WHERE show_seat_id = ?",
                    data.showSeatId());
            jdbcTemplate.update(
                    "DELETE FROM show_seats WHERE id = ?",
                    data.showSeatId());
        }

        if (data.seatId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM seats WHERE id = ?",
                    data.seatId());
        }

        if (data.showId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM shows WHERE id = ?",
                    data.showId());
        }

        if (data.eventId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM events WHERE id = ?",
                    data.eventId());
        }
    }

    private record TestData(
            Long userId,
            Long eventId,
            Long showId,
            Long seatId,
            Long showSeatId) {
    }
}
