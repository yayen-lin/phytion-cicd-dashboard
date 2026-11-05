import {useEffect, useState} from 'react'
import {CircleCheck, CircleMinus, CircleX} from 'lucide-react'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {Skeleton} from '@/components/ui/skeleton'
import {getTestReport, type CiTestCase, type CiTestReport} from '@/api/ci'
import {formatTestDuration} from '@/lib/format'
import TestCounts from './TestCounts'

type Props = {
  /** e.g. "jenkins:sample-app" */
  pipeline: string
  buildId: string
}

// Failed first, then skipped, then passed
const order: Record<CiTestCase['status'], number> = {FAILED: 0, SKIPPED: 1, PASSED: 2}

const icons = {
  PASSED: <CircleCheck className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" aria-label="Passed"/>,
  FAILED: <CircleX className="size-4 shrink-0 text-red-600 dark:text-red-400" aria-label="Failed"/>,
  SKIPPED: <CircleMinus className="size-4 shrink-0 text-muted-foreground" aria-label="Skipped"/>,
}

/** The tests grouped by class: classes with failures first, and in each class failed tests first. */
function groupByClass(cases: CiTestCase[]): [string, CiTestCase[]][] {
  const groups = new Map<string, CiTestCase[]>()
  for (const testCase of cases) {
    groups.set(testCase.className, [...(groups.get(testCase.className) ?? []), testCase])
  }
  const worst = (tests: CiTestCase[]) => Math.min(...tests.map((t) => order[t.status]))
  return [...groups.entries()]
    .map(([className, tests]): [string, CiTestCase[]] => [className, tests.toSorted((a, b) => order[a.status] - order[b.status])])
    .sort(([, a], [, b]) => worst(a) - worst(b))
}

/** Loads one build's test results and lists every test, grouped by class, failures first. */
function TestResults({pipeline, buildId}: Props) {
  // undefined while loading, null when the build has no test results
  const [report, setReport] = useState<CiTestReport | null | undefined>(undefined)
  const [error, setError] = useState('')

  useEffect(() => {
    getTestReport(pipeline, buildId)
      .then(setReport)
      .catch(() => setError('Could not load the test results from the CI tool'))
  }, [pipeline, buildId])

  if (error) return <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>
  if (report === undefined) {
    return (
      <div className="space-y-3 py-2">
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-6 w-full"/>)}
      </div>
    )
  }
  if (report === null) return <p className="py-2 text-sm text-muted-foreground">No test results.</p>

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <TestCounts counts={report.counts}/>
        <span className="text-sm text-muted-foreground tabular-nums">{formatTestDuration(report.durationMillis)} in total</span>
      </div>
      {groupByClass(report.cases).map(([className, tests]) => (
        <section key={className} className="rounded-md border">
          {/* "com.phytion.dummyapp.CreditCalculatorTests" -> "CreditCalculatorTests", the full name on hover */}
          <h4 className="border-b bg-muted/50 px-3 py-2 text-sm font-medium" title={className}>
            {className.split('.').pop()}
          </h4>
          <ul className="divide-y">
            {tests.map((test) => (
              <li key={test.name} className="px-3 py-2">
                <div className="flex items-center gap-2 text-sm">
                  {icons[test.status]}
                  <span className="min-w-0 flex-1 truncate font-mono text-xs" title={test.name}>{test.name}</span>
                  <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{formatTestDuration(test.durationMillis)}</span>
                </div>
                {test.errorMessage && (
                  <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded bg-red-500/10 p-2 font-mono text-xs text-red-800 dark:text-red-200">
                    {test.errorMessage}
                  </pre>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

export default TestResults
