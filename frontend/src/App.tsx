import { Bell, CalendarClock, CheckCircle2, RefreshCcw, Sparkles, UsersRound } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

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
              <Button size="lg" className="rounded-full" disabled>
                <Sparkles className="size-4" aria-hidden="true" />
                Create schedule soon
              </Button>
              <Button size="lg" variant="outline" className="rounded-full" disabled>
                Review requests soon
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
                  className="flex min-w-0 flex-col gap-1 rounded-xl border bg-background/70 px-4 py-3 text-sm transition hover:border-primary/40 hover:bg-accent/50 sm:flex-row sm:items-center sm:justify-between"
                  target="_blank"
                  rel="noreferrer"
                >
                  <span className="shrink-0 font-medium">{label}</span>
                  <span className="min-w-0 break-all text-muted-foreground sm:truncate sm:break-normal">
                    {href.replace('http://', '')}
                  </span>
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
