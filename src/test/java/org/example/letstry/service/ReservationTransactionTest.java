package org.example.letstry.service;

import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationTransactionTest {

    private ReservationService service;
    private BookingReservationTransactionService transactions;
    private Reservation reservation;
    private CreateReservationRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        transactions = mock(BookingReservationTransactionService.class);

        service = spy(new ReservationService(
                mock(ReservationRepository.class),
                mock(HallService.class),
                mock(GraphTokenService.class),
                WebClient.builder().build(),
                mock(OutlookRoomResponseMapper.class)
        ));

        ReflectionTestUtils.setField(
                service,
                "bookingTransactionService",
                transactions
        );

        user = new User();

        Hall hall = new Hall();
        hall.setId(7L);
        hall.setName("Sala Alfa");
        hall.setEmail("room@example.com");

        Instant start = Instant.now().plusSeconds(3600);

        request = new CreateReservationRequest(
                "Spotkanie",
                start,
                start.plusSeconds(3600)
        );

        reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHall(hall);
        reservation.setUser(user);
        reservation.setTitle("Spotkanie");
        reservation.setStartMeeting(start);
        reservation.setEndMeeting(start.plusSeconds(3600));

        when(transactions.prepare(7L, user, request))
                .thenReturn(reservation);
    }

    @Test
    void shouldSaveResultAfterGraphSuccess() {
        doReturn(ReservationStatus.PENDING)
                .when(service)
                .createOutlookEventForUser(
                        eq("token"),
                        same(reservation),
                        same(reservation.getHall())
                );

        ReservationResult result = service.reserve(
                7L,
                user,
                request,
                "token"
        );

        assertEquals(42L, result.bookingId());
        assertEquals(ReservationStatus.PENDING, result.status());

        var order = inOrder(transactions, service);

        order.verify(transactions).prepare(7L, user, request);
        order.verify(service).createOutlookEventForUser(
                eq("token"),
                same(reservation),
                same(reservation.getHall())
        );
        order.verify(transactions).markLinked(42L, reservation);

        verify(transactions, never()).markUncertain(anyLong());
    }

    @Test
    void shouldMarkUncertainWhenGraphFails() {
        doThrow(new GraphIntegrationException("Brak odpowiedzi"))
                .when(service)
                .createOutlookEventForUser(
                        eq("token"),
                        same(reservation),
                        same(reservation.getHall())
                );

        ReservationUncertainException exception = assertThrows(
                ReservationUncertainException.class,
                () -> service.reserve(7L, user, request, "token")
        );

        assertEquals(42L, exception.getBookingId());

        verify(transactions).markUncertain(42L);
        verify(transactions, never()).markLinked(anyLong(), any());
    }

    @Test
    void shouldMarkUncertainWhenFinalDatabaseSaveFails() {
        doReturn(ReservationStatus.PENDING)
                .when(service)
                .createOutlookEventForUser(
                        eq("token"),
                        same(reservation),
                        same(reservation.getHall())
                );

        doThrow(new IllegalStateException("Database error"))
                .when(transactions)
                .markLinked(42L, reservation);

        ReservationUncertainException exception = assertThrows(
                ReservationUncertainException.class,
                () -> service.reserve(7L, user, request, "token")
        );

        assertEquals(42L, exception.getBookingId());
        verify(transactions).markUncertain(42L);
    }

    @Test
    void shouldRejectMissingTokenBeforeDatabaseWrite() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(7L, user, request, "")
        );

        verifyNoInteractions(transactions);
    }
}