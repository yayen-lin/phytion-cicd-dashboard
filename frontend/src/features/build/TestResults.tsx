import { CircleCheck, CircleMinus, CircleX } from 'lucide-react'
import type { CiTestCase, CiTestReport } from '@/api/ci'
import { formatTestDuration } from '@/lib/format'
import TestCounts from './TestCounts'

type Props = {
  report: CiTestReport
}

// failed first, then skipped, then passed
const order: Record<CiTestCase['status'], number> = { FAILED: 0, SKIPPED: 1, PASSED: 2 }

const icons = {
  PASSED: <CircleCheck className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" aria-label="Passed" />,
  FAILED: <CircleX className="size-4 shrink-0 text-red-600 dark:text-red-400" aria-label="Failed" />,
  SKIPPED: <CircleMinus className="size-4 shrink-0 text-muted-foreground" aria-label="Skipped" />,
}

/** Groups tests by class, failures first. */
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

/** One build's test results, grouped by class. */
function TestResults({ report }: Props) {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <TestCounts counts={report.counts} />
        <span className="text-sm text-muted-foreground tabular-nums">{formatTestDuration(report.durationMillis)} in total</span>
      </div>
      {groupByClass(report.cases).map(([className, tests]) => (
        <section key={className} className="rounded-md border">
          {/* the short class name; the full name on hover */}
          <h4 className="border-b bg-muted/50 px-3 py-2 text-sm font-medium" title={className}>
            {className.split('.').pop()}
          </h4>
          <ul className="divide-y">
            {tests.map((test) => (
              <li key={test.name} className="px-3 py-2">
                <div className="flex items-center gap-2 text-sm">
                  {icons[test.status]}
                  <span className="min-w-0 flex-1 truncate font-mono text-xs" title={test.name}>{test.name}</span>
                  <span
                    className="shrink-0 text-xs text-muted-foreground tabular-nums">{formatTestDuration(test.durationMillis)}</span>
                </div>
                {test.errorMessage && (
                  <pre
                    className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded bg-red-500/10 p-2 font-mono text-xs text-red-800 dark:text-red-200">
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
