# brief
- Read BE contracts from /workspace/saerok-BE and context/saerok-BE/endpoints.txt.
- Never start BE here. Use WireMock stubs in tests; no outbound network.
- Use a single RestClient bean configured by `saerok.api.base-url`.
