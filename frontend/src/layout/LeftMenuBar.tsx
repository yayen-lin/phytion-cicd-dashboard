import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { Boxes, History, LayoutDashboard, Server, Users, Workflow } from 'lucide-react'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  SidebarRail,
} from '@/components/ui/sidebar'
import { getConfiguredEnvironments } from '@/api/environments'
import { usePermissions } from '@/auth/AuthContext'

type MenuLinkProps = {
  label: string
  icon: typeof Server
  path: string
  isActive: boolean
}

function MenuLink({ label, icon: Icon, path, isActive }: MenuLinkProps) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={isActive} tooltip={label}>
        <NavLink to={path}>
          <Icon />
          <span>{label}</span>
        </NavLink>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
}

/** The left sidebar: Overview, Apps, a link per environment, History and Users. */
function LeftMenuBar() {
  const { pathname } = useLocation()
  const { isAdmin } = usePermissions()
  // null while loading
  const [environmentNames, setEnvironmentNames] = useState<string[] | null>(null)
  const [environmentsError, setEnvironmentsError] = useState(false)

  // load the environments
  useEffect(() => {
    getConfiguredEnvironments()
      .then((environments) => setEnvironmentNames(environments.map((e) => e.name)))
      .catch(() => setEnvironmentsError(true))
  }, [])

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <NavLink to="/">
                <span
                  className="flex aspect-square size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
                  <Workflow className="size-4" />
                </span>
                <span className="font-semibold">Phytion CI/CD</span>
              </NavLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <MenuLink label="Overview" icon={LayoutDashboard} path="/" isActive={pathname === '/'} />
              <MenuLink label="Apps" icon={Boxes} path="/apps" isActive={pathname.startsWith('/apps')} />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Environments</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {environmentsError ? (
                <SidebarMenuItem>
                  <SidebarMenuButton disabled tooltip="Could not load the environments">
                    <Server />
                    <span>Could not load</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ) : !environmentNames ? (
                [0, 1, 2].map((i) => (
                  <SidebarMenuItem key={i}>
                    <SidebarMenuSkeleton showIcon />
                  </SidebarMenuItem>
                ))
              ) : (
                environmentNames.map((name) => (
                  <MenuLink key={name} label={name} icon={Server} path={`/environments/${encodeURIComponent(name)}`}
                            isActive={pathname === `/environments/${encodeURIComponent(name)}`} />
                ))
              )}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Management</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <MenuLink label="History" icon={History} path="/history" isActive={pathname.startsWith('/history')} />
              {isAdmin && (
                <MenuLink label="Users" icon={Users} path="/users" isActive={pathname.startsWith('/users')} />
              )}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}

export default LeftMenuBar
