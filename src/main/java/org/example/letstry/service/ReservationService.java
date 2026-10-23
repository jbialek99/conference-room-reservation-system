
package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.dto.ReservationResult;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.BookingIntegrationState;
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
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger logger =
            LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final HallService hallService;
    private final GraphTokenService graphTokenService;
    private final WebClient webClient;
    private final OutlookRoomResponseMapper roomResponseMapper;

    @Value("${booking.mode:graph}")
    private String bookingMode = "graph";

    @Autowired
    private BookingReservationTransactionService bookingTransactionService;

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
        if ("demo".equalsIgnoreCase(bookingMode)) {
            hallService.findHallById(hallId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Sala nie istnieje: " + hallId
                    ));

            return reservationRepository.findByHallId(hallId)
                    .stream()
                    .filter(reservation ->
                            reservation.getIntegrationState()
                                    == org.example.letstry.model.BookingIntegrationState.DEMO
                                    && reservation.getStatus() != ReservationStatus.REJECTED
                    )
                    .map(reservation -> {
                        Map<String, Object> event = new HashMap<>();
                        event.put("id", "demo-" + reservation.getId());
                        event.put("title", reservation.getTitle());
                        event.put("start", reservation.getStartMeeting().toString());
                        event.put("end", reservation.getEndMeeting().toString());
                        event.put("email", "demo");
                        return event;
                    })
                    .toList();
        }
        Hall hall = hallService.findHallById(hallId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Sala nie istnieje: " + hallId)
                );

        String hallEmail = hall.getEmail();

        if (hallEmail == null || hallEmail.isBlank()) {
            return List.of();
        }
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

        if (reservation.getGraphTransactionId() == null
                || reservation.getGraphTransactionId().isBlank()) {
            throw new IllegalArgumentException("Brak identyfikatora operacji Graph.");
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
                "transactionId", reservation.getGraphTransactionId(),
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

            throw new GraphIntegrationException(
                    "Nie udało się potwierdzić utworzenia wydarzenia w Microsoft Graph.",
                    exception
            );
        }

        if (response == null) {
            throw new GraphIntegrationException(
                    "Microsoft Graph nie zwrócił danych utworzonego wydarzenia."
            );
        }

        String eventId = response.path("id").asText("");

        if (eventId.isBlank()) {
            throw new GraphIntegrationException(
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
        reservation.setIntegrationState(BookingIntegrationState.LINKED);
        reservation.setOrganizerEmail(organizerEmail);
        reservation.setStatus(status);

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

    public ReservationResult reserve(
            Long hallId,
            User user,
            CreateReservationRequest request,
            String accessToken
    ) {
        if ("demo".equalsIgnoreCase(bookingMode)) {
            Reservation reservation = bookingTransactionService.reserveDemo(
                    hallId,
                    user,
                    request
            );

            return new ReservationResult(
                    reservation.getId(),
                    reservation.getStatus()
            );
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Brak tokenu Microsoft Graph.");
        }

        Reservation reservation = bookingTransactionService.prepare(
                hallId,
                user,
                request
        );

        long bookingId = reservation.getId();

        ReservationStatus status;

        try {
            status = createOutlookEventForUser(
                    accessToken,
                    reservation,
                    reservation.getHall()
            );
        } catch (GraphIntegrationException exception) {
            bookingTransactionService.markUncertain(bookingId);
            throw new ReservationUncertainException(bookingId, exception);
        }

        try {
            bookingTransactionService.markLinked(bookingId, reservation);
        } catch (RuntimeException exception) {
            try {
                bookingTransactionService.markUncertain(bookingId);
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            throw new ReservationUncertainException(bookingId, exception);
        }

        return new ReservationResult(bookingId, status);
    }
    public boolean isReservedAtThisTime(Long hallId, Reservation reservation) {
        if (hallId == null || reservation == null
                || reservation.getStartMeeting() == null
                || reservation.getEndMeeting() == null
                || !reservation.getEndMeeting().isAfter(reservation.getStartMeeting())) {
            throw new IllegalArgumentException("Nieprawidlowy termin rezerwacji.");
        }

        return reservationRepository
                .existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
                        hallId,
                        List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED),
                        reservation.getEndMeeting(),
                        reservation.getStartMeeting()
                );
    }

    public void createReservation(Hall hall, User user, Reservation reservation) {
        if (hall == null || user == null || reservation == null) {
            throw new IllegalArgumentException("Brak danych rezerwacji.");
        }

        if (reservation.getStartMeeting() == null
                || reservation.getEndMeeting() == null
                || !reservation.getEndMeeting().isAfter(reservation.getStartMeeting())) {
            throw new IllegalArgumentException("Nieprawidlowy termin rezerwacji.");
        }

        reservation.setHall(hall);
        reservation.setUser(user);
        reservation.setDate(Instant.now());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIntegrationState(BookingIntegrationState.CREATING);
        reservation.setGraphTransactionId(UUID.randomUUID().toString());
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
