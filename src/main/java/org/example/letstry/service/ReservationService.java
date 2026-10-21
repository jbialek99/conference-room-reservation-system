
package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservationService {

    private static final Logger logger =
            LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final HallService hallService;
    private final GraphTokenService graphTokenService;
    private final WebClient webClient;
    private final OutlookRoomResponseMapper roomResponseMapper;

    public ReservationService(
            ReservationRepository reservationRepository,
            HallService hallService,
            GraphTokenService graphTokenService,
            WebClient webClient,
            OutlookRoomResponseMapper roomResponseMapper
    ) {
        this.reservationRepository = reservationRepository;
        this.hallService = hallService;
        this.graphTokenService = graphTokenService;
        this.webClient = webClient;
        this.roomResponseMapper = roomResponseMapper;
    }

    public List<Map<String, Object>> getOutlookReservationsByHall(Long hallId) {
        Hall hall = hallService.findHallById(hallId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Sala nie istnieje: " + hallId)
                );

        String hallEmail = hall.getEmail();
        String accessToken = graphTokenService.getAppAccessToken();

        List<Map<String, Object>> events = new ArrayList<>();

        try {
            JsonNode response = webClient.get()
                    .uri("/users/{email}/calendar/events", hallEmail)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("value").isArray()) {
                return events;
            }

            for (JsonNode event : response.path("value")) {
                try {
                    Map<String, Object> mappedEvent = new HashMap<>();

                    mappedEvent.put("id", event.path("id").asText());
                    mappedEvent.put("title", event.path("subject").asText());
                    mappedEvent.put(
                            "start",
                            event.path("start").path("dateTime").asText()
                    );
                    mappedEvent.put(
                            "end",
                            event.path("end").path("dateTime").asText()
                    );
                    mappedEvent.put(
                            "email",
                            event.path("organizer")
                                    .path("emailAddress")
                                    .path("address")
                                    .asText()
                    );

                    events.add(mappedEvent);
                } catch (Exception exception) {
                    logger.warn(
                            "Nie udało się przetworzyć wydarzenia Outlook: {}",
                            exception.getMessage()
                    );
                }
            }
        } catch (Exception exception) {
            logger.error(
                    "Nie udało się pobrać kalendarza sali: {}",
                    exception.getMessage()
            );
        }

        return events;
    }

    public ReservationStatus createOutlookEventForUser(
            String accessToken,
            Reservation reservation,
            Hall hall
    ) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Brak tokenu Microsoft Graph.");
        }

        if (reservation == null || reservation.getUser() == null || hall == null) {
            throw new IllegalArgumentException("Nieprawidłowe dane rezerwacji.");
        }

        if (reservation.getStartMeeting() == null
                || reservation.getEndMeeting() == null) {
            throw new IllegalArgumentException("Brak terminu rezerwacji.");
        }

        String start = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(reservation.getStartMeeting());

        String end = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(reservation.getEndMeeting());

        User user = reservation.getUser();

        String organizerName = (
                user.getFirstName() + " " + user.getLastName()
        ).trim();

        String title = organizerName.isBlank()
                ? reservation.getTitle()
                : organizerName + ": " + reservation.getTitle();

        Map<String, Object> event = Map.of(
                "subject", title,
                "body", Map.of(
                        "contentType", "HTML",
                        "content", "Rezerwacja sali przez aplikację"
                ),
                "start", Map.of(
                        "dateTime", start,
                        "timeZone", "UTC"
                ),
                "end", Map.of(
                        "dateTime", end,
                        "timeZone", "UTC"
                ),
                "location", Map.of(
                        "displayName", hall.getName()
                ),
                "attendees", List.of(
                        Map.of(
                                "type", "resource",
                                "emailAddress", Map.of(
                                        "address", hall.getEmail(),
                                        "name", hall.getName()
                                )
                        )
                ),
                "isOnlineMeeting", false,
                "responseRequested", true
        );

        JsonNode response;

        try {
            response = webClient.post()
                    .uri("/me/events")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(event)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception exception) {
            logger.error(
                    "Nie udało się uzyskać potwierdzenia utworzenia wydarzenia Outlook.",
                    exception
            );

            throw new IllegalStateException(
                    "Nie udało się potwierdzić utworzenia wydarzenia w Microsoft Graph.",
                    exception
            );
        }

        if (response == null) {
            throw new IllegalStateException(
                    "Microsoft Graph nie zwrócił danych utworzonego wydarzenia."
            );
        }

        String eventId = response.path("id").asText("");

        if (eventId.isBlank()) {
            throw new IllegalStateException(
                    "Microsoft Graph nie zwrócił identyfikatora wydarzenia."
            );
        }

        String organizerEmail = response.path("organizer")
                .path("emailAddress")
                .path("address")
                .asText("");

        Optional<ReservationStatus> resolvedStatus =
                roomResponseMapper.resolveStatus(response, hall.getEmail());

        if (resolvedStatus.isEmpty()) {
            logger.warn(
                    "Nie udało się jednoznacznie ustalić odpowiedzi sali dla rezerwacji {}.",
                    reservation.getId()
            );
        }

        ReservationStatus status = resolvedStatus
                .orElse(ReservationStatus.PENDING);

        reservation.setTitle(title);
        reservation.setOutlookEventId(eventId);
        reservation.setOrganizerEmail(organizerEmail);
        reservation.setStatus(status);

        reservationRepository.save(reservation);

        logger.info(
                "Zapisano wydarzenie Outlook dla rezerwacji {} ze statusem {}.",
                reservation.getId(),
                status
        );

        return status;
    }

    public boolean deleteEventFromUserCalendar(
            String eventId,
            String accessToken
    ) {
        try {
            webClient.delete()
                    .uri("/me/events/{eventId}", eventId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return true;
        } catch (WebClientResponseException exception) {
            logger.warn(
                    "Microsoft Graph odmówił usunięcia wydarzenia. HTTP {}.",
                    exception.getStatusCode()
            );
            return false;
        }
    }

    public boolean deleteEventFromRoomCalendarIfOwner(
            String eventId,
            String userEmail,
            String hallEmail
    ) {
        try {
            String token = graphTokenService.getAppAccessToken();

            JsonNode event = webClient.get()
                    .uri(
                            "/users/{email}/events/{eventId}",
                            hallEmail,
                            eventId
                    )
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (event == null) {
                return false;
            }

            String organizer = event.path("organizer")
                    .path("emailAddress")
                    .path("address")
                    .asText("");

            if (!organizer.equalsIgnoreCase(userEmail)) {
                return false;
            }

            webClient.delete()
                    .uri(
                            "/users/{email}/events/{eventId}",
                            hallEmail,
                            eventId
                    )
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return true;
        } catch (WebClientResponseException.NotFound exception) {
            return false;
        } catch (Exception exception) {
            logger.error(
                    "Nie udało się usunąć wydarzenia z kalendarza sali.",
                    exception
            );
            return false;
        }
    }

    public boolean isReservedAtThisTime(
            Long hallId,
            Reservation reservation
    ) {
        return !reservationRepository
                .findByHallIdAndStartMeetingBeforeAndEndMeetingAfter(
                        hallId,
                        reservation.getEndMeeting(),
                        reservation.getStartMeeting()
                )
                .isEmpty();
    }

    public void createReservation(
            Hall hall,
            User user,
            Reservation reservation
    ) {
        reservation.setHall(hall);
        reservation.setUser(user);
        reservation.setDate(Instant.now());
        reservation.setStatus(ReservationStatus.PENDING);

        reservationRepository.save(reservation);
    }

    public Optional<Reservation> findByOutlookEventId(String eventId) {
        return reservationRepository.findByOutlookEventId(eventId);
    }

    public Optional<Reservation> findById(Long id) {
        return reservationRepository.findById(id);
    }

    public void removeReservationFromDb(String eventId) {
        reservationRepository.deleteByOutlookEventId(eventId);
    }

    public boolean isEventConfirmed(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .map(reservation ->
                        reservation.getStatus() == ReservationStatus.CONFIRMED
                )
                .orElse(false);
    }
}
