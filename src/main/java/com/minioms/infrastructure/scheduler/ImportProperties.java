package com.minioms.infrastructure.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 取込バッチの設定。
 *
 * @param interval 実行間隔
 * @param lookback 遡って取得する期間。interval より長くして取りこぼしを防ぐ
 */
@ConfigurationProperties(prefix = "minioms.import")
record ImportProperties(Duration interval, Duration lookback) {
}
