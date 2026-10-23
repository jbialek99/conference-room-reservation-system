
package org.example.letstry.controller;

import jakarta.validation.Valid;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.GraphIntegrationException;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final HallService hallService;
    private final UserService userService;
    private final ReservationRepository reservationRepository;

    public ReservationController(
            ReservationService reservationService,
            HallService hallService,
            UserService userService,
            ReservationRepository reservationRepository
    ) {
        this.reservationService = reservationService;
        this.hallService = hallService;
        this.userService = userService;
        this.reservationRepository = reservationRepository;
    }

    @GetMapping("/hall/{hallId}")
    public List<Map<String, Object>> getReservationsByHall(@PathVariable Long hallId) {
        return reservationService.getOutlookReservationsByHall(hallId);
    }

    @PostMapping("/hall/{hallId}/reserve")
    public ResponseEntity<Map<String, Object>> addReservation(
            @PathVariable Long hallId,
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal OidcUser principal,
            @RegisteredOAuth2AuthorizedClient("azure") OAuth2AuthorizedClient authorizedClient
    ) {
        if (principal == null || authorizedClient == null
                || authorizedClient.getAccessToken() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Brak autoryzacji użytkownika."));
        }

        String userEmail = principal.getEmail();

        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Brak adresu e-mail użytkownika."));
        }

        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        try {
            User user = userService.createUserIfNotExists(principal);

            ReservationResult result = reservationService.reserve(
                    hallId,
                    user,
                    request,
                    accessToken
            );

            return ResponseEntity.ok(Map.of(
                    "bookingId", result.bookingId(),
                    "status", result.status().name()
            ));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of(
                            "error",
                            exception.getReason() == null
                                    ? "Nie udalo sie utworzyc rezerwacji."
                                    : exception.getReason()
                    ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nieprawidlowe dane rezerwacji."));
        } catch (GraphIntegrationException exception) {
            return ResponseEntity.status(502)
                    .body(Map.of("error", "Microsoft Graph nie potwierdzil utworzenia wydarzenia."));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Nie udalo sie utworzyc rezerwacji."));
        }
    }

    @DeleteMapping("/delete/{eventId}")
    public ResponseEntity<String> deleteReservation(
            @PathVariable String eventId,
            @AuthenticationPrincipal OidcUser principal,
            @RegisteredOAuth2AuthorizedClient("azure") OAuth2AuthorizedClient authorizedClient
    ) {
        if (principal == null || authorizedClient == null
                || authorizedClient.getAccessToken() == null) {
            return ResponseEntity.status(401).body("Brak autoryzacji użytkownika.");
        }

        String tenantId = normalizeGuid(principal.getClaimAsString("tid"));
        String objectId = normalizeGuid(principal.getClaimAsString("oid"));

        if (tenantId == null || objectId == null) {
            return ResponseEntity.status(403)
                    .body("Nie można zweryfikować tożsamości użytkownika.");
        }

        Optional<Reservation> reservation = reservationRepository
                .findByOutlookEventIdAndUser_TenantIdAndUser_ObjectId(
                        eventId,
                        tenantId,
                        objectId
                );

        if (reservation.isEmpty()) {
            return ResponseEntity.status(404)
                    .body("Nie znaleziono rezerwacji lub brak uprawnień.");
        }

        String token = authorizedClient.getAccessToken().getTokenValue();

        boolean deleted = reservationService.deleteEventFromUserCalendar(
                eventId,
                token
        );

        if (!deleted) {
            return ResponseEntity.internalServerError()
                    .body("Nie udało się anulować wydarzenia w Outlook.");
        }

        reservationService.removeReservationFromDb(eventId);

        return ResponseEntity.ok("Rezerwacja została anulowana.");
    }

    private String normalizeGuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
