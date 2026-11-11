import {useEffect, useState, type ReactNode} from 'react'
import {Check, Copy, ExternalLink, GitBranch, ScrollText} from 'lucide-react'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {Skeleton} from '@/components/ui/skeleton'
import {getBuild, type CiBuildDetails} from '@/api/ci'
import StatusBadge from './StatusBadge'
import TestResults from './TestResults'
import {formatDuration} from '@/lib/format'

type Props = {
  /** e.g. "jenkins:sample-app" */
  pipeline: string
  /** The build to show; null keeps the dialog closed */
  buildId: string | null
  onClose: () => void
}

function Field({label, children}: { label: string, children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] items-center gap-2 py-2">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm">{children}</dd>
    </div>
  )
}

/** The full commit hash, with a button that copies it. */
function CommitHash({commit}: { commit: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(commit)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <span className="flex items-center gap-1">
      <code className="truncate rounded bg-muted px-1.5 py-0.5 font-mono text-xs">{commit}</code>
      <Button variant="ghost" size="icon" className="size-7 shrink-0" onClick={copy} aria-label="Copy commit hash">
        {copied ? <Check/> : <Copy/>}
      </Button>
    </span>
  )
}

/** Loads one build from the backend and shows it. Mounted fresh for each build the dialog opens. */
function BuildDetails({pipeline, buildId}: { pipeline: string, buildId: string }) {
  const [details, setDetails] = useState<CiBuildDetails | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getBuild(pipeline, buildId)
      .then(setDetails)
      .catch(() => setError('Could not load the build from the CI tool'))
  }, [pipeline, buildId])

  const build = details?.build

  return (
    <>
      <DialogHeader>
        <div className="flex items-center gap-3 pr-8">
          <DialogTitle className="text-lg">Build #{buildId}</DialogTitle>
          {build && <StatusBadge status={build.status}/>}
        </div>
        <DialogDescription className="font-mono">{pipeline}</DialogDescription>
      </DialogHeader>

      {error ? (
        <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>
      ) : !details || !build ? (
        <div className="space-y-3 py-2">
          {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-6 w-full"/>)}
        </div>
      ) : (
        <>
          <dl className="divide-y">
            <Field label="Commit">
              {build.commit ? <CommitHash commit={build.commit}/> : '-'}
            </Field>
            <Field label="Branch">
              {build.branch ? (
                <span className="inline-flex items-center gap-1"><GitBranch className="size-3.5"/>{build.branch}</span>
              ) : '-'}
            </Field>
            <Field label="Started">{new Date(build.startedAt).toLocaleString()}</Field>
            <Field label="Duration"><span className="tabular-nums">{formatDuration(build.durationMillis)}</span></Field>
            <Field label="Started by">
              {details.startedBy
                ? (
                  <span className="inline-flex items-center gap-2">
                    <span className="font-medium">{details.startedBy}</span>
                    {details.startedBySource && <Badge variant="outline">via {details.startedBySource}</Badge>}
                  </span>
                )
                : <span className="text-muted-foreground">No user (e.g. a push or a timer)</span>}
            </Field>
          </dl>

          <section className="space-y-3 border-t pt-4">
            <h3 className="text-sm font-medium">Tests</h3>
            {build.tests
              ? <TestResults pipeline={pipeline} buildId={buildId}/>
              : <p className="text-sm text-muted-foreground">
                  {build.status === 'RUNNING' ? 'Running...' : 'No test results (the build stopped before the tests).'}
                </p>}
          </section>

          <DialogFooter>
            <Button variant="outline" asChild>
              <a href={build.url} target="_blank" rel="noreferrer"><ExternalLink/> Open in Jenkins</a>
            </Button>
            <Button asChild>
              {/* Jenkins' log page for the build */}
              <a href={`${build.url.replace(/\/?$/, '/')}console`} target="_blank" rel="noreferrer"><ScrollText/> View log</a>
            </Button>
          </DialogFooter>
        </>
      )}
    </>
  )
}

/** A build's details and, below them, its test results, in a modal with links to its page and log in the CI tool. */
function BuildDetailsDialog({pipeline, buildId, onClose}: Props) {
  return (
    <Dialog open={buildId !== null} onOpenChange={(open) => !open && onClose()}>
      {buildId && (
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
          <BuildDetails key={buildId} pipeline={pipeline} buildId={buildId}/>
        </DialogContent>
      )}
    </Dialog>
  )
}

export default BuildDetailsDialog
