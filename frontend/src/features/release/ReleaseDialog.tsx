import {useEffect, useState, type FormEvent} from 'react'
import {GitBranch, Loader2, Tag, TriangleAlert} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
 
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import type {CodeBranch, CodeCommit} from '@/api/code'
import type {App} from '@/api/apps'
import {getReleaseDraft, release, RELEASE_VERSION, type ReleaseDraft} from '@/api/release'
import {firstLine} from '@/lib/format'

// any branch can be released; a commit from another branch gets a warning
const MAIN_BRANCH = 'main'

/** A commit picked before the dialog opens, e.g. by clicking a row of the commits table */
export type PickedCommit = {
  commit: CodeCommit
  /** The branch it was picked from */
  branch: string
}

type Props = {
  /** An app that is released */
  app: App
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The commit to release, or null to pick a branch and a commit in the dialog */
  pickedCommit?: PickedCommit | null
  /** After a release was made: its version and the tag build's id (null when the build is still queued) */
  onReleased: (version: string, buildId: string | null) => void
}

/**
 * Picks a branch, then one of its commits, from what the form loaded. branches is null while loading, commits null while
 * the picked branch's commits load.
 */
function BranchAndCommitPicker({branches, branch, onBranchChange, commits, commit, onCommitChange, disabled}: {
  branches: CodeBranch[] | null
  branch: string
  onBranchChange: (branch: string) => void
  commits: CodeCommit[] | null
  commit: string
  onCommitChange: (commit: string) => void
  disabled: boolean
}) {
  return (
    <>
      <div className="space-y-2">
        <Label htmlFor="release-branch">Branch</Label>
        {!branches || !branch ? (
          branches ? <p className="text-sm text-muted-foreground">No branches yet.</p> : <Skeleton className="h-9 w-full"/>
        ) : (
          <Select value={branch} onValueChange={onBranchChange} disabled={disabled}>
            <SelectTrigger id="release-branch" className="w-full">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {branches.map((b) => <SelectItem key={b.name} value={b.name}>{b.name}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="release-commit">Commit</Label>
        {!branch ? null : !commits ? (
          <Skeleton className="h-9 w-full"/>
        ) : commits.length === 0 ? (
          <p className="text-sm text-muted-foreground">No commits on {branch}.</p>
        ) : (
          <Select value={commit} onValueChange={onCommitChange} disabled={disabled}>
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
    </>
  )
}

/**
 * The form: a commit (picked before, or in the form) and a version, filled in with the latest tag bumped; one request
 * per branch. Mounted fresh each time the dialog opens, so it starts empty.
 */
function ReleaseForm({app, pickedCommit, onReleased}: Pick<Props, 'app' | 'pickedCommit' | 'onReleased'>) {
  const [branch, setBranch] = useState(pickedCommit?.branch ?? '')
  const [commit, setCommit] = useState(pickedCommit?.commit.hash ?? '')
  const [version, setVersion] = useState('')
  const [releasing, setReleasing] = useState(false)
  const [releaseError, setReleaseError] = useState('')
  // the branch asked for: null for the default one (main), until one is picked in the list
  const [askedBranch, setAskedBranch] = useState<string | null>(null)
  // which branch was asked for, so switching branches shows the skeleton instead of the old branch's commits
  const [loaded, setLoaded] = useState<{ asked: string | null, draft: ReleaseDraft } | null>(null)
  const [loadError, setLoadError] = useState('')
  const withCommits = !pickedCommit

  // load the next version and, unless the commit is picked, the branches with the asked branch's commits; fill in the
  // version unless one was typed, and pick the branch's newest commit
  useEffect(() => {
    getReleaseDraft(app.id, askedBranch, withCommits)
      .then((draft) => {
        setLoaded({asked: askedBranch, draft})
        setVersion((typed) => typed || draft.nextVersion)
        if (withCommits) {
          setBranch(draft.branch ?? '')
          setCommit(draft.commits?.[0]?.hash ?? '')
        }
      })
      .catch(() => setLoadError('Could not load the branches, commits and versions from the code tool'))
  }, [app.id, askedBranch, withCommits])

  // no commit is picked until the new branch's commits load, so Release can't tag the old branch's
  function handleBranchChange(next: string) {
    setBranch(next)
    setCommit('')
    setAskedBranch(next)
  }

  // the branch list stays while another branch loads; the commits only show for the asked branch
  const branches = loaded?.draft.branches ?? null
  const shownCommits = loaded && loaded.asked === askedBranch ? loaded.draft.commits : null

  const versionValid = RELEASE_VERSION.test(version)
  const canRelease = versionValid && commit !== '' && !releasing

  // tag the commit and start its build
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
      </DialogHeader>

      {pickedCommit ? (
        <div className="space-y-2">
          <Label>Commit</Label>
          <div className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
            <code className="font-mono text-xs">{pickedCommit.commit.hash.slice(0, 7)}</code>
            <span className="min-w-0 flex-1 truncate" title={pickedCommit.commit.message}>
              {firstLine(pickedCommit.commit.message)}
            </span>
            <span className="inline-flex shrink-0 items-center gap-1 text-muted-foreground">
              <GitBranch className="size-3.5"/>{pickedCommit.branch}
            </span>
          </div>
        </div>
      ) : (
        loadError
          ? <Alert variant="destructive"><AlertDescription>{loadError}</AlertDescription></Alert>
          : <BranchAndCommitPicker branches={branches} branch={branch} onBranchChange={handleBranchChange}
                                   commits={shownCommits} commit={commit} onCommitChange={setCommit}
                                   disabled={releasing}/>
      )}

      {branch && branch !== MAIN_BRANCH && (
        <Alert>
          <TriangleAlert/>
          <AlertTitle>Not on {MAIN_BRANCH}</AlertTitle>
          <AlertDescription>This commit is from {branch}. Its version can be deployed to any environment, prod included.</AlertDescription>
        </Alert>
      )}

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
function ReleaseDialog({app, open, onOpenChange, pickedCommit = null, onReleased}: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <DialogContent aria-describedby={undefined} className="sm:max-w-lg">
          <ReleaseForm app={app} pickedCommit={pickedCommit} onReleased={onReleased}/>
        </DialogContent>
      )}
    </Dialog>
  )
}

export default ReleaseDialog
