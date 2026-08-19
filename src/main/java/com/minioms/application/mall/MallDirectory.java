package com.minioms.application.mall;

import java.util.Optional;

/**
 * モールコード({@code MALL_A})とDB採番のモールID(1)を相互に解決する。
 *
 * <p>Why not: モールIDをそのまま外部に出さない。IDはDBが採番する内部識別子で
 * 環境ごとに値が変わりうるため、API契約やログにはコードを使う。
 * この変換の実装(DB参照・キャッシュ)は infrastructure 層に委ねる。</p>
 */
public interface MallDirectory {

    /** 未知のコードなら空。入力値の検証に使うため例外にはしない */
    Optional<Long> idOf(String mallCode);

    /** 未知のIDなら空。参照整合性で守られているため通常は空にならない */
    Optional<String> codeOf(long mallId);
}
