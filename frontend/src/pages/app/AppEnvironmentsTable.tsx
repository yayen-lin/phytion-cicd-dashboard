import { useCallback, useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import type { App } from '@/api/apps'
import { getEnvironments, type EnvironmentApp, type Environment } from '@/api/environments'
import { usePermissions } from '@/auth/AuthContext'
import CommitLink from '@/components/CommitLink'
import HealthBadge from '@/components/HealthBadge'
import AppActionsMenu from '@/features/actions/AppActionsMenu'
import { deployedLabel } from '@/lib/format'

type Props = {
  /** An app that is deployed */
  app: App
}

type Row = {
  environment: Environment
  nextEnvironment: Environment | null
  app: EnvironmentApp
}

/** Where one app runs: one row per environment with its version, health and last deploy. */
function AppEnvironmentsTable({ app: configuredApp }: Props) {
  const appId = configuredApp.id
  const { canAct } = usePermissions()
  // null while loading
  const [rows, setRows] = useState<Row[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const loadEnvironments = useCallback(() => {
    return getEnvironments({ appId })
      .then((environments) => {
        // one row per environment where the app is deployable
        setRows(environments
          .map((e) => ({
            environment: { name: e.environment, needsConfirmation: e.needsConfirmation, needsAdmin: e.needsAdmin },
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
            <AlertTitle>Environments unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !rows ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full" />)}
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
                {canAct && <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const { environment, nextEnvironment, app } = row
                return (
                  <TableRow key={environment.name}>
                    <TableCell className="font-medium">{environment.name}</TableCell>
                    {app.version === null ? (
                      <TableCell colSpan={4} className="text-muted-foreground">Not deployed</TableCell>
                    ) : (
                      <>
                        <TableCell className="font-mono text-sm">{app.version}</TableCell>
                        <TableCell><HealthBadge app={app} /></TableCell>
                        <TableCell>
                          {app.commit && app.commitUrl ?
                            <CommitLink commit={{ hash: app.commit, url: app.commitUrl }} /> : '-'}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {deployedLabel(app.deployedAt, app.deployedBy)}
                        </TableCell>
                      </>
                    )}
                    {canAct && <TableCell>
                      <AppActionsMenu appId={appId} app={configuredApp}
                                      environments={[{ environment, version: app.version, nextEnvironment }]}
                                      onChanged={loadEnvironments} />
                    </TableCell>}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

export default AppEnvironmentsTable
