package com.minioms.infrastructure.persistence.stock;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.stock.StockRepository;
import com.minioms.domain.stock.DuplicateStockException;
import com.minioms.domain.stock.Stock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在庫リポジトリの仕様。
 *
 * <p>在庫の数え方が壊れた状態(引当済が実在庫を超える等)をDBに残さないことを、
 * アプリを迂回した直接SQLで確認する。ドメイン(Stock)でも同じ不変条件を守っているが、
 * 手作業のSQLや将来の不具合で壊れた在庫が入ると原因特定が極端に難しくなるため、
 * DB制約を最終防衛線として二重に持つ。</p>
 */
@DisplayName("在庫リポジトリ")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class StockRepositoryAdapterTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 保存した在庫は商品コードで取り出せて内容が往復する() {
        stockRepository.save(Stock.of("SKU-TEST-001", 10));

        entityManager.flush();
        entityManager.clear();

        assertThat(stockRepository.findByProductCode("SKU-TEST-001"))
                .get()
                .satisfies(stock -> {
                    assertThat(stock.quantityOnHand()).isEqualTo(10);
                    assertThat(stock.quantityAllocated()).isZero();
                    assertThat(stock.availableQuantity()).isEqualTo(10);
                });
    }

    @Test
    void 引当した在庫は引当済数が保存される() {
        Stock saved = stockRepository.save(Stock.of("SKU-TEST-002", 10));

        stockRepository.save(saved.allocate(3));

        entityManager.flush();
        entityManager.clear();

        assertThat(stockRepository.findByProductCode("SKU-TEST-002"))
                .get()
                .satisfies(stock -> {
                    assertThat(stock.quantityOnHand()).isEqualTo(10);
                    assertThat(stock.quantityAllocated()).isEqualTo(3);
                });
    }

    @Test
    void 同じ商品コードの在庫は二重に登録できない() {
        // 同じ商品の在庫行が2つあると、どちらを引き当てたかで数が食い違う
        stockRepository.save(Stock.of("SKU-TEST-003", 10));

        assertThatThrownBy(() -> stockRepository.save(Stock.of("SKU-TEST-003", 5)))
                .isInstanceOf(DuplicateStockException.class);
    }

    @Test
    void 引当済が実在庫を超える在庫はDBが受け付けない() {
        // アプリを迂回した直接SQL。ドメインを通らない経路でも壊れた在庫は残せない
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO stocks (product_code, quantity_on_hand, quantity_allocated) VALUES (?, ?, ?)",
                "SKU-TEST-004", 3, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 実在庫が負の在庫はDBが受け付けない() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO stocks (product_code, quantity_on_hand) VALUES (?, ?)",
                "SKU-TEST-005", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 行ロック付きの取得でも同じ在庫が読める() {
        stockRepository.save(Stock.of("SKU-TEST-006", 7));

        entityManager.flush();
        entityManager.clear();

        assertThat(stockRepository.findByProductCodeForUpdate("SKU-TEST-006"))
                .get()
                .extracting(Stock::quantityOnHand)
                .isEqualTo(7);
    }

    @Test
    void 未登録の商品コードでは何も返らない() {
        assertThat(stockRepository.findByProductCode("SKU-存在しない")).isEmpty();
    }

    @Test
    void マイグレーションでモックモールの商品の在庫が登録されている() {
        // モックモールが返す受注をそのまま引当まで通せる状態を初期データで用意している
        assertThat(stockRepository.findByProductCode("SKU-001")).isPresent();
        assertThat(stockRepository.findByProductCode("SKU-777")).isPresent();
    }
}
