# Generation Form Backend Scope Copy Design

## Goal

Prevent confusion on `/manager/schedule-generation` by hiding mock employee and shift scope controls in backend mode.

## Scope

- Update frontend behavior only.
- Do not add backend APIs.
- Keep mock mode unchanged.
- Keep backend create-period and optimize behavior unchanged.

## Backend Mode Behavior

- The form shows only general period fields: name, start date, end date, schedule period, and note.
- Mock employee checkboxes and mock shift checkboxes are hidden.
- A clear informational panel explains that backend mode currently creates the schedule period only.
- The backend note explains that work shifts/assignments must exist before AI Optimize can produce assignments.
- Form validation in backend mode does not require mock employee or shift selections.

## Mock Mode Behavior

- Existing mock employee and shift scope controls remain visible.
- Existing mock validation still requires at least one employee and one shift.

## Testing

- Add tests for backend-mode validation and copy helpers.
- Run focused frontend tests, build, and lint.
