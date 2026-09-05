# Frontend shadcn/ui And TailwindCSS Setup Design

## Goal

Set up TailwindCSS v4 and shadcn/ui in the existing Vite React TypeScript frontend, then replace the default Vite starter UI with a simple scheduling platform landing page.

## Scope

This change covers frontend styling infrastructure and a lightweight starter screen.

It includes:

- Installing TailwindCSS v4 for Vite through `tailwindcss` and `@tailwindcss/vite`.
- Initializing shadcn/ui for the existing Vite React TypeScript project.
- Adding the `@/*` TypeScript and Vite import alias.
- Adding shadcn support files such as `components.json` and `src/lib/utils.ts`.
- Adding only the initial shadcn components needed by the starter page: `button`, `card`, and `badge`.
- Replacing the default Vite starter UI with a scheduling platform landing page.

It does not include route setup, authentication UI, forms, table-heavy screens, scheduling workflows, dark-mode toggle behavior, or production design polish.

## Frontend Architecture

The frontend remains a single Vite React app under `frontend/`.

TailwindCSS is wired into Vite through `@tailwindcss/vite` in `frontend/vite.config.ts`. Vite keeps the existing Docker-friendly dev server config: host `0.0.0.0`, port `5173`, and `strictPort: true`.

The `@/*` alias maps to `frontend/src/*` across TypeScript and Vite config so shadcn-generated components can use imports such as `@/components/ui/button` and `@/lib/utils`.

shadcn/ui components live under `frontend/src/components/ui/`. Shared utility code lives under `frontend/src/lib/`.

## Styling

`frontend/src/index.css` becomes the Tailwind entrypoint and theme token location. It should import Tailwind and include the CSS variables required by shadcn/ui.

The existing Vite starter CSS in `frontend/src/App.css` should be removed or reduced to nothing if the new page uses Tailwind utilities directly. The new UI should avoid depending on the old Vite asset/logo styling.

## Starter Page

`frontend/src/App.tsx` should become a scheduling platform landing page that proves Tailwind and shadcn work together.

The page should include:

- A hero area for “Staff Scheduling Platform”.
- A shadcn `Badge` indicating the project is Docker-ready.
- shadcn `Button` quick actions for common future flows.
- shadcn `Card` sections for key capabilities: automatic scheduling, shift swap requests, and notifications.
- A small service URL section listing the local frontend, backend health, AI health, and Adminer URLs.

The layout should be responsive for desktop and mobile. It should be intentionally simple and suitable as a scaffold, not a final product screen.

## Dependencies

The frontend should gain only the dependencies needed for Tailwind and the selected shadcn components. Expected additions include Tailwind, `@tailwindcss/vite`, shadcn component dependencies, and utility packages required by generated shadcn code.

The package manager remains npm because the project already uses `package-lock.json` and `npm.cmd` in documentation.

## Verification

The change is complete when these commands pass from `frontend/`:

```powershell
npm.cmd run build
npm.cmd run lint
```

The Docker dev stack should still build and expose the frontend:

```powershell
docker compose -f infra/docker-compose.dev.yml up --build -d
```

Then verify `http://localhost:5173` returns HTTP `200`, and stop the stack with:

```powershell
docker compose -f infra/docker-compose.dev.yml down
```

## Risks And Constraints

shadcn/ui generates source files into the app instead of acting as a black-box component package. Generated files should be committed and reviewed like normal application code.

Tailwind v4 setup differs from older Tailwind v3 projects. The implementation should follow the current shadcn Vite docs and avoid adding obsolete `tailwind.config.js` or `postcss.config.js` unless the current CLI creates them.

The current frontend is still a scaffold, so the starter page should avoid deep application state and avoid adding dependencies for forms, data tables, routing, or charts.
