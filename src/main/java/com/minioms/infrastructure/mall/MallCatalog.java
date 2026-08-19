package com.minioms.infrastructure.mall;

import com.minioms.application.mall.MallDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * モールコードとモールIDを相互に解決する({@link MallDirectory} の実装)。
 *
 * <p>Why not: 起動時に一括ロードしない。Flyway のマイグレーション完了より前に
 * Bean が初期化されるとテーブルが存在せず起動に失敗するため、初回参照時に
 * 引いてキャッシュする。モールの追加は稀なので無効化は考えない。</p>
 *
 * <p>Why not: 未知の値を例外にしない。モールコードは利用者が指定しうる入力であり、
 * 「存在しない」は異常ではなく入力検証の結果として扱う。</p>
 */
@Component
@RequiredArgsConstructor
class MallCatalog implements MallDirectory {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Long> idByCode = new ConcurrentHashMap<>();
    private final Map<Long, String> codeById = new ConcurrentHashMap<>();

    @Override
    public Optional<Long> idOf(String mallCode) {
        if (mallCode == null || mallCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idByCode.computeIfAbsent(mallCode, code ->
                queryForNullable("SELECT id FROM malls WHERE code = ?", Long.class, code)));
    }

    @Override
    public Optional<String> codeOf(long mallId) {
        return Optional.ofNullable(codeById.computeIfAbsent(mallId, id ->
                queryForNullable("SELECT code FROM malls WHERE id = ?", String.class, id)));
    }

    /** モールクライアントは存在するモールを前提に動くため、未登録は設定の誤りとして例外にする */
    long requireIdOf(String mallCode) {
        return idOf(mallCode).orElseThrow(() ->
                new IllegalStateException("モールが未登録です: code=" + mallCode));
    }

    // Why not: computeIfAbsent に null を返させたままにしない。
    // ConcurrentHashMap は null 値を保持できず、未登録の値を毎回DBに問い合わせ直すことになるが、
    // 未登録の問い合わせは入力ミス時にしか起きないため許容する
    private <T> T queryForNullable(String sql, Class<T> type, Object argument) {
        try {
            return jdbcTemplate.queryForObject(sql, type, argument);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
