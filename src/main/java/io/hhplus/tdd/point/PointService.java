package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, Lock> userLocks = new ConcurrentHashMap<>();
    private final PointValidator pointValidator;  // Validator 의존성 추가
    private final ConcurrentHashMap<Long, AtomicLong> userPoints = new ConcurrentHashMap<>();

    /**
     * 사용자의 포인트 정보를 조회합니다.
     * @param userId
     * @return UserPoint
     */
    public UserPoint getUserPoint(final long userId) {
        return userPointTable.selectById(userId);
    }

    /**
     * 사용자의 포인트 내역을 조회합니다.
     * @param userId
     * @return List<PointHistory>
     */
    public List<PointHistory> getUserPointHistories(final long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * 사용자의 포인트를 충전합니다.
     * @param userId
     * @param amount
     * @return UserPoint
     */
    public UserPoint chargeUserPoint(final long userId, final long amount) {
        AtomicLong atomicUserPoints = userPoints.computeIfAbsent(userId, k -> new AtomicLong(getUserPoint(userId).point()));

        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(10, TimeUnit.SECONDS);

            if (!lockAcquired) {
                throw new RuntimeException("Lock을 획득할 수 없습니다. 충전 요청을 처리할 수 없습니다.");
            }

            long currentPoint = atomicUserPoints.get();
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            pointValidator.validate(userPoint, amount, TransactionType.CHARGE);

            long newPointAmount = atomicUserPoints.addAndGet(amount);
            UserPoint updatedUserPoint = updateUserPoint(userId, newPointAmount);
            recordPointHistory(userId, amount);

            return updatedUserPoint;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            throw new RuntimeException("Lock 획득 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if(lockAcquired) {
                lock.unlock();
                userLocks.remove(userId, lock);
            }
        }
    }

    /**
     * 사용자의 포인트를 사용합니다.
     * @param userId
     * @param amount
     * @return UserPoint
     */
    public UserPoint useUserPoint(final long userId, final long amount) {
        AtomicLong atomicUserPoints = userPoints.computeIfAbsent(userId, k -> new AtomicLong(getUserPoint(userId).point()));

        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(10, TimeUnit.SECONDS);

            if (!lockAcquired) {
                throw new RuntimeException("Lock을 획득할 수 없습니다. 포인트 사용 요청을 처리할 수 없습니다.");
            }

            long currentPoint = atomicUserPoints.get();

            // 유효성 검사, 실패 시 예외 발생
            pointValidator.validate(new UserPoint(userId, currentPoint, System.currentTimeMillis()), amount, TransactionType.USE);

            long newPointAmount = atomicUserPoints.addAndGet(-amount);
            UserPoint updatedUserPoint = updateUserPoint(userId, newPointAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

            return updatedUserPoint;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            throw new RuntimeException("Lock 획득 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (lockAcquired) {
                lock.unlock();
                userLocks.remove(userId);
            }
        }
    }

    /**
     * 포인트 충전 내역을 기록하는 synchronized 메서드입니다.
     * @param userId
     * @param amount
     */
    private synchronized void recordPointHistory(long userId, long amount) {
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
    }

    /**
     * 사용자 포인트를 업데이트하는 synchronized 메서드입니다.
     * @param userId
     * @param newPointAmount
     * @return UserPoint
     */
    private synchronized UserPoint updateUserPoint(long userId, long newPointAmount) {
        // 실제 데이터베이스 또는 테이블에서 포인트를 업데이트하고, 업데이트된 UserPoint 객체를 반환합니다.
        return userPointTable.insertOrUpdate(userId, newPointAmount);
    }

}
