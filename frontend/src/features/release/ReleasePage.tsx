import {useEffect, useState} from 'react'
import {useSearchParams} from 'react-router'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getApps, appToImageLabel, type App} from '@/api/apps'
import AppSelect from '@/features/apps/AppSelect'
import VersionsTable from './VersionsTable'

/**
 * The Release stage: pick an app, then see its released versions (images in the registry). Only apps with a release
 * configured are listed.
 * The app is in the query (?app=sample-app), so every view has its own link.
 */
function ReleasePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [apps, setApps] = useState<App[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getApps()
      .then((all) => setApps(all.filter((a) => a.release)))
      .catch(() => setError('Could not load the apps'))
  }, [])

  // The app in the address, or the first one when there is none (or it isn't configured any more)
  const app = apps?.find((a) => a.id === searchParams.get('app')) ?? apps?.[0]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Release</h1>
          <p className="text-sm text-muted-foreground">Versions of the app that are ready to deploy</p>
        </div>
        {apps && app && (
          <AppSelect apps={apps} value={app} onChange={(id) => setSearchParams({app: id})} part={appToImageLabel}/>
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
          <AlertTitle>No released apps</AlertTitle>
          <AlertDescription>Add a <code>release</code> to an app in <code>dashboard.apps</code> in the backend's application.properties.</AlertDescription>
        </Alert>
      ) : (
        // key: switching apps starts the table fresh (empty, loading) instead of showing the old app's rows
        <VersionsTable key={app.id} app={app}/>
      )}
    </div>
  )
}

export default ReleasePage
