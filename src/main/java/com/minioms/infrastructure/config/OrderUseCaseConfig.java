package com.minioms.infrastructure.config;

import com.minioms.application.TransactionRunner;
import com.minioms.application.order.FindOrdersUseCase;
import com.minioms.application.order.ImportOrdersUseCase;
import com.minioms.application.order.OrderLifecycleUseCase;
import com.minioms.application.order.MallOrderClient;
import com.minioms.application.order.OrderRepository;
import com.minioms.application.order.OrderSearchQuery;
import com.minioms.application.stock.StockAllocationService;
import com.minioms.application.stock.StockRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * application 層のユースケースを Bean として登録する。
 *
 * <p>Why not: ユースケース側に {@code @Service} を付ける案は採らない。
 * application をフレームワーク非依存に保つため、DIの知識はこの層に閉じ込める。</p>
 */
@Configuration
class OrderUseCaseConfig {

    // Why not: List<MallOrderClient> を直接受け取らない。実装が1つも無い状態だと
    // Spring が依存解決に失敗して起動できなくなるため、0件を許容する ObjectProvider を使う
    @Bean
    ImportOrdersUseCase importOrdersUseCase(ObjectProvider<MallOrderClient> mallOrderClients,
                                            OrderRepository orderRepository) {
        return new ImportOrdersUseCase(mallOrderClients.stream().toList(), orderRepository);
    }

    @Bean
    StockAllocationService stockAllocationService(StockRepository stockRepository) {
        return new StockAllocationService(stockRepository);
    }

    @Bean
    OrderLifecycleUseCase orderLifecycleUseCase(OrderRepository orderRepository,
                                                StockAllocationService stockAllocationService,
                                                TransactionRunner transactionRunner) {
        return new OrderLifecycleUseCase(orderRepository, stockAllocationService, transactionRunner);
    }

    @Bean
    FindOrdersUseCase findOrdersUseCase(OrderSearchQuery orderSearchQuery, OrderRepository orderRepository) {
        return new FindOrdersUseCase(orderSearchQuery, orderRepository);
    }
}
