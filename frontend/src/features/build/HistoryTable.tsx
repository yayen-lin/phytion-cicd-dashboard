import {useCallback, useEffect, useState} from 'react'
import {RefreshCw} from 'lucide-react'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getAuditLog, type AuditEntry} from '@/api/audit'
import {getBuilds, type CiBuild} from '@/api/ci'
import StatusBadge from './StatusBadge'

type Props = {
  /** Only actions on this pipeline, e.g. "jenkins:sample-app" */
  pipeline: string
}

/** "TRIGGER_BUILD" -> "Trigger build" */
function formatActionType(actionType: string): string {
  const words = actionType.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** The actions taken on a pipeline through the dashboard, newest first: who did what, and when. */
function HistoryTable({pipeline}: Props) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  const [error, setError] = useState('')
  // The CI builds, to show the status of the build each entry started
  const [builds, setBuilds] = useState<CiBuild[]>([])
  const [refreshing, setRefreshing] = useState(false)

  const loadHistory = useCallback(() => {
    return Promise.all([
      getAuditLog(pipeline)
        .then((log) => {
          setEntries(log)
          setError('')
        })
        .catch(() => setError('Could not load the audit log')),
      // Only for the build status column: if the CI tool is down, that column shows "-"
      getBuilds(pipeline)
        .then((data) => setBuilds(data.builds))
        .catch(() => setBuilds([])),
    ])
  }, [pipeline])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadHistory()
    setRefreshing(false)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">History</CardTitle>
        <CardDescription>Actions taken on this pipeline through the dashboard: who did what, and when</CardDescription>
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
            <AlertTitle>History unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !entries ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : entries.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No actions yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>When</TableHead>
                <TableHead>Who</TableHead>
                <TableHead>What</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Build</TableHead>
                <TableHead>Build status</TableHead>
                <TableHead>Action result</TableHead>
                <TableHead>Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((entry) => {
                const build = builds.find((b) => b.id === entry.buildId)
                return (
                  <TableRow key={entry.id}>
                    <TableCell className="text-muted-foreground">{new Date(entry.createdAt).toLocaleString()}</TableCell>
                    <TableCell className="font-medium">{entry.username}</TableCell>
                    <TableCell>{formatActionType(entry.actionType)}</TableCell>
                    <TableCell className="font-mono text-xs">{entry.target}</TableCell>
                    <TableCell>{entry.buildId ? `#${entry.buildId}` : '-'}</TableCell>
                    <TableCell>{build ? <StatusBadge status={build.status}/> : '-'}</TableCell>
                    <TableCell>
                      <Badge variant={entry.actionResult === 'SUCCESS' ? 'outline' : 'destructive'}>
                        {entry.actionResult === 'SUCCESS' ? 'Done' : 'Failed'}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-xs truncate text-muted-foreground" title={entry.details ?? undefined}>
                      {entry.details ?? ''}
                    </TableCell>
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

export default HistoryTable
