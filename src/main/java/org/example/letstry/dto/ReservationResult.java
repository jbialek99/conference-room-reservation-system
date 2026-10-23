package org.example.letstry.dto;

import org.example.letstry.model.ReservationStatus;

public record ReservationResult(
        long bookingId,
        ReservationStatus status
) {
}