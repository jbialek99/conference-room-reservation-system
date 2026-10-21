
package org.example.letstry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingStatusSyncServiceTest {

    private static final Long BOOKING_ID = 10L;
    private static final String EVENT_ID = "outlook-event-123";
    private static final String TOKEN = "test-access-token";
    private static final String HALL_EMAIL = "room@example.com";

    private final String tenantId = UUID.randomUUID().toString();
    private final String objectId = UUID.randomUUID().toString();

    private ReservationRepository repository;
    private Reservation reservation;
    private AtomicInteger graphRequests;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        graphRequests = new AtomicInteger();

        Hall hall = mock(Hall.class);
        when(hall.getEmail()).thenReturn(HALL_EMAIL);

        reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(BOOKING_ID);
        when(reservation.getHall()).thenReturn(hall);
        when(reservation.getOutlookEventId()).thenReturn(EVENT_ID);
        when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    }

    @Test
    void shouldConfirmReservationWhenRoomAccepts() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("accepted")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        Map<String, Object> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        verify(reservation).setStatus(ReservationStatus.CONFIRMED);
        verify(reservation).setLastSynchronizedAt(any(Instant.class));
        verify(repository).save(reservation);

        assertEquals("SUCCESS", result.get("syncStatus"));
        assertEquals(1, graphRequests.get());
    }

    @Test
    void shouldRejectReservationWhenRoomDeclines() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("declined")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        Map<String, Object> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        verify(reservation).setStatus(ReservationStatus.REJECTED);
        verify(repository).save(reservation);

        assertEquals("SUCCESS", result.get("syncStatus"));
        assertEquals(1, graphRequests.get());
    }

    @Test
    void shouldKeepPendingWhenRoomHasNotResponded() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("none")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        Map<String, Object> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        verify(reservation).setStatus(ReservationStatus.PENDING);
        verify(repository).save(reservation);

        assertEquals("SUCCESS", result.get("syncStatus"));
    }

    @Test
    void shouldKeepLastStatusWhenGraphFails() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"error\":{\"code\":\"ServiceUnavailable\"}}"
        );

        when(reservation.getStatus())
                .thenReturn(ReservationStatus.CONFIRMED);

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        Map<String, Object> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        assertEquals("CONFIRMED", result.get("status"));
        assertEquals("FAILED", result.get("syncStatus"));

        verify(reservation, never()).setStatus(any());
        verify(reservation, never()).setLastSynchronizedAt(any());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotAccessGraphForOtherUsersReservation() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("accepted")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.empty());

        Optional<Map<String, Object>> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        );

        assertTrue(result.isEmpty());
        assertEquals(0, graphRequests.get());

        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotCallGraphTwiceWithinFifteenSeconds() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("accepted")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        service.getStatus(BOOKING_ID, tenantId, objectId, TOKEN);

        Map<String, Object> secondResult = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        assertEquals(1, graphRequests.get());
        assertEquals("CACHED", secondResult.get("syncStatus"));
    }

    @Test
    void shouldRememberFailedSynchronizationWithinFifteenSeconds() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"error\":{\"code\":\"ServiceUnavailable\"}}"
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        service.getStatus(BOOKING_ID, tenantId, objectId, TOKEN);

        Map<String, Object> secondResult = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        assertEquals("FAILED", secondResult.get("syncStatus"));
        assertEquals(1, graphRequests.get());
    }

    @Test
    void shouldNotUpdateStatusWhenRoomResponseIsUnknown() {
        BookingStatusSyncService service = serviceWithGraphResponse(
                HttpStatus.OK,
                eventJson("unknown")
        );

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                BOOKING_ID, tenantId, objectId
        )).thenReturn(Optional.of(reservation));

        Map<String, Object> result = service.getStatus(
                BOOKING_ID, tenantId, objectId, TOKEN
        ).orElseThrow();

        assertEquals("FAILED", result.get("syncStatus"));

        verify(reservation, never()).setStatus(any());
        verify(repository, never()).save(any());
    }

    private BookingStatusSyncService serviceWithGraphResponse(
            HttpStatus status,
            String json
    ) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://graph.microsoft.com/v1.0")
                .exchangeFunction(request -> {
                    graphRequests.incrementAndGet();

                    ClientResponse response = ClientResponse.create(status)
                            .header(
                                    "Content-Type",
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .body(json)
                            .build();

                    return Mono.just(response);
                })
                .build();

        return new BookingStatusSyncService(
                repository,
                new OutlookRoomResponseMapper(),
                webClient
        );
    }

    private String eventJson(String response) {
        return """
                {
                    "id": "outlook-event-123",
                    "attendees": [
                        {
                            "emailAddress": {
                                "address": "room@example.com"
                            },
                            "status": {
                                "response": "%s"
                            }
                        }
                    ]
                }
                """.formatted(response);
    }
}
