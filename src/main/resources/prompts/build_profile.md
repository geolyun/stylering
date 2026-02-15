Build a preference profile JSON from the recent conversation.
Mode: {{mode}}

Conversation:
{{conversation}}

Return JSON object only. No markdown.

Schema:
{
  "style_archetypes": ["string"],
  "colors": { "like": ["string"], "avoid": ["string"] },
  "fit": { "top": "string", "pants": "string" },
  "brands": { "like": ["string"], "avoid": ["string"] },
  "budget": { "min": 0, "max": 0 },
  "context": { "ageRange": "string", "occasion": ["string"] },
  "constraints": ["string"],
  "confidence": 0.0,
  "summary": "string"
}

If mode is FINAL, resolve ambiguities conservatively and maximize consistency.
