import {useEffect, useState} from 'react'
import {Navigate, useNavigate, useParams, useSearchParams} from 'react-router'
import {History, ListChecks} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {Tabs, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {getPipelines, type CiPipeline} from '@/api/ci'
import BuildsTable from './BuildsTable'
import HistoryTable from './HistoryTable'

const tabs = ['builds', 'history'] as const
type Tab = typeof tabs[number]

const toolNames: Record<string, string> = {
  'jenkins': 'Jenkins',
  'github-actions': 'GitHub Actions',
}

/** "jenkins:sample-app" -> "Jenkins · sample-app" */
function pipelineLabel(pipeline: CiPipeline): string {
  return `${toolNames[pipeline.tool] ?? pipeline.tool} · ${pipeline.job}`
}

/**
 * The Build stage: pick a pipeline, then see its builds or its history in tabs.
 * The tab is in the path (/build/builds, /build/history), the pipeline in the query (?pipeline=jenkins:sample-app),
 * so every view has its own link.
 */
function BuildPage() {
  const {tab} = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [pipelines, setPipelines] = useState<CiPipeline[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getPipelines()
      .then(setPipelines)
      .catch(() => setError('Could not load the pipelines'))
  }, [])

  if (!tabs.includes(tab as Tab)) return <Navigate to="/build/builds" replace/>

  // The pipeline in the address, or the first one when there is none (or it isn't configured any more)
  const pipeline = pipelines?.find((p) => p.id === searchParams.get('pipeline')) ?? pipelines?.[0]

  function goTo(nextTab: string, pipelineId: string | undefined) {
    navigate({pathname: `/build/${nextTab}`, search: pipelineId ? `?${new URLSearchParams({pipeline: pipelineId})}` : ''})
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Build</h1>
          <p className="text-sm text-muted-foreground">Builds and tests run by the CI tool</p>
        </div>
        {pipelines && pipeline && (
          <Select value={pipeline.id} onValueChange={(id) => goTo(tab!, id)}>
            <SelectTrigger className="w-64" aria-label="Pipeline">
              <SelectValue/>
            </SelectTrigger>
            <SelectContent>
              {pipelines.map((p) => (
                <SelectItem key={p.id} value={p.id}>{pipelineLabel(p)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Pipelines unavailable</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !pipelines ? (
        <Skeleton className="h-64 w-full"/>
      ) : !pipeline ? (
        <Alert>
          <AlertTitle>No pipelines configured</AlertTitle>
          <AlertDescription>Add one to <code>dashboard.ci.pipelines</code> in the backend's application.properties.</AlertDescription>
        </Alert>
      ) : (
        <Tabs value={tab} onValueChange={(nextTab) => goTo(nextTab, pipeline.id)}>
          <TabsList>
            <TabsTrigger value="builds"><ListChecks/> Builds</TabsTrigger>
            <TabsTrigger value="history"><History/> History</TabsTrigger>
          </TabsList>
          {/* key: switching pipelines starts the table fresh (empty, loading) instead of showing the old pipeline's rows */}
          <div className="pt-2">
            {tab === 'builds'
              ? <BuildsTable key={pipeline.id} pipeline={pipeline.id}/>
              : <HistoryTable key={pipeline.id} pipeline={pipeline.id}/>}
          </div>
        </Tabs>
      )}
    </div>
  )
}

export default BuildPage
