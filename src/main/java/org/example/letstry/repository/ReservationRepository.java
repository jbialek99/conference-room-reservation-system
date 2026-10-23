
package org.example.letstry.repository;

import org.example.letstry.model.Reservation;
import org.example.letstry.model.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @org.springframework.data.jpa.repository.Query("""
            update Reservation r
            set r.integrationState = :uncertain
            where r.integrationState = :creating
            and r.date < :cutoff
            """)
    int markStaleCreatingReservations(
            @org.springframework.data.repository.query.Param("creating")
            org.example.letstry.model.BookingIntegrationState creating,
            @org.springframework.data.repository.query.Param("uncertain")
            org.example.letstry.model.BookingIntegrationState uncertain,
            @org.springframework.data.repository.query.Param("cutoff")
            Instant cutoff
    );

    List<Reservation> findByHallId(Long hallId);

    boolean existsByHallIdAndStatusInAndStartMeetingBeforeAndEndMeetingAfter(
            Long hallId,
            List<ReservationStatus> statuses,
            Instant end,
            Instant start
    );

    boolean existsByHallIdAndStatusInAndIntegrationStateAndStartMeetingBeforeAndEndMeetingAfter(
            Long hallId,
            List<ReservationStatus> statuses,
            org.example.letstry.model.BookingIntegrationState integrationState,
            Instant end,
            Instant start
    );

    Optional<Reservation> findByOutlookEventId(String outlookEventId);

    Optional<Reservation> findByOutlookEventIdAndUser_TenantIdAndUser_ObjectId(
            String outlookEventId,
            String tenantId,
            String objectId
    );

    @EntityGraph(attributePaths = {"hall"})
    Optional<Reservation> findByIdAndUser_TenantIdAndUser_ObjectId(
            Long id,
            String tenantId,
            String objectId
    );

    void deleteByOutlookEventId(String outlookEventId);
}
