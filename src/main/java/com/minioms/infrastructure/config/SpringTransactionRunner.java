package com.minioms.infrastructure.config;

import com.minioms.application.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * {@link TransactionRunner} の Spring 実装。
 *
 * <p>Why not: {@code @Transactional} のプロキシに任せない。ユースケースは Spring の
 * Bean として組み立てているが、アノテーション経由だと「同一クラス内の呼び出しでは
 * 効かない」といったプロキシの制約が業務コードの書き方を縛るため、
 * 境界を明示的に呼び出す形にする。</p>
 */
@Component
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
