package org.example.letstry.controller;

import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Reservation;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoCancellationSecurityTest {

    private static final String TENANT =
            "11111111-1111-4111-8111-111111111111";

    private static final String OBJECT =
            "22222222-2222-4222-8222-222222222222";

    private ReservationRepository repository;
    private ReservationService service;
    private ReservationController controller;
    private OidcUser principal;
    private OAuth2AuthorizedClient authorizedClient;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        service = mock(ReservationService.class);

        controller = new ReservationController(
                service,
                mock(HallService.class),
                mock(UserService.class),
                repository
        );

        principal = mock(OidcUser.class);

        when(principal.getClaimAsString("tid")).thenReturn(TENANT);
        when(principal.getClaimAsString("oid")).thenReturn(OBJECT);

        authorizedClient = mock(OAuth2AuthorizedClient.class);

        OAuth2AccessToken token = mock(OAuth2AccessToken.class);

        when(authorizedClient.getAccessToken()).thenReturn(token);
        when(token.getTokenValue()).thenReturn("test-access-token");
    }

    @Test
    void ownerCanDeleteOwnDemoReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setIntegrationState(BookingIntegrationState.DEMO);

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                42L,
                TENANT,
                OBJECT
        )).thenReturn(Optional.of(reservation));

        ResponseEntity<String> result = controller.deleteReservation(
                "demo-42",
                principal,
                authorizedClient
        );

        assertEquals(200, result.getStatusCode().value());

        verify(repository).delete(same(reservation));
        verifyNoInteractions(service);
    }

    @Test
    void otherUserCannotDeleteDemoReservation() {
        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                42L,
                TENANT,
                OBJECT
        )).thenReturn(Optional.empty());

        ResponseEntity<String> result = controller.deleteReservation(
                "demo-42",
                principal,
                authorizedClient
        );

        assertEquals(404, result.getStatusCode().value());

        verify(repository, never()).delete(any(Reservation.class));
        verifyNoInteractions(service);
    }

    @Test
    void invalidDemoIdentifierCannotDeleteAnything() {
        ResponseEntity<String> result = controller.deleteReservation(
                "demo-invalid",
                principal,
                authorizedClient
        );

        assertEquals(404, result.getStatusCode().value());

        verify(repository, never()).delete(any(Reservation.class));
        verifyNoInteractions(service);
    }

    @Test
    void graphReservationCannotBeDeletedAsDemo() {
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setIntegrationState(BookingIntegrationState.LINKED);
        reservation.setOutlookEventId("graph-event-id");

        when(repository.findByIdAndUser_TenantIdAndUser_ObjectId(
                42L,
                TENANT,
                OBJECT
        )).thenReturn(Optional.of(reservation));

        ResponseEntity<String> result = controller.deleteReservation(
                "demo-42",
                principal,
                authorizedClient
        );

        assertEquals(404, result.getStatusCode().value());

        verify(repository, never()).delete(any(Reservation.class));
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserCannotDeleteDemoReservation() {
        ResponseEntity<String> result = controller.deleteReservation(
                "demo-42",
                null,
                authorizedClient
        );

        assertEquals(401, result.getStatusCode().value());

        verifyNoInteractions(repository, service);
    }

    @Test
    void missingTenantIdentityCannotDeleteReservation() {
        when(principal.getClaimAsString("tid")).thenReturn(null);

        ResponseEntity<String> result = controller.deleteReservation(
                "demo-42",
                principal,
                authorizedClient
        );

        assertEquals(403, result.getStatusCode().value());

        verifyNoInteractions(repository, service);
    }
}