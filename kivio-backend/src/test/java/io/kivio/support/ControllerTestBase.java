package io.kivio.support;

import io.kivio.config.SecurityConfig;
import io.kivio.config.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(SecurityConfig.class)
public abstract class ControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // JwtAuthenticationFilter が @WebMvcTest に含まれる Filter として読み込まれ、
    // SecurityConfig 経由で JwtProvider に依存するためモックが必要。
    @MockitoBean
    protected JwtProvider jwtProvider;
}
