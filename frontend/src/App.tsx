import {useEffect, useState} from 'react'
import {getBuilds, getMe, logout, type CiBuilds, type User} from './api'
import LoginPage from './LoginPage'
import BuildsTable from './BuildsTable'
import AuditLogTable from './AuditLogTable'

function App() {
  // undefined = still checking, null = not logged in
  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('loading...')
  const [auditLogVersion, setAuditLogVersion] = useState(0)
  // Loaded here because both tables use them: the audit log shows each build's status
  const [builds, setBuilds] = useState<CiBuilds | null>(null)
  const [buildsError, setBuildsError] = useState('')

  function loadUser() {
    getMe()
      .then(setUser)
      .catch(() => {
        setUser(null)
        setError('Backend not reachable')
      })
  }

  useEffect(loadUser, [])

  useEffect(() => {
    if (!user) return
    fetch('/api/status')
      .then((res) => res.json())
      .then((data: { status: string }) => setStatus(data.status))
      .catch(() => setStatus('backend not reachable'))
  }, [user])

  function loadBuilds() {
    getBuilds()
      .then((data) => {
        setBuilds(data)
        setBuildsError('')
      })
      .catch(() => setBuildsError('Could not load builds from the CI tool'))
  }

  useEffect(() => {
    if (user) loadBuilds()
  }, [user])

  async function handleLogout() {
    await logout()
    setUser(null)
  }

  if (user === undefined) return <p>Loading...</p>

  if (user === null) {
    return (
      <>
        {error && <p role="alert">{error}</p>}
        <LoginPage onLogin={() => {
          setError('');
          loadUser()
        }}/>
      </>
    )
  }

  return (
    <main>
      <header>
        <h1>Phytion CI/CD Dashboard</h1>
        <p>
          Logged in as <strong>{user.username}</strong>{' '}
          <button type="button" onClick={handleLogout}>Log out</button>
        </p>
      </header>
      <p>Backend status: {status}</p>
      <BuildsTable builds={builds} error={buildsError} onTriggered={() => {
        loadBuilds()
        setAuditLogVersion((v) => v + 1)
      }}/>
      {/* A new key makes React recreate the table, which loads the audit log again */}
      <AuditLogTable key={auditLogVersion} builds={builds?.builds ?? []}/>
    </main>
  )
}

export default App
