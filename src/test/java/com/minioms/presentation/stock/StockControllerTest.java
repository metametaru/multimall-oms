package com.minioms.presentation.stock;

import com.minioms.application.stock.FindStocksUseCase;
import com.minioms.domain.stock.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在庫参照APIのHTTP契約。
 *
 * <p>オペレーターが見たいのは「あと何個売れるか(引当可能数)」であり、
 * 実在庫だけでは判断できない。3つの数がそのまま読めることを契約とする。</p>
 */
@DisplayName("在庫参照API")
@WebMvcTest(StockController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindStocksUseCase findStocksUseCase;

    @Test
    void 実在庫と引当済と引当可能数を返す() throws Exception {
        given(findStocksUseCase.findAll()).willReturn(List.of(new Stock(1L, "SKU-001", 10, 4, 2L)));

        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productCode").value("SKU-001"))
                .andExpect(jsonPath("$[0].quantityOnHand").value(10))
                .andExpect(jsonPath("$[0].quantityAllocated").value(4))
                .andExpect(jsonPath("$[0].availableQuantity").value(6));
    }

    @Test
    void 在庫が1件も無くても空の配列を返す() {
        // 「在庫が無い」は異常ではない。エラーにすると画面が原因不明の失敗になる
        given(findStocksUseCase.findAll()).willReturn(List.of());

        org.assertj.core.api.Assertions.assertThatCode(() ->
                mockMvc.perform(get("/api/stocks"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(0)))
                .doesNotThrowAnyException();
    }
}
