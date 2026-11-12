import {useEffect, useState, type FormEvent} from 'react'
import {Loader2, Tag} from 'lucide-react'
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
import {getCommits, type CodeCommit} from '@/api/code'
import type {App} from '@/api/apps'
import {release, RELEASE_VERSION} from '@/api/release'
import {firstLine} from '@/lib/format'

// Releases are made from main (see PLAN.md: any commit on main can be released)
const BRANCH = 'main'

type Props = {
  /** An app that is released */
  app: App
  open: boolean
  onOpenChange: (open: boolean) => void
  /** After a release was made: its version and the tag build's id (null when the build is still queued) */
  onReleased: (version: string, buildId: string | null) => void
}

/** The form: a commit on main and a version. Mounted fresh each time the dialog opens, so it starts empty. */
function ReleaseForm({app, onReleased}: Pick<Props, 'app' | 'onReleased'>) {
  // null while loading
  const [commits, setCommits] = useState<CodeCommit[] | null>(null)
  const [loadError, setLoadError] = useState('')
  const [commit, setCommit] = useState('')
  const [version, setVersion] = useState('')
  const [releasing, setReleasing] = useState(false)
  const [releaseError, setReleaseError] = useState('')

  useEffect(() => {
    getCommits(app.id, BRANCH)
      .then((data) => {
        setCommits(data.commits)
        // The newest commit, the one usually released
        setCommit(data.commits[0]?.hash ?? '')
      })
      .catch(() => setLoadError(`Could not load the commits on ${BRANCH}`))
  }, [app.id])

  const versionValid = RELEASE_VERSION.test(version)
  const canRelease = versionValid && commit !== '' && !releasing

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canRelease) return
    setReleasing(true)
    setReleaseError('')
    try {
      const buildId = await release(app.id, version, commit)
      onReleased(version, buildId)
    } catch (e) {
      setReleaseError(e instanceof Error ? e.message : 'Could not release')
      setReleasing(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <DialogHeader>
        <DialogTitle className="text-lg">Release {app.id}</DialogTitle>
        <DialogDescription>
          Tags a commit on {BRANCH} with the version and builds it. If its tests pass, the build pushes the image.
        </DialogDescription>
      </DialogHeader>

      <div className="space-y-2">
        <Label htmlFor="release-commit">Commit on {BRANCH}</Label>
        {loadError ? (
          <Alert variant="destructive"><AlertDescription>{loadError}</AlertDescription></Alert>
        ) : !commits ? (
          <Skeleton className="h-9 w-full"/>
        ) : commits.length === 0 ? (
          <p className="text-sm text-muted-foreground">No commits on {BRANCH}.</p>
        ) : (
          <Select value={commit} onValueChange={setCommit} disabled={releasing}>
            <SelectTrigger id="release-commit" className="w-full">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {commits.map((c) => (
                <SelectItem key={c.hash} value={c.hash}>
                  <code className="font-mono text-xs">{c.hash.slice(0, 7)}</code>
                  <span className="max-w-80 truncate">{firstLine(c.message)}</span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="release-version">Version</Label>
        <Input
          id="release-version"
          placeholder="v1.4.0"
          autoComplete="off"
          spellCheck={false}
          value={version}
          onChange={(e) => setVersion(e.target.value.trim())}
          disabled={releasing}
          aria-invalid={version !== '' && !versionValid}
          aria-describedby="release-version-hint"
        />
        <p id="release-version-hint" className="text-sm text-muted-foreground">
          {version !== '' && !versionValid
            ? <span className="text-destructive">A version looks like v1.4.0</span>
            : versionValid
              ? <>The image will be <code className="font-mono">{app.release?.image}:{version.slice(1)}</code></>
              : 'The git tag to create, e.g. v1.4.0'}
        </p>
      </div>

      {releaseError && (
        <Alert variant="destructive">
          <AlertTitle>Not released</AlertTitle>
          <AlertDescription>{releaseError}</AlertDescription>
        </Alert>
      )}

      <DialogFooter>
        <Button type="submit" disabled={!canRelease}>
          {releasing ? <Loader2 className="animate-spin"/> : <Tag/>}
          {releasing ? 'Releasing...' : 'Release'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Releases a commit of an app as a new version. */
function ReleaseDialog({app, open, onOpenChange, onReleased}: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <DialogContent className="sm:max-w-lg">
          <ReleaseForm app={app} onReleased={onReleased}/>
        </DialogContent>
      )}
    </Dialog>
  )
}

export default ReleaseDialog
