import {useCallback, useEffect, useState} from 'react'
import {useSearchParams} from 'react-router'
import {RefreshCw, X} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Skeleton} from '@/components/ui/skeleton'
import {getAuditLog, type AuditEntry, type AuditFilter} from '@/api/audit'
import {getBuilds, getPipelines, type CiBuild} from '@/api/ci'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import {formatActionType} from '@/lib/format'
import AuditTable from './AuditTable'

// The Select can't have an empty value, so "all" stands for "no filter"
const ALL = 'all'

const filterKeys = ['target', 'actionType', 'username', 'actionResult'] as const
type FilterKey = typeof filterKeys[number]

type FilterSelectProps = {
  label: string
  value: string | undefined
  options: { value: string, label: string }[]
  onChange: (value: string | undefined) => void
}

/** One filter dropdown, with "All ..." to turn it off */
function FilterSelect({label, value, options, onChange}: FilterSelectProps) {
  return (
    <Select value={value ?? ALL} onValueChange={(v) => onChange(v === ALL ? undefined : v)}>
      <SelectTrigger className="w-48" aria-label={label}>
        <SelectValue/>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>All {label.toLowerCase()}</SelectItem>
        {/* The value in the address may not be in the log any more: keep it selectable so the dropdown shows it */}
        {value && !options.some((o) => o.value === value) && <SelectItem value={value}>{value}</SelectItem>}
        {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}

/**
 * Every action taken through the dashboard, on any pipeline: who did what, to what, and when.
 * The filters are in the query (?target=jenkins:sample-app&username=andy...), so a filtered view has its own link.
 */
function HistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  // The configured pipelines' ids: only their builds can be looked up and opened
  const [pipelineIds, setPipelineIds] = useState<string[]>([])
  // Each pipeline's recent builds, for the build status column
  const [builds, setBuilds] = useState<Record<string, CiBuild[]>>({})
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  // The build shown in the details modal, or null when it is closed
  const [selected, setSelected] = useState<{ pipeline: string, buildId: string } | null>(null)

  const target = searchParams.get('target') ?? undefined
  const actionType = searchParams.get('actionType') ?? undefined
  const username = searchParams.get('username') ?? undefined
  const actionResult = (searchParams.get('actionResult') ?? undefined) as AuditFilter['actionResult']

  // Loads the whole (recent) log once; the filters only pick rows from it, without asking the server again
  const loadHistory = useCallback(() => {
    return Promise.all([
      getAuditLog(),
      getPipelines(),
    ])
      .then(([log, pipelines]) => {
        setEntries(log)
        setError('')

        // The builds of each pipeline in the table, once per pipeline. If the CI tool is down, that column shows "-"
        const ids = pipelines.map((p) => p.id)
        setPipelineIds(ids)
        const shown = [...new Set(log.map((e) => e.target))].filter((t) => ids.includes(t))
        return Promise.all(shown.map((pipeline) =>
          getBuilds(pipeline).then((data) => [pipeline, data.builds] as const).catch(() => [pipeline, []] as const)))
      })
      .then((results) => setBuilds(Object.fromEntries(results)))
      .catch(() => setError('Could not load the audit log'))
  }, [])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  // Keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadHistory()
    setRefreshing(false)
  }

  function setFilter(key: FilterKey, value: string | undefined) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
  }

  // The values each dropdown can pick: the ones in the loaded log, sorted
  const distinct = (pick: (e: AuditEntry) => string) => [...new Set((entries ?? []).map(pick))].sort()
  const options = {
    targets: distinct((e) => e.target),
    actionTypes: distinct((e) => e.actionType),
    usernames: distinct((e) => e.username),
  }

  const filtered = filterKeys.some((key) => searchParams.has(key))
  const shownEntries = entries?.filter((e) =>
    (!target || e.target === target)
    && (!actionType || e.actionType === actionType)
    && (!username || e.username === username)
    && (!actionResult || e.actionResult === actionResult))

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">History</h1>
        <p className="text-sm text-muted-foreground">Every action taken through the dashboard: who did what, to what, and when</p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <FilterSelect label="Targets" value={target}
                      options={options.targets.map((t) => ({value: t, label: t}))}
                      onChange={(v) => setFilter('target', v)}/>
        <FilterSelect label="Actions" value={actionType}
                      options={options.actionTypes.map((a) => ({value: a, label: formatActionType(a)}))}
                      onChange={(v) => setFilter('actionType', v)}/>
        <FilterSelect label="Users" value={username}
                      options={options.usernames.map((u) => ({value: u, label: u}))}
                      onChange={(v) => setFilter('username', v)}/>
        <FilterSelect label="Results" value={actionResult}
                      options={[{value: 'SUCCESS', label: 'Done'}, {value: 'FAILED', label: 'Failed'}]}
                      onChange={(v) => setFilter('actionResult', v)}/>
        {filtered && (
          <Button variant="ghost" onClick={() => setSearchParams({})}>
            <X/> Clear filters
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Actions</CardTitle>
          <CardDescription>The most recent first</CardDescription>
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
              <AlertTitle>History unavailable</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : !shownEntries ? (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
            </div>
          ) : shownEntries.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {filtered ? 'No actions match these filters.' : 'No actions yet.'}
            </p>
          ) : (
            <AuditTable
              entries={shownEntries}
              showTarget
              buildOf={(entry) => builds[entry.target]?.find((b) => b.id === entry.buildId)}
              canOpenBuild={(entry) => pipelineIds.includes(entry.target)}
              onOpenBuild={(entry) => entry.buildId && setSelected({pipeline: entry.target, buildId: entry.buildId})}
            />
          )}
        </CardContent>
      </Card>

      <BuildDetailsDialog pipeline={selected?.pipeline ?? ''} buildId={selected?.buildId ?? null}
                          onClose={() => setSelected(null)}/>
    </div>
  )
}

export default HistoryPage
