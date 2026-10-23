package org.example.letstry.controller;

import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationCreateResponseTest {

    @Test
    void shouldReturnBookingIdAndStatus() {
        ReservationService reservationService = mock(ReservationService.class);
        UserService userService = mock(UserService.class);

        ReservationController controller = new ReservationController(
                reservationService,
                mock(HallService.class),
                userService,
                mock(ReservationRepository.class)
        );

        OidcUser principal = mock(OidcUser.class);
        when(principal.getEmail()).thenReturn("user@example.com");

        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken token = mock(OAuth2AccessToken.class);

        when(client.getAccessToken()).thenReturn(token);
        when(token.getTokenValue()).thenReturn("test-token");

        User user = new User();
        when(userService.createUserIfNotExists(principal)).thenReturn(user);

        Instant start = Instant.now().plusSeconds(3600);

        CreateReservationRequest request = new CreateReservationRequest(
                "Spotkanie",
                start,
                start.plusSeconds(3600)
        );

        when(reservationService.reserve(
                eq(7L),
                same(user),
                same(request),
                eq("test-token")
        )).thenReturn(new ReservationResult(42L, ReservationStatus.PENDING));

        var response = controller.addReservation(
                7L,
                request,
                principal,
                client
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(42L, response.getBody().get("bookingId"));
        assertEquals("PENDING", response.getBody().get("status"));

        verify(reservationService).reserve(
                eq(7L),
                same(user),
                same(request),
                eq("test-token")
        );
    }
}