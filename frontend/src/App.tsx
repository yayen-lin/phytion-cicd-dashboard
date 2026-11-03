import { useEffect, useState } from 'react'

function App() {
  const [status, setStatus] = useState('loading...')

  useEffect(() => {
    fetch('/api/status')
      .then((res) => res.json())
      .then((data: { status: string }) => setStatus(data.status))
      .catch(() => setStatus('backend not reachable'))
  }, [])

  return (
    <main>
      <h1>Phytion CI/CD Dashboard</h1>
      <p>Backend status: {status}</p>
    </main>
  )
}

export default App
