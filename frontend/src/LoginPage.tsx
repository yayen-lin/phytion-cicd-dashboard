import {useState, type FormEvent} from 'react'
import {login} from './api'

type Props = {
  onLogin: () => void
}

function LoginPage({onLogin}: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      if (await login(username, password)) {
        onLogin()
      } else {
        setError('Wrong username or password')
      }
    } catch {
      setError('Backend not reachable')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Phytion CI/CD Dashboard</h1>
      <form onSubmit={handleSubmit}>
        <p>
          <label>
            Username <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username"
                            required/>
          </label>
        </p>
        <p>
          <label>
            Password <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                            autoComplete="current-password" required/>
          </label>
        </p>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>
    </main>
  )
}

export default LoginPage
