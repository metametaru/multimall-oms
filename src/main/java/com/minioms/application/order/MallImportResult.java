package com.minioms.application.order;

/**
 * モール1件あたりの取込結果。
 *
 * @param imported    新規に取り込めた件数
 * @param skipped     既に取込済みだった件数(正常系)
 * @param failed      個々の受注の保存に失敗した件数
 * @param fetchFailed モールAPIからの取得自体に失敗したか
 */
public record MallImportResult(long mallId, int imported, int skipped, int failed, boolean fetchFailed) {

    static MallImportResult of(long mallId, int imported, int skipped, int failed) {
        return new MallImportResult(mallId, imported, skipped, failed, false);
    }

    static MallImportResult fetchFailure(long mallId) {
        return new MallImportResult(mallId, 0, 0, 0, true);
    }

    public boolean hasFailure() {
        return fetchFailed || failed > 0;
    }
}
