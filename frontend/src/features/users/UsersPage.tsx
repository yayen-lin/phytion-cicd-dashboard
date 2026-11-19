import {useCallback, useEffect, useState} from 'react'
import {Navigate} from 'react-router'
import {toast} from 'sonner'
import {MoreHorizontal, RefreshCw, Shield, UserCheck, UserPlus, Users, UserX} from 'lucide-react'
import {Alert, AlertDescription, AlertTitle} from '@/components/ui/alert'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {Card, CardAction, CardContent, CardHeader, CardTitle} from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Skeleton} from '@/components/ui/skeleton'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import type {User} from '@/api/auth'
import {changeActive, getUsers} from '@/api/users'
import {usePermissions} from '@/auth/AuthContext'
import {formatRole} from '@/lib/format'
import AddUserDialog from './AddUserDialog'
import ChangeRoleDialog from './ChangeRoleDialog'
import DeactivateUserDialog from './DeactivateUserDialog'

/**
 * Every dashboard user (/users), admin only: add one, change a role, deactivate or reactivate.
 */
function UsersPage() {
  const {isAdmin} = usePermissions()
  // null while loading
  const [users, setUsers] = useState<User[] | null>(null)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [adding, setAdding] = useState(false)
  // the user whose Change role or Deactivate dialog is open, or null when it is closed
  const [changingRole, setChangingRole] = useState<User | null>(null)
  const [deactivating, setDeactivating] = useState<User | null>(null)

  const loadUsers = useCallback(() => {
    return getUsers()
      .then((data) => {
        setUsers(data)
        setError('')
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Could not load the users'))
  }, [])

  useEffect(() => {
    if (isAdmin) loadUsers()
  }, [isAdmin, loadUsers])

  // keeps the current rows on screen while reloading
  async function handleRefresh() {
    setRefreshing(true)
    await loadUsers()
    setRefreshing(false)
  }

  // reactivating needs no confirmation
  async function handleReactivate(user: User) {
    try {
      await changeActive(user.username, true)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : `Could not reactivate ${user.username}`)
    } finally {
      loadUsers()
    }
  }

  if (!isAdmin) return <Navigate to="/" replace/>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight"><Users className="size-6"/>Users</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Users</CardTitle>
          <CardAction className="flex gap-2">
            <Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
              <RefreshCw className={refreshing ? 'animate-spin' : undefined}/>
              Refresh
            </Button>
            <Button onClick={() => setAdding(true)}>
              <UserPlus/>
              Add user
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          {error ? (
            <Alert variant="destructive">
              <AlertTitle>Users unavailable</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : !users ? (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-9 w-full"/>)}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Username</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Added</TableHead>
                  <TableHead className="w-10"><span className="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => (
                  <TableRow key={user.username}>
                    <TableCell className="font-medium">{user.username}</TableCell>
                    <TableCell><Badge variant="secondary">{formatRole(user.role)}</Badge></TableCell>
                    <TableCell>
                      <Badge variant={user.active ? 'outline' : 'destructive'}>
                        {user.active ? 'Active' : 'Deactivated'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{new Date(user.createdAt).toLocaleString()}</TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${user.username}`}>
                            <MoreHorizontal/>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => setChangingRole(user)}>
                            <Shield/> Change role
                          </DropdownMenuItem>
                          {user.active ? (
                            <DropdownMenuItem variant="destructive" onSelect={() => setDeactivating(user)}>
                              <UserX/> Deactivate
                            </DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem onSelect={() => handleReactivate(user)}>
                              <UserCheck/> Reactivate
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AddUserDialog open={adding} onOpenChange={setAdding} onAdded={() => {
        setAdding(false)
        loadUsers()
      }}/>
      {changingRole && (
        <ChangeRoleDialog user={changingRole} onOpenChange={(open) => !open && setChangingRole(null)} onChanged={() => {
          setChangingRole(null)
          loadUsers()
        }}/>
      )}
      {deactivating && (
        <DeactivateUserDialog user={deactivating} onOpenChange={(open) => !open && setDeactivating(null)}
                              onDeactivated={() => {
                                setDeactivating(null)
                                loadUsers()
                              }}/>
      )}
    </div>
  )
}

export default UsersPage
