package com.minioms.infrastructure.mall;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * モール接続設定。
 *
 * @param mallABaseUrl モールA(JSON形式)のベースURL
 * @param mallBBaseUrl モールB(XML形式)のベースURL
 * @param timeout      モールAPIを待つ上限
 */
@ConfigurationProperties(prefix = "minioms.mall")
record MallProperties(String mallABaseUrl, String mallBBaseUrl, @DefaultValue Timeouts timeout) {

    /**
     * モールAPIを待つ上限。
     *
     * <p>Why not: 既定値を application.yml に置かない。設定ファイルに書くと、
     * その行を消した環境だけが無制限に戻る。取込を止めないための仕組みが
     * 設定漏れで無効になるのは本末転倒なので、既定は型が持つ。</p>
     *
     * <p>3秒 / 10秒という値は「1モールの不調で一巡が終わらなくなる」ことを避ける側から決めた。
     * 全モールが最悪でも 2モール × 13秒 = 26秒 で一巡し、取込間隔(PT1M)の中に収まる。
     * 速く諦めすぎても取り逃すが、取り逃した受注は遡り期間つきの次回取込が拾う。
     * 一方で一巡が終わらない状態は次回も来ないので、こちらに寄せている。</p>
     *
     * <p>Why not: モールごとに別の値を持たせない。相手ごとのSLAが実際に分かれてから
     * 分ければよく、今は「どのモールも一定時間で諦める」以上のことを決められない。</p>
     *
     * @param connect 接続確立までの上限
     * @param read    応答を受け取るまでの上限
     */
    record Timeouts(@DefaultValue("3s") Duration connect, @DefaultValue("10s") Duration read) {
    }
}
