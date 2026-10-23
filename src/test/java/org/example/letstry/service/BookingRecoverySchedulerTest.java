package org.example.letstry.service;

import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingRecoverySchedulerTest {

    @Test
    void shouldMarkStaleCreatingReservationsAsUncertain() {
        ReservationRepository repository = mock(ReservationRepository.class);
        BookingRecoveryScheduler scheduler = new BookingRecoveryScheduler(repository, 10);

        Instant before = Instant.now();

        when(repository.markStaleCreatingReservations(
                eq(BookingIntegrationState.CREATING),
                eq(BookingIntegrationState.UNCERTAIN),
                any(Instant.class)
        )).thenReturn(2);

        scheduler.recoverStaleReservations();

        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);

        verify(repository).markStaleCreatingReservations(
                eq(BookingIntegrationState.CREATING),
                eq(BookingIntegrationState.UNCERTAIN),
                captor.capture()
        );

        assertFalse(captor.getValue().isBefore(before.minus(Duration.ofMinutes(10))));
        assertFalse(captor.getValue().isAfter(after.minus(Duration.ofMinutes(10))));
    }

    @Test
    void shouldAllowNoStaleReservations() {
        ReservationRepository repository = mock(ReservationRepository.class);
        BookingRecoveryScheduler scheduler = new BookingRecoveryScheduler(repository, 10);

        scheduler.recoverStaleReservations();

        verify(repository).markStaleCreatingReservations(
                eq(BookingIntegrationState.CREATING),
                eq(BookingIntegrationState.UNCERTAIN),
                any(Instant.class)
        );
    }

    @Test
    void shouldRejectInvalidTimeout() {
        ReservationRepository repository = mock(ReservationRepository.class);

        assertThrows(IllegalArgumentException.class,
                () -> new BookingRecoveryScheduler(repository, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new BookingRecoveryScheduler(repository, -1));
    }
}