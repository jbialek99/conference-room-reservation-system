package org.example.letstry.service;

import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(
        name = "booking.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BookingRecoveryScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(BookingRecoveryScheduler.class);

    private final ReservationRepository reservationRepository;
    private final Duration staleAfter;

    public BookingRecoveryScheduler(
            ReservationRepository reservationRepository,
            @Value("${booking.recovery.stale-after-minutes:10}")
            long staleAfterMinutes
    ) {
        if (staleAfterMinutes < 1) {
            throw new IllegalArgumentException("Nieprawidlowy recovery timeout.");
        }

        this.reservationRepository = reservationRepository;
        this.staleAfter = Duration.ofMinutes(staleAfterMinutes);
    }

    @Scheduled(
            fixedDelayString = "${booking.recovery.scan-delay-ms:60000}",
            initialDelayString = "${booking.recovery.initial-delay-ms:60000}"
    )
    public void recoverStaleReservations() {
        Instant cutoff = Instant.now().minus(staleAfter);

        int updated = reservationRepository.markStaleCreatingReservations(
                BookingIntegrationState.CREATING,
                BookingIntegrationState.UNCERTAIN,
                cutoff
        );

        if (updated > 0) {
            logger.warn(
                    "Oznaczono {} rezerwacji jako UNCERTAIN.",
                    updated
            );
        }
    }
}