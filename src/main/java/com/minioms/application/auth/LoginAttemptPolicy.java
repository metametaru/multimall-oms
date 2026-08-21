package com.minioms.application.auth;

import com.minioms.domain.user.TooManyLoginAttemptsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 連続して失敗したログインを一定時間受け付けなくする。
 *
 * <p>パスワードのハッシュ化だけでは、総当たりを「遅くする」ことしかできない。
 * 試行そのものに上限を設けないと、時間さえかければ通ってしまう。</p>
 *
 * <p>Why not: 実在するユーザーだけを数えない。存在しないユーザー名も同じように数えることで、
 * 「ロックされたかどうか」からユーザー名の実在を判定できないようにする。</p>
 *
 * <p>Why not: 恒久的なロックにしない。ロックが解けない仕様だと、第三者が他人の
 * ユーザー名を叩き続けるだけでその人を締め出せる。一定時間で自動的に解ける形にして、
 * 総当たりの速度を落とすことに目的を絞る。</p>
 *
 * <p>Why not: 記録をDBや外部ストアに持たない。複数インスタンスで運用するなら共有が要るが、
 * 本デモは単一インスタンス構成であり、そのために依存を増やす段階ではない。
 * 分散させる時点でこのクラスをポート化して差し替える。</p>
 */
public class LoginAttemptPolicy {

    /**
     * 記録を保持するユーザー名の上限。無制限に持つと、毎回異なるユーザー名を
     * 送りつけるだけでメモリを食い潰せてしまう。
     */
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, FailureRecord> records = new ConcurrentHashMap<>();

    public LoginAttemptPolicy(int maxFailures, Duration lockout, Clock clock) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("失敗の許容回数は1以上である必要があります: " + maxFailures);
        }
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    /**
     * いま試行を受け付けてよいかを確かめる。
     *
     * @throws TooManyLoginAttemptsException 直近の失敗が上限に達し、まだ解除時刻を過ぎていない場合
     */
    public void verifyAccepting(String username) {
        FailureRecord record = records.get(username);
        if (record != null && record.blocksAt(clock.instant(), maxFailures, lockout)) {
            throw new TooManyLoginAttemptsException(lockout);
        }
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        purgeExpiredIfCrowded(now);
        records.compute(username, (key, current) ->
                current == null || current.hasExpired(now, lockout)
                        ? FailureRecord.first(now)
                        : current.next(now));
    }

    /** 成功したら記録を捨てる。正しく入れ直せた利用者に、以降の試行制限を残さない */
    public void recordSuccess(String username) {
        records.remove(username);
    }

    private void purgeExpiredIfCrowded(Instant now) {
        if (records.size() < MAX_TRACKED_USERNAMES) {
            return;
        }
        records.values().removeIf(record -> record.hasExpired(now, lockout));
    }

    /**
     * 連続失敗の記録。
     * Why not: 失敗時刻を全件持たない。必要なのは「何回続いたか」と「最後はいつか」だけで、
     * 履歴を持つと試行回数に比例してメモリが増える。
     */
    private record FailureRecord(int count, Instant lastFailedAt) {

        static FailureRecord first(Instant now) {
            return new FailureRecord(1, now);
        }

        FailureRecord next(Instant now) {
            return new FailureRecord(count + 1, now);
        }

        boolean hasExpired(Instant now, Duration lockout) {
            return !now.isBefore(lastFailedAt.plus(lockout));
        }

        boolean blocksAt(Instant now, int maxFailures, Duration lockout) {
            return count >= maxFailures && !hasExpired(now, lockout);
        }
    }
}
