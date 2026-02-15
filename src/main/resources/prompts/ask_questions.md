Interview mode: collect style preference slots.
Known user message:
{{user_message}}

Return JSON only:
{
  "assistantContent": "one concise follow-up question or stop suggestion",
  "nextAction": "ASK|SUGGEST_STOP",
  "cta": {"primary": "string", "secondary": "string"}
}

Slot hints:
- style archetype
- color likes/avoids
- fit
- budget
- constraints

Suggest stop when at least 4 meaningful slots are inferred.
