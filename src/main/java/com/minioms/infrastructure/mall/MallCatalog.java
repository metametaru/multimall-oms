package com.minioms.infrastructure.mall;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * モールコードから mall_id を解決する。
 *
 * <p>Why not: 起動時に一括ロードしない。Flyway のマイグレーション完了より前に
 * Bean が初期化されるとテーブルが存在せず起動に失敗するため、初回参照時に
 * 引いてキャッシュする。モールの追加は稀なので無効化は考えない。</p>
 */
@Component
@RequiredArgsConstructor
class MallCatalog {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    long idOf(String mallCode) {
        return cache.computeIfAbsent(mallCode, code ->
                jdbcTemplate.queryForObject("SELECT id FROM malls WHERE code = ?", Long.class, code));
    }
}
