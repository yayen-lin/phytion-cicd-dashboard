import {CircleCheck, CircleX} from 'lucide-react'
import {Badge} from '@/components/ui/badge'
import type {EnvironmentApp} from '@/api/deploy'

/** A deployed app's health as a colored badge with its ready pods, e.g. "Healthy 2/2" or "Unhealthy 0/1". */
function HealthBadge({app}: { app: EnvironmentApp }) {
  const Icon = app.healthy ? CircleCheck : CircleX
  return (
    <Badge variant="secondary"
           className={app.healthy
             ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
             : 'bg-red-500/15 text-red-700 dark:text-red-300'}>
      <Icon/>
      {app.healthy ? 'Healthy' : 'Unhealthy'}
      <span className="tabular-nums opacity-70">{app.readyPods ?? 0}/{app.desiredPods ?? 0}</span>
    </Badge>
  )
}

export default HealthBadge
