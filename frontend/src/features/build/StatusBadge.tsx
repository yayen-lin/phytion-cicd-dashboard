import {Ban, CircleCheck, CircleHelp, CircleX, Loader2, TriangleAlert} from 'lucide-react'
import {Badge} from '@/components/ui/badge'
import {cn} from '@/lib/utils'
import type {CiBuildStatus} from '@/api/ci'

const styles: Record<CiBuildStatus, { className: string, icon: typeof CircleCheck, label: string }> = {
  RUNNING: {className: 'bg-sky-500/15 text-sky-700 dark:text-sky-300', icon: Loader2, label: 'Running'},
  SUCCESS: {className: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300', icon: CircleCheck, label: 'Success'},
  FAILED: {className: 'bg-red-500/15 text-red-700 dark:text-red-300', icon: CircleX, label: 'Failed'},
  UNSTABLE: {className: 'bg-amber-500/15 text-amber-700 dark:text-amber-300', icon: TriangleAlert, label: 'Unstable'},
  ABORTED: {className: 'bg-muted text-muted-foreground', icon: Ban, label: 'Aborted'},
  UNKNOWN: {className: 'bg-muted text-muted-foreground', icon: CircleHelp, label: 'Unknown'},
}

/** A build's status as a colored badge with an icon. */
function StatusBadge({status}: { status: CiBuildStatus }) {
  const {className, icon: Icon, label} = styles[status]
  return (
    <Badge variant="secondary" className={className}>
      <Icon className={cn(status === 'RUNNING' && 'animate-spin')}/>
      {label}
    </Badge>
  )
}

export default StatusBadge
