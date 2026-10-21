
package org.example.letstry.controller;

import org.example.letstry.service.BookingStatusSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BookingControllerTest {

    private static final Long BOOKING_ID = 10L;

    private final String tenantId = UUID.randomUUID().toString();
    private final String objectId = UUID.randomUUID().toString();

    private BookingStatusSyncService syncService;
    private BookingController controller;
    private OAuth2AuthorizedClient authorizedClient;

    @BeforeEach
    void setUp() {
        syncService = mock(BookingStatusSyncService.class);
        controller = new BookingController(syncService);

        authorizedClient = mock(OAuth2AuthorizedClient.class);

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    }

    @Test
    void shouldRejectUnauthenticatedRequest() {
        var response = controller.getReservationStatus(
                BOOKING_ID,
                null,
                authorizedClient
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldRejectMissingTenantId() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "oid", objectId
        ));

        var response = controller.getReservationStatus(
                BOOKING_ID,
                principal,
                authorizedClient
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldRejectMissingObjectId() {
        OidcUser principal = principal(Map.of(
                "sub", "subject",
                "tid", tenantId
        ));

        var response = controller.getReservationStatus(
                BOOKING_ID,
                principal,
                authorizedClient
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldReturnNotFoundForOtherUsersReservation() {
        OidcUser principal = validPrincipal();

        when(syncService.getStatus(
                BOOKING_ID,
                tenantId,
                objectId,
                "test-access-token"
        )).thenReturn(Optional.empty());

        var response = controller.getReservationStatus(
                BOOKING_ID,
                principal,
                authorizedClient
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturnCurrentReservationStatus() {
        OidcUser principal = validPrincipal();

        when(syncService.getStatus(
                BOOKING_ID,
                tenantId,
                objectId,
                "test-access-token"
        )).thenReturn(Optional.of(Map.of(
                "status", "CONFIRMED",
                "syncStatus", "SUCCESS"
        )));

        var response = controller.getReservationStatus(
                BOOKING_ID,
                principal,
                authorizedClient
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CONFIRMED", response.getBody().get("status"));
        assertEquals("SUCCESS", response.getBody().get("syncStatus"));
    }

    @Test
    void shouldReturnLastStatusWhenSynchronizationFails() {
        OidcUser principal = validPrincipal();

        when(syncService.getStatus(
                BOOKING_ID,
                tenantId,
                objectId,
                "test-access-token"
        )).thenReturn(Optional.of(Map.of(
                "status", "PENDING",
                "syncStatus", "FAILED"
        )));

        var response = controller.getReservationStatus(
                BOOKING_ID,
                principal,
                authorizedClient
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PENDING", response.getBody().get("status"));
        assertEquals("FAILED", response.getBody().get("syncStatus"));
    }

    private OidcUser validPrincipal() {
        return principal(Map.of(
                "sub", "subject",
                "tid", tenantId,
                "oid", objectId
        ));
    }

    private OidcUser principal(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                claims
        );

        return new DefaultOidcUser(List.of(), idToken);
    }
}
