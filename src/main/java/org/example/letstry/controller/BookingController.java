
package org.example.letstry.controller;

import org.example.letstry.service.BookingStatusSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingStatusSyncService bookingStatusSyncService;

    public BookingController(
            BookingStatusSyncService bookingStatusSyncService
    ) {
        this.bookingStatusSyncService = bookingStatusSyncService;
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getReservationStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser principal,
            @RegisteredOAuth2AuthorizedClient("azure")
            OAuth2AuthorizedClient authorizedClient
    ) {
        if (principal == null
                || authorizedClient == null
                || authorizedClient.getAccessToken() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "Brak autoryzacji użytkownika."
                    ));
        }

        String tenantId = normalizeGuid(
                principal.getClaimAsString("tid")
        );

        String objectId = normalizeGuid(
                principal.getClaimAsString("oid")
        );

        if (tenantId == null || objectId == null) {
            return ResponseEntity.status(403)
                    .body(Map.of(
                            "error", "Nie można zweryfikować tożsamości użytkownika."
                    ));
        }

        String accessToken =
                authorizedClient.getAccessToken().getTokenValue();

        Optional<Map<String, Object>> result =
                bookingStatusSyncService.getStatus(
                        id,
                        tenantId,
                        objectId,
                        accessToken
                );

        return result
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of(
                                "error", "Nie znaleziono rezerwacji lub brak uprawnień."
                        )));
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
