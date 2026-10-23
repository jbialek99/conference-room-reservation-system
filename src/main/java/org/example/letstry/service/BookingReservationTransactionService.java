package org.example.letstry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.letstry.dto.CreateReservationRequest;
import org.example.letstry.model.BookingIntegrationState;
import org.example.letstry.model.Hall;
import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.example.letstry.model.User;
import org.example.letstry.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingReservationTransactionService {

    private final ReservationRepository reservationRepository;
    private final EntityManager entityManager;

    @Value("${booking.mode:graph}")
    private String bookingMode = "graph";

    public BookingReservationTransactionService(
            ReservationRepository reservationRepository,
            EntityManager entityManager
    ) {
        this.reservationRepository = reservationRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Reservation prepare(
            Long hallId,
            User user,
            CreateReservationRequest request
    ) {
        if (hallId == null || user == null || request == null) {
            throw new IllegalArgumentException("Nieprawidlowe dane rezerwacji.");
        }

        if (request.title() == null
                || request.title().isBlank()
                || request.startMeeting() == null
                || request.endMeeting() == null
                || !request.startMeeting().isAfter(Instant.now())
                || !request.endMeeting().isAfter(request.startMeeting())) {
            throw new IllegalArgumentException("Nieprawidlowe dane rezerwacji.");
        }

        Hall hall = entityManager.find(
                Hall.class,
                hallId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (hall == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Nie znaleziono sali."
            );
        }

        if (!hall.isAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sala jest niedostepna."
            );
        }

        if (!"demo".equalsIgnoreCase(bookingMode)
                && (hall.getEmail() == null || hall.getEmail().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sala nie posiada kalendarza Exchange."
            );
        }

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED
        );

        boolean occupied;

        if ("demo".equalsIgnoreCase(bookingMode)) {
            occupied = reservationRepository
                    .existsByHallIdAndStatusInAndIntegrationStateAndStartMeetingBeforeAndEndMeetingAfter(
                            hallId,
                            blockingStatuses,
                            BookingIntegrationState.DEMO,
                            request.endMeeting(),
                            request.startMeeting()
                    );
        } else {
            occupied = reservationRepository
                    .existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
                            hallId,
                            blockingStatuses,
                            request.endMeeting(),
                            request.startMeeting()
                    );
        }

        if (occupied) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sala jest juz zarezerwowana w tym terminie."
            );
        }

        Reservation reservation = new Reservation();
        reservation.setHall(hall);
        reservation.setUser(user);
        reservation.setTitle(request.title().trim());
        reservation.setStartMeeting(request.startMeeting());
        reservation.setEndMeeting(request.endMeeting());
        reservation.setDate(Instant.now());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setIntegrationState(BookingIntegrationState.CREATING);
        reservation.setGraphTransactionId(UUID.randomUUID().toString());

        return reservationRepository.saveAndFlush(reservation);
    }

    @Transactional
    public Reservation reserveDemo(
            Long hallId,
            User user,
            CreateReservationRequest request
    ) {
        if (!"demo".equalsIgnoreCase(bookingMode)) {
            throw new IllegalStateException("Tryb DEMO jest wylaczony.");
        }

        Reservation reservation = prepare(hallId, user, request);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setIntegrationState(BookingIntegrationState.DEMO);
        reservation.setGraphTransactionId(null);

        return reservationRepository.saveAndFlush(reservation);
    }
    @Transactional
    public void markLinked(
            long reservationId,
            Reservation graphResult
    ) {
        Reservation stored = reservationRepository.findById(reservationId)
                .orElseThrow(() ->
                        new IllegalStateException("Nie znaleziono rezerwacji.")
                );

        if (graphResult.getOutlookEventId() == null
                || graphResult.getOutlookEventId().isBlank()) {
            throw new IllegalArgumentException("Brak identyfikatora wydarzenia.");
        }

        if (stored.getIntegrationState() != BookingIntegrationState.CREATING
                && stored.getIntegrationState() != BookingIntegrationState.UNCERTAIN) {
            throw new IllegalStateException("Nieprawidlowy stan integracji.");
        }

        stored.setOutlookEventId(graphResult.getOutlookEventId());
        stored.setOrganizerEmail(graphResult.getOrganizerEmail());
        stored.setTitle(graphResult.getTitle());
        stored.setStatus(graphResult.getStatus());
        stored.setIntegrationState(BookingIntegrationState.LINKED);

        reservationRepository.save(stored);
    }

    @Transactional
    public void markUncertain(long reservationId) {
        Reservation stored = reservationRepository.findById(reservationId)
                .orElseThrow(() ->
                        new IllegalStateException("Nie znaleziono rezerwacji.")
                );

        if (stored.getIntegrationState() == BookingIntegrationState.CREATING) {
            stored.setIntegrationState(BookingIntegrationState.UNCERTAIN);
            stored.setStatus(ReservationStatus.PENDING);
            reservationRepository.save(stored);
        }
    }
}