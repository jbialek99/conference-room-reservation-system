
package org.example.letstry.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReservationRequest(

        @NotBlank
        @Size(max = 150)
        String title,

        @NotNull
        @Future
        Instant startMeeting,

        @NotNull
        Instant endMeeting

) {

        @AssertTrue(message = "Data zakończenia musi być późniejsza niż data rozpoczęcia.")
        public boolean isMeetingPeriodValid() {
                if (startMeeting == null || endMeeting == null) {
                        return true;
                }

                return endMeeting.isAfter(startMeeting);
        }
}
