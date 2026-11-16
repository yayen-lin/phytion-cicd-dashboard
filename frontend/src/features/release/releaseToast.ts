import {toast} from 'sonner'

/**
 * Says a release started, with a "View build" button when the tag build has an id.
 * e.g. ("v1.4.0", "12") -> "Releasing v1.4.0: Build #12 started..."; ("v1.4.0", null) -> "...The build is queued..."
 */
export function showReleaseStarted(version: string, buildId: string | null, onViewBuild: (buildId: string) => void) {
  // the new version isn't in the registry until its build has run the tests and pushed the image
  toast.success(`Releasing ${version}`, {
    description: buildId
      ? `Build #${buildId} started. The version shows up in Versions once the build has pushed it.`
      : 'The build is queued. The version shows up in Versions once the build has pushed it.',
    action: buildId ? {label: 'View build', onClick: () => onViewBuild(buildId)} : undefined,
  })
}
