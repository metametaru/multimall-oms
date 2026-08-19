package com.minioms.domain;

/**
 * 業務ルール違反を表す例外の基底クラス。
 * HTTPステータスへの変換は presentation 層がこの型を起点に行うため、
 * 業務ルール違反は必ずこのサブクラスとして表現する。
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
