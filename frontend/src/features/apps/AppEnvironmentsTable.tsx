import {useCallback, useEffect, useState} from 'react'
import {ArrowUpRight, MoreHorizontal, RefreshCw} from 'lucide-react'
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
import {getEnvironments, type EnvironmentApp} from '@/api/deploy'
import type {Environment} from '@/api/environments'
import CommitLink from '@/features/code/CommitLink'
import HealthBadge from '@/features/environments/HealthBadge'
import {deployedLabel} from '@/features/deploy/deployedLabel'
import PromoteDialog from '@/features/deploy/PromoteDialog'

type Props = {
  /** An app that is deployed, e.g. "sample-app" */
  appId: string
}

/** One environment's row: the app in it, and the environment a promote goes to (null for the last one) */
type Row = {
  environment: string
  nextEnvironment: Environment | null
  app: EnvironmentApp
}

/**
 * Where one app runs: one row per environment with its version, health, and who deployed it and when. A row's menu
 * promotes the running version to the next environment.
 */
function AppEnvironmentsTable({appId}: Props) {
  // null while loading
  const [rows, setRows] = useState<Row[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  // the row whose Promote dialog is open, or null when it is closed
  const [promotingRow, setPromotingRow] = useState<Row | null>(null)

  const loadEnvironments = useCallback(() => {
    return getEnvironments({appId})
      .then((environments) => {
        // one row per environment: the app is the only one in each
        setRows(environments
          .map((e) => ({environment: e.environment, nextEnvironment: e.nextEnvironment, app: e.apps[0]}))
          .filter((r) => r.app))
        setError('')
      })
      .catch(() => setError('Could not load the environments from the operate tool'))
  }, [appId])

  useEffect(() => {
    loadEnvironments()
  }, [loadEnvironments])

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
                  <TableRow key={environment}>
                    <TableCell className="font-medium">{environment}</TableCell>
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
                    {/* no promote from the last environment, or of a version that isn't there or can't be told */}
                    <TableCell>
                      {nextEnvironment && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="size-8"
                                    aria-label={`Actions for ${appId} in ${environment}`}>
                              <MoreHorizontal/>
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem disabled={!app.version} onSelect={() => setPromotingRow(row)}>
                              <ArrowUpRight/> Promote
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
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
          fromEnvironment: promotingRow.environment,
          toEnvironment: promotingRow.nextEnvironment,
        }]}
                       open onOpenChange={(open) => !open && setPromotingRow(null)} onPromoted={loadEnvironments}/>
      )}
    </Card>
  )
}

export default AppEnvironmentsTable
