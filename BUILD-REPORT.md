# OrionFFA-Core 3.0.0 Build Report

## Target
- Paper API: 26.2.build.48-alpha
- Java target: 25
- Gradle target: 9.1+

## Environment limitation
The execution environment contains Java 21.0.11 and no Gradle installation. Outbound DNS/TCP access is unavailable, so JDK 25 and Gradle cannot be downloaded into this runtime.

Paper's own developer documentation specifies Java 25 for the 26.2.x API line. Gradle 9.1.0 is the first Gradle release with Java 25 runtime support.

## Offline verification performed
- Compiled all main Java sources successfully with `javac --release 21` against the supplied Paper 26.2.build.48-alpha API and bundled API dependencies.
- The supplied Paper API class files were used only as a compile-time compatibility surface; their class-file version was lowered in a temporary copy because JDK 21 cannot read Java 25 class files. The project source itself was not modified for this compatibility check.
- Compiled all test sources successfully.
- Ran `ArenaReservationCheck` with assertions enabled successfully.
- Parsed all bundled YAML resources successfully.
- Fixed two source/test issues exposed by the compile pass:
  - `ArenaManager.delete()` incorrectly treated `ArenaManager#get` as returning Optional.
  - `FfaService` was missing the `Vector` import.
  - `ArenaReservationCheck` had an outdated `Arena` constructor invocation.
- Hardened spectator exit so arena capacity is claimed before teleport and rolled back if teleport fails.

## Artifact
`build/OrionFFA-Core-3.0.0-offline-compat.jar` is a manually assembled compatibility artifact. It is **not** claimed as the authoritative Java-25 Gradle/Shadow build because the required JDK 25 and Gradle runtime could not be installed in this environment.

For the authoritative release build, run the project's Gradle build under JDK 25 with Gradle 9.1+ and network access to the declared Maven repositories.
