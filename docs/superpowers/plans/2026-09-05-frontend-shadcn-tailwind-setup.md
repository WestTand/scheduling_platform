# Frontend shadcn/ui And TailwindCSS Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up TailwindCSS v4 and shadcn/ui in the existing Vite React frontend, then replace the default Vite starter UI with a scheduling platform landing page.

**Architecture:** Keep the frontend as a single Vite React TypeScript app under `frontend/`. Wire Tailwind through the Vite plugin, configure the `@/*` alias consistently across TypeScript and Vite, and commit shadcn-generated source files directly under `src/components/ui` and `src/lib`. Replace the old Vite starter screen with one Tailwind/shadcn page that proves the setup works without adding routing or business workflows.

**Tech Stack:** React 19, TypeScript 6, Vite 8, TailwindCSS 4, shadcn/ui source components, npm.

---

## File Structure

- Modify: `frontend/package.json` and `frontend/package-lock.json` for Tailwind and shadcn dependencies.
- Create: `frontend/components.json` for shadcn/ui configuration.
- Modify: `frontend/tsconfig.json` and `frontend/tsconfig.app.json` for `@/*` alias.
- Modify: `frontend/vite.config.ts` for Tailwind plugin and `@` alias while preserving Docker dev server settings.
- Modify: `frontend/src/index.css` as the Tailwind v4 and shadcn token entrypoint.
- Delete or empty: `frontend/src/App.css` because the starter page uses Tailwind utilities.
- Modify: `frontend/src/App.tsx` for the scheduling platform landing page.
- Create: `frontend/src/lib/utils.ts` for shadcn `cn` helper.
- Create: `frontend/src/components/ui/button.tsx`, `frontend/src/components/ui/card.tsx`, and `frontend/src/components/ui/badge.tsx`.

## Task 1: Add Tailwind And shadcn Dependencies

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`

- [ ] **Step 1: Install dependencies**

Run from `frontend/`:

```powershell
npm.cmd install tailwindcss @tailwindcss/vite tw-animate-css class-variance-authority clsx tailwind-merge lucide-react
```

Expected: npm exits with code `0`, and both `package.json` and `package-lock.json` are updated.

- [ ] **Step 2: Confirm dependency entries**

Check `frontend/package.json` includes these dependencies:

```json
{
  "dependencies": {
    "@tailwindcss/vite": "...",
    "class-variance-authority": "...",
    "clsx": "...",
    "lucide-react": "...",
    "react": "^19.2.8",
    "react-dom": "^19.2.8",
    "tailwind-merge": "...",
    "tailwindcss": "...",
    "tw-animate-css": "..."
  }
}
```

Exact installed versions can be npm-selected semver versions.

- [ ] **Step 3: Verify install did not break existing build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build exits with code `0`.

## Task 2: Configure Tailwind, shadcn, And Import Alias

**Files:**
- Create: `frontend/components.json`
- Modify: `frontend/tsconfig.json`
- Modify: `frontend/tsconfig.app.json`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/index.css`

- [ ] **Step 1: Create shadcn config**

Write `frontend/components.json`:

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/index.css",
    "baseColor": "slate",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

- [ ] **Step 2: Configure root TypeScript alias**

Replace `frontend/tsconfig.json` with:

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ],
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

- [ ] **Step 3: Configure app TypeScript alias**

Replace `frontend/tsconfig.app.json` with:

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "es2023",
    "lib": ["ES2023", "DOM"],
    "module": "esnext",
    "types": ["vite/client"],
    "allowArbitraryExtensions": true,
    "skipLibCheck": true,

    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    },

    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "erasableSyntaxOnly": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"]
}
```

- [ ] **Step 4: Configure Vite Tailwind plugin and alias**

Replace `frontend/vite.config.ts` with:

```ts
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
  },
})
```

- [ ] **Step 5: Replace global CSS with Tailwind and shadcn tokens**

Replace `frontend/src/index.css` with:

```css
@import "tailwindcss";
@import "tw-animate-css";

@custom-variant dark (&:is(.dark *));

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
}

:root {
  --radius: 0.75rem;
  --background: oklch(0.99 0.01 95);
  --foreground: oklch(0.2 0.03 260);
  --card: oklch(1 0 0);
  --card-foreground: oklch(0.2 0.03 260);
  --popover: oklch(1 0 0);
  --popover-foreground: oklch(0.2 0.03 260);
  --primary: oklch(0.48 0.16 255);
  --primary-foreground: oklch(0.99 0.01 95);
  --secondary: oklch(0.95 0.02 250);
  --secondary-foreground: oklch(0.26 0.04 260);
  --muted: oklch(0.95 0.02 250);
  --muted-foreground: oklch(0.5 0.03 260);
  --accent: oklch(0.92 0.05 170);
  --accent-foreground: oklch(0.25 0.05 180);
  --destructive: oklch(0.58 0.22 25);
  --border: oklch(0.9 0.02 250);
  --input: oklch(0.9 0.02 250);
  --ring: oklch(0.6 0.15 255);
}

