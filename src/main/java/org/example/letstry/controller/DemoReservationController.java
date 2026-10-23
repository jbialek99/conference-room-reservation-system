package org.example.letstry.controller;

import jakarta.validation.Valid;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.User;
import org.example.letstry.repository.HallRepository;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.DemoIdentityService;
import org.example.letstry.service.ReservationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@ConditionalOnProperty(name = "booking.mode", havingValue = "demo")
public class DemoReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final HallRepository hallRepository;
    private final DemoIdentityService identities;

    public DemoReservationController(ReservationService reservationService,
                                     ReservationRepository reservationRepository,
                                     HallRepository hallRepository,
                                     DemoIdentityService identities) {
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
        this.hallRepository = hallRepository;
        this.identities = identities;
    }

    @GetMapping("/api/demo/halls")
    public List<Map<String, Object>> halls() {
        return hallRepository.findAll().stream()
                .filter(hall -> hall.isAvailable())
                .map(hall -> Map.<String, Object>of(
                        "id", hall.getId(),
                        "name", hall.getName()
                )).toList();
    }

    @GetMapping("/api/reservations/hall/{hallId}")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(
            @PathVariable Long hallId,
            Authentication authentication
    ) {
        User currentUser = identities.resolve(authentication);

        java.util.Set<String> ownedIds = reservationRepository.findByHallId(hallId)
                .stream()
                .filter(reservation ->
                        reservation.getIntegrationState() == BookingIntegrationState.DEMO
                                && reservation.getUser() != null
                                && currentUser.getTenantId().equals(
                                        reservation.getUser().getTenantId()
                                )
                                && currentUser.getObjectId().equals(
                                        reservation.getUser().getObjectId()
                                )
                )
                .map(reservation -> "demo-" + reservation.getId())
                .collect(java.util.stream.Collectors.toSet());

        return reservationService.getOutlookReservationsByHall(hallId)
                .stream()
                .map(event -> {
                    Map<String, Object> result = new java.util.HashMap<>(event);
                    result.put("canCancel", ownedIds.contains(event.get("id")));
                    return result;
                })
                .toList();
    }

    @PostMapping("/api/reservations/hall/{hallId}/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@PathVariable Long hallId,
                                                       @Valid @RequestBody CreateReservationRequest request,
                                                       Authentication authentication) {
        try {
            User user = identities.resolve(authentication);
            ReservationResult result = reservationService.reserve(hallId, user, request, null);
            return ResponseEntity.ok(Map.of(
                    "bookingId", result.bookingId(),
                    "status", result.status().name()
            ));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of("error", Optional.ofNullable(exception.getReason()).orElse("Blad rezerwacji.")));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nieprawidlowe dane rezerwacji."));
        }
    }

    @Transactional
    @DeleteMapping("/api/reservations/delete/{eventId}")
    public ResponseEntity<String> delete(@PathVariable String eventId,
                                         Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Brak autoryzacji.");
        }
        if (!eventId.matches("demo-[1-9][0-9]*")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nie znaleziono rezerwacji DEMO.");
        }
        long id;
        try {
            id = Long.parseLong(eventId.substring(5));
        } catch (NumberFormatException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nie znaleziono rezerwacji DEMO.");
        }
        Optional<Reservation> owned = reservationRepository.findByIdAndUser_TenantIdAndUser_ObjectId(
                id, identities.tenantId(), identities.objectId(authentication.getName()));
        if (owned.isEmpty() || owned.get().getIntegrationState() != BookingIntegrationState.DEMO) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nie znaleziono rezerwacji DEMO.");
        }
        reservationRepository.delete(owned.get());
        return ResponseEntity.ok("Rezerwacja zostala anulowana.");
    }
}