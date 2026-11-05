/** How long a build took, e.g. "42s" or "3m 5s"; "running..." while it has no duration yet. */
export function formatDuration(millis: number | null): string {
  if (millis === null) return 'running...'
  const seconds = Math.round(millis / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}
