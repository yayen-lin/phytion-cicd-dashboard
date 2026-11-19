import { useCallback, useEffect, useState, type KeyboardEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { RefreshCw, Server } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { getEnvironments, type EnvironmentStatus } from '@/api/environments'
import { usePermissions } from '@/auth/AuthContext'
import CommitLink from '@/components/CommitLink'
import HealthBadge from '@/components/HealthBadge'
import AppActionsMenu from '@/features/actions/AppActionsMenu'
import { deployedLabel } from '@/lib/format'

/** One environment's page: the apps running there with their version, health and last deploy. */
function EnvironmentPage() {
  const { environment = '' } = useParams()
  const navigate = useNavigate()
  const { canAct } = usePermissions()
  // which environment was loaded, so switching environments shows the skeleton
  const [loaded, setLoaded] = useState<{ environment: string, status: EnvironmentStatus } | null>(null)
  const [error, setError] = useState<{ environment: string, message: string } | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const loadEnvironment = useCallback(() => {
    return getEnvironments({ environment })
      .then(([status]) => {
        setLoaded({ environment, status })
        setError(null)
      })
      .catch((e) => setError({
        environment,
        message: e instanceof Error ? e.message : 'Could not load the environment'
      }))
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
  // only the apps deployed here
  const runningApps = status?.apps.filter((app) => app.desiredPods !== null)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight"><Server
          className="size-6" />{environment}</h1>
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
          {shownError ? (
            <Alert variant="destructive">
              <AlertTitle>Environment unavailable</AlertTitle>
              <AlertDescription>{shownError}</AlertDescription>
            </Alert>
          ) : !runningApps ? (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full" />)}
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
                  {canAct && <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>}
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
                    <TableCell><HealthBadge app={app} /></TableCell>
                    {/* the link opens the code tool, not the app's page */}
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {app.commit && app.commitUrl ?
                        <CommitLink commit={{ hash: app.commit, url: app.commitUrl }} /> : '-'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {deployedLabel(app.deployedAt, app.deployedBy)}
                    </TableCell>
                    {/* the menu's clicks stay in the menu instead of opening the app */}
                    {canAct && <TableCell onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                      {status && (
                        <AppActionsMenu appId={app.appId}
                                        environments={[{
                                          environment: {
                                            name: environment,
                                            needsConfirmation: status.needsConfirmation,
                                            needsAdmin: status.needsAdmin
                                          },
                                          version: app.version,
                                          nextEnvironment: status.nextEnvironment,
                                        }]}
                                        onChanged={loadEnvironment} />
                      )}
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

export default EnvironmentPage
