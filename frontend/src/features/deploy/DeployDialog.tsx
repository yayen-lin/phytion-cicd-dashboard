import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react'
import {ExternalLink, Loader2, Rocket} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import type {CiBuild} from '@/api/ci'
import {deploy, getDeployBuild} from '@/api/deploy'
import type {Environment} from '@/api/environments'
import {getVersions, type ReleaseVersion} from '@/api/release'
import {usePollWhileRunning} from '@/hooks/use-poll-while-running'
import StatusBadge from '@/features/build/StatusBadge'

type Props = {
  /** An app that is released and deployed, e.g. "sample-app" */
  appId: string
  /** The environments to pick from, in order */
  environments: Environment[]
  /** The environment already picked (e.g. opened from that environment's page), or null to pick one */
  pickedEnvironment?: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
  /** After the deploy job was started, and again when its build finished, e.g. to reload what runs where */
  onDeployed?: () => void
}

/** A started deploy: which version went where, and the deploy build (null id while queued) */
export type StartedDeploy = {
  version: string
  environment: string
  buildId: string | null
}

/** The form: a version from the registry, an environment, and the environment's name typed when it needs confirmation. */
function DeployForm({appId, environments, pickedEnvironment, onStarted}: Pick<Props, 'appId' | 'environments' | 'pickedEnvironment'> & {
  onStarted: (started: StartedDeploy) => void
}) {
  // null while loading
  const [versions, setVersions] = useState<ReleaseVersion[] | null>(null)
  const [loadError, setLoadError] = useState('')
  const [version, setVersion] = useState('')
  const [environment, setEnvironment] = useState(pickedEnvironment ?? environments[0]?.name ?? '')
  const [confirmation, setConfirmation] = useState('')
  const [deploying, setDeploying] = useState(false)
  const [deployError, setDeployError] = useState('')

  // load the versions and pick the highest, the one usually deployed
  useEffect(() => {
    getVersions(appId)
      .then((data) => {
        setVersions(data.versions)
        setVersion(data.versions[0]?.version ?? '')
      })
      .catch(() => setLoadError('Could not load the versions from the registry'))
  }, [appId])

  const needsConfirmation = environments.find((e) => e.name === environment)?.needsConfirmation ?? false
  const confirmed = !needsConfirmation || confirmation === environment
  const canDeploy = version !== '' && environment !== '' && confirmed && !deploying

  // start the deploy job
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canDeploy) return
    setDeploying(true)
    setDeployError('')
    try {
      const buildId = await deploy(appId, version, environment)
      onStarted({version, environment, buildId})
    } catch (e) {
      setDeployError(e instanceof Error ? e.message : 'Could not deploy')
      setDeploying(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <DialogHeader>
        <DialogTitle className="text-lg">Deploy {appId}</DialogTitle>
        <DialogDescription>Starts the deploy job, which installs the version in the environment.</DialogDescription>
      </DialogHeader>

      <div className="space-y-2">
        <Label htmlFor="deploy-version">Version</Label>
        {loadError ? (
          <Alert variant="destructive"><AlertDescription>{loadError}</AlertDescription></Alert>
        ) : !versions ? (
          <Skeleton className="h-9 w-full"/>
        ) : versions.length === 0 ? (
          <p className="text-sm text-muted-foreground">No versions yet. Release a commit to make one.</p>
        ) : (
          <Select value={version} onValueChange={setVersion} disabled={deploying}>
            <SelectTrigger id="deploy-version" className="w-full">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {versions.map((v) => (
                <SelectItem key={v.version} value={v.version}>
                  <span className="font-mono">{v.version}</span>
                  {v.createdAt && (
                    <span className="text-muted-foreground">released {new Date(v.createdAt).toLocaleDateString()}</span>
                  )}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="deploy-environment">Environment</Label>
        {pickedEnvironment ? (
          <p className="rounded-md border px-3 py-2 text-sm font-medium">{pickedEnvironment}</p>
        ) : (
          <Select value={environment} onValueChange={(e) => {
            setEnvironment(e)
            setConfirmation('')
          }} disabled={deploying}>
            <SelectTrigger id="deploy-environment" className="w-full">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {environments.map((e) => <SelectItem key={e.name} value={e.name}>{e.name}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
      </div>

      {needsConfirmation && (
        <div className="space-y-2">
          <Label htmlFor="deploy-confirmation">
            Type <code className="font-mono">{environment}</code> to confirm
          </Label>
          <Input
            id="deploy-confirmation"
            autoComplete="off"
            spellCheck={false}
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value.trim())}
            disabled={deploying}
          />
        </div>
      )}

      {deployError && (
        <Alert variant="destructive">
          <AlertTitle>Not deployed</AlertTitle>
          <AlertDescription>{deployError}</AlertDescription>
        </Alert>
      )}

      <DialogFooter>
        <Button type="submit" disabled={!canDeploy}>
          {deploying ? <Loader2 className="animate-spin"/> : <Rocket/>}
          {deploying ? 'Deploying...' : 'Deploy'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/**
 * The started deploy's build: its status, read again every few seconds while it runs, and a link to it in the CI tool.
 * Calls onFinished once when the build is no longer running, e.g. to reload the table behind the dialog.
 */
export function StartedDeployView({appId, started, onFinished}: {
  appId: string
  started: StartedDeploy
  onFinished?: () => void
}) {
  // null while loading
  const [build, setBuild] = useState<CiBuild | null>(null)
  const [error, setError] = useState('')

  // read the deploy build; nothing to read while it's queued
  const loadBuild = useCallback(() => {
    if (!started.buildId) return
    getDeployBuild(appId, started.buildId)
      .then((data) => {
        setBuild(data)
        setError(data ? '' : `The CI tool has no deploy build #${started.buildId}`)
      })
      .catch(() => setError('Could not load the deploy build from the CI tool'))
  }, [appId, started.buildId])

  useEffect(() => {
    loadBuild()
  }, [loadBuild])

  // load again while it runs
  usePollWhileRunning(build, build?.status === 'RUNNING', loadBuild)

  // say once that the build finished
  const finishedReported = useRef(false)
  useEffect(() => {
    if (!build || build.status === 'RUNNING' || finishedReported.current) return
    finishedReported.current = true
    onFinished?.()
  }, [build, onFinished])

  // the title follows the build: spinning while it's queued, loading or running, then how it ended
  const what = `${appId} ${started.version} to ${started.environment}`
  const inProgress = !error && (!build || build.status === 'RUNNING')
  const title = inProgress ? `Deploying ${what}`
    : build?.status === 'SUCCESS' ? `Deployed ${what}`
      : `Deploy of ${what} didn't finish`

  return (
    <div className="space-y-5">
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2 text-lg">
          {inProgress && <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground"/>}
          {title}
        </DialogTitle>
        <DialogDescription>
          The deploy job waits for the new pods to be ready, so it fails if they don't start.
        </DialogDescription>
      </DialogHeader>

      {!started.buildId ? (
        <p className="text-sm text-muted-foreground">The deploy build is queued; the CI tool hasn't started it yet.</p>
      ) : error ? (
        <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>
      ) : !build ? (
        <Skeleton className="h-9 w-full"/>
      ) : (
        <div className="flex items-center gap-3 rounded-md border px-3 py-2 text-sm">
          <span className="font-medium">Build #{build.id}</span>
          <StatusBadge status={build.status}/>
          <a href={build.url} target="_blank" rel="noreferrer"
             className="ml-auto inline-flex items-center gap-1 text-muted-foreground hover:underline">
            Open in Jenkins <ExternalLink className="size-3.5"/>
          </a>
        </div>
      )}
    </div>
  )
}

/** Deploys a released version of an app to an environment, then shows the started deploy build. */
function DeployDialog({appId, environments, pickedEnvironment = null, open, onOpenChange, onDeployed}: Props) {
  // null until the deploy job was started
  const [started, setStarted] = useState<StartedDeploy | null>(null)

  // closing forgets the started deploy, so the next open starts at the form
  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) setStarted(null)
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      {open && (
        <DialogContent className="sm:max-w-lg">
          {started ? (
            <StartedDeployView appId={appId} started={started} onFinished={onDeployed}/>
          ) : (
            <DeployForm appId={appId} environments={environments} pickedEnvironment={pickedEnvironment}
                        onStarted={(s) => {
                          setStarted(s)
                          onDeployed?.()
                        }}/>
          )}
        </DialogContent>
      )}
    </Dialog>
  )
}

export default DeployDialog
