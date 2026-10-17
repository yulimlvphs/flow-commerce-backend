
<div align="center">

# ⚡ FlowCommerce — Flash Deal

**한정 수량 · 정시 오픈 · 선착순 구매**
타임딜 전문 이커머스 백엔드 프로젝트

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](#license)

</div>

---

## 📌 About

**FlowCommerce Flash Deal**은 매일 정해진 시간에 한정 수량의 상품을 특가로 오픈하고,
선착순으로 구매 기회를 제공하는 타임딜 전문 플랫폼이다.

일반 쇼핑몰과 달리 **"시간 제한 + 수량 제한"**이 핵심 메커니즘이며,
이 프로젝트의 목표는 **동시성 제어, 실시간 처리, 대용량 트래픽 대응**을
직접 겪고 해결하는 과정을 백엔드 개발 역량으로 증명하는 것이다.

> 📄 상세 기획 배경은 [기획 문서](./FlowCommerce-FlashDeal-기획문서.md)를 참고

---

## 🎯 Why This Project?

| 일반적인 CRUD 쇼핑몰 | Flash Deal |
|---|---|
| 언제든 구매 가능 | **정시 오픈**에만 구매 가능 |
| 재고 걱정 없음 | **한정 수량**, 오버셀링 리스크 존재 |
| 트래픽이 평이함 | 오픈 순간 **트래픽 폭증** |
| 동시성 이슈 거의 없음 | **동시성 제어가 서비스 품질을 좌우** |

→ 신입 개발자가 흔히 겪기 어려운 **동시성/대용량 트래픽 문제를 의도적으로 설계에 내재화**한 프로젝트

---

## 🧩 핵심 도메인

```
User ──< Order >── Deal ──< Product
  │                  │
  │                  └──< DealInventory
  │
  ├──< Payment
  ├──< Notification
  └──< AbuseLog

Deal ──< WaitingQueue
Deal ──< DealTimeSlot
```

| 도메인 | 설명 |
|---|---|
| **Deal** | 타임딜 정보 (오픈/종료 시각, 재고, 가격) |
| **Order** | 재고 홀딩 → 결제 대기 → 확정/만료 상태 관리 |
| **WaitingQueue** | 동시 접속 폭주 시 대기열 순번 관리 |
| **Payment** | 결제 요청/승인/취소/환불 |
| **Notification** | 딜 오픈 임박, 결제 임박 알림 |

---
## 브랜치 접두사
| 접두사         | 용도             | 예시                       |
| ----------- | -------------- | ------------------------ |
| `feat/`     | 새로운 기능         | `feat/order-create`      |
| `fix/`      | 오류 수정          | `fix/payment-validation` |
| `refactor/` | 기능 변경 없는 구조 개선 | `refactor/order-service` |
| `test/`     | 테스트 추가·수정      | `test/payment-service`   |
| `docs/`     | README 등 문서    | `docs/readme`            |
| `chore/`    | 설정·의존성·기타 작업   | `chore/project-setup`    |
| `infra/`    | Docker·AWS·배포  | `infra/docker`           |
| `ci/`       | GitHub Actions | `ci/github-actions`      |

---

## ✨ Features

### 👤 인증/인가
- JWT 기반 로그인 (Access / Refresh Token, Rotation 전략)
- 역할 기반 접근 제어 (`USER` / `SELLER` / `ADMIN`)

### 🛍️ 딜/상품
- 딜 등록 및 오픈/종료 스케줄링
- 실시간 재고 노출 및 상태 자동 전이

### 📦 주문/재고 (핵심)
- 재고 **홀딩 → 확정/반환** 상태 머신
- 1인 1회 구매 제한 검증
- 동시 요청 상황에서 **오버셀링 0건** 보장

### 💳 결제
- 결제 타임아웃 자동 만료 처리
- 결제 승인/실패/환불 흐름

### 🚦 대기열
- Redis Sorted Set 기반 순번 관리
- 입장 토큰 발급 및 TTL 관리

### 🔔 알림
- 딜 오픈 임박 / 결제 임박 알림 (비동기 처리)

---

## 🛠️ Tech Stack

| 영역 | 기술 |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot, Spring Security, Spring Data JPA |
| **Database** | MySQL |
| **Cache / Lock** | Redis (원자적 연산, 분산 락, 대기열) |
| **Infra** | Docker, AWS (EC2/RDS 등) |
| **Test** | JUnit5, Testcontainers, k6(부하 테스트) |
| **Monitoring** | Spring Actuator, Prometheus, Grafana |


### Health Check
```bash
GET http://localhost:8080/actuator/health
```
---

## 📝 관련 기록

개발 중 겪은 트러블슈팅과 설계 고민은 블로그에 정리하고 있다.

- [ ] `[FlowCommerce][Auth]` GlobalExceptionHandler로는 401/403을 못 잡는 이유
- [ ] `[FlowCommerce][Order]` 동시성 이슈 재현부터 해결까지
- [ ] `[FlowCommerce][Queue]` Redis Sorted Set으로 대기열 구현하기


## 사용자 도메인 이름 변경

사용자 도메인은 `domain.user`의 `User`, `UserRole`, `UserRepository`로 통일했다.
인증 응답은 `UserResponse`, 프론트 인증 상태는 `user`, 사용자 오류 코드는 `USER_001`~`USER_003`을 사용한다.

기존 MySQL DB에는 애플리케이션을 중지한 뒤
[`scripts/sql/20261001_rename_user_domain.sql`](scripts/sql/20261001_rename_user_domain.sql)을 한 번 실행하고 새 버전을 시작한다.
테이블과 컬럼을 이름 변경하므로 기존 사용자 ID, 비밀번호, 역할, 리프레시 토큰 및 상품의 판매자 ID가 유지된다.
신규 DB는 JPA가 `users`와 `refresh_tokens.user_id`를 생성하므로 이 이관 스크립트를 실행하지 않는다.

## 공통 생성·수정 시각

`User`, `Product`, `Deal`은 `common.entity.BaseEntity`를 상속한다.
`createdAt`과 `updatedAt`은 `Instant`로 저장하며, JPA의 `@PrePersist`와
`@PreUpdate` 콜백에서 자동 설정한다. 생성 시 두 값은 같고, 변경 감지로
UPDATE할 때는 `updatedAt`만 갱신한다. JPQL 벌크 UPDATE나 native SQL은
콜백을 실행하지 않으므로 수정 시각을 별도로 갱신해야 한다.

상품 응답의 `createdAt`은 UTC 오프셋을 포함하는 ISO-8601 형식으로 반환한다.
신청·심사·삭제·만료 시각은 도메인 이벤트 시각이므로 각 엔티티에서 관리한다.

기존 DB에는 애플리케이션을 중지한 뒤
[`scripts/sql/20261006_add_entity_updated_at.sql`](scripts/sql/20261006_add_entity_updated_at.sql)을
한 번 실행한다. 기존 상품·딜의 수정 시각은 생성 시각으로 초기화한다.
기존 `created_at`이 로컬 시각으로 저장되어 있다면 실제 저장 시간대를 확인하고
스크립트 실행 전에 UTC로 변환해야 한다. 이미 실행했다면 `created_at`과
`updated_at` 모두에 필요한 시간대 보정을 적용해야 한다. 신규 DB에는 이 스크립트를 실행하지 않는다.

## 타임딜 기본 API

Deal 생성·목록·상세·부분 수정·취소와 10초 주기 상태 전이를 구현했다.
시간 기준 상태 판단, 상품 단위 동시성 제어, 상품 정가·삭제 제한 및 테스트는
[Deal 기본 기능 문서](docs/deal-basic-api.md)에 정리했다.
