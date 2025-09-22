# AGENTS

- Editable code: /workspace/saerok-admin
- Read-only context: /workspace/saerok-BE (cloned by setup), context/saerok-BE/endpoints.txt
- Do NOT run saerok-BE in this container. (BE runs locally via ./up.sh ./down.sh)

- API contract source:
  - Controllers/DTOs under /workspace/saerok-BE/src/main/**
  - Quick index: context/saerok-BE/endpoints.txt
  - Global prefix in BE is configurable (api_prefix). Treat it as external config.

- How to run admin here:
  - Build: ./gradlew clean build --console=plain
  - Run:   ./gradlew bootRun --console=plain --args='--server.port=8081'
  - Inject BE base URL via: --saerok.api.base-url=... or env SAEROK_API_BASE_URL
