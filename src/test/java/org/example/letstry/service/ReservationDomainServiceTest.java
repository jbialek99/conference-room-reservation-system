package org.example.letstry.service;

import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationDomainServiceTest {

    private ReservationRepository repository;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);

        service = new ReservationService(
                repository,
                mock(HallService.class),
                mock(GraphTokenService.class),
                WebClient.builder().build(),
                mock(OutlookRoomResponseMapper.class)
        );
    }

    @Test
    void shouldPrepareReservationWithoutSavingBeforeGraphConfirmation() {
        Hall hall = new Hall();
        User user = new User();
        Reservation reservation = validReservation();

        service.createReservation(hall, user, reservation);

        assertSame(hall, reservation.getHall());
        assertSame(user, reservation.getUser());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertNotNull(reservation.getDate());

        verify(repository, never()).save(any(Reservation.class));
    }

    @Test
    void shouldRejectInvalidTimeRange() {
        Reservation reservation = validReservation();
        reservation.setEndMeeting(reservation.getStartMeeting());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createReservation(new Hall(), new User(), reservation)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectMissingReservationData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createReservation(null, new User(), validReservation())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createReservation(new Hall(), null, validReservation())
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldCheckPendingAndConfirmedReservations() {
        Reservation reservation = validReservation();

        when(repository.existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
                eq(7L),
                eq(List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)),
                eq(reservation.getEndMeeting()),
                eq(reservation.getStartMeeting())
        )).thenReturn(true);

        assertTrue(service.isReservedAtThisTime(7L, reservation));
    }

    @Test
    void shouldAllowFreeTimeRange() {
        assertFalse(service.isReservedAtThisTime(7L, validReservation()));
    }

    @Test
    void shouldRejectInvalidRangeDuringConflictCheck() {
        Reservation reservation = validReservation();
        reservation.setEndMeeting(reservation.getStartMeeting().minusSeconds(60));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.isReservedAtThisTime(7L, reservation)
        );

        verifyNoInteractions(repository);
    }

    private Reservation validReservation() {
        Reservation reservation = new Reservation();
        Instant start = Instant.now().plusSeconds(3600);
        reservation.setStartMeeting(start);
        reservation.setEndMeeting(start.plusSeconds(3600));
        reservation.setTitle("Spotkanie");
        return reservation;
    }
}