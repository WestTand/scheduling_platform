# Backend Mode Badge Consistency Design

## Context

The sidebar badge counts currently read directly from mock seed data for several navigation items. This is correct in mock mode, but misleading in backend mode because the visible pages fetch real backend data. Users can see a badge count, click the tab, and find no matching records.

Recent fixes already hid mock-derived badges for `Ngày nghỉ` and `Yêu cầu đổi ca` when `VITE_USE_MOCK_API=false`. This slice finishes the consistency cleanup for the rest of the sidebar badge surface.

## Goal

When the frontend runs in backend mode, sidebar badges must not show counts derived from mock seed data. Mock mode should keep the existing demo badge behavior.

## Scope

- Audit `Sidebar.getBadgeCount` for every mock-seed-derived badge.
- Hide mock-derived sidebar badge counts in backend mode for tabs that do not have a backend-backed count source.
- Preserve existing mock-mode behavior.
- Add regression coverage that renders the sidebar in backend mode and verifies mock counts are not shown.

## Non-Goals

- Do not add new backend count endpoints in this slice.
- Do not introduce polling or global notification state.
- Do not change page-level lists, filters, or dashboard content outside the sidebar badge mismatch.

## Design

Keep the change centralized in `frontend/src/components/layout/Sidebar.tsx`. `getBadgeCount` will treat mock seed data as valid only when `apiConfig.useMockApi` is true. In backend mode, any count sourced from mock arrays should return `0` unless a real backend-backed count is introduced.

This keeps behavior predictable and avoids showing stale demo data as if it were live state.

## Testing

Extend the existing `Sidebar.test.tsx` regression tests to cover all sidebar labels that previously derived counts from mock seeds. The tests will stub `VITE_USE_MOCK_API=false`, render the sidebar, and assert that mock count badges are not present.

Run frontend tests, lint, and build after implementation.
