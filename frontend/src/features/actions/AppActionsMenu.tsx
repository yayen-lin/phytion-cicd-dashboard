import { useState } from 'react'
import { toast } from 'sonner'
import { ArrowUpRight, MoreHorizontal, Play, Rocket, Tag, Undo2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { getApp, type App } from '@/api/apps'
import { triggerBuild } from '@/api/ci'
import type { Environment } from '@/api/environments'
import { usePermissions } from '@/auth/AuthContext'
import BuildDetailsDialog from '@/features/build/BuildDetailsDialog'
import DeployDialog from '@/features/deploy/DeployDialog'
import PromoteDialog, { type PromoteChoice } from '@/features/deploy/PromoteDialog'
import RollbackDialog from '@/features/deploy/RollbackDialog'
import ReleaseDialog from '@/features/release/ReleaseDialog'

/** An environment the menu acts in, with the app's version there */
export type AppInEnvironment = {
  environment: Environment
  version: string | null // null when not running there
  nextEnvironment: Environment | null // null for the last one
}

type Props = {
  appId: string
  /** The app's config; when left out, it's read when Release is picked */
  app?: App
  /** The environments the menu acts in, in order */
  environments: AppInEnvironment[]
  /** After an action starts or ends, to reload the view */
  onChanged: () => void
}

/**
 * One app's action menu and its dialogs; actions the role can't use are hidden, ones with nothing to act on disabled.
 */
function AppActionsMenu({ appId, app, environments, onChanged }: Props) {
  const { canActIn } = usePermissions()
  // the open dialog
  const [releasing, setReleasing] = useState<App | null>(null)
  const [deploying, setDeploying] = useState(false)
  const [promoting, setPromoting] = useState(false)
  const [rollingBack, setRollingBack] = useState(false)
  // the build shown in the details modal, or null when it's closed
  const [selectedBuildId, setSelectedBuildId] = useState<string | null>(null)

  // what each action can act on
  const deployEnvironments = environments.filter((e) => canActIn(e.environment)).map((e) => e.environment)
  const promoteFrom = environments.filter((e) => e.nextEnvironment && canActIn(e.nextEnvironment))
  const promoteChoices: PromoteChoice[] = promoteFrom.flatMap((e) => e.version && e.nextEnvironment
    ? [{ version: e.version, fromEnvironment: e.environment.name, toEnvironment: e.nextEnvironment }]
    : [])
  const rollbackEnvironments = environments
    .filter((e) => e.version && canActIn(e.environment))
    .map((e) => e.environment)

  const releaseConfigured = !app || !!app.release
  const deployConfigured = !app || (!!app.release && !!app.deploy)

  // start a build and say so, with a button to open it
  async function handleRunBuild() {
    try {
      const buildId = await triggerBuild(appId)
      if (buildId) {
        toast.success(`Started build #${buildId} of ${appId}`, {
          action: { label: 'View build', onClick: () => setSelectedBuildId(buildId) },
        })
      } else {
        toast.info(`Build of ${appId} queued, but the CI tool has not started it yet`)
      }
    } catch {
      toast.error(`Could not start a build of ${appId}`)
    } finally {
      onChanged()
    }
  }

  // the Release dialog needs the app's config, so read it first when it wasn't given
  async function handleRelease() {
    if (app) {
      setReleasing(app)
      return
    }
    try {
      const loadedApp = await getApp(appId)
      if (!loadedApp) toast.error(`Unknown app: ${appId}`)
      else if (!loadedApp.release) toast.error(`${appId} has no release configured`)
      else setReleasing(loadedApp)
    } catch {
      toast.error(`Could not load ${appId}`)
    }
  }

  function handleReleased(version: string, buildId: string | null) {
    setReleasing(null)

    // show the tag build right away
    if (buildId) setSelectedBuildId(buildId)
    else toast.info(`Releasing ${version}: the build is queued, but the CI tool has not started it yet`)
  }

  const label = environments.length === 1
    ? `Actions for ${appId} in ${environments[0].environment.name}`
    : `Actions for ${appId}`

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="size-8" aria-label={label}>
            <MoreHorizontal />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={handleRunBuild}>
            <Play /> Run build
          </DropdownMenuItem>
          <DropdownMenuItem disabled={!releaseConfigured} onSelect={handleRelease}>
            <Tag /> Release
          </DropdownMenuItem>
          {deployEnvironments.length > 0 && (
            <DropdownMenuItem disabled={!deployConfigured} onSelect={() => setDeploying(true)}>
              <Rocket /> Deploy
            </DropdownMenuItem>
          )}
          {/* hidden from the last environment; disabled when no version runs where it could promote from */}
          {promoteFrom.length > 0 && (
            <DropdownMenuItem disabled={promoteChoices.length === 0} onSelect={() => setPromoting(true)}>
              <ArrowUpRight /> Promote
            </DropdownMenuItem>
          )}
          {deployEnvironments.length > 0 && (
            <DropdownMenuItem disabled={rollbackEnvironments.length === 0} onSelect={() => setRollingBack(true)}>
              <Undo2 /> Rollback
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {releasing && (
        <ReleaseDialog app={releasing} open onOpenChange={(open) => !open && setReleasing(null)}
                       onReleased={handleReleased} />
      )}
      {deploying && (
        <DeployDialog appId={appId} environments={deployEnvironments}
                      pickedEnvironment={deployEnvironments.length === 1 ? deployEnvironments[0].name : null}
                      open onOpenChange={setDeploying} onDeployed={onChanged} />
      )}
      {promoting && (
        <PromoteDialog appId={appId} choices={promoteChoices}
                       open onOpenChange={setPromoting} onPromoted={onChanged} />
      )}
      {rollingBack && (
        <RollbackDialog appId={appId} environments={rollbackEnvironments}
                        open onOpenChange={setRollingBack} onRolledBack={onChanged} />
      )}
      <BuildDetailsDialog appId={appId} buildId={selectedBuildId} onClose={() => setSelectedBuildId(null)} />
    </>
  )
}

export default AppActionsMenu
