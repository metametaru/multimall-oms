package com.minioms.presentation.order;

import jakarta.validation.constraints.NotNull;

/**
 * ステータスを進める操作のリクエスト。
 *
 * @param version 操作対象として画面が表示していた受注のバージョン
 *
 * <p>Why not: バージョンを任意項目にしない。省略を許すと「今のDBの状態に対して
 * 無条件に適用する」意味になり、他のオペレーターの操作を黙って上書きしうる。
 * 楽観ロックは省略できる時点で機能しなくなるため必須とする。</p>
 */
record StatusChangeRequest(@NotNull(message = "version は必須です") Long version) {
}
