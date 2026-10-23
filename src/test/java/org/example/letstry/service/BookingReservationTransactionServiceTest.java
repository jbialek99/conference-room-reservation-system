package org.example.letstry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingReservationTransactionServiceTest {

    private ReservationRepository repository;
    private EntityManager entityManager;
    private BookingReservationTransactionService service;
    private Hall hall;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        entityManager = mock(EntityManager.class);

        service = new BookingReservationTransactionService(
                repository,
                entityManager
        );

        hall = new Hall();
        hall.setId(7L);
        hall.setAvailable(true);
        hall.setEmail("room@example.com");

        user = new User();

        when(entityManager.find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(hall);

        when(repository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldSaveCreatingReservationUnderHallLock() {
        Reservation reservation = service.prepare(7L, user, request());

        assertSame(hall, reservation.getHall());
        assertSame(user, reservation.getUser());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertEquals(BookingIntegrationState.CREATING, reservation.getIntegrationState());
        assertNotNull(reservation.getGraphTransactionId());

        verify(entityManager).find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        );

        verify(repository).saveAndFlush(reservation);
    }

    @Test
    void shouldRejectConflictWithoutSaving() {
        when(repository.existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
                eq(7L),
                eq(List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.prepare(7L, user, request())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldPreservePendingReservationOnUncertainResult() {
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIntegrationState(BookingIntegrationState.CREATING);

        when(repository.findById(42L))
                .thenReturn(java.util.Optional.of(reservation));

        service.markUncertain(42L);

        assertEquals(
                BookingIntegrationState.UNCERTAIN,
                reservation.getIntegrationState()
        );
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());

        verify(repository).save(reservation);
    }

    @Test
    void shouldLinkExistingReservation() {
        Reservation stored = new Reservation();
        stored.setId(42L);
        stored.setIntegrationState(BookingIntegrationState.CREATING);

        Reservation graphResult = new Reservation();
        graphResult.setOutlookEventId("event-123");
        graphResult.setTitle("Spotkanie");
        graphResult.setStatus(ReservationStatus.PENDING);

        when(repository.findById(42L))
                .thenReturn(java.util.Optional.of(stored));

        service.markLinked(42L, graphResult);

        assertEquals("event-123", stored.getOutlookEventId());
        assertEquals(BookingIntegrationState.LINKED, stored.getIntegrationState());
        verify(repository).save(stored);
    }

    private CreateReservationRequest request() {
        Instant start = Instant.now().plusSeconds(3600);

        return new CreateReservationRequest(
                "Spotkanie",
                start,
                start.plusSeconds(3600)
        );
    }
}