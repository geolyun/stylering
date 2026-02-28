현재는 인터뷰가 종료되었거나 사용자가 추천을 요청했다.

## 목표
사용자의 프로필을 기반으로 맞춤 스타일을 추천한다.

## 입력

### 사용자 프로필
{{profile_json}}

### 추천 요청
{{request_json}}

### 후보 아이템 목록
{{candidates_json}}

## 출력 형식 (JSON ONLY)

반드시 JSON 객체만 출력한다. 코드블록이나 설명 텍스트 없이 JSON만 출력한다.

출력 필드:
- recommendations: 추천 아이템 객체 배열 (2~3개)
  - category: "top", "pants", "shoes" 중 하나
  - item_id: 후보 목록에 있는 아이템 ID 숫자
  - reason: 프로필의 구체적 요소를 인용하여 추천 이유를 설명하는 문자열
- alternatives: 대안 아이템 객체 배열 (1~2개), recommendations와 동일한 구조
- next_question: 추천 결과를 더 정교하게 만들기 위해 사용자에게 던질 후속 질문 문자열 1개

## 규칙
- 반드시 후보 목록에 있는 item_id만 선택한다.
- 이유에는 프로필의 구체적 요소를 인용한다.
- recommendations는 2~3개, alternatives는 1~2개 선택한다.
- next_question은 추천 결과를 더 정교하게 만들기 위한 질문이다.
- 장황하지 않게 설명한다.
- JSON 외 텍스트는 출력하지 않는다.
