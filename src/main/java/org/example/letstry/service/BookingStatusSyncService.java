
package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingStatusSyncService {

    private static final Logger logger =
            LoggerFactory.getLogger(BookingStatusSyncService.class);

    private static final Duration SYNC_INTERVAL =
            Duration.ofSeconds(15);

    private final ReservationRepository reservationRepository;
    private final OutlookRoomResponseMapper roomResponseMapper;
    private final WebClient webClient;

    private final ConcurrentHashMap<Long, Instant> lastAttempts =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, String> lastOutcomes =
            new ConcurrentHashMap<>();

    private final Object[] locks = new Object[64];

    public BookingStatusSyncService(
            ReservationRepository reservationRepository,
            OutlookRoomResponseMapper roomResponseMapper,
            WebClient webClient
    ) {
        this.reservationRepository = reservationRepository;
        this.roomResponseMapper = roomResponseMapper;
        this.webClient = webClient;

        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    public Optional<Map<String, Object>> getStatus(
            Long reservationId,
            String tenantId,
            String objectId,
            String accessToken
    ) {
        Object lock = locks[Math.floorMod(reservationId.hashCode(), locks.length)];

        synchronized (lock) {
            Optional<Reservation> result = reservationRepository
                    .findByIdAndUser_TenantIdAndUser_ObjectId(
                            reservationId,
                            tenantId,
                            objectId
                    );

            if (result.isEmpty()) {
                return Optional.empty();
            }

            Reservation reservation = result.get();
            Instant now = Instant.now();

            Instant lastAttempt = lastAttempts.get(reservationId);

            if (lastAttempt != null
                    && now.isBefore(lastAttempt.plus(SYNC_INTERVAL))) {
                return Optional.of(buildResponse(
                        reservation,
                        "FAILED".equals(lastOutcomes.get(reservationId))
                                ? "FAILED" : "CACHED"
                ));
            }

            lastAttempts.put(reservationId, now);

            Map<String, Object> response = synchronize(
                    reservation,
                    accessToken
            );

            lastOutcomes.put(
                    reservationId,
                    response.get("syncStatus").toString()
            );

            return Optional.of(response);
        }
    }

    private Map<String, Object> synchronize(
            Reservation reservation,
            String accessToken
    ) {
        String eventId = reservation.getOutlookEventId();

        if (eventId == null || eventId.isBlank()) {
            return buildResponse(reservation, "FAILED");
        }

        if (accessToken == null || accessToken.isBlank()) {
            return buildResponse(reservation, "FAILED");
        }

        try {
            JsonNode event = webClient.get()
                    .uri("/me/events/{eventId}", eventId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            Optional<ReservationStatus> status =
                    roomResponseMapper.resolveStatus(
                            event,
                            reservation.getHall().getEmail()
                    );

            if (status.isEmpty()) {
                return buildResponse(reservation, "FAILED");
            }

            reservation.setStatus(status.get());
            reservation.setLastSynchronizedAt(Instant.now());

            reservationRepository.save(reservation);

            return buildResponse(reservation, "SUCCESS");

        } catch (Exception exception) {
            logger.warn(
                    "Błąd synchronizacji rezerwacji {}: {}",
                    reservation.getId(),
                    exception.getMessage()
            );

            return buildResponse(reservation, "FAILED");
        }
    }

    private Map<String, Object> buildResponse(
            Reservation reservation,
            String syncStatus
    ) {
        Map<String, Object> response = new HashMap<>();

        response.put("status", reservation.getStatus().name());
        response.put("syncStatus", syncStatus);

        Instant lastSynchronizedAt =
                reservation.getLastSynchronizedAt();

        if (lastSynchronizedAt != null) {
            response.put(
                    "lastSynchronizedAt",
                    lastSynchronizedAt.toString()
            );
        }

        return response;
    }
}
