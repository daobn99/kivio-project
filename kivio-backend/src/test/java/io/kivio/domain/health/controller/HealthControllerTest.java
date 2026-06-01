package io.kivio.domain.health.controller;

import io.kivio.support.ControllerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest extends ControllerTestBase {

    @MockitoBean
    private Clock clock;

    @Test
    void should_return_200_with_status_up_when_health_endpoint_called() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Tokyo"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").value("2026-06-01T21:00:00+09:00"));
    }
}
