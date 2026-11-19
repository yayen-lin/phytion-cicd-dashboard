import { useEffect } from 'react'
import { getPollIntervalMillis } from '@/api/status'

/** Reloads a view every poll interval while something in it runs; stops when a load fails. */
export function usePollWhileRunning(data: unknown, running: boolean, load: () => unknown) {
  useEffect(() => {
    if (!running) return
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined

    // wait the configured interval, then load again
    getPollIntervalMillis().then((millis) => {
      if (!stopped) timer = setTimeout(load, millis)
    })

    return () => {
      stopped = true
      clearTimeout(timer)
    }
  }, [data, running, load])
}
