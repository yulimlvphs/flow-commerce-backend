# Deal 기본 기능

현재 프로젝트의 `com.yulim.flowcommerce` 패키지와 공통 `ErrorCode` / `BusinessException` 구조를 따른다.
요청 문서의 `com.flowcommerce`, 별도 `ProductErrorCode`, `ProductServiceTest`는 작업 시작 시 저장소에 없었다.
기존 enum에 딜 오류를 추가했고, `ProductServiceTest`는 Mockito 단위 테스트로 새로 작성했다.

## API

| Method | Path | 접근 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/api/deals` | 상품 소유 SELLER 또는 ADMIN | 201, `ApiResponse<DealResponse>` |
| GET | `/api/deals` | 공개 | 200, `ApiResponse<Page<DealResponse>>` |
| GET | `/api/deals/{dealId}` | 공개 | 200, `ApiResponse<DealResponse>` |
| PATCH | `/api/deals/{dealId}` | 상품 소유 SELLER 또는 ADMIN | 200, `ApiResponse<DealResponse>` |
| DELETE | `/api/deals/{dealId}` | 상품 소유 SELLER 또는 ADMIN | 200, `ApiResponse<Void>` |

현재 사용자 ID는 `@Auth`에서 얻는다. 쓰기 서비스는 DB에서 사용자 역할을 조회해
관리자 여부 및 상품 소유권을 다시 검증한다.

### 생성 요청

```json
{
  "productId": 1,
  "dealPrice": 7000,
  "totalStock": 100,
  "maxPurchasePerUser": 2,
  "openAt": "2030-01-01T10:00:00",
  "closeAt": "2030-01-01T11:00:00",
  "holdDurationMinutes": 10
}
```

날짜는 예시이며 실제 요청에서는 현재보다 이후인 시각을 사용한다.
모든 영업 시각은 `Asia/Seoul` 기준이며 `Clock`을 주입받는다.
정가보다 낮은 양수 가격, 유효한 기간, 총 재고 이하의 구매 한도,
같은 상품의 활성 딜과 겹치지 않는 기간을 검증한다.
기간은 `[openAt, closeAt)`이므로 이전 종료와 다음 오픈 시각이 같은 경우는 허용한다.
총 재고·구매 한도는 1 이상, 홀딩 시간은 1~30분이며 DTO Bean Validation으로 검사한다.

### 부분 수정과 취소

PATCH는 생성 요청에서 `productId`를 제외한 6개 필드만 받는다.
생략하거나 null인 필드는 기존 값을 유지하며, 병합 후 생성과 같은 업무 검증을 수행한다.
오픈 전 재고를 변경하면 `remainingStock`도 변경된 `totalStock`으로 맞춘다.
DELETE는 행을 삭제하지 않고 CLOSED로 취소한다.
둘 다 `status == SCHEDULED && now < openAt`인 경우에만 허용된다.

### 조회

`status`는 생략하거나 `UPCOMING`, `ON_SALE`, `SOLD_OUT`을 지정한다.
`page`는 0부터, 기본 `size`는 20이고 최대 50이다.
목록은 CLOSED와 종료 시각이 지난 딜을 제외한다.

| 필터 | 조건 | 정렬 |
| --- | --- | --- |
| 없음 | `now < closeAt` | closeAt 오름차순 |
| UPCOMING | `now < openAt` | openAt 오름차순 |
| ON_SALE | `openAt <= now < closeAt && remainingStock > 0` | closeAt 오름차순 |
| SOLD_OUT | `openAt <= now < closeAt && remainingStock == 0` | closeAt 오름차순 |

동일한 시각의 페이지 순서는 ID 오름차순으로 안정화한다. 클라이언트의 `sort`는 사용하지 않는다.
목록의 상품 ID를 중복 제거하여 `findAllById` 한 번으로 상품을 조회한다.
상품마다 추가 조회하지 않으며, 한 페이지의 모든 응답은 같은 `serverNow`를 사용한다.
활성 상품의 종료·취소 딜은 상세 API에서 조회할 수 있다.

응답 필드:
`dealId`, `productId`, `productName`, `productDescription`, `thumbnailUrl`,
`originalPrice`, `dealPrice`, `discountRate`, `totalStock`, `remainingStock`,
`maxPurchasePerUser`, `openAt`, `closeAt`, `holdDurationMinutes`, `displayStatus`, `serverNow`.
기존 응답의 `id`, `name`, `description`, `status`는 제거했으며 기존 조회 화면도 함께 변경했다.

## 정합성과 트랜잭션

- 구매 가능 여부·표시 상태는 요청 시각으로 판단한다. SCHEDULED가 DB에 남아 있어도
  오픈 시각 이후라면 판매 중으로 표시하고 수정·취소를 거절한다.
- `DealStatusScheduler`는 10초 fixed delay로 `DealTransitionService`만 호출한다.
  전이 서비스의 한 트랜잭션에서 조건부 벌크 UPDATE 두 개를 실행한다.
- 오픈 조건에 `closeAt > now`가 있으므로 이미 종료 시각이 지난 SCHEDULED는
  OPEN으로 바뀌지 않고 바로 CLOSED가 된다.
- 벌크 UPDATE는 `version`을 명시적으로 1 증가시키고 `updatedAt`도 갱신한다.
  조건이 충족된 행만 갱신하므로 반복·동시 실행 시 같은 행의 중복 전이를 막는다.
- PATCH/DELETE는 명시적으로 `flush()`하여 낙관적 락 충돌을 서비스 안에서
  `DEAL_NOT_MODIFIABLE`로 변환한다. 실패한 트랜잭션은 롤백한다.
- 기간 중복 검사만으로는 동시 생성 요청을 막을 수 없어 상품 행에 비관적 쓰기 잠금을 사용한다.
  생성·수정·취소와 상품 정가 변경·삭제가 동일한 상품 잠금을 사용한다.
  쓰기 트랜잭션의 READ_COMMITTED 격리 수준은 잠금 대기 후 중복 검사에서 최신 커밋을 읽도록 한다.
- 활성 상태 목록에는 SCHEDULED / OPEN / SOLD_OUT이 포함된다.
  이 상태의 딜이 있으면 상품 삭제 및 실제 정가 변경을 차단한다.
  소수점 자릿수만 다른 동일 금액은 가격 변경으로 취급하지 않는다.

## 오류 코드

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| COMMON_001 | 400 | DTO 또는 파라미터 형식 검증 실패 |
| PRODUCT_001 | 404 | 상품 없음 또는 비활성 |
| PRODUCT_003 | 409 | 활성 딜이 있어 상품 삭제 불가 |
| PRODUCT_004 | 409 | 활성 딜이 있어 상품 정가 변경 불가 |
| DEAL_001 | 404 | 딜 없음 |
| DEAL_002 | 403 | 딜 관리 권한 없음 |
| DEAL_003 | 400 | 오픈·종료 기간 오류 |
| DEAL_004 | 400 | 할인 가격 오류 |
| DEAL_005 | 400 | 구매 한도가 총 재고 초과 |
| DEAL_006 | 409 | 같은 상품의 딜 기간 중복 |
| DEAL_007 | 409 | 수정·취소 불가 또는 낙관적 락 충돌 |

## 테스트

```bash
./gradlew test
cd frontend && npm run build
```

- `DealTest`: 시간 경계, 스케줄러 지연, 표시 상태, 취소, 복원·할인율.
- `DealServiceTest`: 생성 검증, 권한, 병합 후 검증, 수정·취소 조건,
  낙관적 락 오류 변환, 상품 일괄 조회.
- `DealRepositoryTest`: 기간 경계, 자기 자신·다른 상품·취소된 딜 제외.
- `DealApiIntegrationTest`: 역할·소유권, CRUD 흐름, Bean Validation 오류 코드, 상품 연동.
- `DealQueryIntegrationTest`: 공개 조회, 응답 필드, 시간 필터, 정렬, 페이지 크기, 종료 상세.
- `DealSchedulerIntegrationTest`: 고정 Clock, 상태 전이, 멱등성, 버전·감사 시각, 오래된 버전 충돌.
- `DealConcurrencyIntegrationTest`: 서로 다른 트랜잭션의 동시 기간 중복 생성과 동시 스케줄러 실행.
- `ProductServiceTest`: 활성 딜별 삭제·정가 제한, 동일 금액, 종료 딜, 삭제 시각.

통합 테스트는 H2 MySQL 모드에서 실행한다. 실제 MySQL 서버 여러 대를 대상으로 한
운영 부하 검증은 이번 테스트에 포함하지 않는다.
`test` 프로필에서는 스케줄링 등록을 비활성화하고 전이 서비스를 직접 호출한다.

## 명세 확인 TODO

코드에도 같은 TODO를 남겼다. 다음 항목은 최종 확정된 업무 정책으로 간주하지 않는다.

1. 첨부 Deal 보강본이 제공되지 않아 텍스트에 명시된 동작을 기준으로 보강했다. 보강본 제공 시 대조 필요.
2. 할인율 반환 정밀도와 반올림 정책이 없다. 현재 구현은 정수 백분율 소수점 버림.
3. 네 번째 표시 상태의 문자열이 없다. 현재 구현은 기존 종료 상태명 `CLOSED` 사용.
4. 비활성 상품에 연결된 과거 딜의 공개 정책이 없다. 기존 활성 상품 조회 조건을 유지하여
   목록에서 제외하고, 상세에서는 요구된 상품 오류 코드 `PRODUCT_001`을 사용한다.

재고 차감 서비스/API, 주문·결제·대기열·알림, Redis·캐시·ShedLock은 추가하지 않았다.
기존 `restoreStock`만 요구된 종료 시간 경계에 맞춰 보강했다.
