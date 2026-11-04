export type User = {
  username: string
  roles: string[]
}

// The backend puts a CSRF token in the XSRF-TOKEN cookie; every POST must send it back
function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : undefined
}

async function post(url: string, body?: URLSearchParams): Promise<Response> {
  // The cookie is gone after logout; any GET makes the backend set a new one
  if (!csrfToken()) await fetch('/api/status')
  return fetch(url, {
    method: 'POST',
    headers: {'X-XSRF-TOKEN': csrfToken() ?? ''},
    body,
  })
}

/** Returns the logged-in user, or null when nobody is logged in. */
export async function getMe(): Promise<User | null> {
  const res = await fetch('/api/me')
  if (res.status === 401) return null
  if (!res.ok) throw new Error(`GET /api/me failed: ${res.status}`)
  return res.json()
}

/** Returns true when the username and password are correct. */
export async function login(username: string, password: string): Promise<boolean> {
  const res = await post('/api/login', new URLSearchParams({username, password}))
  if (res.status === 401) return false
  if (!res.ok) throw new Error(`POST /api/login failed: ${res.status}`)
  return true
}

export async function logout(): Promise<void> {
  await post('/api/logout')
}
