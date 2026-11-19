import { useCallback, useEffect, useState } from 'react'
import { ExternalLink, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { getDeployHistory, type DeployHistory } from '@/api/deploy'
import StatusBadge from '@/components/StatusBadge'
import CommitLink from '@/components/CommitLink'
import { formatActionType, formatDuration } from '@/lib/format'
import { usePollWhileRunning } from '@/hooks/use-poll-while-running'

// the Select can't have an empty value
const ALL = 'all'

type Props = {
  /** An app that is deployed */
  appId: string
}

/** An app's recent deploys, newest first, with an environment filter; reloads while a deploy runs. */
function DeploysTable({ appId }: Props) {
  // null while loading
  const [history, setHistory] = useState<DeployHistory | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [environment, setEnvironment] = useState(ALL)

  const loadDeploys = useCallback(() => {
    return getDeployHistory(appId)
      .then((data) => {
        setHistory(data)
        setError('')
      })
      .catch(() => setError('Could not load the deploys from the CI tool'))
  }, [appId])

  useEffect(() => {
    loadDeploys()
  }, [loadDeploys])

  // load again while a deploy runs
  usePollWhileRunning(history, !!history?.deploys.some((deploy) => deploy.run.build.status === 'RUNNING'), loadDeploys)

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadDeploys()
    setRefreshing(false)
  }

  const deploys = history?.deploys.filter((deploy) => environment === ALL || deploy.run.environment === environment)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Deploys</CardTitle>
        <CardAction className="flex gap-2">
          <Select value={environment} onValueChange={setEnvironment}>
            <SelectTrigger className="w-44" aria-label="Environment">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All environments</SelectItem>
              {history?.environments.map((name) => <SelectItem key={name} value={name}>{name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'animate-spin' : undefined} />
            Refresh
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-4">
        {history?.unavailable.includes('COMMITS') && (
          <Alert>
            <AlertTitle>Commits unavailable</AlertTitle>
            <AlertDescription>The registry couldn't be reached, so the deploys show without their
              commits.</AlertDescription>
          </Alert>
        )}

        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Deploys unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !deploys ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full" />)}
          </div>
        ) : deploys.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {environment === ALL ? 'No deploys yet.' : `No deploys to ${environment} among the recent deploys.`}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Build</TableHead>
                <TableHead>Started</TableHead>
                <TableHead>Environment</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Commit</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>By</TableHead>
                <TableHead className="text-right">Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {deploys.map(({ run, commit, commitUrl }) => (
                <TableRow key={run.build.id}>
                  <TableCell>
                    <a href={run.build.url} target="_blank" rel="noreferrer"
                       className="inline-flex items-center gap-1 font-medium underline-offset-4 hover:underline">
                      #{run.build.id} <ExternalLink className="size-3.5" />
                    </a>
                  </TableCell>
                  <TableCell
                    className="text-muted-foreground">{new Date(run.build.startedAt).toLocaleString()}</TableCell>
                  <TableCell className="font-medium">{run.environment}</TableCell>
                  <TableCell className="font-mono text-sm">{run.version}</TableCell>
                  <TableCell>{commit && commitUrl ?
                    <CommitLink commit={{ hash: commit, url: commitUrl }} /> : '-'}</TableCell>
                  <TableCell>{formatActionType(run.action)}</TableCell>
                  <TableCell><StatusBadge status={run.build.status} /></TableCell>
                  <TableCell>{run.deployedBy}</TableCell>
                  <TableCell className="text-right tabular-nums text-muted-foreground">
                    {formatDuration(run.build.durationMillis)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

export default DeploysTable
