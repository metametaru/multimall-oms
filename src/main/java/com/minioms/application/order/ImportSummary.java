package com.minioms.application.order;

import java.util.List;

/**
 * 取込バッチ1回分の結果。
 *
 * <p>Why not: 失敗を例外で伝えない。1モールの障害で例外を投げると呼び出し元が
 * 成功した分の件数を受け取れず、「何件入って何件落ちたか」を記録できないため。</p>
 */
public record ImportSummary(List<MallImportResult> results) {

    public ImportSummary {
        results = List.copyOf(results);
    }

    public int totalImported() {
        return sum(MallImportResult::imported);
    }

    public int totalSkipped() {
        return sum(MallImportResult::skipped);
    }

    public int totalFailed() {
        return sum(MallImportResult::failed);
    }

    /** 取得自体に失敗したモールのID */
    public List<Long> failedMallIds() {
        return results.stream()
                .filter(MallImportResult::fetchFailed)
                .map(MallImportResult::mallId)
                .toList();
    }

    public boolean hasFailure() {
        return results.stream().anyMatch(MallImportResult::hasFailure);
    }

    private int sum(java.util.function.ToIntFunction<MallImportResult> field) {
        return results.stream().mapToInt(field).sum();
    }
}
