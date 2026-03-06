대화 내용을 바탕으로 사용자 스타일 프로필을 JSON으로 구조화한다.

## 모드
{{mode}}

## 대화 내용
{{conversation}}

## 출력 형식 (JSON ONLY)
설명 없이 JSON 객체만 출력한다.

필수 키:
- style_archetypes: 문자열 배열 (소문자, 아래 중 선택)
  - minimal, street, casual, formal, sporty, preppy, vintage, clean_fit, gorpcore, workwear, y2k, romantic
- colors:
  - like: 문자열 배열 (소문자: black, white, navy, beige, grey, brown, olive, blue, green, burgundy, khaki, cream)
  - avoid: 문자열 배열 (동일 규칙)
- fit:
  - top: slim | regular | overfit | wide | relaxed | ""
  - pants: slim | regular | overfit | wide | relaxed | ""
- body_type:
  - height: "petite" | "regular" | "tall" | ""  (petite: ~160cm, regular: 161~170cm, tall: 171cm~)
  - proportion: "balanced" | "upper_heavy" | "lower_heavy" | "athletic" | ""
    - upper_heavy: 어깨 넓거나 상체가 발달
    - lower_heavy: 엉덩이·허벅지가 발달
    - athletic: 전체적으로 탄탄한 체형
- material_pref:
  - like: 문자열 배열 (예: "cotton", "linen", "denim", "wool", "knit", "leather", "fleece")
  - avoid: 문자열 배열 (예: "polyester", "synthetic", "wool" — 알러지·불편함)
- brands:
  - like: 문자열 배열
  - avoid: 문자열 배열
- budget:
  - min: 숫자 또는 null
  - max: 숫자 또는 null
- context:
  - ageRange: 문자열 (예: "20s")
  - occasion: 문자열 배열 (daily, office, date, campus, outdoor, travel, homewear)
- shopping_intent: 문자열 배열
  - 지금 워드로브에서 부족하거나 새로 채우고 싶은 아이템 유형
  - 예: "need_office_bottom", "want_signature_outer", "looking_for_daily_top", "complete_casual_set"
- style_references: 문자열 배열
  - 사용자가 언급한 브랜드 이미지·연예인·인플루언서·무드 키워드
  - 예: "zara minimalist", "BTS 정국 캐주얼", "무신사 스탠다드 톤"
- constraints: 문자열 배열
  - 소재·관리·착화감·로고 등 hard constraint
  - 예: "no_leather", "easy_care_only", "avoid_big_logo", "avoid_bright_colors", "no_synthetic"
- confidence: 0.0~1.0 숫자
- followup_questions: 문자열 배열 (1~2개 권장)
- summary: 한국어 1~2문장

## 정규화 규칙
- 카테고리/태그 값은 영문 소문자로 정규화한다.
- 중복 항목은 제거한다.
- 근거가 약한 값은 과감히 비워두고 confidence를 낮춘다.
- 확실하지 않은 추측은 constraints나 summary에 단정적으로 쓰지 않는다.

## 모드별 규칙
- INCREMENTAL:
  - 새 근거가 있는 필드만 갱신한다.
  - 기존에 강하게 확정된 취향을 불필요하게 뒤집지 않는다.
- FINAL:
  - 누락 필드를 대화 맥락으로 합리적으로 보완 가능하다.
  - 단, 과도한 추측은 금지하고 confidence에 반영한다.

## confidence 가이드
- 0.85~1.0: 스타일/색/핏/예산/상황이 모두 명확, 체형 또는 소재까지 파악됨
- 0.65~0.84: 핵심 4개 이상 명확, 체형·소재는 일부 누락
- 0.40~0.64: 핵심 3개 이하 명확 또는 단편 정보 위주
- 0.39 이하: 근거 부족

## followup_questions 작성 가이드
- 아직 불확실하지만 추천 정확도에 큰 영향을 주는 질문만 만든다.
- 정보 가치 순서: 체형/비율 > 소재 회피 > 쇼핑목적 > 스타일 레퍼런스 > 브랜드
- 선택지형 문장으로 작성한다.
- 예: "상의는 슬림/레귤러/오버핏 중 어떤 실루엣이 편한가요?"
- 예: "키가 어느 정도 되세요? 160이하/160~170/170이상으로만 알려줘도 돼요."
- 예: "지금 가장 부족하다고 느끼는 아이템이 있나요? (예: 코트, 출근용 바지)"
