import {NavLink, Outlet, useLocation} from 'react-router'
import {useTheme} from 'next-themes'
import {
  Activity,
  Boxes,
  ClipboardList,
  Code,
  FlaskConical,
  Hammer,
  LogOut,
  Monitor,
  Moon,
  Rocket,
  Sun,
  Tag,
  UserRound,
  Workflow,
} from 'lucide-react'
import {Button} from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {Separator} from '@/components/ui/separator'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import {Tooltip, TooltipContent, TooltipTrigger} from '@/components/ui/tooltip'
import {cn} from '@/lib/utils'
import type {User} from './api'

type Props = {
  user: User
  /** What /api/status answered, e.g. "ok" */
  backendStatus: string
  onLogout: () => void
}

/** The CI/CD stages, in pipeline order. Stages without a path aren't built yet. */
const stages = [
  {label: 'Code', icon: Code},
  {label: 'Build', icon: Hammer, path: '/build'},
  {label: 'Test', icon: FlaskConical},
  {label: 'Release', icon: Tag},
  {label: 'Deploy', icon: Rocket},
  {label: 'Operate', icon: Boxes},
  {label: 'Monitor', icon: Activity},
  {label: 'Plan', icon: ClipboardList},
]

function StageMenu() {
  const {pathname} = useLocation()
  return (
    <SidebarMenu>
      {stages.map(({label, icon: Icon, path}) => (
        <SidebarMenuItem key={label}>
          {path ? (
            <SidebarMenuButton asChild isActive={pathname.startsWith(path)} tooltip={label}>
              <NavLink to={path}>
                <Icon/>
                <span>{label}</span>
              </NavLink>
            </SidebarMenuButton>
          ) : (
            <>
              <SidebarMenuButton disabled tooltip={`${label} (not yet)`}>
                <Icon/>
                <span>{label}</span>
              </SidebarMenuButton>
              <SidebarMenuBadge className="text-muted-foreground">soon</SidebarMenuBadge>
            </>
          )}
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  )
}

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

/** The frame around every page once logged in: the stage sidebar, and a top bar with theme and user menus. */
function AppLayout({user, backendStatus, onLogout}: Props) {
  const backendOk = backendStatus === 'ok'

  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton size="lg" asChild>
                <NavLink to="/">
                  <span className="flex aspect-square size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
                    <Workflow className="size-4"/>
                  </span>
                  <span className="font-semibold">Phytion CI/CD</span>
                </NavLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Stages</SidebarGroupLabel>
            <SidebarGroupContent>
              <StageMenu/>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarRail/>
      </Sidebar>

      <SidebarInset className="bg-muted/30">
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1"/>
          <Separator orientation="vertical" className="mr-2 data-vertical:h-4"/>

          <div className="ml-auto flex items-center gap-1">
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="mr-2 flex items-center gap-2 text-xs text-muted-foreground">
                  <span className={cn('size-2 rounded-full', backendOk ? 'bg-emerald-500' : 'bg-amber-500')}/>
                  <span className="hidden sm:inline">Backend</span>
                </span>
              </TooltipTrigger>
              <TooltipContent>Backend status: {backendStatus}</TooltipContent>
            </Tooltip>

            <ThemeMenu/>

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
                <DropdownMenuItem onSelect={onLogout}>
                  <LogOut/> Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        <div className="mx-auto w-full max-w-6xl px-4 py-8">
          <Outlet/>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default AppLayout
