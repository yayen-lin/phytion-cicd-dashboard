import {useState, type SubmitEvent} from 'react'
import {Loader2, Workflow} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {login} from '@/api/auth'

type Props = {
  /** An error from outside the form (e.g. backend not reachable), or empty */
  error: string
  onLogin: () => void
}

function LoginPage({error: outsideError, onLogin}: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
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

  const shownError = error || outsideError

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/30 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <span className="mx-auto mb-2 flex size-10 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Workflow className="size-5"/>
          </span>
          <CardTitle className="text-xl">Phytion CI/CD Dashboard</CardTitle>
          <CardDescription>Log in to continue</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">Username</Label>
              <Input id="username" value={username} onChange={(e) => setUsername(e.target.value)}
                     autoComplete="username" required autoFocus/>
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                     autoComplete="current-password" required/>
            </div>
            {shownError && (
              <Alert variant="destructive">
                <AlertDescription>{shownError}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting && <Loader2 className="animate-spin"/>}
              {submitting ? 'Logging in...' : 'Log in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}

export default LoginPage
