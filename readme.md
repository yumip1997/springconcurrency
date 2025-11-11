## 아키텍처

### 1. SQS 방식 (비동기 처리)

#### 전체 흐름

```
[주문 생성] 
    │
    ▼
[OrderService.order()]
    │
    ▼
[StockService.sendStockReduceRequest()]
    │
    ▼
[AwsSqsMessageSender.sendMessage()]
    │
    ▼
┌─────────────────────────────────────┐
│   AWS SQS FIFO Queue               │
│   (상품별 messageGroupId로 분류)    │
└─────────────────────────────────────┘
    │
    ▼
[StockMessageListener.listenOrderQueue()]
    │
    ▼
[StockService.reduceStock()]
    │
    ▼
[DB 재고 차감]
```

### 2. Lua Script 방식 (동기 처리)

#### 전체 흐름

```
[주문 생성] 
    │
    ▼
[OrderService.orderWithLua()]
    │
    ▼
[StockServiceWithLua.reduceStock()]
    │
    ▼
[LuaExecutor.executeWithLazyLoad()]
    │
    ├─→ [Lazy Loading: Redis에 key 없으면 DB 조회]
    │   │
    │   └─→ [Redis에 저장 (SETNX)]
    │
    ▼
┌─────────────────────────────────────┐
│   Redis Lua Script 실행            │
│   (원자적 연산으로 재고 차감)       │
└─────────────────────────────────────┘
    │
    ▼
[Redis 재고 차감 완료]
```

## 핵심 구성 요소

### SQS 방식

#### 1. StockReduceRequest (DTO)

재고 차감 요청 정보를 담는 DTO입니다.

```java
public class StockReduceRequest {
    private Long orderId;
    private Long productNo;
    private Integer quantity;
    
    // 상품별 messageGroupId 생성
    private String messageGroupId() {
        return "STOCK_GROUP" + this.productNo;
    }
    
    // 중복 방지를 위한 deduplicationId 생성
    private String deduplicationId() {
        return "STOCK_" + this.productNo + "ORDER_" + this.orderId;
    }
}
```

**핵심 포인트:**
- `messageGroupId`: 상품번호(`productNo`) 기반으로 생성
- 같은 상품의 모든 메시지는 동일한 `messageGroupId`를 가짐
- FIFO SQS는 같은 `messageGroupId`를 가진 메시지를 순차적으로 처리

#### 2. AwsSqsMessageSender

SQS FIFO 대기열로 메시지를 전송하는 컴포넌트입니다.

```java
public <T> SendResult<T> sendMessage(SqsMessageEnvelope<T> request) {
    return sqsTemplate.send(to -> to
        .queue(request.getQueueName())
        .messageGroupId(request.getMessageGroupId())  // 상품별 그룹 ID
        .messageDeduplicationId(request.getDeduplicationId())  // 중복 방지
        .payload(request.getPayload()));
}
```

#### 3. StockMessageListener

SQS FIFO 대기열에서 메시지를 수신하고 처리하는 리스너입니다.

```java
@SqsListener(queueNames = "${spring.cloud.aws.sqs.stock-queue.name}")
public void listenOrderQueue(
    @Headers Map<String, Object> headers, 
    StockReduceRequest stockReduceRequest
) {
    stockService.reduceStock(stockReduceRequest);
}
```

### Lua Script 방식

#### 1. LuaType (Enum)

Lua 스크립트 타입을 정의하는 enum입니다.

```java
public enum LuaType {
    DECREASE("lua/decrease.lua");

    private final String path;

    public RedisScript<Long> createRedisScript() {
        // resources/lua/ 하위의 Lua 스크립트를 RedisScript로 변환
    }
}
```

**핵심 포인트:**
- `path`: Lua 스크립트 파일 경로
- `createRedisScript()`: 스크립트를 `RedisScript<Long>` 타입으로 변환
- 확장 가능: 새로운 Lua 스크립트는 enum에 추가만 하면 됨

#### 2. LuaFactory

LuaType에 등록된 모든 스크립트를 미리 로드하여 관리하는 팩토리입니다.

```java
@Component
public class LuaFactory {
    private final Map<LuaType, RedisScript<Long>> scriptMap;

    public LuaFactory() {
        // 생성자에서 모든 LuaType의 스크립트를 로드
        this.scriptMap = Arrays.stream(LuaType.values())
                .collect(Collectors.toMap(
                        luaType -> luaType,
                        LuaType::createRedisScript
                ));
    }

    public RedisScript<Long> getRedisScript(LuaType luaType) {
        return scriptMap.get(luaType);
    }
}
```

