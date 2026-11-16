import {useCallback, useEffect, useState} from 'react'
import {RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getEnvironments, type EnvironmentApp} from '@/api/deploy'
import CommitLink from '@/features/code/CommitLink'
import HealthBadge from '@/features/environments/HealthBadge'
import {deployedLabel} from '@/features/deploy/deployedLabel'

type Props = {
  /** An app that is deployed, e.g. "sample-app" */
  appId: string
}

/** Where one app runs: one row per environment with its version, health, and who deployed it and when. */
function AppEnvironmentsTable({appId}: Props) {
  // null while loading
  const [rows, setRows] = useState<{ environment: string, app: EnvironmentApp }[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const loadEnvironments = useCallback(() => {
    return getEnvironments({appId})
      .then((environments) => {
        // one row per environment: the app is the only one in each
        setRows(environments.map((e) => ({environment: e.environment, app: e.apps[0]})).filter((r) => r.app))
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
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map(({environment, app}) => (
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

export default AppEnvironmentsTable