.dark {
  --background: oklch(0.16 0.03 260);
  --foreground: oklch(0.96 0.01 95);
  --card: oklch(0.21 0.03 260);
  --card-foreground: oklch(0.96 0.01 95);
  --popover: oklch(0.21 0.03 260);
  --popover-foreground: oklch(0.96 0.01 95);
  --primary: oklch(0.72 0.15 250);
  --primary-foreground: oklch(0.16 0.03 260);
  --secondary: oklch(0.28 0.03 260);
  --secondary-foreground: oklch(0.96 0.01 95);
  --muted: oklch(0.28 0.03 260);
  --muted-foreground: oklch(0.72 0.02 260);
  --accent: oklch(0.3 0.06 180);
  --accent-foreground: oklch(0.95 0.03 170);
  --destructive: oklch(0.7 0.19 25);
  --border: oklch(1 0 0 / 10%);
  --input: oklch(1 0 0 / 15%);
  --ring: oklch(0.64 0.14 250);
}

@layer base {
  * {
    @apply border-border outline-ring/50;
  }

  body {
    @apply bg-background text-foreground antialiased;
    margin: 0;
    min-width: 320px;
    min-height: 100vh;
    font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  }
}
```

- [ ] **Step 6: Verify config compiles before UI rewrite**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build exits with code `0`.

## Task 3: Add shadcn Utility And Base Components

**Files:**
- Create: `frontend/src/lib/utils.ts`
- Create: `frontend/src/components/ui/button.tsx`
- Create: `frontend/src/components/ui/card.tsx`
- Create: `frontend/src/components/ui/badge.tsx`

- [ ] **Step 1: Add `cn` utility**

Write `frontend/src/lib/utils.ts`:

```ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

- [ ] **Step 2: Add Button component**

Write `frontend/src/components/ui/button.tsx`:

```tsx
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import type * as React from 'react'

import { cn } from '@/lib/utils'

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-all disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive",
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow-xs hover:bg-primary/90',
        destructive: 'bg-destructive text-white shadow-xs hover:bg-destructive/90 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 dark:bg-destructive/60',
        outline: 'border bg-background shadow-xs hover:bg-accent hover:text-accent-foreground dark:bg-input/30 dark:border-input dark:hover:bg-input/50',
        secondary: 'bg-secondary text-secondary-foreground shadow-xs hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent/50',
        link: 'text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-9 px-4 py-2 has-[>svg]:px-3',
        sm: 'h-8 rounded-md gap-1.5 px-3 has-[>svg]:px-2.5',
        lg: 'h-10 rounded-md px-6 has-[>svg]:px-4',
        icon: 'size-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot : 'button'

  return <Comp data-slot="button" className={cn(buttonVariants({ variant, size, className }))} {...props} />
}

export { Button, buttonVariants }
```

- [ ] **Step 3: Install missing Radix slot dependency if build reports it missing**

Run from `frontend/`:

```powershell
npm.cmd install @radix-ui/react-slot
```

Expected: npm exits with code `0` and updates `package.json` plus `package-lock.json`. If `@radix-ui/react-slot` already exists in `package.json`, skip this step.

- [ ] **Step 4: Add Card component**

Write `frontend/src/components/ui/card.tsx`:

```tsx
import type * as React from 'react'

import { cn } from '@/lib/utils'

function Card({ className, ...props }: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot="card"
      className={cn('bg-card text-card-foreground flex flex-col gap-6 rounded-xl border py-6 shadow-sm', className)}
      {...props}
    />
  )
}

function CardHeader({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-header" className={cn('@container/card-header grid auto-rows-min grid-rows-[auto_auto] items-start gap-1.5 px-6', className)} {...props} />
}

function CardTitle({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-title" className={cn('leading-none font-semibold', className)} {...props} />
}

function CardDescription({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-description" className={cn('text-muted-foreground text-sm', className)} {...props} />
}

function CardContent({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-content" className={cn('px-6', className)} {...props} />
}

function CardFooter({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-footer" className={cn('flex items-center px-6', className)} {...props} />
}

export { Card, CardHeader, CardFooter, CardTitle, CardDescription, CardContent }
```

- [ ] **Step 5: Add Badge component**

Write `frontend/src/components/ui/badge.tsx`:

```tsx
import { cva, type VariantProps } from 'class-variance-authority'
import type * as React from 'react'

import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center justify-center rounded-md border px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive transition-[color,box-shadow] overflow-hidden',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground [a&]:hover:bg-primary/90',
        secondary: 'border-transparent bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90',
        destructive: 'border-transparent bg-destructive text-white [a&]:hover:bg-destructive/90 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 dark:bg-destructive/60',
        outline: 'text-foreground [a&]:hover:bg-accent [a&]:hover:text-accent-foreground',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

function Badge({ className, variant, ...props }: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge, badgeVariants }
```

- [ ] **Step 6: Verify component imports compile**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build exits with code `0`.

## Task 4: Replace Vite Starter With Scheduling Landing Page

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: Replace App component**

Replace `frontend/src/App.tsx` with:

