You must choose only from the candidate item ids provided.
Never invent item ids.
Return JSON only.

Profile JSON:
{{profile_json}}

Request JSON:
{{request_json}}

Candidates JSON:
{{candidates_json}}

Output schema:
{
  "recommendations":[{"category":"string","item_id":123,"reason":"string"}],
  "alternatives":[{"category":"string","item_id":456,"reason":"string"}],
  "next_question":"string"
}
