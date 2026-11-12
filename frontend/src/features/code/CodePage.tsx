import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams, useSearchParams } from 'react-router'
import { GitBranch, GitCommitHorizontal } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getApps, appToRepoLabel, type App } from '@/api/apps'
import AppSelect from '@/features/apps/AppSelect'
import BranchesTable from './BranchesTable'
import CommitsTable from './CommitsTable'

const tabs = ['branches', 'commits'] as const
type Tab = typeof tabs[number]

/**
 * The Code stage: pick an app, then see its repo's branches or commits in tabs.
 * The tab is in the path (/code/branches, /code/commits), the app and branch in the query
 * (?app=sample-app&branch=main), so every view has its own link.
 */
function CodePage() {
  const { tab } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [apps, setApps] = useState<App[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getApps()
      .then(setApps)
      .catch(() => setError('Could not load the apps'))
  }, [])

  if (!tabs.includes(tab as Tab)) return <Navigate to="/code/branches" replace />

  // The app in the address, or the first one when there is none (or it isn't configured any more)
  const app = apps?.find((a) => a.id === searchParams.get('app')) ?? apps?.[0]
  const branch = searchParams.get('branch')

  function goTo(nextTab: string, appId: string | undefined, nextBranch?: string | null) {
    const params = new URLSearchParams()
    if (appId) params.set('app', appId)
    if (nextBranch) params.set('branch', nextBranch)
    navigate({ pathname: `/code/${nextTab}`, search: params.size ? `?${params}` : '' })
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Code</h1>
          <p className="text-sm text-muted-foreground">Branches and commits in the code repo</p>
        </div>
        {apps && app && (
          // Switching apps drops the branch: the other app's repo may not have it
          <AppSelect apps={apps} value={app} onChange={(id) => goTo(tab!, id)} part={appToRepoLabel} />
        )}
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Apps unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !apps ? (
        <Skeleton className="h-64 w-full" />
      ) : !app ? (
        <Alert>
          <AlertTitle>No apps configured</AlertTitle>
          <AlertDescription>Add one to <code>dashboard.apps</code> in the backend's
            application.properties.</AlertDescription>
        </Alert>
      ) : (
        <Tabs value={tab} onValueChange={(nextTab) => goTo(nextTab, app.id, branch)}>
          <TabsList>
            <TabsTrigger value="branches"><GitBranch /> Branches</TabsTrigger>
            <TabsTrigger value="commits"><GitCommitHorizontal /> Commits</TabsTrigger>
          </TabsList>
          {/* key: switching apps starts the table fresh (empty, loading) instead of showing the old app's rows */}
          <div className="pt-2">
            {tab === 'branches'
              ? <BranchesTable key={app.id} appId={app.id} />
              : <CommitsTable key={app.id} appId={app.id} branch={branch}
                              onBranchChange={(nextBranch) => goTo(tab!, app.id, nextBranch)} />}
          </div>
        </Tabs>
      )}
    </div>
  )
}

export default CodePage
