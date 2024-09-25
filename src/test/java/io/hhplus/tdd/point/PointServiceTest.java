package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    @Test
    @DisplayName("userId를 넘기면 포인트를 조회한다.")
    void returnUserPointWhenUserIdIsProvided() {
        // given
        long userId = 1L;
        UserPoint userPoint = new UserPoint(userId, 100L, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(userPoint);

        // when
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.point()).isEqualTo(100L);
        then(userPointTable).should().selectById(userId);
    }

    @Test
    void shouldReturnPointHistoryForUser() {
        // given
        long userId = 1L;
        List<PointHistory> expectedHistories = List.of(
                new PointHistory(1L, userId, 100L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, userId, -50L, TransactionType.USE, System.currentTimeMillis())
        );
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(expectedHistories);

        // when
        List<PointHistory> histories = pointService.getUserPointHistories(userId);

        // then
        assertThat(histories).hasSize(2);
        assertThat(histories).isEqualTo(expectedHistories);
        then(pointHistoryTable).should().selectAllByUserId(userId);
    }

}
