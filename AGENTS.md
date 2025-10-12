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

## Coding conventions

To keep the admin service consistent with the existing style in `saerok-BE`, follow these conventions for all Java and Kotlin source under this repository:

- **Indentation:** Use 4 spaces per indentation level. Do not use tab characters. Method bodies, control-flow blocks, and class members should be indented once under their declarations, mirroring the backend project.
- **Braces:** Place opening braces on the same line as the declaration (classes, methods, `if`/`else`, `try`/`catch`, etc.) and closing braces on their own line aligned with the start of the declaration.
- **Spacing:** Keep a single space between keywords and parentheses (e.g., `if (`), around binary operators, and after commas. Avoid trailing whitespace at the end of lines.
- **Imports:** Group imports by package (standard library, third-party, project) and keep each group sorted alphabetically without unused imports. Leave a single blank line between groups, matching the structure in `saerok-BE`.
- **Blank lines:** Use blank lines to separate logical sections—between package and import statements, between import groups, and between class members when it improves readability. Avoid multiple consecutive blank lines.
- **Naming:** Follow Java naming conventions as seen in `saerok-BE`: `PascalCase` for classes, `camelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants.
- **Annotations:** Place annotations directly above the element they decorate without blank lines, preserving the order used in the backend (Spring stereotypes closest to the declaration, followed by additional annotations).

These rules should be treated as mandatory for any code you add or modify so that both repositories share the same formatting baseline.

### Thymeleaf templates

- Prefer Thymeleaf literal syntax (`|...|`) or dedicated `th:onclick`, `th:href`, etc. attributes when interpolating strings that contain quotes or concatenating literals with expressions. Avoid building inline JavaScript or attribute values with nested quotes inside `th:attr`, as it triggers parsing errors.
- Use the utility methods that actually exist in Thymeleaf expression objects. For example, prefer `#strings.isEmpty(...)`/`!#strings.isEmpty(...)` rather than `#strings.hasText(...)`, which is unavailable in Thymeleaf 3.

### Gotchas

- When building ternary expressions that concatenate literals and values (e.g., `cond ? 'text' : value + 'suffix'`), always wrap the entire expression with Thymeleaf's literal syntax (`|...|`). This prevents parsing errors like the one we recently fixed in `reports/list.html` and keeps the expression engine from misinterpreting string concatenations.
