import {useState} from 'react'
import {triggerBuild, type CiBuilds} from './api'

type Props = {
  /** The builds to show; null while loading */
  builds: CiBuilds | null
  /** Why the builds could not be loaded, or empty */
  error: string
  /** Called after "Run build" was clicked, whether it worked or not (it is recorded in the audit log either way) */
  onTriggered: () => void
}

function formatDuration(millis: number | null): string {
  if (millis === null) return 'running...'
  const seconds = Math.round(millis / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

/** The builds of the CI job, newest first, with a button to start a new one. */
function BuildsTable({builds: data, error, onTriggered}: Props) {
  const [triggering, setTriggering] = useState(false)
  const [triggerMessage, setTriggerMessage] = useState('')

  async function handleRunBuild() {
    setTriggering(true)
    setTriggerMessage('Starting build...')
    try {
      const buildId = await triggerBuild()
      setTriggerMessage(buildId ? `Started build #${buildId}` : 'Build queued, but the CI tool has not started it yet')
    } catch {
      setTriggerMessage('Could not start the build')
    } finally {
      setTriggering(false)
      onTriggered()
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!data) return <p>Loading builds...</p>

  return (
    <section>
      <h2>Builds: {data.job}</h2>
      <p>
        <button type="button" onClick={handleRunBuild} disabled={triggering}>
          {triggering ? 'Starting...' : 'Run build'}
        </button>{' '}
        {triggerMessage && <span role="status">{triggerMessage}</span>}
      </p>
      {data.builds.length === 0 ? (
        <p>No builds yet.</p>
      ) : (
        <table>
          <thead>
          <tr>
            <th>#</th>
            <th>Status</th>
            <th>Commit</th>
            <th>Branch</th>
            <th>Started</th>
            <th>Duration</th>
          </tr>
          </thead>
          <tbody>
          {data.builds.map((build) => (
            <tr key={build.id}>
              <td><a href={build.url} target="_blank" rel="noreferrer">{build.id}</a></td>
              <td>{build.status}</td>
              <td><code title={build.commit ?? undefined}>{build.commit?.slice(0, 7) ?? '-'}</code></td>
              <td>{build.branch ?? '-'}</td>
              <td>{new Date(build.startedAt).toLocaleString()}</td>
              <td>{formatDuration(build.durationMillis)}</td>
            </tr>
          ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export default BuildsTable
