
package org.example.letstry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.letstry.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlookRoomResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutlookRoomResponseMapper mapper = new OutlookRoomResponseMapper();

    private static final String HALL_EMAIL = "room@example.com";

    @Test
    void shouldReturnConfirmedWhenRoomAccepted() throws Exception {
        JsonNode event = eventWithResponse("accepted");

        assertEquals(
                Optional.of(ReservationStatus.CONFIRMED),
                mapper.resolveStatus(event, HALL_EMAIL)
        );
    }

    @Test
    void shouldReturnRejectedWhenRoomDeclined() throws Exception {
        JsonNode event = eventWithResponse("declined");

        assertEquals(
                Optional.of(ReservationStatus.REJECTED),
                mapper.resolveStatus(event, HALL_EMAIL)
        );
    }

    @Test
    void shouldReturnPendingWhenRoomHasNotResponded() throws Exception {
        for (String response : new String[]{
                "none", "notResponded", "tentativelyAccepted"
        }) {
            JsonNode event = eventWithResponse(response);

            assertEquals(
                    Optional.of(ReservationStatus.PENDING),
                    mapper.resolveStatus(event, HALL_EMAIL)
            );
        }
    }

    @Test
    void shouldIgnoreOtherAttendees() throws Exception {
        JsonNode event = objectMapper.readTree("""
                {
                    "attendees": [
                        {
                            "emailAddress": {
                                "address": "person@example.com"
                            },
                            "status": {
                                "response": "accepted"
                            }
                        },
                        {
                            "emailAddress": {
                                "address": "room@example.com"
                            },
                            "status": {
                                "response": "declined"
                            }
                        }
                    ]
                }
                """);

        assertEquals(
                Optional.of(ReservationStatus.REJECTED),
                mapper.resolveStatus(event, HALL_EMAIL)
        );
    }

    @Test
    void shouldReturnEmptyWhenRoomIsMissing() throws Exception {
        JsonNode event = objectMapper.readTree("""
                {
                    "attendees": [
                        {
                            "emailAddress": {
                                "address": "person@example.com"
                            },
                            "status": {
                                "response": "accepted"
                            }
                        }
                    ]
                }
                """);

        assertTrue(
                mapper.resolveStatus(event, HALL_EMAIL).isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyForUnknownResponse() throws Exception {
        JsonNode event = eventWithResponse("unknown");

        assertTrue(
                mapper.resolveStatus(event, HALL_EMAIL).isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyForMissingAttendees() throws Exception {
        JsonNode event = objectMapper.readTree("{}");

        assertTrue(
                mapper.resolveStatus(event, HALL_EMAIL).isEmpty()
        );
    }

    private JsonNode eventWithResponse(String response) throws Exception {
        return objectMapper.readTree("""
                {
                    "attendees": [
                        {
                            "emailAddress": {
                                "address": "room@example.com"
                            },
                            "status": {
                                "response": "%s"
                            }
                        }
                    ]
                }
                """.formatted(response));
    }
}
