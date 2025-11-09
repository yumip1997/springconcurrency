## 아키텍처

### 전체 흐름

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

## 핵심 구성 요소

### 1. StockReduceRequest (DTO)

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

### 2. AwsSqsMessageSender

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

### 3. StockMessageListener

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

### 1. FIFO SQS MessageGroup

- 같은 상품(`productNo`)의 모든 메시지는 동일한 `messageGroupId`를 가짐
- FIFO SQS는 같은 `messageGroupId`를 가진 메시지를 순차적으로 처리
- 다른 `messageGroupId`를 가진 메시지는 병렬 처리 가능

### 2. DB 레벨 동시성 제어 (권장)

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

## 장점

1. 순서 보장: 같은 상품의 재고 차감 요청이 순차적으로 처리됨
2. 확장성: 다른 상품의 메시지는 병렬 처리 가능
3. 중복 방지: `messageDeduplicationId`로 중복 메시지 방지
4. 비동기 처리: 주문 생성과 재고 차감을 분리하여 응답 속도 개선

## 주의사항

1. DB 레벨 락 권장: FIFO SQS만으로는 완전한 동시성 보장이 어려울 수 있음
2. 메시지 그룹 분산: `messageGroupId`가 너무 많으면 처리 효율이 떨어질 수 있음
3. 에러 처리: 메시지 처리 실패 시 재시도 및 DLQ(Dead Letter Queue) 설정 필요