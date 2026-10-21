
package org.example.letstry.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateReservationRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidReservation() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        CreateReservationRequest request =
                new CreateReservationRequest("Spotkanie zespołu", start, end);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectBlankTitle() {
        CreateReservationRequest request =
                validRequest("   ");

        assertEquals(
                1,
                validator.validateProperty(request, "title").size()
        );
    }

    @Test
    void shouldRejectTitleLongerThan150Characters() {
        CreateReservationRequest request =
                validRequest("A".repeat(151));

        assertEquals(
                1,
                validator.validateProperty(request, "title").size()
        );
    }

    @Test
    void shouldRejectMissingStartMeeting() {
        CreateReservationRequest request =
                new CreateReservationRequest(
                        "Spotkanie",
                        null,
                        Instant.now().plus(3, ChronoUnit.DAYS)
                );

        assertEquals(
                1,
                validator.validateProperty(request, "startMeeting").size()
        );
    }

    @Test
    void shouldRejectPastStartMeeting() {
        CreateReservationRequest request =
                new CreateReservationRequest(
                        "Spotkanie",
                        Instant.now().minus(2, ChronoUnit.DAYS),
                        Instant.now().plus(3, ChronoUnit.DAYS)
                );

        assertEquals(
                1,
                validator.validateProperty(request, "startMeeting").size()
        );
    }

    @Test
    void shouldRejectMissingEndMeeting() {
        CreateReservationRequest request =
                new CreateReservationRequest(
                        "Spotkanie",
                        Instant.now().plus(2, ChronoUnit.DAYS),
                        null
                );

        assertEquals(
                1,
                validator.validateProperty(request, "endMeeting").size()
        );
    }

    @Test
    void shouldAcceptTitleWithExactly150Characters() {
        CreateReservationRequest request =
                validRequest("A".repeat(150));

        assertTrue(validator.validate(request).isEmpty());
    }

    private CreateReservationRequest validRequest(String title) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);

        return new CreateReservationRequest(
                title,
                start,
                start.plus(1, ChronoUnit.HOURS)
        );
    }


    @Test
    void shouldRejectEndMeetingBeforeStartMeeting() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);

        CreateReservationRequest request =
                new CreateReservationRequest(
                        "Spotkanie",
                        start,
                        start.minus(1, ChronoUnit.HOURS)
                );

        assertTrue(
                validator.validate(request).stream()
                        .anyMatch(violation ->
                                violation.getPropertyPath().toString()
                                        .equals("meetingPeriodValid"))
        );
    }

    @Test
    void shouldRejectEndMeetingEqualToStartMeeting() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);

        CreateReservationRequest request =
                new CreateReservationRequest(
                        "Spotkanie",
                        start,
                        start
                );

        assertTrue(
                validator.validate(request).stream()
                        .anyMatch(violation ->
                                violation.getMessage().equals(
                                        "Data zakończenia musi być późniejsza niż data rozpoczęcia."
                                ))
        );
    }

}
