# Generation Form Backend Scope Copy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide mock employee/shift scope controls in backend mode and make create-period scope explicit.

**Architecture:** Add a `backendMode` prop to `ScheduleGenerationForm`. The form keeps mock controls and validation only when `backendMode` is false. `ScheduleGenerationPage` passes `backendMode={!apiConfig.useMockApi}`.

**Tech Stack:** React 19, TypeScript, Vitest static render/helper tests.

---

## File Structure

- Modify: `frontend/src/components/schedule/ScheduleGenerationForm.tsx` for `backendMode`, conditional rendering, and validation.
- Create: `frontend/src/components/schedule/ScheduleGenerationForm.test.tsx` for static render tests.
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx` to pass `backendMode`.

## Task 1: Backend Mode Rendering

**Files:**
- Create: `frontend/src/components/schedule/ScheduleGenerationForm.test.tsx`
- Modify: `frontend/src/components/schedule/ScheduleGenerationForm.tsx`

- [ ] **Step 1: Write failing render test**

Create a test that renders `ScheduleGenerationForm backendMode` to static markup and asserts:

- `Phạm vi nhân viên` is absent.
- `Loại ca áp dụng` is absent.
- `Backend hiện chỉ tạo kỳ lịch` is present.

- [ ] **Step 2: Verify RED**

Run `npm.cmd test -- src/components/schedule/ScheduleGenerationForm.test.tsx`.

Expected: FAIL because `backendMode` behavior does not exist.

- [ ] **Step 3: Implement `backendMode` prop**

In `ScheduleGenerationForm.tsx`:

- Extend props with `backendMode?: boolean`.
- Default it to `false`.
- Skip employee/shift validation when `backendMode` is true.
- Hide employee and shift cards when `backendMode` is true.
- Render an informational panel in backend mode explaining backend creates only the schedule period.

- [ ] **Step 4: Verify GREEN**

Run `npm.cmd test -- src/components/schedule/ScheduleGenerationForm.test.tsx`.

Expected: PASS.

## Task 2: Page Wiring

**Files:**
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.tsx`

- [ ] **Step 1: Pass backendMode**

Change the form render to:

```tsx
<ScheduleGenerationForm backendMode={!apiConfig.useMockApi} onSubmit={handleSubmit} onCancel={() => navigate('/manager/schedules')} />
```

- [ ] **Step 2: Update backend copy**

Ensure the top backend info text no longer says the lower form is a scope confirmation. It should say the form creates a backend period and AI Optimize requires work shifts.

## Task 3: Verification

**Files:**
- No code changes unless verification reveals a defect.

- [ ] **Step 1: Run focused tests**

Run `npm.cmd test -- src/components/schedule/ScheduleGenerationForm.test.tsx src/pages/manager/ScheduleGenerationPage.test.tsx`.

Expected: PASS.

- [ ] **Step 2: Run build**

Run `npm.cmd run build`.

Expected: PASS with existing chunk-size warning acceptable.

- [ ] **Step 3: Run lint**

Run `npm.cmd run lint`.

Expected: 0 errors. Existing `AuthContext.tsx` warning may remain.

## Self-Review

- Spec coverage: backend hides mock controls, mock unchanged, backend validation no longer requires mock selections, and copy explains scope.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: `backendMode?: boolean` is local to the form.
