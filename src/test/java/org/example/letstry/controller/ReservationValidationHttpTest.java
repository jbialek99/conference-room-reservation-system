
package org.example.letstry.controller;

import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationValidationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private HallService hallService;

    @MockitoBean
    private ReservationRepository reservationRepository;

    @Test
    void shouldRejectBlankTitle() throws Exception {
        String json = """
                {
                    "title": "   ",
                    "startMeeting": "2099-10-21T10:00:00Z",
                    "endMeeting": "2099-10-21T11:00:00Z"
                }
                """;

        performInvalidRequest(json);

        verifyNoInteractions(
                reservationService,
                userService,
                hallService,
                reservationRepository
        );
    }

    @Test
    void shouldRejectMissingStartMeeting() throws Exception {
        String json = """
                {
                    "title": "Spotkanie",
                    "endMeeting": "2099-10-21T11:00:00Z"
                }
                """;

        performInvalidRequest(json);

        verifyNoInteractions(
                reservationService,
                userService,
                hallService,
                reservationRepository
        );
    }

    @Test
    void shouldRejectInvalidMeetingPeriod() throws Exception {
        String json = """
                {
                    "title": "Spotkanie",
                    "startMeeting": "2099-10-21T11:00:00Z",
                    "endMeeting": "2099-10-21T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/reservations/hall/1/reserve")
                        .with(user("test-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Data zakończenia musi być późniejsza niż data rozpoczęcia."
                ));

        verifyNoInteractions(
                reservationService,
                userService,
                hallService,
                reservationRepository
        );
    }

    @Test
    void shouldRejectTooLongTitle() throws Exception {
        String json = """
                {
                    "title": "%s",
                    "startMeeting": "2099-10-21T10:00:00Z",
                    "endMeeting": "2099-10-21T11:00:00Z"
                }
                """.formatted("A".repeat(151));

        performInvalidRequest(json);

        verifyNoInteractions(
                reservationService,
                userService,
                hallService,
                reservationRepository
        );
    }

    private void performInvalidRequest(String json) throws Exception {
        mockMvc.perform(post("/api/reservations/hall/1/reserve")
                        .with(user("test-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