**핵심 포인트:**
- 애플리케이션 시작 시 모든 스크립트를 미리 로드
- 런타임에 스크립트 조회 시 O(1) 시간 복잡도
- 스크립트 재사용으로 성능 최적화

#### 3. LuaExecutor

Redis Lua 스크립트를 실행하는 실행기입니다.

```java
@Component
public class LuaExecutor {
    private final RedisTemplate<String, Object> redisTemplate;
    private final LuaFactory luaFactory;

    // Lazy loading을 포함한 실행
    public Long executeWithLazyLoad(
            LuaType luaType, 
            String key, 
            Supplier<Object> valueLoader,
            Object... args) {
        
        // 1. Lazy loading: Redis에 key가 없으면 Supplier로부터 값 로드
        loadValueIfAbsent(key, valueLoader);
        
        // 2. Lua 스크립트 실행
        return execute(luaType, key, args);
    }

    private void loadValueIfAbsent(String key, Supplier<Object> valueLoader) {
        if (redisTemplate.hasKey(key)) {
            return; // 이미 존재하면 스킵
        }

        // DB에서 조회하여 Redis에 저장 (SETNX로 동시성 보장)
        Object value = valueLoader.get();
        redisTemplate.opsForValue().setIfAbsent(key, value);
    }
}
```

**핵심 포인트:**
- `Supplier<Object>`: Redis에 key가 없을 때만 실행되는 lazy loading 로직
- `setIfAbsent`: 동시성 문제 방지 (여러 스레드가 동시에 로드 시도해도 한 번만 저장)
- 확장 가능: 쿠폰, 접수 등 다른 모듈에서도 동일한 패턴 사용 가능

#### 4. StockServiceWithLua

Lua 스크립트를 사용한 재고 차감 서비스입니다.

```java
@Service
public class StockServiceWithLua {
    private final LuaExecutor luaExecutor;
    private final StockRepository stockRepository;

    public void reduceStock(StockReduceRequest request) {
        String key = "stock:" + request.getProductNo();
        
        Long result = luaExecutor.executeWithLazyLoad(
            LuaType.DECREASE, 
            key, 
            () -> {
                // Supplier: Redis에 key가 없을 때만 실행
                Stock stock = stockRepository.findByProductNo(request.getProductNo())
                        .orElseThrow(() -> new RuntimeException("재고를 찾을 수 없습니다"));
                return stock.getStockQuantity();
            },
            request.getQuantity()
        );

        if (result == null || result < 0) {
            throw new RuntimeException("재고수량이 부족합니다");
        }
    }
}
```

**핵심 포인트:**
- Lazy loading: 필요할 때만 DB에서 조회하여 Redis에 저장
- 원자적 연산: Lua 스크립트로 재고 차감이 원자적으로 실행
- 확장성: 다른 도메인(쿠폰, 접수)에서도 동일한 패턴 적용 가능

#### 5. decrease.lua 스크립트

재고 차감을 수행하는 Lua 스크립트입니다.

```lua
local key = KEYS[1]
local value = tonumber(ARGV[1])

local current = tonumber(redis.call('GET', key) or 0)

if current < value then
    return -1  -- 재고 부족
end

local result = redis.call('DECRBY', key, value)
return result  -- 남은 재고 반환
```

**핵심 포인트:**
- 원자적 연산: Redis에서 단일 명령으로 실행되어 동시성 문제 해결
- 재고 부족 체크: 차감 전 재고 확인
- 반환값: 남은 재고 수량 반환 (음수면 재고 부족)

## FIFO SQS MessageGroup 동작 원리

### 상품별 그룹 분류

```
주문 요청들:
┌─────────────────────────────────────────┐
│ 주문1: productNo=1, quantity=5         │ → messageGroupId: "STOCK_GROUP1"
│ 주문2: productNo=1, quantity=3         │ → messageGroupId: "STOCK_GROUP1"
│ 주문3: productNo=2, quantity=10        │ → messageGroupId: "STOCK_GROUP2"
│ 주문4: productNo=1, quantity=2         │ → messageGroupId: "STOCK_GROUP1"
│ 주문5: productNo=2, quantity=5         │ → messageGroupId: "STOCK_GROUP2"
└─────────────────────────────────────────┘
```

### FIFO Queue 내부 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    SQS FIFO Queue                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  MessageGroup: "STOCK_GROUP1" (상품1)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ [1] 주문1: productNo=1, quantity=5                 │   │
│  │ [2] 주문2: productNo=1, quantity=3                 │   │
│  │ [3] 주문4: productNo=1, quantity=2                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  MessageGroup: "STOCK_GROUP2" (상품2)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ [1] 주문3: productNo=2, quantity=10                │   │
│  │ [2] 주문5: productNo=2, quantity=5                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 메시지 폴링 및 처리 흐름

