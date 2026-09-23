# Activity Log Friendly Render Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or direct TDD for this small approved UI mapping change. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render activity-log table target/action/description as business-facing Vietnamese text.

**Architecture:** Keep backend unchanged. Add mapping helpers in `frontend/src/adapters/activityLogAdapter.ts` and update adapter tests.

**Tech Stack:** React, TypeScript, Vitest.

---

### Task 1: Friendly Activity Log Adapter

**Files:**
- Modify: `frontend/src/adapters/activityLogAdapter.ts`
- Modify: `frontend/src/adapters/activityLogAdapter.test.ts`

- [ ] Write failing adapter tests for friendly target/action/description.
- [ ] Implement entity/action/description mapping helpers in the adapter.
- [ ] Run focused adapter test.
- [ ] Run frontend build.
- [ ] Commit changes.
