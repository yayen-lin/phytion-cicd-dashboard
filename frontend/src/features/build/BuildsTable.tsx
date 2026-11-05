import {useCallback, useEffect, useState} from 'react'
import {toast} from 'sonner'
import {GitBranch, Loader2, Play, RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getBuilds, triggerBuild, type CiBuilds} from '@/api/ci'
import StatusBadge from './StatusBadge'
import BuildDetailsDialog from './BuildDetailsDialog'
import {formatDuration} from '@/lib/format'

type Props = {
  /** e.g. "jenkins:sample-app" */
  pipeline: string
}

/** The builds of a pipeline, newest first, with a button to start a new one. */
function BuildsTable({pipeline}: Props) {
  // null while loading
  const [data, setData] = useState<CiBuilds | null>(null)
  const [error, setError] = useState('')
  const [triggering, setTriggering] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  // The id of the build shown in the details modal, or null when it is closed
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const loadBuilds = useCallback(() => {
    return getBuilds(pipeline)
      .then((builds) => {
        setData(builds)
        setError('')
      })
      .catch(() => setError('Could not load builds from the CI tool'))
  }, [pipeline])

  useEffect(() => {
    loadBuilds()
  }, [loadBuilds])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadBuilds()
    setRefreshing(false)
  }

  async function handleRunBuild() {
    setTriggering(true)
    try {
      const buildId = await triggerBuild(pipeline)
      if (buildId) toast.success(`Started build #${buildId}`)
      else toast.info('Build queued, but the CI tool has not started it yet')
    } catch {
      toast.error('Could not start the build')
    } finally {
      setTriggering(false)
      loadBuilds()
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Builds</CardTitle>
        <CardDescription>Recent builds, newest first</CardDescription>
        <CardAction className="flex gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
            Refresh
          </Button>
          <Button onClick={handleRunBuild} disabled={triggering || !data}>
            {triggering ? <Loader2 className="animate-spin"/> : <Play/>}
            {triggering ? 'Starting...' : 'Run build'}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Builds unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !data ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : data.builds.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No builds yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Commit</TableHead>
                <TableHead>Branch</TableHead>
                <TableHead>Started</TableHead>
                <TableHead className="text-right">Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.builds.map((build) => (
                <TableRow
                  key={build.id}
                  className="cursor-pointer"
                  tabIndex={0}
                  aria-label={`Build #${build.id} details`}
                  onClick={() => setSelectedId(build.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      setSelectedId(build.id)
                    }
                  }}
                >
                  <TableCell className="font-medium">#{build.id}</TableCell>
                  <TableCell><StatusBadge status={build.status}/></TableCell>
                  <TableCell>
                    <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs" title={build.commit ?? undefined}>
                      {build.commit?.slice(0, 7) ?? '-'}
                    </code>
                  </TableCell>
                  <TableCell>
                    {build.branch ? (
                      <span className="inline-flex items-center gap-1 text-muted-foreground">
                        <GitBranch className="size-3.5"/>{build.branch}
                      </span>
                    ) : '-'}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{new Date(build.startedAt).toLocaleString()}</TableCell>
                  <TableCell className="text-right tabular-nums text-muted-foreground">
                    {formatDuration(build.durationMillis)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
      <BuildDetailsDialog pipeline={pipeline} buildId={selectedId} onClose={() => setSelectedId(null)}/>
    </Card>
  )
}

export default BuildsTable
