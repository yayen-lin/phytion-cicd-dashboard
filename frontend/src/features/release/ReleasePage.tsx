import {useEffect, useState} from 'react'
import {useSearchParams} from 'react-router'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {getApps, type ReleaseApp} from '@/api/release'
import VersionsTable from './VersionsTable'

const toolNames: Record<string, string> = {
  'registry': 'Registry',
  'ecr': 'AWS ECR',
}

/** "registry:sample-app" -> "Registry · sample-app" */
function appLabel(app: ReleaseApp): string {
  return `${toolNames[app.tool] ?? app.tool} · ${app.image}`
}

/**
 * The Release stage: pick an app, then see its released versions (images in the registry).
 * The app is in the query (?app=registry:sample-app), so every view has its own link.
 */
function ReleasePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [apps, setApps] = useState<ReleaseApp[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getApps()
      .then(setApps)
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
          <Select value={app.id} onValueChange={(id) => setSearchParams({app: id})}>
            <SelectTrigger className="w-64" aria-label="App">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {apps.map((a) => <SelectItem key={a.id} value={a.id}>{appLabel(a)}</SelectItem>)}
            </SelectContent>
          </Select>
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
          <AlertDescription>Add one to <code>dashboard.release.apps</code> in the backend's application.properties.</AlertDescription>
        </Alert>
      ) : (
        // key: switching apps starts the table fresh (empty, loading) instead of showing the old app's rows
        <VersionsTable key={app.id} app={app}/>
      )}
    </div>
  )
}

export default ReleasePage
