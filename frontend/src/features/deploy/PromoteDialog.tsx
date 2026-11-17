import {useState, type FormEvent} from 'react'
import {ArrowRight, ArrowUpRight, Loader2} from 'lucide-react'
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
import {promote} from '@/api/deploy'
import type {Environment} from '@/api/environments'
import {StartedDeployView, type StartedDeploy} from './DeployDialog'

/** A promote that can be picked: the version running in an environment and the environment after it */
export type PromoteChoice = {
  /** The version running in the source environment, as the page shows it, e.g. "1.0.1" */
  version: string
  /** The source environment, e.g. "staging" */
  fromEnvironment: string
  /** The environment after it, e.g. prod; asks to type its name when it needs confirmation */
  toEnvironment: Environment
}

type Props = {
  /** An app that is released and deployed, e.g. "sample-app" */
  appId: string
  /** What can be promoted, in the environments' order; the first is picked, and a picker shows when there are more */
  choices: PromoteChoice[]
  open: boolean
  onOpenChange: (open: boolean) => void
  /** After the deploy job was started, and again when its build finished, e.g. to reload what runs where */
  onPromoted?: () => void
}

/** The form: which environment to promote from, what moves where, and the target's name typed when it needs confirmation. */
function PromoteForm({appId, choices, onStarted}: Pick<Props, 'appId' | 'choices'> & {
  onStarted: (started: StartedDeploy) => void
}) {
  const [fromEnvironment, setFromEnvironment] = useState(choices[0]?.fromEnvironment ?? '')
  const [confirmation, setConfirmation] = useState('')
  const [promoting, setPromoting] = useState(false)
  const [promoteError, setPromoteError] = useState('')

  const choice = choices.find((c) => c.fromEnvironment === fromEnvironment)
  if (!choice) return null
  const {version, toEnvironment} = choice

  const confirmed = !toEnvironment.needsConfirmation || confirmation === toEnvironment.name
  const canPromote = confirmed && !promoting

  // start the deploy job; the backend reads the running version again, so show the one it promoted
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canPromote) return
    setPromoting(true)
    setPromoteError('')
    try {
      const promotion = await promote(appId, fromEnvironment)
      onStarted({version: promotion.version, environment: promotion.toEnvironment, buildId: promotion.buildId})
    } catch (e) {
      setPromoteError(e instanceof Error ? e.message : 'Could not promote')
      setPromoting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <DialogHeader>
        <DialogTitle className="text-lg">Promote {appId}</DialogTitle>
        <DialogDescription>
          Starts the deploy job, which installs the version running in {fromEnvironment} in {toEnvironment.name}.
        </DialogDescription>
      </DialogHeader>

      {choices.length > 1 && (
        <div className="space-y-2">
          <Label htmlFor="promote-from">From</Label>
          <Select value={fromEnvironment} onValueChange={(e) => {
            setFromEnvironment(e)
            setConfirmation('')
            setPromoteError('')
          }} disabled={promoting}>
            <SelectTrigger id="promote-from" className="w-full">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {choices.map((c) => <SelectItem key={c.fromEnvironment} value={c.fromEnvironment}>{c.fromEnvironment}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      )}

      <div className="flex items-center gap-3 rounded-md border px-3 py-2 text-sm">
        <span className="font-medium">{appId}</span>
        <span className="font-mono">{version}</span>
        <span className="ml-auto inline-flex items-center gap-2">
          {fromEnvironment}
          <ArrowRight className="size-4 text-muted-foreground" aria-label="to"/>
          <span className="font-medium">{toEnvironment.name}</span>
        </span>
      </div>

      {toEnvironment.needsConfirmation && (
        <div className="space-y-2">
          <Label htmlFor="promote-confirmation">
            Type <code className="font-mono">{toEnvironment.name}</code> to confirm
          </Label>
          <Input
            id="promote-confirmation"
            autoComplete="off"
            spellCheck={false}
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value.trim())}
            disabled={promoting}
          />
        </div>
      )}

      {promoteError && (
        <Alert variant="destructive">
          <AlertTitle>Not promoted</AlertTitle>
          <AlertDescription>{promoteError}</AlertDescription>
        </Alert>
      )}

      <DialogFooter>
        <Button type="submit" disabled={!canPromote}>
          {promoting ? <Loader2 className="animate-spin"/> : <ArrowUpRight/>}
          {promoting ? 'Promoting...' : `Promote to ${toEnvironment.name}`}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Promotes the version running in an environment to the next one, then shows the started deploy build. */
function PromoteDialog({appId, choices, open, onOpenChange, onPromoted}: Props) {
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
            <StartedDeployView appId={appId} started={started} onFinished={onPromoted}/>
          ) : (
            <PromoteForm appId={appId} choices={choices}
                         onStarted={(s) => {
                           setStarted(s)
                           onPromoted?.()
                         }}/>
          )}
        </DialogContent>
      )}
    </Dialog>
  )
}

export default PromoteDialog
