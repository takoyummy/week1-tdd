package io.hhplus.tdd.point;

import org.springframework.stereotype.Component;

@Component
public class PointValidator {
    /**
     * 포인트 충전 또는 사용이 유효한지 검사하고, 유효하지 않은 경우 예외를 던집니다.
     * @param userPoint 현재 사용자의 포인트
     * @param amount    충전 또는 사용할 포인트 양
     * @param transactionType 트랜잭션 타입 (CHARGE, USE)
     */
    public void validate(UserPoint userPoint, long amount, TransactionType transactionType) {
        if (transactionType == TransactionType.CHARGE) {
            if (amount <= 0) {
                throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
            }
        } else if (transactionType == TransactionType.USE) {
            if (amount <= 0) {
                throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다.");
            }
            if (userPoint.point() < amount) {
                throw new IllegalArgumentException("잔액이 부족합니다.");
            }
        }
    }
}
