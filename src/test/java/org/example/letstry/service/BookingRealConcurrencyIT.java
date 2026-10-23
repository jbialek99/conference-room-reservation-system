package org.example.letstry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "booking.mode=demo",
        "booking.recovery.enabled=false"
})
class BookingRealConcurrencyIT {

    @Autowired
    private BookingReservationTransactionService bookingTransactions;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void concurrentReservationsShouldCreateExactlyOneBooking() throws Exception {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()",
                String.class
        );

        assertEquals(
                "conference_pr6_backup_verify",
                database,
                "Test musi dzialac wylacznie na bazie kontrolnej."
        );

        EntityManager entityManager = entityManagerFactory.createEntityManager();

        Long hallId;
        User user;

        try {
            List<Long> hallIds = jdbcTemplate.queryForList(
                    "SELECT id FROM halls ORDER BY id",
                    Long.class
            );

            Hall selectedHall = null;

            for (Long candidateId : hallIds) {
                Hall candidate = entityManager.find(Hall.class, candidateId);

                if (candidate != null && candidate.isAvailable()) {
                    selectedHall = candidate;
                    break;
                }
            }

            assertNotNull(selectedHall, "Brak dostepnej sali testowej.");
            hallId = selectedHall.getId();

            Long userId = jdbcTemplate.queryForObject(
                    "SELECT MIN(id) FROM users",
                    Long.class
            );

            assertNotNull(userId, "Brak uzytkownika testowego.");

            user = entityManager.find(User.class, userId);
            assertNotNull(user);
        } finally {
            entityManager.close();
        }

        String title = "PR6_JAVA_CONCURRENCY_" + UUID.randomUUID();

        Instant start = Instant.parse("2042-04-15T10:00:00Z");
        Instant end = Instant.parse("2042-04-15T11:00:00Z");

        Integer existingBookings = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reservations
                WHERE hall_id = ?
                  AND status IN ('PENDING', 'CONFIRMED')
                  AND start_meeting < ?
                  AND end_meeting > ?
                """,
                Integer.class,
                hallId,
                java.sql.Timestamp.from(end),
                java.sql.Timestamp.from(start)
        );

        assertEquals(
                0,
                existingBookings,
                "Wybrany termin jest juz zajety."
        );

        CreateReservationRequest request = new CreateReservationRequest(
                title,
                start,
                end
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        Callable<BookingOutcome> attempt = () -> {
            ready.countDown();

            if (!startSignal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Nie otrzymano sygnalu startu.");
            }

            try {
                Reservation reservation = bookingTransactions.reserveDemo(
                        hallId,
                        user,
                        request
                );

                return new BookingOutcome(true, reservation.getId());
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                    return new BookingOutcome(false, null);
                }

                throw exception;
            }
        };

        List<Future<BookingOutcome>> futures = new ArrayList<>();

        try {
            futures.add(executor.submit(attempt));
            futures.add(executor.submit(attempt));

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Watki nie byly gotowe."
            );

            startSignal.countDown();

            BookingOutcome first = futures.get(0).get(30, TimeUnit.SECONDS);
            BookingOutcome second = futures.get(1).get(30, TimeUnit.SECONDS);

            long successes = List.of(first, second)
                    .stream()
                    .filter(BookingOutcome::success)
                    .count();

            assertEquals(1, successes, "Dokladnie jedna rezerwacja musi sie udac.");

            Long winningId = first.success() ? first.bookingId() : second.bookingId();

            assertNotNull(winningId);

            Integer persisted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reservations WHERE title = ?",
                    Integer.class,
                    title
            );

            assertEquals(1, persisted);

            Map<String, Object> state = jdbcTemplate.queryForMap(
                    """
                    SELECT status, integration_state, outlook_event_id
                    FROM reservations
                    WHERE id = ?
                    """,
                    winningId
            );

            assertEquals(
                    ReservationStatus.CONFIRMED.name(),
                    state.get("status").toString()
            );

            assertEquals(
                    BookingIntegrationState.DEMO.name(),
                    state.get("integration_state").toString()
            );

            assertNull(state.get("outlook_event_id"));

            List<Map<String, Object>> calendar =
                    reservationService.getOutlookReservationsByHall(hallId);

            Map<String, Object> event = calendar.stream()
                    .filter(item ->
                            ("demo-" + winningId).equals(item.get("id"))
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new AssertionError("Rezerwacji nie ma w kalendarzu DEMO.")
                    );

            assertEquals(title, event.get("title"));
            assertEquals(start.toString(), event.get("start"));
            assertEquals(end.toString(), event.get("end"));
        } finally {
            startSignal.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(15, TimeUnit.SECONDS),
                    "Watki testowe nie zakonczyly pracy."
            );

            jdbcTemplate.update(
                    "DELETE FROM reservations WHERE title = ?",
                    title
            );
        }
    }

    private record BookingOutcome(
            boolean success,
            Long bookingId
    ) {
    }
}