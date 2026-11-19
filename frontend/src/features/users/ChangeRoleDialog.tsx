import {useState, type FormEvent} from 'react'
import {Loader2} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Button} from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import type {Role, User} from '@/api/auth'
import {changeRole, roles} from '@/api/users'
import {formatRole} from '@/lib/format'

type Props = {
  /** the user whose role changes; the dialog is open while set */
  user: User
  onOpenChange: (open: boolean) => void
  onChanged: () => void
}

/** Picks another role for a user, who is logged out when it's saved. */
function ChangeRoleDialog({user, onOpenChange, onChanged}: Props) {
  const [role, setRole] = useState<Role>(user.role)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  // save the role
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (role === user.role || saving) return
    setSaving(true)
    setError('')
    try {
      await changeRole(user.username, role)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change the role')
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-5">
          <DialogHeader>
            <DialogTitle className="text-lg">Change role of {user.username}</DialogTitle>
            <DialogDescription>{user.username} is logged out when you save.</DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor="change-role">Role</Label>
            <Select value={role} onValueChange={(r) => setRole(r as Role)} disabled={saving}>
              <SelectTrigger id="change-role" className="w-full">
                <SelectValue/>
              </SelectTrigger>
              <SelectContent>
                {roles.map((r) => <SelectItem key={r} value={r}>{formatRole(r)}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          {error && (
            <Alert variant="destructive">
              <AlertTitle>Not changed</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="submit" disabled={role === user.role || saving}>
              {saving && <Loader2 className="animate-spin"/>}
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default ChangeRoleDialog
