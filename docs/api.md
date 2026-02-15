# API Policy

## Endpoints

### POST /api/v1/chat/sessions
- Auth: required (`Authorization: Bearer <token>`)
- Behavior: create `OPEN` chat session for current user
- Response `200`:
  - `sessionId`

### POST /api/v1/chat/messages
- Auth: required
- Request body:
  - `sessionId` (required)
  - `content` (required, not blank, max 2000 chars)
- Behavior:
  1. save USER message
  2. generate one next question using LLM
  3. on LLM failure, return fallback question
  4. save ASSISTANT message
- Response `200`:
  - `sessionId`
  - `userMessageId`
  - `assistantMessageId`
  - `assistantContent`

### GET /api/v1/profile
- Auth: required
- Behavior: return latest profile for current user
- Response `200`:
  - `version`
  - `profileJson`
  - `summary`
  - `updatedAt`

### POST /api/v1/recommendations
- Auth: required
- Request body:
  - `sessionId` (optional)
  - `category` (optional, e.g. `shoes`)
  - `budgetMax` (optional)
- Behavior:
  1. load latest profile
  2. rule-based candidate filtering (max 30)
  3. LLM chooses only from candidate item ids
  4. invalid item ids from LLM => fallback top-N rule
  5. save request/result to `recommendation_history`
- Response `200`:
  - `recommendations[]` (itemId/category/name/brand/priceRange/reason)
  - `alternatives[]`
  - `nextQuestion`

## Error Status / Codes

- `400 VALIDATION_ERROR`
  - invalid request payload
- `401 AUTH_MISSING_TOKEN`, `AUTH_INVALID_TOKEN`, `AUTH_UNAUTHORIZED`
  - authentication failure
- `403 CHAT_SESSION_FORBIDDEN`
  - session does not belong to current user
- `404 CHAT_SESSION_NOT_FOUND`
  - session id does not exist
- `404 PROFILE_NOT_FOUND`
  - no profile exists yet
- `429 RATE_LIMIT_EXCEEDED`
  - per-user requests exceeded minute window limit
- `500 INTERNAL_ERROR`
  - unexpected server error
