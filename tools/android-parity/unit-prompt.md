You are working in the Pebo repository (Kotlin Multiplatform + Compose Multiplatform).
Your job is to complete EXACTLY ONE parity unit that moves the new Android target toward a
1:1 match with the existing desktop ("Windows") app. The shared UI in src/commonMain must be
reused as-is — do NOT fork, redesign, or rewrite UI. Match the desktop look and behavior.

## Project context
{{GLOBAL_CONTEXT}}

## The ONE unit to complete now
- id: {{UNIT_ID}}
- title: {{UNIT_TITLE}}
- depends on (already done): {{DEPENDS_ON}}

### What to do
{{UNIT_DESCRIPTION}}

### Done criteria
{{UNIT_ACCEPTANCE}}

## Hard rules
1. Implement ONLY this unit. Do not start other units. Keep changes surgical and complete.
2. Never break the desktop target or existing tests. The shared commonMain UI stays shared.
3. The Android UI must be the SAME shared Compose UI (App.kt CompactLayout handles phones).
4. Prefer idiomatic Android APIs for actuals; never block the main thread.
5. Do not commit. Do not edit anything under tools/android-parity/ (the loop owns that).
6. When you believe the unit is complete, make sure the build verifies. The loop will run:
   `./gradlew {{VERIFY}}` and treat a zero exit code as success. Your change MUST make that pass.
7. If the SDK/Gradle needs configuration (local.properties etc.), set it up; ANDROID_HOME is
   already exported in the environment.

Work autonomously until the verify command passes. Then stop and briefly summarize what you
changed and why, and note anything the next unit should know.
