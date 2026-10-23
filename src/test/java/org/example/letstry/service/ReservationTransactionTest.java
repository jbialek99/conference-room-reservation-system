package org.example.letstry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationTransactionTest {

    private EntityManager entityManager;
    private ReservationRepository repository;
    private ReservationService service;
    private Hall hall;
    private User user;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        repository = mock(ReservationRepository.class);

        service = spy(new ReservationService(
                repository,
                mock(HallService.class),
                mock(GraphTokenService.class),
                WebClient.builder().build(),
                mock(OutlookRoomResponseMapper.class)
        ));

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );

        hall = new Hall();
        hall.setId(7L);
        hall.setName("Sala Alfa");
        hall.setAvailable(true);
        hall.setEmail("room@example.com");

        user = new User();

        when(entityManager.find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(hall);
    }

    @Test
    void shouldRejectUnknownHall() {
        when(entityManager.find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(7L, user, request(), "token")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository, never()).save(any(Reservation.class));
    }

    @Test
    void shouldRejectUnavailableHall() {
        hall.setAvailable(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(7L, user, request(), "token")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void shouldRejectHallWithoutExchange() {
        hall.setEmail(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(7L, user, request(), "token")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void shouldRejectOverlappingReservation() {
        doReturn(true)
                .when(service)
                .isReservedAtThisTime(eq(7L), any(Reservation.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(7L, user, request(), "token")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(service, never()).createOutlookEventForUser(
                anyString(),
                any(Reservation.class),
                any(Hall.class)
        );
    }

    @Test
    void shouldPrepareAndSendReservationToGraph() {
        doReturn(false)
                .when(service)
                .isReservedAtThisTime(eq(7L), any(Reservation.class));

        doReturn(ReservationStatus.PENDING)
                .when(service)
                .createOutlookEventForUser(
                        eq("token"),
                        any(Reservation.class),
                        eq(hall)
                );

        ReservationResult result = service.reserve(
                7L,
                user,
                request(),
                "token"
        );

        assertEquals(ReservationStatus.PENDING, result.status());

        verify(entityManager).find(
                Hall.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE
        );

        verify(service).createOutlookEventForUser(
                eq("token"),
                any(Reservation.class),
                eq(hall)
        );
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