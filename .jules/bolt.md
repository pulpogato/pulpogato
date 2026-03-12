## 2026-03-12 - Map Pre-allocation Performance
**Learning:** Using `HashMap.newHashMap(int)` and `LinkedHashMap.newLinkedHashMap(int)` (introduced in Java 19) provides a clean way to pre-allocate collections when the size is known, avoiding multiple rehashes. However, care must be taken regarding the project's target Java version.
**Action:** Always check the project's toolchain configuration in `build.gradle.kts` before using newer JDK APIs.

## 2026-03-12 - Initial StringBuilder Capacity
**Learning:** For performance-critical hot paths like cache key generation, providing a sensible initial capacity to `StringBuilder` (e.g., 128 chars for typical URLs) prevents internal array copies during growth.
**Action:** Use `new StringBuilder(capacity)` instead of the default constructor in hot paths.
