package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;

    /**
     * 사용자의 포인트 정보를 조회합니다.
     * @param userId
     * @return
     */
    public UserPoint getUserPoint(final long userId) {
        return userPointTable.selectById(userId);
    }

}
