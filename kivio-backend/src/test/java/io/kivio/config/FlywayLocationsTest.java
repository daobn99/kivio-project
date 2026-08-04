package io.kivio.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway の locations 分離を検証します。
 *
 * <p>Flyway は locations を<strong>再帰的に</strong>走査する。そのため開発用シードを
 * {@code db/migration} 配下（例: {@code db/migration/dev/}）へ置くと、
 * {@code spring.flyway.locations: classpath:db/migration} を使う prod / test プロファイルでも
 * 適用されてしまい、本番 DB にテストユーザーやダミー申請が投入される。
 *
 * <p>この退行はマイグレーション実行まで表面化せず、気付いた時点では本番 DB に
 * 不正なデータが入った後になる。配置規約をテストで固定する。
 */
class FlywayLocationsTest {

    /** prod / test プロファイルが読み込む locations。 */
    private static final String PRODUCTION_LOCATION = "classpath*:db/migration/**/*.sql";
    /** dev プロファイルのみが追加で読み込む locations。 */
    private static final String DEV_SEED_LOCATION = "classpath*:db/seed/dev/*.sql";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    void production_migrations_contain_no_development_seed() throws IOException {
        List<String> fileNames = resolveFileNames(PRODUCTION_LOCATION);

        assertThat(fileNames)
                .as("db/migration 配下は本番に適用される。開発用シードは db/seed/dev へ置くこと")
                .isNotEmpty()
                .noneMatch(name -> name.contains("seed_development_data"))
                .noneMatch(name -> name.contains("seed_addresses"))
                .noneMatch(name -> name.contains("seed_seller_applications"));
    }

    @Test
    void development_seed_is_resolvable_from_its_own_location() throws IOException {
        // dev プロファイルの locations が空振りしていないこと（移動時の取りこぼし検知）
        assertThat(resolveFileNames(DEV_SEED_LOCATION))
                .as("dev プロファイルの spring.flyway.locations が参照する場所")
                .isNotEmpty();
    }

    private List<String> resolveFileNames(String pattern) throws IOException {
        return Arrays.stream(resolver.getResources(pattern))
                .map(Resource::getFilename)
                .toList();
    }
}
