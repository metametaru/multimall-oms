package com.minioms.infrastructure.mall;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.math.BigDecimal;
import java.util.List;

/**
 * モールB(XML形式)のレスポンス。
 * 要素名の省略、日時のタイムゾーンなし独自フォーマット、独自ステータスコードと、
 * モールAとは何もかも異なる。この差異を {@link MallBOrderClient} が吸収する。
 */
@JacksonXmlRootElement(localName = "orderList")
record MallBOrderResponse(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "order")
        List<MallBOrder> orders) {

    record MallBOrder(
            @JacksonXmlProperty(localName = "no") String no,
            @JacksonXmlProperty(localName = "buyer") String buyer,
            /** yyyyMMddHHmmss 形式。タイムゾーンの指定がない(JST固定の想定) */
            @JacksonXmlProperty(localName = "orderDatetime") String orderDatetime,
            @JacksonXmlProperty(localName = "amount") BigDecimal amount,
            @JacksonXmlProperty(localName = "statusCode") String statusCode,
            @JacksonXmlProperty(localName = "detailList") MallBDetailList detailList) {

        /**
         * Why not: XML上のラッパー要素をそのまま呼び出し側に見せない。
         * detailList は形式都合の入れ物でしかないため、ここで畳んで明細の並びとして扱う。
         * 明細なしの受注はドメイン側(Order)が弾くので、ここでは空リストに正規化するに留める。
         */
        List<MallBDetail> details() {
            return detailList == null || detailList.details() == null ? List.of() : detailList.details();
        }
    }

    /**
     * {@code <detailList><detail/>...</detailList>} のラッパー要素。
     *
     * <p>Why not: {@code @JacksonXmlElementWrapper} でラッパーを省略表現しない。
     * record のコンストラクタ引数名の解決とラッパー名の解決が噛み合わず、
     * デシリアライザの生成自体が失敗する(結果はXMLパース不能ではなく
     * 「XMLを読めるコンバータが無い」という無関係な症状で表面化する)。</p>
     */
    record MallBDetailList(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "detail")
            List<MallBDetail> details) {
    }

    record MallBDetail(
            @JacksonXmlProperty(localName = "itemCd") String itemCd,
            @JacksonXmlProperty(localName = "itemNm") String itemNm,
            @JacksonXmlProperty(localName = "price") BigDecimal price,
            @JacksonXmlProperty(localName = "cnt") int cnt) {
    }
}
