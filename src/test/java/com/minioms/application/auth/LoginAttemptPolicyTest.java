package com.minioms.application.auth;

import com.minioms.domain.user.TooManyLoginAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ログイン試行制限の仕様。
 *
 * <p>パスワードのハッシュ化は総当たりを遅くするだけで止めはしない。
 * 試行そのものに上限を設けないと、時間さえかければ通る。</p>
 */
@DisplayName("ログイン試行の制限")
class LoginAttemptPolicyTest {

    private static final int MAX_FAILURES = 3;
    private static final Duration LOCKOUT = Duration.ofMinutes(1);
    private static final Instant 開始 = Instant.parse("2026-08-21T00:00:00Z");

    private MutableClock clock;
    private LoginAttemptPolicy policy;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(開始);
        policy = new LoginAttemptPolicy(MAX_FAILURES, LOCKOUT, clock);
    }

    @Test
    void 失敗が上限に達するまでは受け付ける() {
        失敗を重ねる("operator", MAX_FAILURES - 1);

        assertThatCode(() -> policy.verifyAccepting("operator")).doesNotThrowAnyException();
    }

    @Test
    void 上限に達すると受け付けなくなる() {
        失敗を重ねる("operator", MAX_FAILURES);

        assertThatThrownBy(() -> policy.verifyAccepting("operator"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("60秒");
    }

    @Test
    void 一定時間が過ぎれば自動的に解ける() {
        // Why: 解けない仕様だと、他人のユーザー名を叩き続けるだけでその人を締め出せる
        失敗を重ねる("operator", MAX_FAILURES);
        clock.進める(LOCKOUT);

        assertThatCode(() -> policy.verifyAccepting("operator")).doesNotThrowAnyException();
    }

    @Test
    void 成功すれば記録は消える() {
        失敗を重ねる("operator", MAX_FAILURES - 1);
        policy.recordSuccess("operator");
        policy.recordFailure("operator");

        // 直前の失敗1回だけが残っている状態なので、まだ上限には達しない
        assertThatCode(() -> policy.verifyAccepting("operator")).doesNotThrowAnyException();
    }

    @Test
    void 制限は利用者ごとに独立している() {
        失敗を重ねる("operator", MAX_FAILURES);

        assertThatCode(() -> policy.verifyAccepting("viewer")).doesNotThrowAnyException();
    }

    @Test
    void 存在しないユーザー名も同じように数える() {
        // Why: 実在する利用者だけを数えると、ロックされるかどうかで
        // ユーザー名の実在を判定できてしまう
        失敗を重ねる("no-such-user", MAX_FAILURES);

        assertThatThrownBy(() -> policy.verifyAccepting("no-such-user"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void 間隔が空いた失敗は積み上がらない() {
        // 解除時刻を過ぎた記録は捨てる。何日もかけた散発的な失敗で締め出さない
        失敗を重ねる("operator", MAX_FAILURES - 1);
        clock.進める(LOCKOUT.plusSeconds(1));
        失敗を重ねる("operator", MAX_FAILURES - 1);

        assertThatCode(() -> policy.verifyAccepting("operator")).doesNotThrowAnyException();
    }

    @Test
    void 失敗の許容回数は1以上でなければならない() {
        assertThatThrownBy(() -> new LoginAttemptPolicy(0, LOCKOUT, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void 失敗を重ねる(String username, int 回数) {
        for (int i = 0; i < 回数; i++) {
            policy.recordFailure(username);
        }
    }

    /** 時間の経過を任意に進められる時計 */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void 進める(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
