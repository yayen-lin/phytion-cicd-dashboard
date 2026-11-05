import {LogOut, UserRound} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {useAuth} from '@/auth/AuthContext'

/** The logged-in user's name, with Log out. */
function UserMenu() {
  const {user, logout} = useAuth()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <UserRound/>
          <span className="hidden sm:inline">{user.username}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          Logged in as <span className="font-semibold">{user.username}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator/>
        <DropdownMenuItem onSelect={logout}>
          <LogOut/> Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default UserMenu