#### 시나리오: 여러 상품에 대한 동시 주문

```
시간축: T0 → T1 → T2 → T3 → T4 → T5

T0: 주문 요청들 동시 발생
    ├─ 주문1: 상품1, 수량5
    ├─ 주문2: 상품1, 수량3
    ├─ 주문3: 상품2, 수량10
    ├─ 주문4: 상품1, 수량2
    └─ 주문5: 상품2, 수량5

T1: SQS FIFO Queue에 메시지 도착
    ┌─────────────────────────────────────┐
    │ STOCK_GROUP1: [주문1, 주문2, 주문4] │
    │ STOCK_GROUP2: [주문3, 주문5]        │
    └─────────────────────────────────────┘
```

#### Consumer에서의 순차 처리

```
Consumer 1 (STOCK_GROUP1 처리 전담)
┌─────────────────────────────────────────┐
│ T2: 주문1 폴링 및 처리 시작            │
│     - 재고 조회: 10개                   │
│     - 재고 차감: 10 - 5 = 5개           │
│     - DB 업데이트                       │
│                                         │
│ T3: 주문1 처리 완료                     │
│     주문2 폴링 및 처리 시작             │
│     - 재고 조회: 5개                    │
│     - 재고 차감: 5 - 3 = 2개            │
│     - DB 업데이트                       │
│                                         │
│ T4: 주문2 처리 완료                     │
│     주문4 폴링 및 처리 시작             │
│     - 재고 조회: 2개                    │
│     - 재고 차감: 2 - 2 = 0개            │
│     - DB 업데이트                       │
│                                         │
│ T5: 주문4 처리 완료                     │
└─────────────────────────────────────────┘

Consumer 2 (STOCK_GROUP2 처리 전담)
┌─────────────────────────────────────────┐
│ T2: 주문3 폴링 및 처리 시작            │
│     - 재고 조회: 20개                   │
│     - 재고 차감: 20 - 10 = 10개         │
│     - DB 업데이트                       │
│                                         │
│ T3: 주문3 처리 완료                     │
│     주문5 폴링 및 처리 시작             │
│     - 재고 조회: 10개                   │
│     - 재고 차감: 10 - 5 = 5개           │
│     - DB 업데이트                       │
│                                         │
│ T4: 주문5 처리 완료                     │
└─────────────────────────────────────────┘
```

### 병렬 처리 가능성

```
┌─────────────────────────────────────────────────────────────┐
│                    SQS FIFO Queue                          │
│                                                            │
│  STOCK_GROUP1: [주문1, 주문2, 주문4]                       │
│  STOCK_GROUP2: [주문3, 주문5]                              │
│  STOCK_GROUP3: [주문6, 주문7]                              │
└─────────────────────────────────────────────────────────────┘
         │              │              │
         │              │              │
         ▼              ▼              ▼
    Consumer 1      Consumer 2      Consumer 3
    (GROUP1 전담)   (GROUP2 전담)   (GROUP3 전담)
    ┌─────────┐     ┌─────────┐     ┌─────────┐
    │ 주문1   │     │ 주문3   │     │ 주문6   │
    │ 주문2   │     │ 주문5   │     │ 주문7   │
    │ 주문4   │     │         │     │         │
    └─────────┘     └─────────┘     └─────────┘
         │              │              │
         │              │              │
         ▼              ▼              ▼
    DB (상품1)      DB (상품2)      DB (상품3)
```

**핵심 포인트:**
- 다른 `messageGroupId`를 가진 메시지들은 서로 다른 Consumer에서 병렬 처리 가능
- 같은 `messageGroupId`를 가진 메시지들은 반드시 같은 Consumer에서 순차 처리
- 이를 통해 상품별로 재고 차감 순서를 보장하면서도 전체 처리량은 유지

## 동시성 보장 메커니즘

### SQS 방식

#### 1. FIFO SQS MessageGroup

- 같은 상품(`productNo`)의 모든 메시지는 동일한 `messageGroupId`를 가짐
- FIFO SQS는 같은 `messageGroupId`를 가진 메시지를 순차적으로 처리
- 다른 `messageGroupId`를 가진 메시지는 병렬 처리 가능

#### 2. DB 레벨 동시성 제어 (권장)

FIFO SQS만으로는 완전한 동시성 보장이 어려울 수 있으므로, DB 레벨의 락을 함께 사용하는 것을 권장합니다.

#### 비관적 락 (Pessimistic Lock) 예시

```java
@Transactional
public void reduceStock(StockReduceRequest request) {
    // 비관적 락을 사용하여 동시 접근 방지
    Stock stock = stockRepository
        .findByProductNoWithPessimisticLock(request.getProductNo())
        .orElseThrow(() -> new RuntimeException("재고를 찾을 수 없습니다"));
    
    stock.reduceStock(request.getQuantity());
}
```

