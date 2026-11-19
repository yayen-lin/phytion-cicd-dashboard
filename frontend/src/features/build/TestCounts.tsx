import type { CiTestCounts } from '@/api/ci'
import { cn } from '@/lib/utils'

/** Passed, failed and skipped counts, with failures in red. */
function TestCounts({ counts }: { counts: CiTestCounts }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm tabular-nums">
      <span className="text-emerald-700 dark:text-emerald-300">{counts.passed} passed</span>
      <span className="text-muted-foreground">·</span>
      <span className={cn(counts.failed > 0 ? 'font-medium text-red-700 dark:text-red-300' : 'text-muted-foreground')}>
        {counts.failed} failed
      </span>
      <span className="text-muted-foreground">·</span>
      <span className="text-muted-foreground">{counts.skipped} skipped</span>
    </span>
  )
}

export default TestCounts
