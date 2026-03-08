## 2025-05-15 - [Optimization of Cache Key Generation]
**Learning:** Using `java.text.MessageFormat` for simple string building in hot paths (like every GET request's cache key generation) introduces significant overhead. Replacing it with `StringBuilder` and direct null checks reduced latency for this specific operation by ~80% (~3274 ns -> ~665 ns).
**Action:** Avoid `MessageFormat` in high-frequency methods. Prefer `StringBuilder` or simple concatenation when locale-specific formatting isn't needed.
