import { useState, type SubmitEvent } from 'react'
import { Loader2, UserPlus } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { Role } from '@/api/auth'
import { addUser, roles } from '@/api/users'
import { formatRole } from '@/lib/format'

// the backend's minimum
const MIN_PASSWORD_LENGTH = 8

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onAdded: () => void
}

/** The add user form; mounted fresh on each open. */
function AddUserForm({ onAdded }: Pick<Props, 'onAdded'>) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState('')

  const canAdd = username.trim() !== '' && password.length >= MIN_PASSWORD_LENGTH && !adding

  // add the user
  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canAdd) return
    setAdding(true)
    setError('')
    try {
      await addUser(username.trim(), password, role)
      onAdded()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the user')
      setAdding(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <DialogHeader>
        <DialogTitle className="text-lg">Add user</DialogTitle>
      </DialogHeader>

      <div className="space-y-2">
        <Label htmlFor="add-user-username">Username</Label>
        <Input id="add-user-username" autoComplete="off" spellCheck={false} value={username}
               onChange={(e) => setUsername(e.target.value)} disabled={adding} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="add-user-password">Password</Label>
        <Input id="add-user-password" type="password" autoComplete="new-password" value={password}
               onChange={(e) => setPassword(e.target.value)} disabled={adding} />
        <p className="text-xs text-muted-foreground">At least {MIN_PASSWORD_LENGTH} characters</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="add-user-role">Role</Label>
        <Select value={role} onValueChange={(r) => setRole(r as Role)} disabled={adding}>
          <SelectTrigger id="add-user-role" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {roles.map((r) => <SelectItem key={r} value={r}>{formatRole(r)}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Not added</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <DialogFooter>
        <Button type="submit" disabled={!canAdd}>
          {adding ? <Loader2 className="animate-spin" /> : <UserPlus />}
          {adding ? 'Adding...' : 'Add user'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Adds a user, then closes. */
function AddUserDialog({ open, onOpenChange, onAdded }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <DialogContent className="sm:max-w-md">
          <AddUserForm onAdded={onAdded} />
        </DialogContent>
      )}
    </Dialog>
  )
}

export default AddUserDialog
