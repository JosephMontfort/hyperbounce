# HyperBounce Agent Rules & Operating Principles (agent-instructions.md)

This document defines the strict, non-negotiable operational rules for Antigravity agents working on the HyperBounce / Hyper iOS codebase. Every agent and subagent must read and adhere to these guidelines at the start of every single execution round.

---

## 1. Absolute Truth and Honesty (Zero Sugarcoating)
- **No unverified assumptions**: Do not guess or assume. Always verify facts directly against the codebase, decompiled resources, runtime behaviors, or logs.
- **Blunt and objective reporting**: Report findings, trade-offs, bottlenecks, failures, and risks directly and honestly, without soft-pedaling or sugarcoating.
- **Surface hard truths early**: If a requested path is technically unfeasible, inefficient, or prone to instability, say so plainly with evidence and technical rationale.

---

## 2. No Local APK Compilation for Hyper iOS
- **Local compilation strictly prohibited**: Never attempt to build the release or debug HyperBounce APK locally on the device / Termux environment. Local builds cause severe CPU and memory exhaustion (OOM).
- **GitHub Actions Pipeline**:
  1. Commit and push changes cleanly to the target remote branch (`hos4`) on GitHub.
  2. Stand by and monitor GitHub Actions workflow until the build completes.
  3. If the build fails: inspect workflow logs, locate the exact compiler/lint errors, fix them, and push again.
  4. Once GitHub Actions succeeds: download the resulting APK artifact/release.
  5. Install the downloaded APK onto the test device using root privileges (`su -c "pm install -r ..."`).

---

## 3. Professional Engineering & True Root Cause Resolution
- **Fix the true root cause**: Always identify and resolve the underlying root cause of bugs or edge cases.
- **No shallow workarounds**: Avoid fragile hacks, arbitrary sleeps, band-aids, or complex secondary workarounds that mask symptoms while leaving architectural flaws intact.
- **Clean 4-tier modular architecture**: Follow the established pattern:
  - Tier 1: Generic Core Engine (`dev.hyperios.app.nav.LiquidNavigationBar` + `dev.hyperios.app.nav.glass.*`)
  - Tier 2: Isolated Per-App Host (`<App>NavBarHost.kt`)
  - Tier 3: Isolated Per-App Hook (`<App>Hook.kt`)
  - Tier 4: Dispatcher & Preferences (`PhysicsHook.kt`, `HyperBouncePrefs.kt`, `LivePrefs.kt`, `ModuleDefaults.kt`)

---

## 4. Orchestration & Subagent Delegation Pattern
- **Main Agent as Pure Orchestrator**: The main coordinator agent does not perform implementation or exploratory work directly. This keeps the main conversation context clean, focused, and free of context bloating. Every single task, even minor work, must be delegated to subagents.
- **Streamlined 3-Stage Workflow (Verifier Role Removed)**:
  Workflows follow a clean, staged pipeline:
  1. **Parallel Investigators**: Parallel subagents investigate the root cause, inspect decompiled bytecode/smali/resources, analyze runtime behaviors, and inspect device logs.
  2. **Implementation Architectural Planner**: A dedicated subagent synthesizes findings and drafts an architectural implementation plan/spec (`.md`).
  3. **Coder (Implementation)**: A dedicated implementation subagent writes the code cleanly in accordance with the architectural plan.
- **Direct GitHub Push**: The verifier role is explicitly eliminated. Once the coder subagent completes their work, the coordinator / coder directly commits and pushes changes to GitHub to trigger the remote CI build.
- **Full Context Seeding**: Every subagent invocation must receive:
  - Complete background context of the project and current branch.
  - Exact file paths, tools, working directories, and environment constraints.
  - Summary of what was done in preceding commits to avoid regressions or circular fixes.
  - Explicit, unambiguous deliverables.

---

## 5. Staged Phasing & Documentation
- **Research/Plan First**: Complex reverse engineering or structural additions must complete a thorough research and planning phase before any code is modified.
- **Implementation Specs**: Prior to implementation, write a detailed implementation specification document (`.md`) that guides the implementation subagent.
- **User Alignment**: Obtain explicit confirmation on the implementation plan before applying code modifications.
