import {useCallback, useEffect, useState} from 'react'
import {toast} from 'sonner'
import {ArrowUpRight, MoreHorizontal, Play, RefreshCw, Rocket, Tag, Undo2} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import type {App} from '@/api/apps'
import {triggerBuild} from '@/api/ci'
import {getEnvironments, type EnvironmentApp} from '@/api/deploy'
import type {Environment} from '@/api/environments'
import CommitLink from '@/features/code/CommitLink'
import HealthBadge from '@/features/environments/HealthBadge'
import {deployedLabel} from '@/features/deploy/deployedLabel'
import DeployDialog from '@/features/deploy/DeployDialog'
import PromoteDialog from '@/features/deploy/PromoteDialog'
import RollbackDialog from '@/features/deploy/RollbackDialog'

type Props = {
  /** An app that is deployed */
  app: App
  /** Opens the Release dialog */
  onReleaseClick: () => void
  /** Opens a build's details */
  onViewBuild: (buildId: string) => void
}

/**
 * One environment's row: the app in it, whether a deploy there must be confirmed, and the environment a promote goes
 * to (null for the last one)
 */
type Row = {
  environment: Environment
  nextEnvironment: Environment | null
  app: EnvironmentApp
}

/**
 * Where one app runs: one row per environment with its version, health, and who deployed it and when. A row's menu
 * runs a build, releases, deploys to that environment, promotes the running version to the next one, or rolls it back.
 */
function AppEnvironmentsTable({app: configuredApp, onReleaseClick, onViewBuild}: Props) {
  const appId = configuredApp.id
  // null while loading
  const [rows, setRows] = useState<Row[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  // the row whose Promote dialog is open, or null when it is closed
  const [promotingRow, setPromotingRow] = useState<Row | null>(null)
  // the environment whose Deploy or Rollback dialog is open, or null when it is closed
  const [deployingTo, setDeployingTo] = useState<Environment | null>(null)
  const [rollingBack, setRollingBack] = useState<Environment | null>(null)

  const loadEnvironments = useCallback(() => {
    return getEnvironments({appId})
      .then((environments) => {
        // one row per environment: the app is the only one in each
        setRows(environments
          .map((e) => ({
            environment: {name: e.environment, needsConfirmation: e.needsConfirmation},
            nextEnvironment: e.nextEnvironment,
            app: e.apps[0],
          }))
          .filter((r) => r.app))
        setError('')
      })
      .catch(() => setError('Could not load the environments from the operate tool'))
  }, [appId])

  useEffect(() => {
    loadEnvironments()
  }, [loadEnvironments])

  // start a build and say so, with a button to open it
  async function handleRunBuild() {
    try {
      const buildId = await triggerBuild(appId)
      if (buildId) {
        toast.success(`Started build #${buildId} of ${appId}`, {
          action: {label: 'View build', onClick: () => onViewBuild(buildId)},
        })
      } else {
        toast.info(`Build of ${appId} queued, but the CI tool has not started it yet`)
      }
    } catch {
      toast.error(`Could not start a build of ${appId}`)
    }
  }

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadEnvironments()
    setRefreshing(false)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Environments</CardTitle>
        <CardDescription>The version running in each environment</CardDescription>
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
            <AlertTitle>Environments unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !rows ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Environment</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Health</TableHead>
                <TableHead>Commit</TableHead>
                <TableHead>Deployed</TableHead>
                <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const {environment, nextEnvironment, app} = row
                return (
                  <TableRow key={environment.name}>
                    <TableCell className="font-medium">{environment.name}</TableCell>
                    {app.version === null ? (
                      <TableCell colSpan={4} className="text-muted-foreground">Not deployed</TableCell>
                    ) : (
                      <>
                        <TableCell className="font-mono text-sm">{app.version}</TableCell>
                        <TableCell><HealthBadge app={app}/></TableCell>
                        <TableCell>
                          {app.commit && app.commitUrl ? <CommitLink commit={{hash: app.commit, url: app.commitUrl}}/> : '-'}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {deployedLabel(app.deployedAt, app.deployedBy)}
                        </TableCell>
                      </>
                    )}
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8"
                                  aria-label={`Actions for ${appId} in ${environment.name}`}>
                            <MoreHorizontal/>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={handleRunBuild}>
                            <Play/> Run build
                          </DropdownMenuItem>
                          <DropdownMenuItem disabled={!configuredApp.release} onSelect={onReleaseClick}>
                            <Tag/> Release
                          </DropdownMenuItem>
                          <DropdownMenuItem disabled={!configuredApp.release} onSelect={() => setDeployingTo(environment)}>
                            <Rocket/> Deploy
                          </DropdownMenuItem>
                          {/* no promote from the last environment, or of a version that isn't there or can't be told */}
                          {nextEnvironment && (
                            <DropdownMenuItem disabled={!app.version} onSelect={() => setPromotingRow(row)}>
                              <ArrowUpRight/> Promote
                            </DropdownMenuItem>
                          )}
                          {/* nothing to roll back where the app doesn't run */}
                          <DropdownMenuItem disabled={!app.version} onSelect={() => setRollingBack(environment)}>
                            <Undo2/> Rollback
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>

      {promotingRow?.app.version && promotingRow.nextEnvironment && (
        <PromoteDialog appId={appId} choices={[{
          version: promotingRow.app.version,
          fromEnvironment: promotingRow.environment.name,
          toEnvironment: promotingRow.nextEnvironment,
        }]}
                       open onOpenChange={(open) => !open && setPromotingRow(null)} onPromoted={loadEnvironments}/>
      )}

      {deployingTo && (
        <DeployDialog appId={appId} pickedEnvironment={deployingTo.name} environments={[deployingTo]}
                      open onOpenChange={(open) => !open && setDeployingTo(null)} onDeployed={loadEnvironments}/>
      )}

      {rollingBack && (
        <RollbackDialog appId={appId} environments={[rollingBack]}
                        open onOpenChange={(open) => !open && setRollingBack(null)} onRolledBack={loadEnvironments}/>
      )}
    </Card>
  )
}

export default AppEnvironmentsTable
