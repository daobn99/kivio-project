package io.kivio.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true) // Docker 未起動の環境では自動スキップ
public abstract class RepositoryTestBase {

    @SuppressWarnings("resource")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("kivio_test")
                    .withUsername("test")
                    .withPassword("test");
}
