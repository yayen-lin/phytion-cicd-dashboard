import {useEffect} from 'react'
import {getPollIntervalMillis} from '@/api/status'

/**
 * Loads a view again a few seconds after each load, while something in it is running
 * (dashboard.frontend.poll-interval).
 * Stops when nothing runs any more, when a load fails (data doesn't change), or when the view closes.
 */
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
