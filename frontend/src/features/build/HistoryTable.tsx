import {useCallback, useEffect, useState} from 'react'
import {RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getAuditLog, type AuditEntry} from '@/api/audit'
import {getBuilds, type CiBuild} from '@/api/ci'
import AuditTable from '@/features/history/AuditTable'
import BuildDetailsDialog from './BuildDetailsDialog'

type Props = {
  /** Only builds started on this app, e.g. "sample-app" */
  appId: string
}

/** The builds started on one app's pipeline through the dashboard, newest first: who did it, and when. */
function HistoryTable({appId}: Props) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  const [error, setError] = useState('')
  // The CI builds, to show the status of the build each entry started
  const [builds, setBuilds] = useState<CiBuild[]>([])
  const [refreshing, setRefreshing] = useState(false)
  // The build shown in the details modal, or null when it is closed
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const loadHistory = useCallback(() => {
    return Promise.all([
      // Only "Run build": releases also start builds, but they show on the Release stage
      getAuditLog({target: appId, actionType: 'TRIGGER_BUILD'})
        .then((log) => {
          setEntries(log)
          setError('')
        })
        .catch(() => setError('Could not load the audit log')),
      // Only for the build status column: if the CI tool is down, that column shows "-"
      getBuilds(appId)
        .then((data) => setBuilds(data.builds))
        .catch(() => setBuilds([])),
    ])
  }, [appId])

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
          <AuditTable
            entries={entries}
            showTarget={false}
            buildOf={(entry) => builds.find((b) => b.id === entry.buildId)}
            onOpenBuild={(entry) => setSelectedId(entry.buildId)}
          />
        )}
      </CardContent>
      <BuildDetailsDialog appId={appId} buildId={selectedId} onClose={() => setSelectedId(null)}/>
    </Card>
  )
}

export default HistoryTable
