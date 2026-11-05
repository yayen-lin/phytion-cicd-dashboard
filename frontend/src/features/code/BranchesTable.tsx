import {useCallback, useEffect, useState} from 'react'
import {GitBranch, RefreshCw} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getBranches, type CodeBranch} from '@/api/code'
import CommitLink from './CommitLink'
import {firstLine} from '@/lib/format'

type Props = {
  /** e.g. "bitbucket:andy-test-app/sample-app" */
  repo: string
}

/** The branches of a repo, the most recently committed to first. */
function BranchesTable({repo}: Props) {
  // null while loading
  const [branches, setBranches] = useState<CodeBranch[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const loadBranches = useCallback(() => {
    return getBranches(repo)
      .then((data) => {
        setBranches(data.branches)
        setError('')
      })
      .catch(() => setError('Could not load branches from the code tool'))
  }, [repo])

  useEffect(() => {
    loadBranches()
  }, [loadBranches])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadBranches()
    setRefreshing(false)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Branches</CardTitle>
        <CardDescription>Most recently committed to first</CardDescription>
        <CardAction>
          <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
            Refresh
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Branches unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !branches ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : branches.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No branches yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Branch</TableHead>
                <TableHead>Latest commit</TableHead>
                <TableHead>Committed</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {branches.map((branch) => (
                <TableRow key={branch.name}>
                  <TableCell className="font-medium">
                    <a href={branch.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                      <GitBranch className="size-3.5 text-muted-foreground"/>{branch.name}
                    </a>
                  </TableCell>
                  <TableCell>
                    {branch.latestCommit ? (
                      <span className="flex min-w-0 items-center gap-2">
                        <CommitLink commit={branch.latestCommit}/>
                        <span className="max-w-md truncate" title={branch.latestCommit.message}>
                          {firstLine(branch.latestCommit.message)}
                        </span>
                      </span>
                    ) : '-'}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {branch.latestCommit ? new Date(branch.latestCommit.committedAt).toLocaleString() : '-'}
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

export default BranchesTable
