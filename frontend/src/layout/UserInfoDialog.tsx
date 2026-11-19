import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useAuth } from '@/auth/AuthContext'
import { formatRole } from '@/lib/format'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
}

function Field({ label, children }: { label: string, children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] items-start gap-2 py-2">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm">{children}</dd>
    </div>
  )
}

/** The logged-in user's details, read only. */
function UserInfoDialog({ open, onOpenChange }: Props) {
  const { user } = useAuth()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg">{user.username}</DialogTitle>
          <DialogDescription>Your account</DialogDescription>
        </DialogHeader>

        <dl className="divide-y">
          <Field label="Username"><span className="font-mono">{user.username}</span></Field>
          <Field label="Role">
            <Badge variant="secondary">{formatRole(user.role)}</Badge>
          </Field>
          <Field label="Status">
            <Badge variant={user.active ? 'outline' : 'destructive'}>{user.active ? 'Active' : 'Deactivated'}</Badge>
          </Field>
          <Field label="Added">{new Date(user.createdAt).toLocaleString()}</Field>
        </dl>
      </DialogContent>
    </Dialog>
  )
}

export default UserInfoDialog
