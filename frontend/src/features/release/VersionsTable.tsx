import {useCallback, useEffect, useState} from 'react'
import {toast} from 'sonner'
import {RefreshCw, Tag} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getVersions, type ReleaseApp, type ReleaseVersion} from '@/api/release'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import CommitLink from '@/features/code/CommitLink'
import ReleaseDialog from './ReleaseDialog'

type Props = {
  app: ReleaseApp
}

/**
 * The released versions of an app, highest first. A version's build number opens that build's details, and its commit
 * links to the code tool.
 */
function VersionsTable({app}: Props) {
  // null while loading
  const [versions, setVersions] = useState<ReleaseVersion[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  // The id of the build shown in the details modal, or null when it is closed
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [releaseOpen, setReleaseOpen] = useState(false)

  const loadVersions = useCallback(() => {
    return getVersions(app.id)
      .then((data) => {
        setVersions(data.versions)
        setError('')
      })
      .catch(() => setError('Could not load versions from the registry'))
  }, [app.id])

  useEffect(() => {
    loadVersions()
  }, [loadVersions])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadVersions()
    setRefreshing(false)
  }

  // The new version isn't in the registry until its build has run the tests and pushed the image
  function handleReleased(version: string, buildId: string | null) {
    setReleaseOpen(false)
    toast.success(`Releasing ${version}`, {
      description: buildId
        ? `Build #${buildId} started. The version shows up here once the build has pushed it.`
        : 'The build is queued. The version shows up here once the build has pushed it.',
      action: buildId ? {label: 'View build', onClick: () => setSelectedId(buildId)} : undefined,
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Versions</CardTitle>
        <CardDescription>Images in the registry, highest version first</CardDescription>
        <CardAction className="flex gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
            Refresh
          </Button>
          <Button onClick={() => setReleaseOpen(true)}>
            <Tag/>
            Release
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Versions unavailable</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : !versions ? (
          <div className="space-y-2">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
          </div>
        ) : versions.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            No versions yet. Release a commit to make one.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Version</TableHead>
                <TableHead>Build</TableHead>
                <TableHead>Commit</TableHead>
                <TableHead>Released</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {versions.map((v) => (
                <TableRow key={v.version}>
                  <TableCell className="font-mono text-sm font-medium">{v.version}</TableCell>
                  <TableCell>
                    {v.buildId ? (
                      <button
                        type="button"
                        className="font-medium underline-offset-4 hover:underline"
                        aria-label={`Build #${v.buildId} details`}
                        onClick={() => setSelectedId(v.buildId)}
                      >
                        #{v.buildId}
                      </button>
                    ) : '-'}
                  </TableCell>
                  <TableCell>
                    {v.commit && v.commitUrl ? <CommitLink commit={{hash: v.commit, url: v.commitUrl}}/> : '-'}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {v.createdAt ? new Date(v.createdAt).toLocaleString() : '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
      <BuildDetailsDialog pipeline={app.pipeline} buildId={selectedId} onClose={() => setSelectedId(null)}/>
      <ReleaseDialog app={app} open={releaseOpen} onOpenChange={setReleaseOpen} onReleased={handleReleased}/>
    </Card>
  )
}

export default VersionsTable
