import { useState } from 'react'
import { IdCard, LogOut, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuth } from '@/auth/AuthContext'
import UserInfoDialog from './UserInfoDialog'

/** The logged-in user's name, with Account and Log out. */
function UserMenu() {
  const { user, logout } = useAuth()
  const [showingAccount, setShowingAccount] = useState(false)

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="sm" className="gap-2">
            <UserRound />
            <span className="hidden sm:inline">{user.username}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuLabel>
            Logged in as <span className="font-semibold">{user.username}</span>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setShowingAccount(true)}>
            <IdCard /> Account
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={logout}>
            <LogOut /> Log out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <UserInfoDialog open={showingAccount} onOpenChange={setShowingAccount} />
    </>
  )
}

export default UserMenu
