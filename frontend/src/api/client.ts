/** What every backend call shares: CSRF token for POSTs, and the session-ended (401) handling. */

// The backend puts a CSRF token in the XSRF-TOKEN cookie; every POST must send it back
function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : undefined
}

let onUnauthorized: () => void = () => {}

/** Sets what happens when the backend says the user isn't logged in (any more), e.g. the session expired. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** For calls that need a login: a 401 means the session is gone, so the app shows the login form again. */
export async function checkLogin(response: Promise<Response>): Promise<Response> {
  const res = await response
  if (res.status === 401) onUnauthorized()
  return res
}

/** POST with the CSRF token the backend asks for */
export async function post(url: string, body?: URLSearchParams): Promise<Response> {
  // The cookie is gone after logout; any GET makes the backend set a new one
  if (!csrfToken()) await fetch('/api/status')
  return fetch(url, {
    method: 'POST',
    headers: {'X-XSRF-TOKEN': csrfToken() ?? ''},
    body,
  })
}
