## Point 동시성 제어
-------------------

## 주요 동시성 제어 메커니즘

### 1. `ConcurrentHashMap`을 이용한 사용자별 `Lock` 관리

`PointService` 클래스에서는 `ConcurrentHashMap<Long, Lock>`을 사용하여 사용자별로 `Lock` 객체를 관리합니다. 
`userLocks` 맵은 `userId`를 키로 하고, 해당 사용자의 포인트 충전 또는 사용 시 동시성 제어를 위한 `Lock` 객체를 값으로 가지는 구조입니다.

- **`computeIfAbsent` 메서드**: 특정 `userId`에 대한 `Lock`이 존재하지 않을 경우 새로운 `ReentrantLock`을 생성하여 맵에 추가합니다.
- 이는 동일한 사용자의 포인트에 여러 스레드가 동시에 접근하는 것을 방지하기 위함입니다.

```java
Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
```

### 2. `tryLock`을 이용한 비블로킹 락 획득

충전(`chargeUserPoint`) 및 사용(`useUserPoint`) 메서드에서는 `tryLock` 메서드를 사용하여 일정 시간 동안 `Lock`을 시도합니다. 
이 방법은 블로킹을 피하고, 일정 시간 내에 `Lock`을 획득하지 못하면 예외를 발생시켜 요청이 처리되지 않도록 합니다.

- **타임아웃 설정**: `tryLock(10, TimeUnit.SECONDS)`로 10초 동안 `Lock`을 시도하며, 이 시간 내에 `Lock`을 얻지 못하면 예외를 발생시킵니다.

```java
lockAcquired = lock.tryLock(10, TimeUnit.SECONDS);
if (!lockAcquired) {
    throw new RuntimeException("Lock을 획득할 수 없습니다. 충전 요청을 처리할 수 없습니다.");
}
```

### 3. `AtomicLong`을 이용한 안전한 포인트 업데이트

사용자의 포인트는 `ConcurrentHashMap<Long, AtomicLong>`을 사용하여 관리되며, 
`AtomicLong`은 원자적 연산을 지원하여 안전하게 포인트를 업데이트할 수 있습니다.

- **`addAndGet` 메서드**: 포인트를 더하거나 뺄 때 `AtomicLong`의 `addAndGet` 메서드를 사용하여 연산이 중간에 끼어들지 않도록 보장합니다.

```java
long newPointAmount = atomicUserPoints.addAndGet(amount);
```

### 4. `synchronized` 키워드를 통한 기록 메서드 동기화

포인트 충전 및 사용 내역을 기록하는 `recordPointHistory`와 사용자 포인트를 업데이트하는 
`updateUserPoint` 메서드는 `synchronized` 키워드를 사용하여 메서드 수준에서 동기화를 처리합니다. 이를 통해 포인트 기록 및 업데이트 작업이 동시성 문제 없이 일관되게 수행될 수 있도록 보장합니다.

```java
private synchronized void recordPointHistory(long userId, long amount) {
    pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
}

private synchronized UserPoint updateUserPoint(long userId, long newPointAmount) {
    return userPointTable.insertOrUpdate(userId, newPointAmount);
}
```

## 동시성 테스트

`PointServiceTest` 클래스에서는 동시성 시나리오에 대한 다양한 테스트 케이스를 통해 `PointService` 클래스의 동시성 제어 기능을 검증하였습니다. 

### 1. 동시 포인트 충전 테스트

여러 스레드가 동시에 동일한 사용자에게 포인트를 충전하는 경우, 예상한 대로 모든 충전 작업이 완료된 후의 포인트 합계가 정확히 계산되는지 테스트하였습니다.

### 2. 동시 포인트 사용 테스트

여러 스레드가 동시에 동일한 사용자의 포인트를 사용하는 경우, 예상한 대로 모든 사용 작업이 완료된 후의 포인트 합계가 정확히 계산되는지 테스트하였습니다.

### 3. 충전과 사용의 동시 수행 테스트

포인트 충전과 사용이 동시에 발생하는 시나리오를 테스트하여, 이 과정에서 데이터의 일관성이 유지되는지 확인하였습니다.

---

 위와 같은 동시성 제어 메커니즘을 통해 안전하게 사용자 포인트를 관리할 수 있으며,
 다양한 동시성 테스트를 통해 해당 기능이 제대로 작동함을 검증했습니다. 