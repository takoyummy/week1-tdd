package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

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

}
