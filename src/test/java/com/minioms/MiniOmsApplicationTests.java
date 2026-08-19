package com.minioms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * アプリケーション起動とFlywayマイグレーション適用の疎通確認。
 * 実PostgreSQL上で ddl-auto:validate まで通ることを、この1本で担保する。
 */
@DisplayName("アプリケーション起動")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class MiniOmsApplicationTests {

    @Test
    void Flywayマイグレーション適用後にコンテキストが起動する() {
    }
}
