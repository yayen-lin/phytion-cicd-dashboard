/** An audit log action: "BUILD" -> "Build" */
export function formatActionType(actionType: string): string {
  const words = actionType.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** A commit message's summary: "Fix login\n\nLonger explanation" -> "Fix login" */
export function firstLine(message: string): string {
  return message.split('\n')[0]
}

/** How long a test took, e.g. "14 ms" or "1.2 s" */
export function formatTestDuration(millis: number): string {
  return millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(1)} s`
}

/** How long a build took, e.g. "42s" or "3m 5s"; "running..." while it has no duration yet. */
export function formatDuration(millis: number | null): string {
  if (millis === null) return 'running...'
  const seconds = Math.round(millis / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}
