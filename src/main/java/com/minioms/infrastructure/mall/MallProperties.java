package com.minioms.infrastructure.mall;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * モール接続設定。
 *
 * @param mallABaseUrl モールA(JSON形式)のベースURL
 * @param mallBBaseUrl モールB(XML形式)のベースURL
 */
@ConfigurationProperties(prefix = "minioms.mall")
record MallProperties(String mallABaseUrl, String mallBBaseUrl) {
}
