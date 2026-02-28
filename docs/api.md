# StyleRing API Contract

프론트엔드 개발을 위한 백엔드 API 계약 문서.

---

## 공통 정보

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8080` (local) |
| Content-Type | `application/json` |
| 인증 | `Authorization: Bearer <Firebase ID Token>` |
| CORS Allowed Origins | `http://localhost:3000` |
| CORS Allowed Methods | `GET, POST, PUT, PATCH, DELETE, OPTIONS` |
| CORS Allowed Headers | `Authorization, Content-Type` |
| 세션 관리 | Stateless (서버 세션 없음) |

> 모든 `/api/v1/**` 엔드포인트는 Firebase ID Token 인증이 필요하다.
> `Authorization` 헤더가 없거나 토큰이 유효하지 않으면 `401` 에러를 반환한다.

---

## 엔드포인트

### POST /api/v1/chat/sessions

채팅 세션을 생성한다. 세션은 `INTERVIEWING` 상태로 시작된다.

**Request**

헤더만 필요. Body 없음.

**Response `200`**

```json
{
  "sessionId": 1
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `sessionId` | `number` | 생성된 채팅 세션 ID |

**에러**

| 코드 | 상태 | 설명 |
|---|---|---|
| `AUTH_MISSING_TOKEN` | 401 | Authorization 헤더 누락 |
| `AUTH_INVALID_TOKEN` | 401 | Firebase 토큰 검증 실패 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청 횟수 초과 |

---

### POST /api/v1/chat/messages

사용자 메시지를 전송하고 어시스턴트 응답을 받는다.

**처리 흐름**

1. USER 메시지 저장
2. 중단 의도 감지 ("그만", "추천해줘", "끝" 등)
3. 중단 의도 → 프로필 확정 + 추천 생성
4. 그 외 → LLM으로 다음 질문 생성 (대화 히스토리 포함)
5. 주기적으로 사용자 프로필 갱신 (incremental)
6. ASSISTANT 메시지 저장

**Request**

```json
{
  "sessionId": 1,
  "message": "캐주얼한 스타일을 좋아해요"
}
```

| 필드 | 타입 | 필수 | 제약조건 |
|---|---|---|---|
| `sessionId` | `number` | Yes | not null |
| `message` | `string` | Yes | not blank, 최대 2000자 |

**Response `200`**

```json
{
  "sessionId": 1,
  "userMessageId": 10,
  "assistantMessageId": 11,
  "assistantContent": "캐주얼 스타일이시군요! 주로 어떤 상황에서 입으시나요?",
  "nextAction": "ASK",
  "sessionStatus": "INTERVIEWING",
  "cta": {
    "primary": "계속 대화하기",
    "secondary": "추천 받기"
  },
  "recommendations": null,
  "profileUpdated": false
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `sessionId` | `number` | No | 세션 ID |
| `userMessageId` | `number` | No | 저장된 사용자 메시지 ID |
| `assistantMessageId` | `number` | No | 저장된 어시스턴트 메시지 ID |
| `assistantContent` | `string` | No | 어시스턴트 응답 텍스트 |
| `nextAction` | `string` | No | [`NextAction`](#nextaction) enum |
| `sessionStatus` | `string` | No | [`ChatSessionStatus`](#chatsessionstatus) enum |
| `cta` | `object` | No | CTA 버튼 텍스트 ([`ChatCta`](#chatcta)) |
| `recommendations` | `array \| null` | Yes | `nextAction`이 `RECOMMEND`일 때만 존재 ([`RecommendationItem[]`](#recommendationitem)) |
| `profileUpdated` | `boolean` | No | 이번 응답에서 프로필이 갱신되었는지 |

**`nextAction`이 `RECOMMEND`일 때 Response 예시**

```json
{
  "sessionId": 1,
  "userMessageId": 20,
  "assistantMessageId": 21,
  "assistantContent": "취향을 잘 파악했어요! 추천 아이템을 준비했습니다.",
  "nextAction": "RECOMMEND",
  "sessionStatus": "RECOMMENDED",
  "cta": {
    "primary": "추천 보기",
    "secondary": null
  },
  "recommendations": [
    {
      "itemId": 42,
      "category": "top",
      "name": "오버핏 코튼 셔츠",
      "brand": "MUJI",
      "priceRange": "30000-50000",
      "reason": "캐주얼하면서도 깔끔한 실루엣",
      "shopUrl": "https://search.shopping.naver.com/search/all?query=MUJI+%EC%98%A4%EB%B2%84%ED%95%8F+%EC%BD%94%ED%8A%BC+%EC%85%94%EC%B8%A0"
    }
  ],
  "profileUpdated": true
}
```

**에러**

| 코드 | 상태 | 설명 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 요청 필드 검증 실패 (sessionId 누락, message 공백 등) |
| `AUTH_MISSING_TOKEN` | 401 | Authorization 헤더 누락 |
| `AUTH_INVALID_TOKEN` | 401 | Firebase 토큰 검증 실패 |
| `CHAT_SESSION_NOT_FOUND` | 404 | 해당 sessionId가 존재하지 않음 |
| `CHAT_SESSION_FORBIDDEN` | 403 | 세션이 현재 사용자 소유가 아님 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청 횟수 초과 |

---

### POST /api/v1/recommendations

프로필 기반으로 스타일 추천을 요청한다.

**처리 흐름**

1. 최신 프로필 로드
2. 규칙 기반 후보 필터링 (최대 30개)
3. LLM이 후보 아이템 ID 중에서 선택
4. LLM이 잘못된 ID를 반환하면 규칙 기반 top-N으로 fallback
5. `recommendation_history`에 요청/결과 저장

**Request**

```json
{
  "sessionId": 1,
  "category": "shoes",
  "budgetMax": 100000
}
```

| 필드 | 타입 | 필수 | 제약조건 |
|---|---|---|---|
| `sessionId` | `number \| null` | No | 연관 채팅 세션 ID |
| `category` | `string \| null` | No | 카테고리 필터 (예: `shoes`, `top`) |
| `budgetMax` | `number \| null` | No | 최대 예산, 1 ~ 100,000,000 |

**Response `200`**

```json
{
  "recommendations": [
    {
      "itemId": 42,
      "category": "shoes",
      "name": "클래식 레더 로퍼",
      "brand": "ZARA",
      "priceRange": "80000-120000",
      "reason": "캐주얼과 포멀 모두 소화 가능한 로퍼",
      "shopUrl": "https://search.shopping.naver.com/search/all?query=ZARA+%ED%81%B4%EB%9E%98%EC%8B%9D+%EB%A0%88%EB%8D%94+%EB%A1%9C%ED%8D%BC"
    }
  ],
  "alternatives": [
    {
      "itemId": 55,
      "category": "shoes",
      "name": "캔버스 스니커즈",
      "brand": "Converse",
      "priceRange": "50000-70000",
      "reason": "가격대가 낮으면서도 캐주얼 스타일에 적합",
      "shopUrl": "https://search.shopping.naver.com/search/all?query=Converse+%EC%BA%94%EB%B2%84%EC%8A%A4+%EC%8A%A4%EB%8B%88%EC%BB%A4%EC%A6%88"
    }
  ],
  "nextQuestion": "어떤 색상을 선호하시나요?"
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `recommendations` | `array` | No | 메인 추천 아이템 목록 ([`RecommendationItem[]`](#recommendationitem)) |
| `alternatives` | `array` | No | 대안 아이템 목록 ([`RecommendationItem[]`](#recommendationitem)) |
| `nextQuestion` | `string \| null` | Yes | 추가 질문 (없으면 null) |

**에러**

| 코드 | 상태 | 설명 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | budgetMax 범위 위반 등 |
| `AUTH_MISSING_TOKEN` | 401 | Authorization 헤더 누락 |
| `AUTH_INVALID_TOKEN` | 401 | Firebase 토큰 검증 실패 |
| `PROFILE_NOT_FOUND` | 404 | 프로필이 아직 생성되지 않음 |
| `RATE_LIMIT_EXCEEDED` | 429 | 요청 횟수 초과 |

---

### GET /api/v1/profile

현재 사용자의 최신 스타일 프로필을 조회한다.

**Request**

헤더만 필요. Body 없음.

**Response `200`**

```json
{
  "version": 3,
  "profileJson": "{\"style\":\"casual\",\"colors\":[\"black\",\"white\"]}",
  "summary": "캐주얼 위주, 모노톤 선호, 오버핏 실루엣",
  "updatedAt": "2026-02-18T06:30:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `version` | `number` | No | 프로필 버전 (갱신될 때마다 증가) |
| `profileJson` | `string` | No | 프로필 상세 JSON (문자열 인코딩) |
| `summary` | `string \| null` | Yes | 프로필 요약 텍스트 |
| `updatedAt` | `string` | No | ISO 8601 형식 (예: `2026-02-18T06:30:00Z`) |

**에러**

| 코드 | 상태 | 설명 |
|---|---|---|
| `AUTH_MISSING_TOKEN` | 401 | Authorization 헤더 누락 |
| `AUTH_INVALID_TOKEN` | 401 | Firebase 토큰 검증 실패 |
| `PROFILE_NOT_FOUND` | 404 | 프로필이 아직 생성되지 않음 |

---

### GET /api/v1/me

현재 로그인된 사용자 정보를 조회한다.

**Request**

헤더만 필요. Body 없음.

**Response `200`**

```json
{
  "firebaseUid": "abc123xyz",
  "createdAt": "2026-01-15T12:00:00Z",
  "lastLoginAt": "2026-02-18T06:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `firebaseUid` | `string` | No | Firebase UID |
| `createdAt` | `string` | No | 계정 생성 시각 (ISO 8601) |
| `lastLoginAt` | `string` | No | 마지막 로그인 시각 (ISO 8601) |

**에러**

| 코드 | 상태 | 설명 |
|---|---|---|
| `AUTH_MISSING_TOKEN` | 401 | Authorization 헤더 누락 |
| `AUTH_INVALID_TOKEN` | 401 | Firebase 토큰 검증 실패 |

---

### GET /actuator/health

헬스체크 엔드포인트. 인증 불필요.

**Response `200`**

```json
{
  "status": "UP"
}
```

---

## 공통 타입

### RecommendationItem

추천 아이템 객체. `POST /chat/messages` 및 `POST /recommendations` 응답에서 사용.

| 필드 | 타입 | 설명 |
|---|---|---|
| `itemId` | `number` | 카탈로그 아이템 ID |
| `category` | `string` | 아이템 카테고리 (소문자: `top`, `pants`, `shoes`, `outer`, `accessory`) |
| `name` | `string` | 아이템 이름 |
| `brand` | `string` | 브랜드명 |
| `priceRange` | `string` | 가격 범위 (예: `"30000-50000"`) |
| `reason` | `string` | 추천 사유 |
| `shopUrl` | `string` | 쇼핑몰 상품 페이지 또는 검색 결과 URL. `productUrl`(직링크)이 있고 http/https 스킴이면 그대로 반환, 없거나 유효하지 않은 스킴이면 브랜드+상품명 기반 동적 검색 URL 생성 (기본: 네이버 쇼핑). 항상 non-null. |

### ChatCta

채팅 응답의 CTA(Call To Action) 버튼 텍스트.

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `primary` | `string \| null` | Yes | 주요 액션 버튼 텍스트 |
| `secondary` | `string \| null` | Yes | 보조 액션 버튼 텍스트 |

---

## Enum 정의

### ChatSessionStatus

채팅 세션의 상태.

| 값 | 설명 |
|---|---|
| `INTERVIEWING` | 인터뷰 진행 중 (질문/답변 교환 중) |
| `READY_TO_RECOMMEND` | 충분한 정보 수집 완료, 추천 준비됨 |
| `STOPPED` | 사용자가 대화 중단 |
| `RECOMMENDED` | 추천 완료 |

### NextAction

다음에 프론트엔드가 취해야 할 액션.

| 값 | 설명 |
|---|---|
| `ASK` | 추가 질문이 있음 — 대화 계속 |
| `SUGGEST_STOP` | 충분한 정보 수집됨 — 중단/추천 제안 |
| `RECOMMEND` | 추천 결과가 포함됨 — `recommendations` 필드 확인 |

### CatalogItemType

카탈로그 아이템 종류. 응답에서는 소문자로 내려온다.

| 값 | 응답 값 | 설명 |
|---|---|---|
| `TOP` | `top` | 상의 |
| `PANTS` | `pants` | 하의 |
| `SHOES` | `shoes` | 신발 |
| `OUTER` | `outer` | 아우터 |
| `ACCESSORY` | `accessory` | 액세서리 |

---

## 에러 응답

### 공통 에러 응답 포맷

모든 에러는 동일한 `ApiErrorResponse` 구조로 반환된다.

```json
{
  "timestamp": "2026-02-18T06:30:00.123456Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "message must not be blank",
  "path": "/api/v1/chat/messages"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | `string` | 에러 발생 시각 (ISO 8601) |
| `status` | `number` | HTTP 상태 코드 |
| `code` | `string` | 애플리케이션 에러 코드 |
| `message` | `string` | 사람이 읽을 수 있는 에러 메시지 |
| `path` | `string` | 요청 URI |

### 에러 코드 테이블

| HTTP 상태 | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 요청 필드 검증 실패 (필수 필드 누락, 형식 오류, 범위 초과 등) |
| 401 | `AUTH_MISSING_TOKEN` | `Authorization` 헤더가 없거나 `Bearer ` 접두사가 없음 |
| 401 | `AUTH_INVALID_TOKEN` | Firebase ID Token 검증 실패 (만료, 위조 등) |
| 401 | `AUTH_UNAUTHORIZED` | 기타 인증 실패 |
| 403 | `CHAT_SESSION_FORBIDDEN` | 채팅 세션이 현재 사용자 소유가 아님 |
| 404 | `CHAT_SESSION_NOT_FOUND` | 해당 세션 ID가 존재하지 않음 |
| 404 | `PROFILE_NOT_FOUND` | 사용자 프로필이 아직 생성되지 않음 (채팅 세션을 먼저 진행해야 함) |
| 429 | `RATE_LIMIT_EXCEEDED` | 분당 요청 횟수 제한 초과 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 에러 |

---

## 데이터 흐름

```
┌─────────┐    POST /chat/sessions     ┌──────────────┐
│ Frontend │ ────────────────────────── │ Chat Session │
│          │                            │ (INTERVIEWING)│
│          │    POST /chat/messages     │              │
│          │ ◄════════════════════════► │              │
│          │    (반복: ASK/SUGGEST_STOP) │              │
│          │                            └──────┬───────┘
│          │                                   │ 중단 의도 감지 or
│          │                                   │ SUGGEST_STOP 수락
│          │                                   ▼
│          │                            ┌──────────────┐
│          │                            │ Profile 확정  │
│          │                            │ (build_profile)│
│          │                            └──────┬───────┘
│          │                                   │
│          │                                   ▼
│          │    recommendations 포함     ┌──────────────┐
│          │ ◄───────────────────────── │ 추천 생성     │
│          │    (RECOMMEND 응답)         │ (RECOMMENDED) │
│          │                            └──────────────┘
│          │
│          │    GET /profile            ┌──────────────┐
│          │ ────────────────────────── │ 프로필 조회   │
│          │                            └──────────────┘
│          │
│          │    POST /recommendations   ┌──────────────┐
│          │ ────────────────────────── │ 독립 추천     │
│          │    (프로필 기반 재추천)      │ (카테고리/예산)│
└─────────┘                            └──────────────┘
```

### 일반적인 사용 흐름

1. **로그인** — Firebase Auth로 ID Token 획득
2. **GET /me** — 사용자 정보 확인 (첫 요청 시 자동 계정 생성)
3. **POST /chat/sessions** — 새 채팅 세션 생성
4. **POST /chat/messages** (반복) — `nextAction`에 따라:
   - `ASK` → 사용자 입력 대기, 대화 계속
   - `SUGGEST_STOP` → CTA 버튼으로 중단/계속 선택 UI 표시
   - `RECOMMEND` → `recommendations` 배열로 추천 결과 렌더링
5. **GET /profile** — 현재 프로필 확인 (선택적)
6. **POST /recommendations** — 카테고리/예산 필터로 재추천 요청 (선택적)
