import {useCallback, useEffect, useState, type KeyboardEvent} from 'react'
import {RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {getCommits, type BranchCommits, type CodeCommit} from '@/api/code'
import CommitLink from './CommitLink'
import {firstLine} from '@/lib/format'

type Props = {
  /** e.g. "sample-app" */
  appId: string
  /** The branch in the address, or null when there is none */
  branch: string | null
  onBranchChange: (branch: string) => void
  /** When set, clicking a row calls it with that commit and the branch it's on; e.g. to release it */
  onCommitClick?: (commit: CodeCommit, branch: string) => void
}

/** The most recent commits on one branch of an app's repo, newest first, with a branch selector; one request per branch. */
function CommitsTable({appId, branch, onBranchChange, onCommitClick}: Props) {
  // which branch was asked for, so switching branches shows the skeleton instead of the old branch's commits
  const [loaded, setLoaded] = useState<{ asked: string | null, result: BranchCommits } | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  // the branches with the asked branch's commits (main when none is asked or it's gone)
  const loadCommits = useCallback(() => {
    return getCommits(appId, branch)
      .then((result) => {
        setLoaded({asked: branch, result})
        setError('')
      })
      .catch(() => setError('Could not load the commits from the code tool'))
  }, [appId, branch])

  useEffect(() => {
    loadCommits()
  }, [loadCommits])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadCommits()
    setRefreshing(false)
  }

  // the branch list stays while another branch loads; the rows only show for the asked branch
  const current = loaded && loaded.asked === branch ? loaded.result : null
  const branches = loaded?.result.branches ?? null
  const selected = current ? current.branch : branch
  const shown = current?.commits ?? null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Commits</CardTitle>
        <CardDescription>
          Recent commits on a branch, newest first{onCommitClick && '. Click a commit to release it'}
        </CardDescription>
        <CardAction className="flex gap-2">
          {branches && selected && (
            <Select value={selected} onValueChange={onBranchChange}>
              <SelectTrigger className="w-48" aria-label="Branch">
                <SelectValue/>
              </SelectTrigger>
              <SelectContent>
                {branches.map((b) => <SelectItem key={b.name} value={b.name}>{b.name}</SelectItem>)}
              </SelectContent>
            </Select>
          )}
          <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
            Refresh
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Commits unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : current && !current.branch ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No branches yet.</p>
        ) : !shown ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : shown.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No commits yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Commit</TableHead>
                <TableHead>Message</TableHead>
                <TableHead>Author</TableHead>
                <TableHead>Committed</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shown.map((commit) => (
                <TableRow
                  key={commit.hash}
                  {...(onCommitClick && selected && {
                    className: 'cursor-pointer',
                    tabIndex: 0,
                    'aria-label': `Release commit ${commit.hash.slice(0, 7)}`,
                    onClick: () => onCommitClick(commit, selected),
                    onKeyDown: (e: KeyboardEvent) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onCommitClick(commit, selected)
                      }
                    },
                  })}
                >
                  {/* the link opens the code tool, not the row's action */}
                  <TableCell onClick={(e) => e.stopPropagation()}><CommitLink commit={commit}/></TableCell>
                  <TableCell className="max-w-md truncate" title={commit.message}>{firstLine(commit.message)}</TableCell>
                  <TableCell>{commit.author ?? '-'}</TableCell>
                  <TableCell className="text-muted-foreground">{new Date(commit.committedAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

export default CommitsTable
