import {useCallback, useEffect, useState} from 'react'
import {toast} from 'sonner'
import {ExternalLink, GitBranch, Loader2, Play} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getBuilds, triggerBuild, type CiBuilds} from './api'
import StatusBadge from './StatusBadge'

type Props = {
  /** e.g. "jenkins:sample-app" */
  pipeline: string
}

function formatDuration(millis: number | null): string {
  if (millis === null) return 'running...'
  const seconds = Math.round(millis / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

/** The builds of a pipeline, newest first, with a button to start a new one. */
function BuildsTable({pipeline}: Props) {
  // null while loading
  const [data, setData] = useState<CiBuilds | null>(null)
  const [error, setError] = useState('')
  const [triggering, setTriggering] = useState(false)

  const loadBuilds = useCallback(() => {
    getBuilds(pipeline)
      .then((builds) => {
        setData(builds)
        setError('')
      })
      .catch(() => setError('Could not load builds from the CI tool'))
  }, [pipeline])

  useEffect(loadBuilds, [loadBuilds])

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
        <CardAction>
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
                <TableRow key={build.id}>
                  <TableCell>
                    <a href={build.url} target="_blank" rel="noreferrer"
                       className="inline-flex items-center gap-1 font-medium hover:underline">
                      #{build.id}
                      <ExternalLink className="size-3 text-muted-foreground"/>
                    </a>
                  </TableCell>
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
    </Card>
  )
}

export default BuildsTable
