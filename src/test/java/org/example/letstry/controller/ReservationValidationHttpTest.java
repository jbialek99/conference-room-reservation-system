
package org.example.letstry.controller;

import org.example.letstry.exception.GlobalExceptionHandler;
import org.example.letstry.repository.ReservationRepository;
import org.example.letstry.service.HallService;
import org.example.letstry.service.ReservationService;
import org.example.letstry.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ReservationController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
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
        verifyServicesNotCalled();
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
        verifyServicesNotCalled();
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Data zakończenia musi być późniejsza niż data rozpoczęcia."
                ));

        verifyServicesNotCalled();
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
        verifyServicesNotCalled();
    }

    @Test
    void shouldRejectEmptyRequestBody() throws Exception {
        mockMvc.perform(post("/api/reservations/hall/1/reserve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Nieprawidłowa lub pusta treść żądania JSON."
                ));

        verifyServicesNotCalled();
    }

    @Test
    void shouldRejectInvalidJson() throws Exception {
        String invalidJson = String.join(
                "",
                "{",
                "\"title\"",
                ":"
        );

        mockMvc.perform(post("/api/reservations/hall/1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Nieprawidłowa lub pusta treść żądania JSON."
                ));

        verifyServicesNotCalled();
    }

    private void performInvalidRequest(String json) throws Exception {
        mockMvc.perform(post("/api/reservations/hall/1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    private void verifyServicesNotCalled() {
        verifyNoInteractions(
                reservationService,
                userService,
                hallService,
                reservationRepository
        );
    }
}
