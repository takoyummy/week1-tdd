package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

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
    @DisplayName("userId를 넘기면 해당 사용자의 포인트 내역을 조회한다.")
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

    @Test
    @DisplayName("다수의 스레드에서 포인트 충전이 동시에 발생할 때도 동시성 문제가 발생하지 않는다.")
    void shouldHandleHighConcurrencyInChargeUserPoint() throws InterruptedException, ExecutionException {
        // given
        long userId = 1L;
        long initialAmount = 100L;
        long amountToCharge = 50L;
        int threadCount = 10;

        UserPoint existingUserPoint = new UserPoint(userId, initialAmount, System.currentTimeMillis());

        // Mockito 설정
        given(userPointTable.selectById(userId)).willReturn(existingUserPoint);

        // AtomicLong으로 중간 결과가 무시 될 수 있는 여지 차단
        AtomicLong accumulatedPoints = new AtomicLong(initialAmount);

        given(userPointTable.insertOrUpdate(eq(userId), anyLong())).willAnswer(invocation -> {
            Long amount = invocation.getArgument(1);
            long newAmount = accumulatedPoints.addAndGet(amountToCharge);
            return new UserPoint(userId, newAmount, System.currentTimeMillis());
        });

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<UserPoint>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown(); // 스레드 준비 완료
                startLatch.await(); // 모든 스레드가 준비될 때까지 대기
                try {
                    return pointService.chargeUserPoint(userId, amountToCharge);
                } finally {
                    doneLatch.countDown(); // 작업 완료 알림
                }
            }));
        }

        readyLatch.await(); // 모든 스레드가 준비될 때까지 대기
        startLatch.countDown(); // 모든 스레드에 시작 신호
        doneLatch.await(); // 모든 스레드가 작업을 완료할 때까지 대기

        long expectedFinalAmount = initialAmount + amountToCharge * threadCount;
        UserPoint finalUserPoint = null;

        for (Future<UserPoint> future : futures) {
            UserPoint result = future.get();
            finalUserPoint = result; // 마지막 업데이트된 결과를 가져옴
        }

        executorService.shutdown();

        // 최종 포인트 값이 예상대로인지 검증
        assertThat(finalUserPoint.point()).isEqualTo(expectedFinalAmount);

        // then
        then(userPointTable).should(times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        then(pointHistoryTable).should(times(threadCount)).insert(eq(userId), eq(amountToCharge), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트 충전이 성공적으로 이루어진다.")
    void shouldChargeUserPointSuccessfully() {
        // given
        long userId = 1L;
        long amountToCharge = 50L;
        // 현재 포인트 100점
        UserPoint existingUserPoint = new UserPoint(userId, 100L, System.currentTimeMillis());
        // 충전 후 포인트 100 + 50 = 150점
        UserPoint updatedUserPoint = new UserPoint(userId, 150L, System.currentTimeMillis());

        // existingUserPoint 조회 후 100점 반환
        given(userPointTable.selectById(userId)).willReturn(existingUserPoint);
        // 포인트 충전 후 포인트 150점으로 업데이트
        given(userPointTable.insertOrUpdate(eq(userId), eq(150L))).willReturn(updatedUserPoint);

        // when
        UserPoint result = pointService.chargeUserPoint(userId, amountToCharge);

        // then
        assertThat(result).isEqualTo(updatedUserPoint);

        // 포인트 충전 내역이 기록되었는지 확인
        ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);
        then(userPointTable).should().insertOrUpdate(eq(userId), eq(150L));
        then(pointHistoryTable).should().insert(eq(userId), eq(amountToCharge), eq(TransactionType.CHARGE), timestampCaptor.capture());

        // 캡처된 타임스탬프가 현재 시간으로부터 1초 이내에 있는지 검증
        long capturedTimestamp = timestampCaptor.getValue();
        long currentTime = System.currentTimeMillis();
        assertThat(capturedTimestamp).isBetween(currentTime - 1000, currentTime + 1000); // 1초 이내의 차이만 허용
    }

}
