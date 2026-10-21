
package org.example.letstry.controller;

import org.example.letstry.model.Reservation;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReservationControllerTest {

    private ReservationService reservationService;
    private ReservationRepository reservationRepository;
    private ReservationController controller;
    private OAuth2AuthorizedClient authorizedClient;

    private final String tenantId = UUID.randomUUID().toString();
    private final String objectId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        reservationRepository = mock(ReservationRepository.class);

        controller = new ReservationController(
                reservationService,
                mock(HallService.class),
                mock(UserService.class),
                reservationRepository
        );

        authorizedClient = mock(OAuth2AuthorizedClient.class);

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        when(authorizedClient.getAccessToken()).thenReturn(token);
    }

    @Test
    void shouldRejectCancellationWithoutAuthenticatedUser() {
        ResponseEntity<String> response =
                controller.deleteReservation("event-123", null, authorizedClient);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        verifyNoInteractions(reservationRepository, reservationService);
    }

    @Test
    void shouldRejectCancellationWithoutTenantId() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "oid", objectId
        ));

        ResponseEntity<String> response =
                controller.deleteReservation("event-123", principal, authorizedClient);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        verifyNoInteractions(reservationRepository, reservationService);
    }

    @Test
    void shouldRejectCancellationWithoutObjectId() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "tid", tenantId
        ));

        ResponseEntity<String> response =
                controller.deleteReservation("event-123", principal, authorizedClient);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        verifyNoInteractions(reservationRepository, reservationService);
    }

    @Test
    void shouldNotDeleteUnknownOrOtherUsersReservation() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "tid", tenantId,
                "oid", objectId
        ));

        when(reservationRepository
                .findByOutlookEventIdAndUser_TenantIdAndUser_ObjectId(
                        "event-123", tenantId, objectId
                )).thenReturn(Optional.empty());

        ResponseEntity<String> response =
                controller.deleteReservation("event-123", principal, authorizedClient);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        verify(reservationService, never())
                .deleteEventFromUserCalendar(anyString(), anyString());

        verify(reservationService, never())
                .removeReservationFromDb(anyString());
    }

    @Test
    void shouldDeleteReservationOwnedByAuthenticatedUser() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "tid", tenantId,
                "oid", objectId
        ));

        when(reservationRepository
                .findByOutlookEventIdAndUser_TenantIdAndUser_ObjectId(
                        "event-123", tenantId, objectId
                )).thenReturn(Optional.of(new Reservation()));

        when(reservationService.deleteEventFromUserCalendar(
                "event-123", "test-access-token"
        )).thenReturn(true);

        ResponseEntity<String> response =
                controller.deleteReservation("event-123", principal, authorizedClient);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(reservationService).deleteEventFromUserCalendar(
                "event-123", "test-access-token"
        );

        verify(reservationService).removeReservationFromDb("event-123");
    }

    @Test
    void shouldKeepReservationWhenGraphDeletionFails() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "tid", tenantId,
                "oid", objectId
        ));

        when(reservationRepository
                .findByOutlookEventIdAndUser_TenantIdAndUser_ObjectId(
                        "event-123", tenantId, objectId
                )).thenReturn(Optional.of(new Reservation()));

        when(reservationService.deleteEventFromUserCalendar(
                "event-123", "test-access-token"
        )).thenReturn(false);

        ResponseEntity<String> response =
                controller.deleteReservation("event-123", principal, authorizedClient);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        verify(reservationService, never())
                .removeReservationFromDb(anyString());
    }

    private OidcUser principal(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                claims
        );

        return new DefaultOidcUser(
                java.util.List.of(),
                idToken
        );
    }
}
