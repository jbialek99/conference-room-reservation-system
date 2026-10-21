
package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.letstry.model.ReservationStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OutlookRoomResponseMapper {

    public Optional<ReservationStatus> resolveStatus(
            JsonNode event,
            String hallEmail
    ) {
        if (event == null || hallEmail == null || hallEmail.isBlank()) {
            return Optional.empty();
        }

        JsonNode attendees = event.path("attendees");

        if (!attendees.isArray()) {
            return Optional.empty();
        }

        for (JsonNode attendee : attendees) {
            String email = attendee.path("emailAddress")
                    .path("address")
                    .asText("");

            if (!email.equalsIgnoreCase(hallEmail)) {
                continue;
            }

            String response = attendee.path("status")
                    .path("response")
                    .asText("");

            return switch (response.toLowerCase(java.util.Locale.ROOT)) {
                case "accepted" -> Optional.of(ReservationStatus.CONFIRMED);
                case "declined" -> Optional.of(ReservationStatus.REJECTED);
                case "none", "notresponded", "tentativelyaccepted" ->
                        Optional.of(ReservationStatus.PENDING);
                default -> Optional.empty();
            };
        }

        return Optional.empty();
    }
}
