# HyperBounce Agent Rules & Operating Principles

This document defines the strict, non-negotiable operational rules for Antigravity agents working on the HyperBounce / Hyper iOS codebase. Every agent and subagent must read and adhere to these guidelines during every turn.

---

## 1. Absolute Truth and Honesty (Zero Sugarcoating)
- **No unverified assumptions**: Do not guess or assume. Always verify facts directly against the codebase, decompiled resources, runtime behaviors, or logs.
- **Blunt and objective reporting**: Report findings, trade-offs, bottlenecks, failures, and risks directly and honestly, without soft-pedaling or sugarcoating.
- **Surface hard truths early**: If a requested path is technically unfeasible, inefficient, or prone to instability, say so plainly with evidence and technical rationale.

---

## 2. No Local APK Compilation for Hyper iOS
- **Local compilation prohibited**: Never attempt to build the release or debug HyperBounce APK locally on the device / Termux environment.
- **GitHub Actions Pipeline**:
  1. Commit and push changes cleanly to the target remote branch on GitHub.
  2. Stand by and monitor GitHub Actions workflow until the build completes.
  3. If the build fails: inspect workflow logs, locate the exact compiler/lint errors, fix them, and push again.
  4. Once GitHub Actions succeeds: download the resulting APK artifact/release.
  5. Install the downloaded APK onto the test device using root privileges (`su -c "pm install -r ..."`).

---

## 3. Professional Engineering & True Root Cause Resolution
- **Fix the root cause**: Always identify and resolve the underlying root cause of bugs or edge cases.
- **No shallow workarounds**: Avoid fragile hacks, arbitrary sleeps, band-aids, or complex secondary workarounds that mask symptoms while leaving architectural flaws intact.
- **Clean architecture**: Follow the established 4-tier modular pattern:
  - Tier 1: Generic Core Engine (`dev.hyperios.app.nav.LiquidNavigationBar` + `dev.hyperios.app.nav.glass.*`)
  - Tier 2: Isolated Per-App Host (`<App>NavBarHost.kt`)
  - Tier 3: Isolated Per-App Hook (`<App>Hook.kt`)
  - Tier 4: Dispatcher & Preferences (`PhysicsHook.kt`, `HyperBouncePrefs.kt`, `LivePrefs.kt`)

---

## 4. Orchestration & Subagent Delegation Pattern
- **Main Agent as Orchestrator**: The main coordinator agent does not perform heavy implementation or exploratory work directly to keep the main conversation context clean, focused, and unbloated.
- **Subagent Delegation**:
  - All significant analysis, verification, and code implementation tasks must be delegated to dedicated subagents.
  - Whenever possible, split work into parallel complementary tasks and assign to parallel subagents.
  - Workflows must follow staged pipelines:
    1. **Analysis & Research** (parallel exploration subagents)
    2. **Verification & Review** (subagent verifies feasibility and plan soundness)
    3. **Implementation** (implementation subagent writes code cleanly)
- **Full Context Seeding**: Every subagent invocation must receive:
  - Complete background context of the project and current branch.
  - Exact file paths, tools, and environments.
  - Summary of what was done in the preceding 2–3 commits to avoid regressions or circular fixes.
  - Explicit, unambiguous deliverables.

---

## 5. Staged Phasing & Documentation
- **Research/Plan First**: Complex reverse engineering or structural additions must complete a thorough research and planning phase before any code is modified.
- **Implementation Specs**: Prior to implementation, write a detailed implementation specification document (`.md`) that guides the implementation subagent.
- **User Alignment**: Obtain explicit confirmation on the implementation plan before applying code modifications.
