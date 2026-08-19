package com.minioms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト用のPostgreSQLコンテナ定義。
 * Why not: H2 での代用はしない。方言・型・制約違反時の挙動が実DBと異なり、
 * 「テストは通るが本番で壊れる」事故を招くため(README参照)。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // docker-compose.yml と同じイメージに揃える。ローカルとCIで同一のDB挙動を保証するため
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }
}
