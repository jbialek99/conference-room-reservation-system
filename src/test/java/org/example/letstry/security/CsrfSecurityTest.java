
package org.example.letstry.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CsrfSecurityTest.ProbeController.class)
class CsrfSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void shouldRejectReservationPostWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/reservations/hall/1/reserve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void shouldRejectReservationDeleteWithoutCsrf() throws Exception {
        mockMvc.perform(delete("/api/reservations/delete/event-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void shouldAllowPostWithValidCsrf() throws Exception {
        mockMvc.perform(post("/api/csrf-probe").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void shouldAllowDeleteWithValidCsrf() throws Exception {
        mockMvc.perform(delete("/api/csrf-probe").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void shouldAllowGetWithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/csrf-probe"))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class ProbeController {

        @PostMapping("/api/csrf-probe")
        ResponseEntity<Void> postProbe() {
            return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/api/csrf-probe")
        ResponseEntity<Void> deleteProbe() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/csrf-probe")
        ResponseEntity<Void> getProbe() {
            return ResponseEntity.noContent().build();
        }
    }
}
