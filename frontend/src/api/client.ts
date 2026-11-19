function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : undefined
}

let onUnauthorized: () => void = () => {
}

/** Sets what runs when the session is gone. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** Shows the login form again when a call answers 401. */
export async function checkLogin(response: Promise<Response>): Promise<Response> {
  const res = await response
  if (res.status === 401) onUnauthorized()
  return res
}

export async function post(url: string, body?: URLSearchParams): Promise<Response> {
  return send('POST', url, {}, body)
}

export async function postJson(url: string, data: unknown): Promise<Response> {
  return send('POST', url, { 'Content-Type': 'application/json' }, JSON.stringify(data))
}

export async function putJson(url: string, data: unknown): Promise<Response> {
  return send('PUT', url, { 'Content-Type': 'application/json' }, JSON.stringify(data))
}

async function send(method: 'POST' | 'PUT', url: string, headers: Record<string, string>, body?: BodyInit): Promise<Response> {
  // the CSRF cookie is gone after logout; any GET gets a new one
  if (!csrfToken()) await fetch('/api/status')
  return fetch(url, {
    method,
    headers: { ...headers, 'X-XSRF-TOKEN': csrfToken() ?? '' },
    body,
  })
}

/** Returns the backend's error message, or the status when it gave none. */
export async function errorMessage(res: Response): Promise<string> {
  try {
    const data: { error?: unknown } = await res.json()
    if (typeof data.error === 'string' && data.error) return data.error
  } catch {
    // not JSON, e.g. a proxy's error page
  }
  return `The backend answered ${res.status}`
}
