import {useEffect, useState} from 'react'
import {Tooltip, TooltipContent, TooltipTrigger} from '@/components/ui/tooltip'
import {getBackendStatus} from '@/api/status'
import {cn} from '@/lib/utils'

/** A dot that is green when the backend answers "ok". */
function BackendStatus() {
  const [status, setStatus] = useState('loading')

  useEffect(() => {
    getBackendStatus()
      .then(setStatus)
      .catch(() => setStatus('not reachable'))
  }, [])

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="mr-2 flex items-center gap-2 text-xs text-muted-foreground">
          <span className={cn('size-2 rounded-full', status === 'ok' ? 'bg-emerald-500' : 'bg-amber-500')}/>
          <span className="hidden sm:inline">Backend</span>
        </span>
      </TooltipTrigger>
      <TooltipContent>Backend status: {status}</TooltipContent>
    </Tooltip>
  )
}

export default BackendStatus
