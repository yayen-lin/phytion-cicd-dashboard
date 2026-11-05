import {Outlet} from 'react-router'
import {Separator} from '@/components/ui/separator'
import {SidebarInset, SidebarProvider, SidebarTrigger} from '@/components/ui/sidebar'
import StageSidebar from './StageSidebar'
import BackendStatus from './BackendStatus'
import ThemeMenu from './ThemeMenu'
import UserMenu from './UserMenu'

/** The frame around every page once logged in: the stage sidebar, and a top bar with status, theme and user menus. */
function AppLayout() {
  return (
    <SidebarProvider>
      <StageSidebar/>
      <SidebarInset className="bg-muted/30">
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1"/>
          <Separator orientation="vertical" className="mr-2 data-vertical:h-4"/>
          <div className="ml-auto flex items-center gap-1">
            <BackendStatus/>
            <ThemeMenu/>
            <UserMenu/>
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
