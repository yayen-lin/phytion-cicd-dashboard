import {useEffect, useState} from 'react'
import {Navigate, useNavigate, useParams, useSearchParams} from 'react-router'
import {GitBranch, GitCommitHorizontal} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {Tabs, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {getRepos, type CodeRepo} from '@/api/code'
import BranchesTable from './BranchesTable'
import CommitsTable from './CommitsTable'

const tabs = ['branches', 'commits'] as const
type Tab = typeof tabs[number]

const toolNames: Record<string, string> = {
  'bitbucket': 'Bitbucket',
  'github': 'GitHub',
}

/** "bitbucket:andy-test-app/sample-app" -> "Bitbucket · andy-test-app/sample-app" */
function repoLabel(repo: CodeRepo): string {
  return `${toolNames[repo.tool] ?? repo.tool} · ${repo.repo}`
}

/**
 * The Code stage: pick a repo, then see its branches or its commits in tabs.
 * The tab is in the path (/code/branches, /code/commits), the repo and branch in the query
 * (?repo=bitbucket:andy-test-app/sample-app&branch=main), so every view has its own link.
 */
function CodePage() {
  const {tab} = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [repos, setRepos] = useState<CodeRepo[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getRepos()
      .then(setRepos)
      .catch(() => setError('Could not load the repos'))
  }, [])

  if (!tabs.includes(tab as Tab)) return <Navigate to="/code/branches" replace/>

  // The repo in the address, or the first one when there is none (or it isn't configured any more)
  const repo = repos?.find((r) => r.id === searchParams.get('repo')) ?? repos?.[0]
  const branch = searchParams.get('branch')

  function goTo(nextTab: string, repoId: string | undefined, nextBranch?: string | null) {
    const params = new URLSearchParams()
    if (repoId) params.set('repo', repoId)
    if (nextBranch) params.set('branch', nextBranch)
    navigate({pathname: `/code/${nextTab}`, search: params.size ? `?${params}` : ''})
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Code</h1>
          <p className="text-sm text-muted-foreground">Branches and commits in the code repo</p>
        </div>
        {repos && repo && (
          // Switching repos drops the branch: the other repo may not have it
          <Select value={repo.id} onValueChange={(id) => goTo(tab!, id)}>
            <SelectTrigger className="w-72" aria-label="Repo">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {repos.map((r) => (
                <SelectItem key={r.id} value={r.id}>{repoLabel(r)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Repos unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !repos ? (
        <Skeleton className="h-64 w-full"/>
      ) : !repo ? (
        <Alert>
          <AlertTitle>No repos configured</AlertTitle>
          <AlertDescription>Add one to <code>dashboard.code.repos</code> in the backend's application.properties.</AlertDescription>
        </Alert>
      ) : (
        <Tabs value={tab} onValueChange={(nextTab) => goTo(nextTab, repo.id, branch)}>
          <TabsList>
            <TabsTrigger value="branches"><GitBranch/> Branches</TabsTrigger>
            <TabsTrigger value="commits"><GitCommitHorizontal/> Commits</TabsTrigger>
          </TabsList>
          {/* key: switching repos starts the table fresh (empty, loading) instead of showing the old repo's rows */}
          <div className="pt-2">
            {tab === 'branches'
              ? <BranchesTable key={repo.id} repo={repo.id}/>
              : <CommitsTable key={repo.id} repo={repo.id} branch={branch}
                              onBranchChange={(nextBranch) => goTo(tab!, repo.id, nextBranch)}/>}
          </div>
        </Tabs>
      )}
    </div>
  )
}

export default CodePage
