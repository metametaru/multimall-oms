package com.minioms;

import org.springframework.boot.SpringApplication;

/**
 * Testcontainers のPostgreSQLを自動起動してアプリを立ち上げる開発用エントリポイント。
 * docker compose を使わずに動作確認したいときに使う。
 */
public class TestMiniOmsApplication {

    public static void main(String[] args) {
        SpringApplication.from(MiniOmsApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
