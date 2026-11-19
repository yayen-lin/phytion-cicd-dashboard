import { useCallback, useEffect, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { RefreshCw } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { getAppsSummary, type AppSummary } from '@/api/apps'
import type { Environment } from '@/api/environments'
import { usePermissions } from '@/auth/AuthContext'
import StatusBadge from '@/components/StatusBadge'
import AppActionsMenu, { type AppInEnvironment } from '@/features/actions/AppActionsMenu'
import { cn } from '@/lib/utils'
import { usePollWhileRunning } from '@/hooks/use-poll-while-running'

/** A cell whose tool couldn't be reached */
function Unavailable() {
  return <span className="text-sm text-muted-foreground" title="The tool couldn't be reached">Unavailable</span>
}

/** One app's version, health and pods in one environment. */
function EnvironmentCell({ summary, environment }: { summary: AppSummary, environment: string }) {
  if (!summary.app.deploy) return <span className="text-muted-foreground">-</span>
  if (summary.unavailable.includes('ENVIRONMENTS')) return <Unavailable />

  const state = summary.deployments.find((d) => d.environment === environment)?.state
  if (!state?.version) return <span className="text-sm text-muted-foreground">Not deployed</span>
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('size-2 rounded-full', state.healthy ? 'bg-emerald-500' : 'bg-red-500')} />
      <span className="font-mono text-sm">{state.version}</span>
      <span
        className={cn('text-xs tabular-nums', state.healthy ? 'text-muted-foreground' : 'text-red-700 dark:text-red-300')}
        title="Ready / desired pods">
        {state.readyPods ?? 0}/{state.desiredPods ?? 0}
      </span>
    </span>
  )
}

/** Turns an app's summary into the environments its action menu acts in. */
function appInEnvironments(summary: AppSummary, environments: Environment[]): AppInEnvironment[] {
  return environments.map((environment, i) => ({
    environment,
    version: summary.deployments.find((d) => d.environment === environment.name)?.state.version ?? null,
    nextEnvironment: environments[i + 1] ?? null,
  }))
}

/** Every app with its latest build and what runs in each environment; reloads while a build runs. */
function AppsPage() {
  const navigate = useNavigate()
  const { canAct } = usePermissions()
  // null while loading
  const [summary, setSummary] = useState<{ environments: Environment[], apps: AppSummary[] } | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const loadSummary = useCallback(() => {
    return getAppsSummary()
      .then((data) => {
        setSummary(data)
        setError('')
      })
      .catch(() => setError('Could not load the apps'))
  }, [])

  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  // load again while a latest build runs
  usePollWhileRunning(summary, !!summary?.apps.some((s) => s.latestBuild?.status === 'RUNNING'), loadSummary)

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadSummary()
    setRefreshing(false)
  }

  // a row opens its app's page, by click or by Enter / Space
  function openApp(appId: string) {
    navigate(`/apps/${appId}`)
  }

  function handleRowKeyDown(e: KeyboardEvent, appId: string) {
    if (e.target !== e.currentTarget) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      openApp(appId)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Apps</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Apps</CardTitle>
          <CardAction>
            <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
              <RefreshCw className={refreshing ? 'animate-spin' : undefined} />
              Refresh
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          {error ? (
            <Alert variant="destructive">
              <AlertTitle>Apps unavailable</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : !summary ? (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full" />)}
            </div>
          ) : summary.apps.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No apps configured. Add one to <code>dashboard.apps</code> in the backend's application.properties.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>App</TableHead>
                  <TableHead>Latest build</TableHead>
                  {summary.environments.map((e) => <TableHead key={e.name}>{e.name}</TableHead>)}
                  {canAct && <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {summary.apps.map((s) => (
                  <TableRow key={s.app.id} className="cursor-pointer" tabIndex={0}
                            aria-label={`Open ${s.app.id}`}
                            onClick={() => openApp(s.app.id)}
                            onKeyDown={(e) => handleRowKeyDown(e, s.app.id)}>
                    <TableCell className="font-medium">{s.app.id}</TableCell>
                    <TableCell>
                      {s.unavailable.includes('BUILD') ? <Unavailable /> : s.latestBuild ? (
                        <span className="inline-flex items-center gap-2">
                          <span className="text-sm text-muted-foreground">#{s.latestBuild.id}</span>
                          <StatusBadge status={s.latestBuild.status} />
                        </span>
                      ) : <span className="text-sm text-muted-foreground">No builds yet</span>}
                    </TableCell>
                    {summary.environments.map((e) => (
                      <TableCell key={e.name}><EnvironmentCell summary={s} environment={e.name} /></TableCell>
                    ))}
                    {/* the menu's clicks stay in the menu instead of opening the app */}
                    {canAct && <TableCell onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                      <AppActionsMenu appId={s.app.id} app={s.app}
                                      environments={appInEnvironments(s, summary.environments)}
                                      onChanged={loadSummary} />
                    </TableCell>}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

export default AppsPage
