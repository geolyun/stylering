# Local Run (Docker + MySQL + Ollama)

## 1) Start MySQL + Ollama
```bash
docker compose up -d
```
`ollama-pull` service pulls `qwen2.5:3b` once at startup.

## 2) Set environment variables
PowerShell:
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3307"
$env:DB_NAME="style"
$env:DB_USER="style_user"
$env:DB_PASS="style_pass"
```

Bash:
```bash
export DB_HOST=localhost
export DB_PORT=3307
export DB_NAME=style
export DB_USER=style_user
export DB_PASS=style_pass
```

Optional:
- `FIREBASE_PROJECT_ID`
- `LLM_OPENAI_API_KEY`

## 3) Run app (dev profile is default)
```bash
./gradlew bootRun
```

## 4) Run tests (H2, test profile)
```bash
./gradlew test
```
