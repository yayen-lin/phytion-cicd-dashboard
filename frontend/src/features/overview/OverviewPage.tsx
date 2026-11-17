import {useCallback, useEffect, useState} from 'react'
import {Link} from 'react-router'
import {Loader2, RefreshCw, Server} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card'
import {Skeleton} from '@/components/ui/skeleton'
import type {EnvironmentApp} from '@/api/deploy'
import {getOverview, type EnvironmentOverview} from '@/api/overview'
import {deployedLabel} from '@/features/deploy/deployedLabel'
import {cn} from '@/lib/utils'

/**
 * The environment's status from its running apps: Failing when any app has pods not ready.
 * e.g. [sample-app healthy, dummy-app 1/2 pods] -> Failing (1); no apps -> Nothing running; apps unknown -> Unavailable
 */
function EnvironmentStatusBadge({apps}: { apps: EnvironmentApp[] | null }) {
  if (apps === null) return <Badge variant="secondary" className="bg-muted text-muted-foreground">Unavailable</Badge>
  if (apps.length === 0) return <Badge variant="secondary" className="bg-muted text-muted-foreground">Nothing running</Badge>

  const failing = apps.filter((app) => !app.healthy).length
  return failing > 0
    ? <Badge variant="secondary" className="bg-red-500/15 text-red-700 dark:text-red-300">Failing ({failing})</Badge>
    : <Badge variant="secondary" className="bg-emerald-500/15 text-emerald-700 dark:text-emerald-300">Healthy</Badge>
}

/** One running app: a health dot, name, version, ready / desired pods, and who deployed it when. Opens the app's page. */
function AppRow({app}: { app: EnvironmentApp }) {
  return (
    <li>
      <Link to={`/apps/${encodeURIComponent(app.appId)}`}
            className="flex items-baseline gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted">
        <span className={cn('size-2 shrink-0 self-center rounded-full', app.healthy ? 'bg-emerald-500' : 'bg-red-500')}/>
        <span className="font-medium">{app.appId}</span>
        <span className="font-mono">{app.version ?? '-'}</span>
        {/* ready / desired replicas; red when not all are ready */}
        <span className={cn('shrink-0 tabular-nums', app.healthy ? 'text-muted-foreground' : 'text-red-700 dark:text-red-300')}>
          {app.readyPods ?? 0}/{app.desiredPods ?? 0} pods
        </span>
        <span className="min-w-0 truncate text-muted-foreground">· {deployedLabel(app.deployedAt, app.deployedBy)}</span>
      </Link>
    </li>
  )
}

/** One environment's card: fixed height, its status and running deploys on top, and its apps in a scrollable list. */
function EnvironmentCard({overview}: { overview: EnvironmentOverview }) {
  return (
    <Card className="h-80 gap-3">
      <CardHeader className="space-y-2">
        <CardTitle className="flex items-center gap-2 text-lg">
          <Link to={`/environments/${encodeURIComponent(overview.environment)}`}
                className="flex min-w-0 items-center gap-2 hover:underline">
            <Server className="size-5 shrink-0"/><span className="truncate">{overview.environment}</span>
          </Link>
          <span className="ml-auto"><EnvironmentStatusBadge apps={overview.apps}/></span>
        </CardTitle>
        {overview.runningDeploys.map((deploy) => (
          <a key={`${deploy.appId}-${deploy.buildId}`} href={deploy.url} target="_blank" rel="noreferrer"
             className="flex items-center gap-2 text-sm text-sky-700 hover:underline dark:text-sky-300">
            <Loader2 className="size-3.5 shrink-0 animate-spin"/>
            <span className="truncate">Deploying {deploy.appId} {deploy.version}{deploy.startedBy ? ` by ${deploy.startedBy}` : ''}</span>
          </a>
        ))}
      </CardHeader>

      {/* the apps scroll inside the card, so many apps don't stretch it */}
      <CardContent className="min-h-0 flex-1 overflow-y-auto">
        {overview.apps === null ? (
          <p className="text-sm text-muted-foreground">The operate tool couldn't be reached</p>
        ) : overview.apps.length === 0 ? (
          <p className="text-sm text-muted-foreground">No apps running</p>
        ) : (
          <ul className="-mx-2">
            {overview.apps.map((app) => <AppRow key={app.appId} app={app}/>)}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * The landing page after login: one card per environment, from one request, each with its status (healthy or
 * failing, from Kubernetes), deploys still running, and the apps running there.
 */
function OverviewPage() {
  // null while loading
  const [overviews, setOverviews] = useState<EnvironmentOverview[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const loadOverview = useCallback(() => {
    return getOverview()
      .then((data) => {
        setOverviews(data)
        setError('')
      })
      .catch(() => setError('Could not load the overview'))
  }, [])

  useEffect(() => {
    loadOverview()
  }, [loadOverview])

  // keeps the current cards on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadOverview()
    setRefreshing(false)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Overview</h1>
        </div>
        <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
          <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
          Refresh
        </Button>
      </div>

      {/* one full-width row per environment, so any number of them stacks */}
      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Overview unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !overviews ? (
        <div className="space-y-4">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-80 w-full rounded-xl"/>)}
        </div>
      ) : (
        <div className="space-y-4">
          {overviews.map((overview) => <EnvironmentCard key={overview.environment} overview={overview}/>)}
        </div>
      )}
    </div>
  )
}

export default OverviewPage