```tsx
import { Bell, CalendarClock, CheckCircle2, RefreshCcw, Sparkles, UsersRound } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

import './App.css'

const capabilities = [
  {
    title: 'Auto Scheduling',
    description: 'Prepare fair shift plans faster with constraints ready for future optimization.',
    icon: CalendarClock,
  },
  {
    title: 'Shift Swap Requests',
    description: 'Give employees a clear flow for requesting and reviewing shift changes.',
    icon: RefreshCcw,
  },
  {
    title: 'Notifications',
    description: 'Keep managers and staff aligned when schedules or requests change.',
    icon: Bell,
  },
]

const serviceLinks = [
  ['Frontend', 'http://localhost:5173'],
  ['Backend health', 'http://localhost:8080/api/health'],
  ['AI health', 'http://localhost:8001/health'],
  ['Adminer', 'http://localhost:8081'],
]

function App() {
  return (
    <main className="min-h-svh overflow-hidden bg-[radial-gradient(circle_at_top_left,var(--accent),transparent_32rem),linear-gradient(135deg,var(--background),var(--secondary))] px-4 py-6 text-foreground sm:px-6 lg:px-8">
      <section className="mx-auto flex max-w-6xl flex-col gap-8 rounded-[2rem] border bg-background/85 p-6 shadow-2xl shadow-primary/10 backdrop-blur sm:p-8 lg:p-10">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-11 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg shadow-primary/25">
              <UsersRound className="size-5" aria-hidden="true" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Scheduling Platform</p>
              <h1 className="text-xl font-semibold tracking-tight">Staff Operations Hub</h1>
            </div>
          </div>
          <Badge variant="secondary" className="w-fit gap-1.5 rounded-full px-3 py-1">
            <CheckCircle2 className="size-3.5" aria-hidden="true" />
            Docker-ready scaffold
          </Badge>
        </header>

        <div className="grid gap-8 lg:grid-cols-[1.2fr_0.8fr] lg:items-center">
          <div className="space-y-6">
            <Badge className="rounded-full px-3 py-1">TailwindCSS + shadcn/ui</Badge>
            <div className="space-y-4">
              <h2 className="max-w-3xl text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-6xl">
                Staff Scheduling Platform
              </h2>
              <p className="max-w-2xl text-base leading-7 text-muted-foreground sm:text-lg">
                A clean frontend foundation for schedule planning, shift collaboration, and service health checks while the product grows.
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button size="lg" className="rounded-full">
                <Sparkles className="size-4" aria-hidden="true" />
                Create schedule
              </Button>
              <Button size="lg" variant="outline" className="rounded-full">
                Review swap requests
              </Button>
            </div>
          </div>

          <Card className="border-primary/20 bg-card/90">
            <CardHeader>
              <CardTitle>Local service URLs</CardTitle>
              <CardDescription>Use these during Docker development.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {serviceLinks.map(([label, href]) => (
                <a
                  key={href}
                  href={href}
                  className="flex items-center justify-between rounded-xl border bg-background/70 px-4 py-3 text-sm transition hover:border-primary/40 hover:bg-accent/50"
                  target="_blank"
                  rel="noreferrer"
                >
                  <span className="font-medium">{label}</span>
                  <span className="text-muted-foreground">{href.replace('http://', '')}</span>
                </a>
              ))}
            </CardContent>
          </Card>
        </div>

        <section className="grid gap-4 md:grid-cols-3">
          {capabilities.map((item) => (
            <Card key={item.title} className="bg-card/80">
              <CardHeader>
                <div className="mb-3 flex size-10 items-center justify-center rounded-xl bg-accent text-accent-foreground">
                  <item.icon className="size-5" aria-hidden="true" />
                </div>
                <CardTitle>{item.title}</CardTitle>
                <CardDescription>{item.description}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </section>
      </section>
    </main>
  )
}

export default App
```

- [ ] **Step 2: Empty old App CSS**

Replace `frontend/src/App.css` with:

```css
```

- [ ] **Step 3: Verify landing page compiles**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build exits with code `0`.

- [ ] **Step 4: Verify lint**

Run from `frontend/`:

```powershell
npm.cmd run lint
```

Expected: lint exits with code `0`.

## Task 5: Verify Docker Frontend Runtime

**Files:**
- No planned file edits. If verification exposes a bug, fix the smallest relevant frontend config file and rerun the failed check.

- [ ] **Step 1: Validate Compose config**

Run from repository root:

```powershell
docker compose -f infra/docker-compose.dev.yml config --quiet
```

Expected: command exits with code `0`.

- [ ] **Step 2: Build and start Docker stack**

Run from repository root:

```powershell
docker compose -f infra/docker-compose.dev.yml up --build -d
```

Expected: all services start; backend, AI service, and PostgreSQL become healthy; frontend is running on port `5173`.

- [ ] **Step 3: Verify frontend HTTP response**

Run from repository root or any directory:

```powershell
(Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing).StatusCode
```

Expected: output is `200`.

- [ ] **Step 4: Stop Docker stack**

Run from repository root:

```powershell
docker compose -f infra/docker-compose.dev.yml down
```

Expected: Docker stops and removes service containers while preserving named volumes.

- [ ] **Step 5: Inspect final status**

Run from repository root:

```powershell
git status --short
```

Expected: only intended frontend setup files and plan/spec files are modified or untracked.
