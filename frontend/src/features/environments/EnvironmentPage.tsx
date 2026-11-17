import {useCallback, useEffect, useState, type KeyboardEvent} from 'react'
import {useNavigate, useParams} from 'react-router'
import {toast} from 'sonner'
import {ArrowUpRight, MoreHorizontal, Play, RefreshCw, Rocket, Server, Tag, Undo2} from 'lucide-react'
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
import {getEnvironments, type EnvironmentApp, type EnvironmentStatus} from '@/api/deploy'
import {getApp, type App} from '@/api/apps'
import {triggerBuild} from '@/api/ci'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import CommitLink from '@/features/code/CommitLink'
import DeployDialog from '@/features/deploy/DeployDialog'
import PromoteDialog from '@/features/deploy/PromoteDialog'
import RollbackDialog from '@/features/deploy/RollbackDialog'
import ReleaseDialog from '@/features/release/ReleaseDialog'
import {showReleaseStarted} from '@/features/release/releaseToast'
import HealthBadge from './HealthBadge'
import {deployedLabel} from '@/features/deploy/deployedLabel'

/**
 * One environment's page (/environments/staging), from one request: one row per app running there with its version,
 * health, and who deployed it and when. A row's menu runs a build, releases, deploys another version, promotes the
 * running one to the next environment, or rolls it back one good version; clicking a row opens the app's page.
 */
function EnvironmentPage() {
  const {environment = ''} = useParams()
  const navigate = useNavigate()
  // which environment the status is for, so switching environments shows the skeleton instead of the old one's apps
  const [loaded, setLoaded] = useState<{ environment: string, status: EnvironmentStatus } | null>(null)
  const [error, setError] = useState<{ environment: string, message: string } | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  // the app whose Release dialog is open, or null when it is closed
  const [releasing, setReleasing] = useState<App | null>(null)
  // the app whose Deploy dialog is open, or null when it is closed
  const [deployingAppId, setDeployingAppId] = useState<string | null>(null)
  // the app whose Promote dialog is open, or null when it is closed
  const [promotingApp, setPromotingApp] = useState<EnvironmentApp | null>(null)
  // the app whose Rollback dialog is open, or null when it is closed
  const [rollingBackAppId, setRollingBackAppId] = useState<string | null>(null)
  // the build shown in the details modal, or null when it is closed
  const [selectedBuild, setSelectedBuild] = useState<{ appId: string, buildId: string } | null>(null)

  const loadEnvironment = useCallback(() => {
    return getEnvironments({environment})
      .then(([status]) => {
        setLoaded({environment, status})
        setError(null)
      })
      .catch((e) => setError({environment, message: e instanceof Error ? e.message : 'Could not load the environment'}))
  }, [environment])

  useEffect(() => {
    loadEnvironment()
  }, [loadEnvironment])

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadEnvironment()
    setRefreshing(false)
  }

  // start a build and say so, with a button to open it
  async function handleRunBuild(appId: string) {
    try {
      const buildId = await triggerBuild(appId)
      if (buildId) {
        toast.success(`Started build #${buildId} of ${appId}`, {
          action: {label: 'View build', onClick: () => setSelectedBuild({appId, buildId})},
        })
      } else {
        toast.info(`Build of ${appId} queued, but the CI tool has not started it yet`)
      }
    } catch {
      toast.error(`Could not start a build of ${appId}`)
    }
  }

  // the Release dialog needs the app's config, so read it first
  async function openRelease(appId: string) {
    try {
      const app = await getApp(appId)
      if (app) setReleasing(app)
      else toast.error(`Unknown app: ${appId}`)
    } catch {
      toast.error(`Could not load ${appId}`)
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

  const status = loaded?.environment === environment ? loaded.status : null
  const shownError = error?.environment === environment ? error.message : ''
  // only the apps running here: an app without a workload isn't deployed here
  const runningApps = status?.apps.filter((app) => app.desiredPods !== null)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight"><Server className="size-6"/>{environment}</h1>
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
          {shownError ? (
            <Alert variant="destructive">
              <AlertTitle>Environment unavailable</AlertTitle>
              <AlertDescription>{shownError}</AlertDescription>
            </Alert>
          ) : !runningApps ? (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
            </div>
          ) : runningApps.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No apps running in {environment}. Deploy one from the Apps page.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>App</TableHead>
                  <TableHead>Version</TableHead>
                  <TableHead>Health</TableHead>
                  <TableHead>Commit</TableHead>
                  <TableHead>Deployed</TableHead>
                  <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runningApps.map((app) => (
                  <TableRow key={app.appId} className="cursor-pointer" tabIndex={0}
                            aria-label={`Open ${app.appId}`}
                            onClick={() => openApp(app.appId)}
                            onKeyDown={(e) => handleRowKeyDown(e, app.appId)}>
                    <TableCell className="font-medium">{app.appId}</TableCell>
                    <TableCell className="font-mono text-sm">{app.version ?? '-'}</TableCell>
                    <TableCell><HealthBadge app={app}/></TableCell>
                    {/* the link opens the code tool, not the app's page */}
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {app.commit && app.commitUrl ? <CommitLink commit={{hash: app.commit, url: app.commitUrl}}/> : '-'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {deployedLabel(app.deployedAt, app.deployedBy)}
                    </TableCell>
                    {/* the menu's clicks stay in the menu instead of opening the app */}
                    <TableCell onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${app.appId}`}>
                            <MoreHorizontal/>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => handleRunBuild(app.appId)}>
                            <Play/> Run build
                          </DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => openRelease(app.appId)}>
                            <Tag/> Release
                          </DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => setDeployingAppId(app.appId)}>
                            <Rocket/> Deploy
                          </DropdownMenuItem>
                          {/* no promote from the last environment, or of a version that can't be told */}
                          {status?.nextEnvironment && (
                            <DropdownMenuItem disabled={!app.version} onSelect={() => setPromotingApp(app)}>
                              <ArrowUpRight/> Promote
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem onSelect={() => setRollingBackAppId(app.appId)}>
                            <Undo2/> Rollback
                          </DropdownMenuItem>
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

      {deployingAppId && status && (
        <DeployDialog appId={deployingAppId} pickedEnvironment={environment}
                      environments={[{name: environment, needsConfirmation: status.needsConfirmation}]}
                      open onOpenChange={(open) => !open && setDeployingAppId(null)} onDeployed={loadEnvironment}/>
      )}

      {promotingApp?.version && status?.nextEnvironment && (
        <PromoteDialog appId={promotingApp.appId}
                       choices={[{version: promotingApp.version, fromEnvironment: environment, toEnvironment: status.nextEnvironment}]}
                       open onOpenChange={(open) => !open && setPromotingApp(null)} onPromoted={loadEnvironment}/>
      )}

      {rollingBackAppId && status && (
        <RollbackDialog appId={rollingBackAppId}
                        environments={[{name: environment, needsConfirmation: status.needsConfirmation}]}
                        open onOpenChange={(open) => !open && setRollingBackAppId(null)} onRolledBack={loadEnvironment}/>
      )}

      <BuildDetailsDialog appId={selectedBuild?.appId ?? ''} buildId={selectedBuild?.buildId ?? null}
                          onClose={() => setSelectedBuild(null)}/>
    </div>
  )
}

export default EnvironmentPage
