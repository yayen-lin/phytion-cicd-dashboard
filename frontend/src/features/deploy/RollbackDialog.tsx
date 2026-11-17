import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, Loader2, Undo2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { getRollbackOptions, rollback, type RollbackOptions } from '@/api/deploy'
import type { Environment } from '@/api/environments'
import { StartedDeployView, type StartedDeploy } from './DeployDialog'

type Props = {
  /** An app that is deployed, e.g. "sample-app" */
  appId: string
  /** The environments it can roll back, in order; the first is picked, and a picker shows when there are more.
   * One that needs confirmation asks to type its name */
  environments: Environment[]
  open: boolean
  onOpenChange: (open: boolean) => void
  /** After the deploy job was started, and again when its build finished, e.g. to reload what runs where */
  onRolledBack?: () => void
}

/**
 * The form: the version to go back to (the previous good one picked), what goes back where, and the environment's name
 * typed when it needs confirmation.
 */
function RollbackForm({ appId, environments, onStarted }: Pick<Props, 'appId' | 'environments'> & {
  onStarted: (started: StartedDeploy) => void
}) {
  const [environmentName, setEnvironmentName] = useState(environments[0]?.name ?? '')
  // the options and which environment they're for, so switching environments shows loading instead of the old ones
  const [loaded, setLoaded] = useState<RollbackOptions | null>(null)
  const [loadError, setLoadError] = useState<{ environment: string, message: string } | null>(null)
  // the version picked by hand and in which environment; until then the previous good version is picked
  const [picked, setPicked] = useState<{ environment: string, version: string } | null>(null)
  const [confirmation, setConfirmation] = useState('')
  const [rollingBack, setRollingBack] = useState(false)
  const [rollbackError, setRollbackError] = useState('')

  // load the versions the picked environment can go to
  useEffect(() => {
    getRollbackOptions(appId, environmentName)
      .then(setLoaded)
      .catch((e) => setLoadError({
        environment: environmentName,
        message: e instanceof Error ? e.message : 'Could not load the deploy history',
      }))
  }, [appId, environmentName])

  const environment = environments.find((e) => e.name === environmentName)
  if (!environment) return null
  // null while loading
  const options = loaded?.environment === environmentName ? loaded : null
  const shownLoadError = loadError?.environment === environmentName ? loadError.message : ''
  const version = (picked?.environment === environmentName ? picked.version : null) ?? options?.toVersions[0] ?? null

  const confirmed = !environment.needsConfirmation || confirmation === environment.name
  const canRollBack = !!version && confirmed && !rollingBack

  // start the deploy job; the backend reads the deploy history again, so show the version it went to
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canRollBack || !version) return
    setRollingBack(true)
    setRollbackError('')
    try {
      const started = await rollback(appId, environmentName, version)
      onStarted({ version: started.toVersion, environment: started.environment, buildId: started.buildId })
    } catch (e) {
      setRollbackError(e instanceof Error ? e.message : 'Could not roll back')
      setRollingBack(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <DialogHeader>
        <DialogTitle className="text-lg">Roll back {appId}</DialogTitle>
        <DialogDescription>
          Starts the deploy job, which installs an older version that ran in {environment.name} before.
        </DialogDescription>
      </DialogHeader>

      {environments.length > 1 && (
        <div className="space-y-2">
          <Label htmlFor="rollback-environment">Environment</Label>
          <Select value={environmentName} onValueChange={(e) => {
            setEnvironmentName(e)
            setConfirmation('')
            setRollbackError('')
          }} disabled={rollingBack}>
            <SelectTrigger id="rollback-environment" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {environments.map((e) => <SelectItem key={e.name} value={e.name}>{e.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      )}

      {shownLoadError ? (
        <Alert variant="destructive">
          <AlertTitle>Deploy history unavailable</AlertTitle>
          <AlertDescription>{shownLoadError}</AlertDescription>
        </Alert>
      ) : !options ? (
        <Skeleton className="h-9 w-full" />
      ) : !version ? (
        <p className="text-sm text-muted-foreground">
          No older version was deployed to {environment.name}, so there's nothing to roll back to.
        </p>
      ) : (
        <>
          {/* highest first; the first is the previous good version */}
          <div className="space-y-2">
            <Label htmlFor="rollback-version">Version</Label>
            <Select value={version} onValueChange={(v) => {
              setPicked({ environment: environmentName, version: v })
              setRollbackError('')
            }} disabled={rollingBack}>
              <SelectTrigger id="rollback-version" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {options.toVersions.map((v, i) => (
                  <SelectItem key={v} value={v}>
                    <span className="font-mono">{v}</span>
                    {i === 0 && <span className="text-muted-foreground">previous good version</span>}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-center gap-3 rounded-md border px-3 py-2 text-sm">
            <span className="font-medium">{appId}</span>
            <span className="text-muted-foreground">{environment.name}</span>
            <span className="ml-auto inline-flex items-center gap-2 font-mono">
              {options.fromVersion}
              <ArrowRight className="size-4 text-muted-foreground" aria-label="to" />
              <span className="font-medium">{version}</span>
            </span>
          </div>
        </>
      )}

      {version && environment.needsConfirmation && (
        <div className="space-y-2">
          <Label htmlFor="rollback-confirmation">
            Type <code className="font-mono">{environment.name}</code> to confirm
          </Label>
          <Input
            id="rollback-confirmation"
            autoComplete="off"
            spellCheck={false}
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value.trim())}
            disabled={rollingBack}
          />
        </div>
      )}

      {rollbackError && (
        <Alert variant="destructive">
          <AlertTitle>Not rolled back</AlertTitle>
          <AlertDescription>{rollbackError}</AlertDescription>
        </Alert>
      )}

      <DialogFooter>
        <Button type="submit" disabled={!canRollBack}>
          {rollingBack ? <Loader2 className="animate-spin" /> : <Undo2 />}
          {rollingBack ? 'Rolling back...' : version ? `Roll back to ${version}` : 'Roll back'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Rolls an app in an environment back to an older version, then shows the started deploy build. */
function RollbackDialog({ appId, environments, open, onOpenChange, onRolledBack }: Props) {
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
            <StartedDeployView appId={appId} started={started} onFinished={onRolledBack} />
          ) : (
            <RollbackForm appId={appId} environments={environments}
                          onStarted={(s) => {
                            setStarted(s)
                            onRolledBack?.()
                          }} />
          )}
        </DialogContent>
      )}
    </Dialog>
  )
}

export default RollbackDialog
