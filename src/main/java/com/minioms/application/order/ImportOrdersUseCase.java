package com.minioms.application.order;

import com.minioms.domain.order.DuplicateMallOrderException;
import com.minioms.domain.order.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 全モールから受注を取り込む。
 *
 * <p>Why not: このクラスに {@code @Transactional} は付けない。バッチ全体を1つの
 * トランザクションにすると、1件の異常データで取込済みの全件がロールバックされる。
 * 保存は受注1件ごとに独立したトランザクションで行い、失敗は件数として記録する。</p>
 *
 * <p>Why not: Spring のアノテーション({@code @Service})も付けない。ユースケースを
 * フレームワーク非依存に保つことで、テストがDIコンテナの起動を必要としなくなる。
 * Bean定義は infrastructure 層の設定クラスが行う。</p>
 */
public class ImportOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportOrdersUseCase.class);

    private final List<MallOrderClient> mallOrderClients;
    private final OrderRepository orderRepository;

    public ImportOrdersUseCase(List<MallOrderClient> mallOrderClients, OrderRepository orderRepository) {
        this.mallOrderClients = List.copyOf(mallOrderClients);
        this.orderRepository = orderRepository;
    }

    public ImportSummary execute(OffsetDateTime since) {
        List<MallImportResult> results = mallOrderClients.stream()
                .map(client -> importFrom(client, since))
                .toList();

        ImportSummary summary = new ImportSummary(results);
        log.info("受注取込完了: 取込{}件 スキップ{}件 失敗{}件 取得失敗モール{}",
                summary.totalImported(), summary.totalSkipped(), summary.totalFailed(), summary.failedMallIds());
        return summary;
    }

    private MallImportResult importFrom(MallOrderClient client, OffsetDateTime since) {
        List<Order> fetched;
        try {
            fetched = client.fetchOrdersPlacedSince(since);
        } catch (RuntimeException e) {
            // モールAPIの一時障害は日常的に起きる。他モールの取込は継続させる
            log.error("モールからの受注取得に失敗しました: mallId={}", client.mallId(), e);
            return MallImportResult.fetchFailure(client.mallId());
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        for (Order order : fetched) {
            switch (importOne(order)) {
                case IMPORTED -> imported++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        return MallImportResult.of(client.mallId(), imported, skipped, failed);
    }

    private Outcome importOne(Order order) {
        try {
            orderRepository.save(order);
            return Outcome.IMPORTED;
        } catch (DuplicateMallOrderException e) {
            // 取込ウィンドウを重ねて取得する運用のため、重複は正常系として扱う
            log.debug("取込済みのためスキップしました: {}", order.mallOrderKey());
            return Outcome.SKIPPED;
        } catch (RuntimeException e) {
            log.error("受注の保存に失敗しました: {}", order.mallOrderKey(), e);
            return Outcome.FAILED;
        }
    }

    private enum Outcome {
        IMPORTED, SKIPPED, FAILED
    }
}
