package org.example.letstry.service;

import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingIntegrationStateTest {

    private ReservationService createService() {
        return new ReservationService(
                mock(ReservationRepository.class),
                mock(HallService.class),
                mock(GraphTokenService.class),
                WebClient.builder().build(),
                mock(OutlookRoomResponseMapper.class)
        );
    }

    private Reservation createReservation() {
        Instant start = Instant.now().plusSeconds(3600);

        Reservation reservation = new Reservation();
        reservation.setTitle("Spotkanie");
        reservation.setStartMeeting(start);
        reservation.setEndMeeting(start.plusSeconds(3600));

        return reservation;
    }

    @Test
    void shouldCreateUniqueGraphTransactionId() {
        ReservationService service = createService();

        Reservation first = createReservation();
        Reservation second = createReservation();

        service.createReservation(new Hall(), new User(), first);
        service.createReservation(new Hall(), new User(), second);

        assertNotNull(first.getGraphTransactionId());
        assertNotNull(second.getGraphTransactionId());

        UUID.fromString(first.getGraphTransactionId());
        UUID.fromString(second.getGraphTransactionId());

        assertNotEquals(
                first.getGraphTransactionId(),
                second.getGraphTransactionId()
        );
    }

    @Test
    void shouldInitializeIntegrationStateAsCreating() {
        Reservation reservation = createReservation();

        createService().createReservation(
                new Hall(),
                new User(),
                reservation
        );

        assertEquals(
                BookingIntegrationState.CREATING,
                reservation.getIntegrationState()
        );

        assertEquals(
                ReservationStatus.PENDING,
                reservation.getStatus()
        );
    }

    @Test
    void shouldPreserveTransactionIdOnExistingReservation() {
        Reservation reservation = createReservation();

        createService().createReservation(
                new Hall(),
                new User(),
                reservation
        );

        String transactionId = reservation.getGraphTransactionId();

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setIntegrationState(BookingIntegrationState.LINKED);

        assertEquals(
                transactionId,
                reservation.getGraphTransactionId()
        );

        assertEquals(
                BookingIntegrationState.LINKED,
                reservation.getIntegrationState()
        );
    }
}