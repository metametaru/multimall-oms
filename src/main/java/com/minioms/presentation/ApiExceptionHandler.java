package com.minioms.presentation;

import com.minioms.domain.DomainException;
import com.minioms.domain.order.OrderNotFoundException;
import com.minioms.presentation.order.UnknownMallCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 業務例外とHTTPステータスの対応表。
 *
 * <p>ドメイン層は業務ルール違反を {@link DomainException} で表現し、HTTPの語彙を持たない。
 * 変換をこの1箇所に集約することで、同じ業務例外が呼び出し経路ごとに違う
 * ステータスで返る事故を防ぐ。</p>
 *
 * <p>Why not: 例外クラスに {@code @ResponseStatus} を付ける案は採らない。
 * ドメイン層がSpring MVCに依存してしまい、フレームワーク非依存という前提が崩れる。</p>
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 対象が存在しない: 404 */
    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleOrderNotFound(OrderNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "受注が見つかりません", e.getMessage());
    }

    /** API入力の誤り: 400 */
    @ExceptionHandler({UnknownMallCodeException.class, IllegalArgumentException.class})
    ProblemDetail handleInvalidInput(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "リクエストが不正です", e.getMessage());
    }

    /** リクエストボディの検証違反: 400。どの項目が問題かを利用者に返す */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationError(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("リクエストボディが不正です");
        return problem(HttpStatus.BAD_REQUEST, "リクエストが不正です", detail);
    }

    /** 解釈できないリクエストボディ: 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        // Why not: 例外メッセージをそのまま返さない。内部のクラス名やパース位置が漏れる
        return problem(HttpStatus.BAD_REQUEST, "リクエストが不正です", "リクエストボディを解釈できません");
    }

    /** 型の合わない値(存在しないステータス名など): 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "リクエストが不正です",
                "パラメータ %s の値が不正です: %s".formatted(e.getName(), e.getValue()));
    }

    /**
     * 上記に当てはまらない業務ルール違反: 409。
     * Why not: 400 にしない。リクエストの形式は正しく、受注の「今の状態」と
     * 衝突しているだけであり、同じ要求が状態次第で成功しうるため。
     */
    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException e) {
        return problem(HttpStatus.CONFLICT, "処理できない要求です", e.getMessage());
    }

    /** 想定外の失敗: 500。原因はサーバー側にだけ残し、利用者には内部情報を返さない */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("想定外のエラーが発生しました", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部エラー",
                "処理に失敗しました。時間をおいて再試行してください");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
