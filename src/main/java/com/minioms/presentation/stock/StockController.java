package com.minioms.presentation.stock;

import com.minioms.application.stock.FindStocksUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在庫参照API。受注操作の結果として在庫がどう動いたかを確認する入口。
 *
 * <p>Why not: 在庫を直接増減するAPIは用意しない。在庫が動く理由は受注の
 * ライフサイクル(引当・出荷・キャンセル)に限られ、任意の書き換えを許すと
 * 受注と在庫の対応が追えなくなる。入荷はスコープ外。</p>
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
class StockController {

    private final FindStocksUseCase findStocksUseCase;

    @GetMapping
    List<StockResponse> list() {
        return findStocksUseCase.findAll().stream().map(StockResponse::from).toList();
    }
}
