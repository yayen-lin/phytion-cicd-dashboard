import {useEffect, useState} from 'react'
import {getAuditLog, type AuditEntry, type CiBuild} from './api'

type Props = {
  /** The CI builds, to show the status of the build each entry started */
  builds: CiBuild[]
}

/** Every action taken through the dashboard, newest first: who did what, and when. */
function AuditLogTable({builds}: Props) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getAuditLog()
      .then(setEntries)
      .catch(() => setError('Could not load the audit log'))
  }, [])

  if (error) return <p role="alert">{error}</p>
  if (!entries) return <p>Loading history...</p>

  return (
    <section>
      <h2>History</h2>
      {entries.length === 0 ? (
        <p>No actions yet.</p>
      ) : (
        <table>
          <thead>
          <tr>
            <th>When</th>
            <th>Who</th>
            <th>What</th>
            <th>Target</th>
            <th>Build</th>
            <th>Build status</th>
            <th>Action result</th>
            <th>Details</th>
          </tr>
          </thead>
          <tbody>
          {entries.map((entry) => (
            <tr key={entry.id}>
              <td>{new Date(entry.createdAt).toLocaleString()}</td>
              <td>{entry.username}</td>
              <td>{entry.actionType}</td>
              <td>{entry.target}</td>
              <td>{entry.buildId ? `#${entry.buildId}` : '-'}</td>
              <td>{builds.find((build) => build.id === entry.buildId)?.status ?? '-'}</td>
              <td>{entry.actionResult}</td>
              <td>{entry.details ?? ''}</td>
            </tr>
          ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export default AuditLogTable
