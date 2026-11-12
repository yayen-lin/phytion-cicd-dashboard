import {useEffect, useState} from 'react'
import {Navigate, useNavigate, useParams, useSearchParams} from 'react-router'
import {History, ListChecks} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {Tabs, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {getApps, appToPipelineLabel, type App} from '@/api/apps'
import AppSelect from '@/features/apps/AppSelect'
import BuildsTable from './BuildsTable'
import HistoryTable from './HistoryTable'

const tabs = ['builds', 'history'] as const
type Tab = typeof tabs[number]

function BuildPage() {
  const {tab} = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [apps, setApps] = useState<App[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getApps()
      .then(setApps)
      .catch(() => setError('Could not load the apps'))
  }, [])

  if (!tabs.includes(tab as Tab)) return <Navigate to="/build/builds" replace/>

  // The app in the address, or the first one when there is none (or it isn't configured anymore)
  const app = apps?.find((a) => a.id === searchParams.get('app')) ?? apps?.[0]

  function goTo(nextTab: string, appId: string | undefined) {
    navigate({pathname: `/build/${nextTab}`, search: appId ? `?${new URLSearchParams({app: appId})}` : ''})
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Build & Test</h1>
          <p className="text-sm text-muted-foreground">Builds and tests run by the CI tool</p>
        </div>
        {apps && app && (
          <AppSelect apps={apps} value={app} onChange={(id) => goTo(tab!, id)} part={appToPipelineLabel}/>
        )}
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Apps unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !apps ? (
        <Skeleton className="h-64 w-full"/>
      ) : !app ? (
        <Alert>
          <AlertTitle>No apps configured</AlertTitle>
          <AlertDescription>Add one to <code>dashboard.apps</code> in the backend's application.properties.</AlertDescription>
        </Alert>
      ) : (
        <Tabs value={tab} onValueChange={(nextTab) => goTo(nextTab, app.id)}>
          <TabsList>
            <TabsTrigger value="builds"><ListChecks/> Builds</TabsTrigger>
            <TabsTrigger value="history"><History/> History</TabsTrigger>
          </TabsList>
          {/* key: switching apps starts the table fresh (empty, loading) instead of showing the old app's rows */}
          <div className="pt-2">
            {tab === 'builds'
              ? <BuildsTable key={app.id} appId={app.id}/>
              : <HistoryTable key={app.id} appId={app.id}/>}
          </div>
        </Tabs>
      )}
    </div>
  )
}

export default BuildPage
