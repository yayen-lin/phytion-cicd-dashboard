import {useCallback, useEffect, useState} from 'react'
import {RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {getBranches, getCommits, type CodeBranch, type CodeCommit} from '@/api/code'
import CommitLink from './CommitLink'
import {firstLine} from '@/lib/format'

type Props = {
  /** e.g. "bitbucket:andy-test-app/sample-app" */
  repo: string
  /** The branch in the address, or null when there is none */
  branch: string | null
  onBranchChange: (branch: string) => void
}

/** The branch in the address if the repo has it, else main, else master, else the most recently committed to. */
function pickBranch(branches: CodeBranch[], wanted: string | null): string | null {
  const names = branches.map((b) => b.name)
  return [wanted, 'main', 'master'].find((name) => name && names.includes(name)) ?? names[0] ?? null
}

/** The most recent commits on one branch of a repo, newest first, with a branch selector. */
function CommitsTable({repo, branch, onBranchChange}: Props) {
  // null while loading
  const [branches, setBranches] = useState<CodeBranch[] | null>(null)
  // Which branch the commits are from, so switching branches shows the skeleton instead of the old branch's commits
  const [commits, setCommits] = useState<{ branch: string, commits: CodeCommit[] } | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const selected = branches ? pickBranch(branches, branch) : null

  const loadBranches = useCallback(() => {
    return getBranches(repo)
      .then((data) => {
        setBranches(data.branches)
        setError('')
      })
      .catch(() => setError('Could not load branches from the code tool'))
  }, [repo])

  const loadCommits = useCallback(() => {
    if (!selected) return Promise.resolve()
    return getCommits(repo, selected)
      .then((data) => {
        setCommits({branch: selected, commits: data.commits})
        setError('')
      })
      .catch(() => setError('Could not load commits from the code tool'))
  }, [repo, selected])

  useEffect(() => {
    loadBranches()
  }, [loadBranches])

  useEffect(() => {
    loadCommits()
  }, [loadCommits])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await Promise.all([loadBranches(), loadCommits()])
    setRefreshing(false)
  }

  const shown = commits && commits.branch === selected ? commits.commits : null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Commits</CardTitle>
        <CardDescription>Recent commits on a branch, newest first</CardDescription>
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
        ) : branches && !selected ? (
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
                <TableRow key={commit.hash}>
                  <TableCell><CommitLink commit={commit}/></TableCell>
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
