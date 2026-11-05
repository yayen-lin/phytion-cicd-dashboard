import {NavLink, useLocation} from 'react-router'
import {Activity, Boxes, ClipboardList, Code, FlaskConical, Hammer, Rocket, Tag, Workflow} from 'lucide-react'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'

/** The CI/CD stages, in pipeline order. Stages without a path aren't built yet. */
const stages = [
  {label: 'Code', icon: Code, path: '/code'},
  {label: 'Build', icon: Hammer, path: '/build'},
  {label: 'Test', icon: FlaskConical},
  {label: 'Release', icon: Tag},
  {label: 'Deploy', icon: Rocket},
  {label: 'Operate', icon: Boxes},
  {label: 'Monitor', icon: Activity},
  {label: 'Plan', icon: ClipboardList},
]

/** The left sidebar: logo (links to the landing page) and the CI/CD stages. */
function StageSidebar() {
  const {pathname} = useLocation()

  return (
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
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail/>
    </Sidebar>
  )
}

export default StageSidebar
