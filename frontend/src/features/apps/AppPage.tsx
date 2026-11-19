import {useEffect, useState} from 'react'
import {Navigate, useNavigate, useParams, useSearchParams} from 'react-router'
import {toast} from 'sonner'
import {Boxes, GitBranch, GitCommitHorizontal, ListChecks, Server, Tag} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {Tabs, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {appToImageLabel, appToPipelineLabel, appToRepoLabel, getApp, type App} from '@/api/apps'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import BuildsTable from '@/features/build/BuildsTable'
import BranchesTable from '@/features/code/BranchesTable'
import CommitsTable from '@/features/code/CommitsTable'
import ReleaseDialog, {type PickedCommit} from '@/features/release/ReleaseDialog'
import VersionsTable from '@/features/release/VersionsTable'
import {usePermissions} from '@/auth/AuthContext'
import AppEnvironmentsTable from './AppEnvironmentsTable'

const tabs = ['branches', 'commits', 'builds', 'versions', 'environments'] as const
type Tab = typeof tabs[number]

/** What an app's tab shows when the app has no release or deploy configured */
function NotConfigured({part}: { part: 'release' | 'deploy' }) {
  return (
    <Alert>
      <AlertTitle>No {part} configured</AlertTitle>
      <AlertDescription>Add a <code>{part}</code> to this app in <code>dashboard.apps</code> in the backend's
        application.properties.</AlertDescription>
    </Alert>
  )
}

/**
 * One app's page: its branches, commits, builds (with tests), versions, and where it runs, in tabs.
 * The tab is in the path (/apps/sample-app/commits), the branch in the query (?branch=main), so every view has its own
 * link.
 */
function AppPage() {
  const {appId, tab} = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const {canAct} = usePermissions()
  // which app was loaded (null when it isn't configured), so switching apps shows the skeleton instead of the old one
  const [loaded, setLoaded] = useState<{ appId: string, app: App | null } | null>(null)
  const [error, setError] = useState('')
  // the Release dialog: closed, open to pick a commit (null), or open with a commit picked from the Commits tab
  const [release, setRelease] = useState<{ pickedCommit: PickedCommit | null } | null>(null)
  // the build shown in the details modal, or null when it is closed
  const [selectedBuildId, setSelectedBuildId] = useState<string | null>(null)

  // load this app's repo, pipeline, image and deploy job
  useEffect(() => {
    if (!appId) return
    getApp(appId)
      .then((app) => {
        setLoaded({appId, app})
        setError('')
      })
      .catch(() => setError('Could not load the app'))
  }, [appId])

  if (!tabs.includes(tab as Tab)) return <Navigate to={`/apps/${appId}/commits`} replace/>

  const current = loaded && loaded.appId === appId ? loaded : null
  const app = current?.app
  const branch = searchParams.get('branch')

  // switching tabs keeps the branch, so going back to Commits shows the same one
  function goTo(nextTab: string, nextBranch: string | null) {
    navigate({pathname: `/apps/${appId}/${nextTab}`, search: nextBranch ? `?${new URLSearchParams({branch: nextBranch})}` : ''})
  }

  function handleReleased(version: string, buildId: string | null) {
    setRelease(null)

    // show the tag build right away
    if (buildId) setSelectedBuildId(buildId)
    else toast.info(`Releasing ${version}: the build is queued, but the CI tool has not started it yet`)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight"><Boxes className="size-6"/>{appId}</h1>
        {app && (
          <p className="text-sm text-muted-foreground">
            {[appToRepoLabel(app), appToPipelineLabel(app), appToImageLabel(app)].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>App unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !current ? (
        <Skeleton className="h-64 w-full"/>
      ) : !app ? (
        <Alert>
          <AlertTitle>No app named {appId}</AlertTitle>
          <AlertDescription>The apps are listed in <code>dashboard.apps</code> in the backend's
            application.properties.</AlertDescription>
        </Alert>
      ) : (
        <Tabs value={tab} onValueChange={(nextTab) => goTo(nextTab, branch)}>
          <TabsList>
            <TabsTrigger value="branches"><GitBranch/> Branches</TabsTrigger>
            <TabsTrigger value="commits"><GitCommitHorizontal/> Commits</TabsTrigger>
            <TabsTrigger value="builds"><ListChecks/> Builds</TabsTrigger>
            <TabsTrigger value="versions"><Tag/> Versions</TabsTrigger>
            <TabsTrigger value="environments"><Server/> Environments</TabsTrigger>
          </TabsList>
          {/* key: switching apps starts the table fresh (empty, loading) instead of showing the old app's rows */}
          <div className="pt-2">
            {tab === 'branches' && <BranchesTable key={app.id} appId={app.id}/>}
            {tab === 'commits' && (
              <CommitsTable key={app.id} appId={app.id} branch={branch}
                            onBranchChange={(nextBranch) => goTo('commits', nextBranch)}
                            onCommitClick={app.release && canAct
                              ? (commit, commitBranch) => setRelease({pickedCommit: {commit, branch: commitBranch}})
                              : undefined}/>
            )}
            {tab === 'builds' && <BuildsTable key={app.id} appId={app.id}/>}
            {tab === 'versions' && (app.release ? <VersionsTable key={app.id} app={app} onReleaseClick={() => setRelease({pickedCommit: null})}/> : <NotConfigured part="release"/>)}
            {tab === 'environments' && (app.deploy ? <AppEnvironmentsTable key={app.id} app={app}
                                                                                       onReleaseClick={() => setRelease({pickedCommit: null})}
                                                                                       onViewBuild={setSelectedBuildId}/> : <NotConfigured part="deploy"/>)}
          </div>

          {app.release && (
            <ReleaseDialog app={app} open={release !== null} onOpenChange={(open) => !open && setRelease(null)}
                           pickedCommit={release?.pickedCommit} onReleased={handleReleased}/>
          )}
          <BuildDetailsDialog appId={app.id} buildId={selectedBuildId} onClose={() => setSelectedBuildId(null)}/>
        </Tabs>
      )}
    </div>
  )
}

export default AppPage
