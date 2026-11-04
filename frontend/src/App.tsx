import {useEffect, useState} from 'react'
import {getMe, logout, type User} from './api'
import LoginPage from './LoginPage'

function App() {
  // undefined = still checking, null = not logged in
  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('loading...')

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
    </main>
  )
}

export default App
