# AGENT.md — Style Recommender Chat Service (Spring + Firebase + LLM)

## 0) Goal (제품 목표)
- Firebase 로그인 사용자에게 LLM이 질문을 던지고, 사용자는 응답한다.
- 응답을 누적해 사용자의 성향/취향 프로필을 만든다.
- 프로필 기반으로 옷/신발 추천(이유 포함)을 제공한다.
- 추후 확장: 사용자가 자유 질문 → LLM 답변 + 추천까지 연결.

## 1) Non-goals (이번 단계에서 하지 않을 것)
- 결제/주문/재고/배송 연동
- 실시간 소셜 기능(팔로우/피드)
- 이미지 생성/가상 피팅
- 추천 “정답” 최적화(초기에는 규칙+LLM 혼합으로 충분)

## 2) Tech Stack (고정)
- Backend: Spring Boot (Java 17+), Gradle
- Auth: Firebase Authentication (ID Token 검증)
- DB: PostgreSQL (or MySQL) + JPA/Hibernate
- Cache/Queue(선택): Redis (대화 세션/레이트리밋)
- LLM: OpenAI API (Chat Completions) or compatible
- Observability: Spring Actuator + structured logs

## 3) Local Run Commands (필수)
- Build: `./gradlew clean build`
- Test: `./gradlew test`
- Run: `./gradlew bootRun`
- Lint/format(있다면): `./gradlew spotlessApply` / `./gradlew spotlessCheck`

Codex는 변경 후 반드시:
1) `./gradlew test`
2) 앱 기동 확인(최소한 bootRun 또는 슬라이스 테스트)

## 4) Repo Structure (권장)
- /src/main/java/...
    - config/          (security, firebase, llm client config)
    - auth/            (firebase token verifier, auth filter)
    - chat/            (chat controller/service, session)
    - profile/         (preference profile domain)
    - catalog/         (items, brands, style tags seed)
    - recommend/       (ranking, explanation, response DTO)
    - llm/             (prompt templates, parsing, tool schema)
    - common/          (errors, response wrapper, utils)
- /src/test/java/... (service tests, controller tests)
- /docs/             (API, prompts, ERD)
- /scripts/          (seed data, local setup)

## 5) Data Model (최소 엔티티)
- UserAccount
    - id (pk), firebaseUid, createdAt, lastLoginAt
- ChatSession
    - id, userId, status(OPEN/CLOSED), createdAt, updatedAt
- ChatMessage
    - id, sessionId, role(USER/ASSISTANT/SYSTEM), content, createdAt
- PreferenceProfile
    - id, userId, version, profileJson, summary, updatedAt
- RecommendationHistory
    - id, userId, sessionId, requestJson, resultJson, createdAt
- CatalogItem (초기에는 간단히)
    - id, type(TOP/PANTS/SHOES/etc), name, brand, priceRange, tagsJson, gender, season, imageUrl(optional)

## 6) LLM Contract (중요)
### 6.1 Profile schema (JSON만 출력)
LLM이 분석 결과를 반드시 JSON으로 반환:
{
"style_archetypes": ["minimal", "street", ...],
"colors": {"like": ["black"], "avoid": ["neon"]},
"fit": {"top": "oversized", "pants": "wide"},
"brands": {"like": [], "avoid": []},
"budget": {"min": 50000, "max": 200000},
"context": {"ageRange": "20s", "occasion": ["campus"]},
"constraints": ["no_leather"],
"confidence": 0.0-1.0,
"followup_questions": ["...","..."]
}

### 6.2 Recommendation schema (JSON만 출력)
{
"recommendations": [
{
"category": "shoes",
"item_id": 123,
"reason": "..."
}
],
"alternatives": [...],
"next_question": "..."
}

### 6.3 Parsing rule
- JSON 파싱 실패 시: 재시도 1회(“JSON만” 강제), 실패하면 안전한 fallback 응답.

## 7) Prompts (파일로 관리)
- /docs/prompts/
    - system.md        (정체성/규칙/금지)
    - ask_questions.md (질문 전략)
    - build_profile.md (프로필 생성 JSON)
    - recommend.md     (추천 JSON)
- prompt는 코드에 하드코딩 금지. 리소스 로드 방식 선호.

## 8) Security / Privacy (절대 준수)
- Firebase ID Token은 서버에서 검증해야 함(클라이언트 신뢰 금지).
- 액세스토큰/비밀키는 절대 로그로 남기지 말 것.
- PII 최소 저장: 대화 전문 저장은 옵션(토글)으로 하고, 기본은 요약+구조화 프로필 저장.
- Rate limit: userId 기준(예: 분당 N회). 악성 입력 방어.

## 9) API Endpoints (초기)
- POST /api/v1/chat/sessions        : 세션 생성
- POST /api/v1/chat/messages        : 메시지 전송(assistant 응답 포함)
- GET  /api/v1/profile              : 내 취향 프로필 조회
- POST /api/v1/recommendations      : 추천 요청(또는 메시지 전송 시 함께)
- GET  /actuator/health             : 헬스체크

## 10) Quality Gates (PR 통과 조건)
- 단위테스트/슬라이스테스트 최소 3개 이상 추가(핵심 기능일수록 증가)
- 예외 처리(토큰 오류/LLM 오류/파싱 오류) 테스트 포함
- DB 마이그레이션(있다면) 포함, 롤백 고려
- “변경 파일 목록 + 영향 범위” PR 설명에 포함

## 11) Coding Rules
- 서비스는 얇게: Controller -> Service -> Repository
- DTO 분리, 엔티티 직접 노출 금지
- 시간은 UTC 저장, 응답은 ISO-8601
- 예외는 공통 에러 포맷으로 반환
