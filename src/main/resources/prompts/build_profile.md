지금까지의 대화를 바탕으로 사용자의 패션 프로필을 구조화한다.

## 모드
{{mode}}

## 대화 내용
{{conversation}}

## 출력 형식 (JSON ONLY)

반드시 JSON 객체만 출력한다. 코드블록이나 설명 텍스트 없이 JSON만 출력한다.

출력 필드:
- style_archetypes: 파악된 스타일 유형 문자열 배열
- colors.like: 선호 색상 문자열 배열
- colors.avoid: 기피 색상 문자열 배열
- fit.top: 상의 핏 문자열
- fit.pants: 하의 핏 문자열
- brands.like: 선호 브랜드 문자열 배열
- brands.avoid: 기피 브랜드 문자열 배열
- budget.min: 최소 예산 숫자 (원 단위)
- budget.max: 최대 예산 숫자 (원 단위)
- context.ageRange: 연령대 문자열
- context.occasion: 착용 상황 문자열 배열
- constraints: 금지 소재/조건 문자열 배열
- confidence: 프로필 완성도 0.0 ~ 1.0 숫자
- followup_questions: 아직 파악하지 못한 취향을 알기 위한 후속 질문 문자열 배열 (1~2개)
- summary: 프로필 전체를 요약하는 한 문장 문자열

## 규칙
- 기존 프로필이 있다면 덮어쓰지 말고 보완한다.
- 정보가 부족하면 추정하되 confidence를 낮춘다.
- FINAL 모드일 경우, 가능한 한 현재 정보 기반으로 최대한 완성된 프로필을 생성한다.
- 설명 문장은 포함하지 않는다. JSON만 출력한다.
