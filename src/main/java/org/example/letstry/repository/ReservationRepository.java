
package org.example.letstry.repository;

import org.example.letstry.model.Reservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByHallId(Long hallId);

    List<Reservation> findByHallIdAndStartMeetingBeforeAndEndMeetingAfter(
            Long hallId,
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
