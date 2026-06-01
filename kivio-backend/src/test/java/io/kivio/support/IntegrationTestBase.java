package io.kivio.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true) // Docker 未起動の環境では自動スキップ
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("kivio_test")
                    .withUsername("test")
                    .withPassword("test");
}
