import {useCallback, useEffect, useState, type KeyboardEvent} from 'react'
import {useNavigate} from 'react-router'
import {toast} from 'sonner'
import {ArrowUpRight, MoreHorizontal, Play, RefreshCw, Rocket, Tag} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardHeader, CardTitle} from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Skeleton} from '@/components/ui/skeleton'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {getAppsSummary, type App, type AppSummary} from '@/api/apps'
import {triggerBuild} from '@/api/ci'
import type {Environment} from '@/api/environments'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import StatusBadge from '@/features/build/StatusBadge'
import DeployDialog from '@/features/deploy/DeployDialog'
import PromoteDialog, {type PromoteChoice} from '@/features/deploy/PromoteDialog'
import ReleaseDialog from '@/features/release/ReleaseDialog'
import {showReleaseStarted} from '@/features/release/releaseToast'
import {cn} from '@/lib/utils'

/** What a cell shows when its tool couldn't be reached */
function Unavailable() {
  return <span className="text-sm text-muted-foreground" title="The tool couldn't be reached">Unavailable</span>
}

/** One app in one environment: its version with a green (healthy) or red dot and its ready / desired pods, or why there's none. */
function EnvironmentCell({summary, environment}: { summary: AppSummary, environment: string }) {
  if (!summary.app.deploy) return <span className="text-muted-foreground">-</span>
  if (summary.unavailable.includes('ENVIRONMENTS')) return <Unavailable/>

  const state = summary.deployments.find((d) => d.environment === environment)?.state
  if (!state?.version) return <span className="text-sm text-muted-foreground">Not deployed</span>
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('size-2 rounded-full', state.healthy ? 'bg-emerald-500' : 'bg-red-500')}/>
      <span className="font-mono text-sm">{state.version}</span>
      {/* ready / desired replicas; red when not all are ready */}
      <span className={cn('text-xs tabular-nums', state.healthy ? 'text-muted-foreground' : 'text-red-700 dark:text-red-300')}
            title="Ready / desired pods">
        {state.readyPods ?? 0}/{state.desiredPods ?? 0}
      </span>
    </span>
  )
}

/**
 * What an app can promote: each environment but the last where a version runs, with the environment after it.
 * e.g. sample-app 1.0.1 in dev only -> [{version: "1.0.1", fromEnvironment: "dev", toEnvironment: staging}]
 * Returns none when the app isn't deployed or the operate tool couldn't be reached.
 */
function promoteChoices(summary: AppSummary, environments: Environment[]): PromoteChoice[] {
  if (!summary.app.deploy || summary.unavailable.includes('ENVIRONMENTS')) return []
  return environments.slice(0, -1).flatMap((from, i) => {
    const version = summary.deployments.find((d) => d.environment === from.name)?.state.version
    return version ? [{version, fromEnvironment: from.name, toEnvironment: environments[i + 1]}] : []
  })
}

/**
 * Every app (/apps): latest build, latest version and what runs in each environment, from one request.
 * A row's menu runs a build, releases, deploys or promotes; clicking a row opens the app's page.
 */
function AppsPage() {
  const navigate = useNavigate()
  // null while loading
  const [summary, setSummary] = useState<{ environments: Environment[], apps: AppSummary[] } | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  // the app whose Release or Deploy dialog is open, or null when both are closed
  const [releasing, setReleasing] = useState<App | null>(null)
  const [deploying, setDeploying] = useState<App | null>(null)
  // the app and what it can promote, of the open Promote dialog, or null when it is closed
  const [promoting, setPromoting] = useState<{ appId: string, choices: PromoteChoice[] } | null>(null)
  // the build shown in the details modal, or null when it is closed
  const [selectedBuild, setSelectedBuild] = useState<{ appId: string, buildId: string } | null>(null)

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

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadSummary()
    setRefreshing(false)
  }

  // start a build and say so, with a button to open it
  async function handleRunBuild(app: App) {
    try {
      const buildId = await triggerBuild(app.id)
      if (buildId) {
        toast.success(`Started build #${buildId} of ${app.id}`, {
          action: {label: 'View build', onClick: () => setSelectedBuild({appId: app.id, buildId})},
        })
      } else {
        toast.info(`Build of ${app.id} queued, but the CI tool has not started it yet`)
      }
    } catch {
      toast.error(`Could not start a build of ${app.id}`)
    } finally {
      loadSummary()
    }
  }

  function handleReleased(app: App, version: string, buildId: string | null) {
    setReleasing(null)
    showReleaseStarted(version, buildId, (id) => setSelectedBuild({appId: app.id, buildId: id}))
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
              <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
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
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
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
                  <TableHead>Latest version</TableHead>
                  {summary.environments.map((e) => <TableHead key={e.name}>{e.name}</TableHead>)}
                  <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>
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
                      {s.unavailable.includes('BUILD') ? <Unavailable/> : s.latestBuild ? (
                        <span className="inline-flex items-center gap-2">
                          <span className="text-sm text-muted-foreground">#{s.latestBuild.id}</span>
                          <StatusBadge status={s.latestBuild.status}/>
                        </span>
                      ) : <span className="text-sm text-muted-foreground">No builds yet</span>}
                    </TableCell>
                    <TableCell>
                      {!s.app.release ? <span className="text-muted-foreground">-</span>
                        : s.unavailable.includes('VERSION') ? <Unavailable/>
                          : s.latestVersion ? <span className="font-mono text-sm">{s.latestVersion.version}</span>
                            : <span className="text-sm text-muted-foreground">Not released yet</span>}
                    </TableCell>
                    {summary.environments.map((e) => (
                      <TableCell key={e.name}><EnvironmentCell summary={s} environment={e.name}/></TableCell>
                    ))}
                    {/* the menu's clicks stay in the menu instead of opening the app */}
                    <TableCell onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${s.app.id}`}>
                            <MoreHorizontal/>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => handleRunBuild(s.app)}>
                            <Play/> Run build
                          </DropdownMenuItem>
                          <DropdownMenuItem disabled={!s.app.release} onSelect={() => setReleasing(s.app)}>
                            <Tag/> Release
                          </DropdownMenuItem>
                          <DropdownMenuItem disabled={!s.app.release || !s.app.deploy} onSelect={() => setDeploying(s.app)}>
                            <Rocket/> Deploy
                          </DropdownMenuItem>
                          {/* disabled when nothing runs in any environment but the last */}
                          {(() => {
                            const choices = promoteChoices(s, summary.environments)
                            return (
                              <DropdownMenuItem disabled={choices.length === 0}
                                                onSelect={() => setPromoting({appId: s.app.id, choices})}>
                                <ArrowUpRight/> Promote
                              </DropdownMenuItem>
                            )
                          })()}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {releasing && (
        <ReleaseDialog app={releasing} open onOpenChange={(open) => !open && setReleasing(null)}
                       onReleased={(version, buildId) => handleReleased(releasing, version, buildId)}/>
      )}
      {deploying && summary && (
        <DeployDialog appId={deploying.id} environments={summary.environments} open
                      onOpenChange={(open) => !open && setDeploying(null)} onDeployed={loadSummary}/>
      )}
      {promoting && (
        <PromoteDialog appId={promoting.appId} choices={promoting.choices}
                       open onOpenChange={(open) => !open && setPromoting(null)} onPromoted={loadSummary}/>
      )}
      <BuildDetailsDialog appId={selectedBuild?.appId ?? ''} buildId={selectedBuild?.buildId ?? null}
                          onClose={() => setSelectedBuild(null)}/>
    </div>
  )
}

export default AppsPage
