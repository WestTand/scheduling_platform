# Scheduling Pages Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire manager scheduling pages to real backend APIs in backend mode.

**Architecture:** Add frontend backend DTOs/adapters/services for schedule periods, period schedule detail, work shifts, assignments, and optimization. Update existing pages to consume these services while preserving current UI shape and mock fallback through existing service-level branching patterns.

**Tech Stack:** React, TypeScript, Vite, Vitest, Spring Boot backend APIs.

---

### Task 1: Scheduling API Adapters And Services

**Files:**
- Create: `frontend/src/types/backendSchedule.ts`
- Create: `frontend/src/adapters/scheduleAdapter.ts`
- Create: `frontend/src/adapters/scheduleAdapter.test.ts`
- Create: `frontend/src/services/backendScheduleService.ts`
- Create: `frontend/src/services/backendScheduleService.test.ts`

- [ ] Write failing adapter tests for period and schedule assignment mapping.
- [ ] Implement backend schedule DTOs and adapters.
- [ ] Write failing service tests for list/detail/schedule/work-shifts/assignment/optimize API calls.
- [ ] Implement backend schedule service.
- [ ] Run focused schedule adapter/service tests.

### Task 2: Schedule List And Detail Pages

**Files:**
- Modify: `frontend/src/pages/manager/SchedulesPage.tsx`
- Modify: `frontend/src/pages/manager/ScheduleDetailPage.tsx`

- [ ] Replace mock-only loading with backend schedule service calls.
- [ ] Preserve filters and empty states.
- [ ] Keep warning tab with empty backend warnings.
- [ ] Run frontend tests/build.

### Task 3: Schedule Edit Page

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleEditPage.tsx`

- [ ] Load employees, work shifts, and current schedule assignments from backend.
- [ ] Wire create/update/delete assignment.
- [ ] Wire lock/unlock assignment.
- [ ] Surface backend errors in the existing notice area.
- [ ] Run frontend tests/build.

### Task 4: Schedule Generation Page

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx`

- [ ] Load schedule periods for selection.
- [ ] Call optimize endpoint for selected period.
- [ ] Display success/failure result.
- [ ] Run frontend tests/build.

### Task 5: Runtime Smoke

**Files:**
- Runtime only.

- [ ] Verify backend health.
- [ ] Login as `manager@example.com / Manager@123`.
- [ ] Smoke `/manager/schedules`, detail, edit, and generation pages.
- [ ] Commit and push when verified.
