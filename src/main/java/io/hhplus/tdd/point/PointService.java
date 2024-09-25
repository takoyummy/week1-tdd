package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, Lock> userLocks = new ConcurrentHashMap<>();
    private final PointValidator pointValidator;  // Validator 의존성 추가

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
        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);

            pointValidator.validate(userPoint, amount, TransactionType.CHARGE);

            long newPointAmount = calculateNewPoint(userPoint, amount);
            UserPoint updatedUserPoint = updateUserPoint(userId, newPointAmount);
            recordPointHistory(userId, amount);
            return updatedUserPoint;
        } finally {
            lock.unlock();
            userLocks.remove(userId, lock);
        }
    }

    /**
     * 사용자의 포인트를 사용합니다.
     * @param userId
     * @param amount
     * @return UserPoint
     */
    public UserPoint useUserPoint(final long userId, final long amount) {
        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

        try {
            UserPoint userPoint = userPointTable.selectById(userId);

            // 유효성 검사, 실패 시 예외 발생
            pointValidator.validate(userPoint, amount, TransactionType.USE);

            long newPointAmount = userPoint.point() - amount;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPointAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

            return updatedUserPoint;
        } finally {
            lock.unlock();
            userLocks.remove(userId);
        }
    }

    /**
     * 포인트를 계산합니다.
     * @param userPoint
     * @param amount
     * @return long
     */
    private long calculateNewPoint(UserPoint userPoint, long amount) {
        return userPoint.point() + amount;
    }

    /**
     * 사용자 포인트를 업데이트합니다.
     * @param userId
     * @param newPointAmount
     * @return UserPoint
     */
    private UserPoint updateUserPoint(long userId, long newPointAmount) {
        return userPointTable.insertOrUpdate(userId, newPointAmount);
    }

    /**
     * 포인트 충전 내역을 기록합니다.
     * @param userId
     * @param amount
     */
    private void recordPointHistory(long userId, long amount) {
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
    }

}
