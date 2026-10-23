package org.example.letstry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DemoReservationTest {

    private ReservationRepository repository;
    private EntityManager entityManager;
    private BookingReservationTransactionService transactions;
    private ReservationService service;
    private Hall hall;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        entityManager = mock(EntityManager.class);

        transactions = new BookingReservationTransactionService(
                repository,
                entityManager
        );

        ReflectionTestUtils.setField(
                transactions,
                "bookingMode",
                "demo"
        );

        service = spy(new ReservationService(
                repository,
                mock(HallService.class),
                mock(GraphTokenService.class),
                WebClient.builder().build(),
                mock(OutlookRoomResponseMapper.class)
        ));

        ReflectionTestUtils.setField(
                service,
                "bookingMode",
                "demo"
        );

        ReflectionTestUtils.setField(
                service,
                "bookingTransactionService",
                transactions
        );

        hall = new Hall();
        hall.setId(7L);
        hall.setName("Sala Alfa");
        hall.setAvailable(true);
        hall.setEmail(null);

        user = new User();

        when(entityManager.find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(hall);

        when(repository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    reservation.setId(42L);
                    return reservation;
                });
    }

    @Test
    void shouldReserveHallWithoutExchange() {
        ReservationResult result = service.reserve(
                7L,
                user,
                request(),
                null
        );

        assertEquals(42L, result.bookingId());
        assertEquals(ReservationStatus.CONFIRMED, result.status());

        verify(repository, times(2))
                .saveAndFlush(any(Reservation.class));
    }

    @Test
    void shouldPersistDemoState() {
        Reservation reservation = transactions.reserveDemo(
                7L,
                user,
                request()
        );

        assertEquals(BookingIntegrationState.DEMO,
                reservation.getIntegrationState());

        assertEquals(ReservationStatus.CONFIRMED,
                reservation.getStatus());

        assertNull(reservation.getGraphTransactionId());
        assertNull(reservation.getOutlookEventId());
    }

    @Test
    void shouldRejectConflictingDemoReservation() {
        when(repository.existsByHallIdAndStatusInAndIntegrationStateAndStartMeetingBeforeAndEndMeetingAfter(
                eq(7L),
                eq(List.of(
                        ReservationStatus.PENDING,
                        ReservationStatus.CONFIRMED
                )),
                eq(BookingIntegrationState.DEMO),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(true);

        assertThrows(
                ResponseStatusException.class,
                () -> transactions.reserveDemo(7L, user, request())
        );

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCheckOnlyDemoReservationsForConflicts() {
        Reservation reservation = transactions.reserveDemo(
                7L,
                user,
                request()
        );

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());

        verify(repository)
                .existsByHallIdAndStatusInAndIntegrationStateAndStartMeetingBeforeAndEndMeetingAfter(
                        eq(7L),
                        eq(List.of(
                                ReservationStatus.PENDING,
                                ReservationStatus.CONFIRMED
                        )),
                        eq(BookingIntegrationState.DEMO),
                        any(Instant.class),
                        any(Instant.class)
                );

        verify(repository, never())
                .existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
                        anyLong(),
                        anyList(),
                        any(Instant.class),
                        any(Instant.class)
                );
    }
    @Test
    void shouldRejectUnavailableHall() {
        hall.setAvailable(false);

        assertThrows(
                ResponseStatusException.class,
                () -> transactions.reserveDemo(7L, user, request())
        );
    }

    @Test
    void shouldRejectDemoBookingWhenModeIsGraph() {
        ReflectionTestUtils.setField(
                transactions,
                "bookingMode",
                "graph"
        );

        assertThrows(
                IllegalStateException.class,
                () -> transactions.reserveDemo(7L, user, request())
        );

        verifyNoInteractions(entityManager);
    }

    private CreateReservationRequest request() {
        Instant start = Instant.now().plusSeconds(3600);

        return new CreateReservationRequest(
                "Spotkanie demonstracyjne",
                start,
                start.plusSeconds(3600)
        );
    }
}