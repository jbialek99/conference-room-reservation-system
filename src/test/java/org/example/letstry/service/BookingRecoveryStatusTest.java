package org.example.letstry.service;

import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingRecoveryStatusTest {

    private ReservationRepository repository;
    private BookingStatusSyncService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        service = new BookingStatusSyncService(
                repository,
                mock(OutlookRoomResponseMapper.class),
                WebClient.builder().build()
        );
    }

    @Test
    void shouldReturnCreatingStatusWithoutGraph() {
        Map<String, Object> response = statusFor(BookingIntegrationState.CREATING);
        assertEquals("CREATING", response.get("syncStatus"));
        assertEquals("CREATING", response.get("integrationState"));
    }

    @Test
    void shouldReturnUncertainStatusWithoutGraph() {
        Map<String, Object> response = statusFor(BookingIntegrationState.UNCERTAIN);
        assertEquals("UNCERTAIN", response.get("syncStatus"));
        assertEquals("UNCERTAIN", response.get("integrationState"));
    }

    @Test
    void shouldReturnDemoStatusWithoutGraph() {
        Map<String, Object> response = statusFor(BookingIntegrationState.DEMO);
        assertEquals("LOCAL", response.get("syncStatus"));
        assertEquals("DEMO", response.get("integrationState"));
    }

    private Map<String, Object> statusFor(BookingIntegrationState state) {
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIntegrationState(state);

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                42L, "tenant", "object"
        )).thenReturn(Optional.of(reservation));

        Optional<Map<String, Object>> result =
                service.getStatus(42L, "tenant", "object", "token");

        assertTrue(result.isPresent());
        verify(repository, never()).save(any(Reservation.class));

        return result.orElseThrow();
    }
}