### Lua Script 방식

#### 1. Redis Lua Script의 원자적 연산

Redis Lua 스크립트는 단일 원자적 연산으로 실행됩니다.

```
동시 요청 시나리오:
┌─────────────────────────────────────────┐
│ 시간: T0                                │
│                                         │
│ Thread 1: 재고 100개에서 5개 차감 요청  │
│ Thread 2: 재고 100개에서 3개 차감 요청  │
│ Thread 3: 재고 100개에서 2개 차감 요청  │
└─────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ Redis Lua Script 실행 (원자적 연산)     │
│                                         │
│ 1. GET key → 100                        │
│ 2. 재고 확인 (100 >= 5) ✓               │
│ 3. DECRBY key 5 → 95                    │
│                                         │
│ 다음 스크립트 실행 대기...              │
│                                         │
│ 1. GET key → 95                         │
│ 2. 재고 확인 (95 >= 3) ✓                │
│ 3. DECRBY key 3 → 92                    │
│                                         │
│ 다음 스크립트 실행 대기...              │
│                                         │
│ 1. GET key → 92                         │
│ 2. 재고 확인 (92 >= 2) ✓                │
│ 3. DECRBY key 2 → 90                    │
└─────────────────────────────────────────┘
```

**핵심 포인트:**
- Lua 스크립트는 Redis에서 단일 원자적 명령으로 실행
- 여러 스레드가 동시에 요청해도 순차적으로 처리
- Race condition 발생 불가능

#### 2. Lazy Loading의 동시성 보장

```
동시 요청 시나리오 (Redis에 key가 없는 경우):
┌─────────────────────────────────────────┐
│ Thread 1: loadValueIfAbsent() 호출     │
│ Thread 2: loadValueIfAbsent() 호출     │
│ Thread 3: loadValueIfAbsent() 호출     │
└─────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ 1. hasKey(key) → false (모든 스레드)   │
│                                         │
│ 2. valueLoader.get() 실행               │
│    - Thread 1: DB 조회 → 100            │
│    - Thread 2: DB 조회 → 100            │
│    - Thread 3: DB 조회 → 100            │
│                                         │
│ 3. setIfAbsent(key, 100) 실행           │
│    - Thread 1: true (저장 성공)         │
│    - Thread 2: false (이미 존재)        │
│    - Thread 3: false (이미 존재)        │
└─────────────────────────────────────────┘
```

**핵심 포인트:**
- `setIfAbsent`: 여러 스레드가 동시에 저장 시도해도 한 번만 성공
- DB 조회는 여러 번 발생할 수 있지만, Redis 저장은 한 번만 수행
- 이후 요청은 Redis에서 바로 조회하여 성능 최적화

## 장점 비교

### SQS 방식

1. 순서 보장: 같은 상품의 재고 차감 요청이 순차적으로 처리됨
2. 확장성: 다른 상품의 메시지는 병렬 처리 가능
3. 중복 방지: `messageDeduplicationId`로 중복 메시지 방지
4. 비동기 처리: 주문 생성과 재고 차감을 분리하여 응답 속도 개선
5. 장애 격리: 메시지 큐를 통해 시스템 간 결합도 감소

### Lua Script 방식

1. 원자적 연산: Redis Lua 스크립트로 동시성 문제 완전 해결
2. 동기 처리: 즉시 결과 반환으로 일관성 보장
3. 성능: Redis 인메모리 연산으로 매우 빠른 처리 속도
4. Lazy Loading: 필요할 때만 DB에서 로드하여 메모리 효율적
5. 확장성: Supplier 패턴으로 쿠폰, 접수 등 다양한 모듈에 적용 가능
6. 단순성: 외부 큐 시스템 없이 Redis만으로 해결

## 주의사항

### SQS 방식

1. DB 레벨 락 권장: FIFO SQS만으로는 완전한 동시성 보장이 어려울 수 있음
2. 메시지 그룹 분산: `messageGroupId`가 너무 많으면 처리 효율이 떨어질 수 있음
3. 에러 처리: 메시지 처리 실패 시 재시도 및 DLQ(Dead Letter Queue) 설정 필요
4. 지연 시간: 비동기 처리로 인한 지연 시간 발생 가능

### Lua Script 방식

1. Redis 의존성: Redis 서버가 다운되면 서비스 불가
2. 메모리 관리: Redis 메모리 한계 고려 필요
3. DB 동기화: Redis와 DB 간 데이터 일관성 관리 필요
4. 스크립트 복잡도: 복잡한 비즈니스 로직은 Lua 스크립트로 구현하기 어려울 수 있음