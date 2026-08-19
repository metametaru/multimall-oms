package com.minioms.infrastructure.mall;

import com.minioms.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モールBの独自ステータスコードとOMSのステータスの対応表。
 * モール固有の語彙をここで吸収し、ドメインに持ち込まないための境界。
 */
@DisplayName("モールBのステータスコード変換")
class MallBStatusCodeTest {

    @ParameterizedTest(name = "コード{0} は {1} に対応する")
    @CsvSource({
            "01, NEW",
            "02, CONFIRMED",
            "03, SHIPPED",
            "09, CANCELLED",
    })
    void 独自コードをOMSのステータスに変換する(String code, OrderStatus expected) {
        assertThat(MallBStatusCode.from(code)).hasValueSatisfying(
                status -> assertThat(status.toOrderStatus()).isEqualTo(expected));
    }

    @ParameterizedTest(name = "未知のコード{0}")
    @ValueSource(strings = {"99", "", "SHIPPED"})
    void 未知のコードは例外にせず変換不能として扱う(String code) {
        // モール側の仕様追加で新コードが増えても取込全体が止まらないようにする。
        // 例外にすると、1件の未知コードで同一バッチの正常な受注まで落ちる
        assertThat(MallBStatusCode.from(code)).isEmpty();
    }

    @Test
    void nullコードも変換不能として扱う() {
        assertThat(MallBStatusCode.from(null)).isEmpty();
    }

    @ParameterizedTest(name = "コード{0} の取込対象判定")
    @CsvSource({
            "01, true",
            "02, false",
            "03, false",
            "09, false",
    })
    void 取込対象はモール側で新規受付の受注だけ(String code, boolean importable) {
        // 発送済みやキャンセル済みの受注をOMSに取り込むと、状態機械の起点(NEW)と
        // 実態が食い違う。運用開始前の過去受注はOMSの管理対象外とする
        assertThat(MallBStatusCode.from(code)).hasValueSatisfying(
                status -> assertThat(status.isImportable()).isEqualTo(importable));
    }
}
