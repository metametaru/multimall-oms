package com.minioms.application;

import java.util.function.Supplier;

/**
 * 複数の永続化操作を1つのトランザクションとして実行するポート。
 *
 * <p>受注のステータス更新と在庫の引当は、片方だけ成立してはいけない。
 * 「引当済だが受注は未確認」「確認済だが在庫は押さえられていない」という状態は
 * 実在庫と帳簿の食い違いに直結する。</p>
 *
 * <p>Why not: ユースケースに {@code @Transactional} を付けない。application 層を
 * フレームワーク非依存に保つ方針であり、トランザクション境界も「ここが一体である」
 * という宣言としてポート越しに表現する。Spring への依存は実装側に閉じる。</p>
 */
public interface TransactionRunner {

    <T> T execute(Supplier<T> action);
}
