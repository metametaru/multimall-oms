package com.minioms.infrastructure.scheduler;

import com.minioms.application.order.ImportOrdersUseCase;
import com.minioms.application.order.ImportSummary;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 定期的に全モールから受注を取り込む。
 *
 * <p>Why not: 前回実行時刻を保存して差分取得する方式は採らない。実行が失敗した
 * 場合や複数インスタンスで動かす場合に「どこまで取得済みか」の管理が必要になる。
 * 固定の遡り期間で毎回重複込みで取得し、重複は冪等キーに弾かせる方が壊れにくい。</p>
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
class OrderImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderImportScheduler.class);

    private final ImportOrdersUseCase importOrdersUseCase;
    private final ImportProperties properties;

    @Scheduled(fixedDelayString = "${minioms.import.interval}")
    void importOrders() {
        OffsetDateTime since = OffsetDateTime.now().minus(properties.lookback());
        ImportSummary summary = importOrdersUseCase.execute(since);

        if (summary.hasFailure()) {
            log.warn("取込に失敗が含まれます: 失敗{}件 取得失敗モール{}",
                    summary.totalFailed(), summary.failedMallIds());
        }
    }
}
