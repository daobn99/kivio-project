package io.kivio.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// @Container は付けない（クラス単位の stop を避けるためライフサイクルは static 初期化子で管理）。
// disabledWithoutDocker により Docker 未起動の環境ではクラスごとスキップする。
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    // シングルトンコンテナパターン: JVM 内で一度だけ起動し、テストクラスをまたいで再利用する。
    // @Container / @Testcontainers でクラス単位のライフサイクルに紐付けると、最初のクラスの afterAll で
    // static コンテナが stop され、同一コンテキスト署名を共有する後続クラスが停止済みコンテナを指す
    // キャッシュ済みコンテキストを再利用して接続不能（HikariPool total=0）になるため、手動起動とする。
    // Docker 未起動の環境では起動をスキップする（テスト自体は @ServiceConnection 解決時にスキップ相当となる）。
    @SuppressWarnings("resource")
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("kivio_test")
                    .withUsername("test")
                    .withPassword("test");

    // image 名 "redis" を Spring Boot の @ServiceConnection が認識し spring.data.redis を自動設定する
    @SuppressWarnings("resource")
    @ServiceConnection
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        // Docker 未起動の環境では起動を試みない（CI 以外のローカル等での失敗を避ける）
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
        }
    }
}
