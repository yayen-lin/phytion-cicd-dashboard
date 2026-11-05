import {useTheme} from 'next-themes'
import {Monitor, Moon, Sun} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

/** Light / dark / follow the OS. */
function ThemeMenu() {
  const {theme, setTheme} = useTheme()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Theme">
          <Sun className="dark:hidden"/>
          <Moon className="hidden dark:block"/>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuRadioGroup value={theme} onValueChange={setTheme}>
          <DropdownMenuRadioItem value="light"><Sun/> Light</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark"><Moon/> Dark</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system"><Monitor/> System</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default ThemeMenu
