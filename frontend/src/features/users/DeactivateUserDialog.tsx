import {useState} from 'react'
import {Loader2, UserX} from 'lucide-react'
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
import type {User} from '@/api/auth'
import {changeActive} from '@/api/users'

type Props = {
  /** the user to deactivate; the dialog is open while set */
  user: User
  onOpenChange: (open: boolean) => void
  onDeactivated: () => void
}

/** Asks before deactivating a user, who is logged out and can't log in until reactivated. */
function DeactivateUserDialog({user, onOpenChange, onDeactivated}: Props) {
  const [deactivating, setDeactivating] = useState(false)
  const [error, setError] = useState('')

  // deactivate the user
  async function handleDeactivate() {
    setDeactivating(true)
    setError('')
    try {
      await changeActive(user.username, false)
      onDeactivated()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not deactivate the user')
      setDeactivating(false)
    }
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg">Deactivate {user.username}?</DialogTitle>
          <DialogDescription>
            {user.username} is logged out and can't log in until reactivated.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertTitle>Not deactivated</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <DialogFooter>
          <Button variant="destructive" onClick={handleDeactivate} disabled={deactivating}>
            {deactivating ? <Loader2 className="animate-spin"/> : <UserX/>}
            {deactivating ? 'Deactivating...' : 'Deactivate'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default DeactivateUserDialog
