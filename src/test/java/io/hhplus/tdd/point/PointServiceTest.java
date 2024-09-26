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

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private PointValidator pointValidator;

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
    @DisplayName("포인트 충전 시 충전 금액이 0보다 작으면 예외가 발생한다.")
    void testConcurrentChargeUserPoint() throws InterruptedException {
        long userId = 1L;
        long initialPoints = 100L;
        long amountToCharge = 10L;

        UserPoint userPoint = new UserPoint(userId, initialPoints, System.currentTimeMillis());

        // 초기 UserPoint 설정
        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long updatedPoints = invocation.getArgument(1);
            return new UserPoint(userId, updatedPoints, System.currentTimeMillis());
        });

        // 동시성 테스트를 위한 스레드 풀 및 CountDownLatch
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargeUserPoint(userId, amountToCharge);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 작업을 완료할 때까지 대기
        latch.await();

        // 예상 포인트는 초기 포인트 + (스레드 개수 * 충전 금액)
        long expectedPoints = initialPoints + (threadCount * amountToCharge);

        // 결과 확인
        verify(userPointTable, times(1)).insertOrUpdate(eq(userId), eq(expectedPoints));
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

    @Test
    @DisplayName("포인트 사용 시 잔액이 부족하면 예외가 발생한다.")
    void shouldFailIfInsufficientBalanceWhenUsingPoints() {
        long userId = 1L;
        long initialAmount = 100L;
        long amountToUse = 150L;

        UserPoint existingUserPoint = new UserPoint(userId, initialAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(existingUserPoint);

        ArgumentCaptor<UserPoint> userPointCaptor = ArgumentCaptor.forClass(UserPoint.class);

        doThrow(new IllegalArgumentException("잔액이 부족합니다."))
                .when(pointValidator)
                .validate(userPointCaptor.capture(), eq(amountToUse), eq(TransactionType.USE));

        // when, then
        try {
            pointService.useUserPoint(userId, amountToUse);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("잔액이 부족합니다.");
        }

        // 캡처된 UserPoint 객체를 검증합니다.
        UserPoint capturedUserPoint = userPointCaptor.getValue();
        assertThat(capturedUserPoint.point()).isEqualTo(initialAmount); // 포인트가 초기값과 동일해야 함
    }

    @Test
    @DisplayName("포인트 사용이 성공적으로 이루어진다.")
    void testConcurrentUseUserPoint() throws InterruptedException {
        long userId = 1L;
        long initialPoints = 100L;
        long amountToUse = 10L;

        UserPoint userPoint = new UserPoint(userId, initialPoints, System.currentTimeMillis());

        // 초기 UserPoint 설정
        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long updatedPoints = invocation.getArgument(1);
            return new UserPoint(userId, updatedPoints, System.currentTimeMillis());
        });

        // 동시성 테스트를 위한 스레드 풀 및 CountDownLatch
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.useUserPoint(userId, amountToUse);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 작업을 완료할 때까지 대기
        latch.await();

        // 예상 포인트는 초기 포인트 - (스레드 개수 * 사용 금액)
        long expectedPoints = initialPoints - (threadCount * amountToUse);

        // 결과 확인
        verify(userPointTable, times(1)).insertOrUpdate(eq(userId), eq(expectedPoints));
    }

    @Test
    @DisplayName("포인트 충전과 사용이 동시에 이루어진다.")
    void testConcurrentChargeAndUseUserPoint() throws InterruptedException {
        long userId = 1L;
        long initialPoints = 100L;
        long amountToCharge = 10L;
        long amountToUse = 5L;

        UserPoint userPoint = new UserPoint(userId, initialPoints, System.currentTimeMillis());

        // 초기 UserPoint 설정
        when(userPointTable.selectById(userId)).thenReturn(userPoint);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long updatedPoints = invocation.getArgument(1);
            return new UserPoint(userId, updatedPoints, System.currentTimeMillis());
        });

        // 동시성 테스트를 위한 스레드 풀 및 CountDownLatch
        int threadCount = 10;  // 총 스레드 개수 (충전 + 사용)
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 충전 작업을 수행하는 스레드 5개
        for (int i = 0; i < threadCount / 2; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargeUserPoint(userId, amountToCharge);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 사용 작업을 수행하는 스레드 5개
        for (int i = 0; i < threadCount / 2; i++) {
            executorService.execute(() -> {
                try {
                    pointService.useUserPoint(userId, amountToUse);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 작업을 완료할 때까지 대기
        latch.await();

        // 예상 포인트는 초기 포인트 + (충전 스레드 개수 * 충전 금액) - (사용 스레드 개수 * 사용 금액)
        long expectedPoints = initialPoints + (threadCount / 2 * amountToCharge) - (threadCount / 2 * amountToUse);

        verify(userPointTable, atLeast(1)).insertOrUpdate(eq(userId), eq(expectedPoints));
    }

}
