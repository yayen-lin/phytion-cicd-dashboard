import {Fragment} from 'react'
import {Link, useLocation, useNavigate} from 'react-router'
import {ArrowLeft} from 'lucide-react'
import {Button} from '@/components/ui/button'

/** One step of the breadcrumb; path is null when the step has no page of its own (e.g. "Environments") */
type Crumb = {
  label: string
  path: string | null
}

/**
 * Turns the page's path into breadcrumb steps, like GitHub's "owner / repo".
 * e.g. "/apps/sample-app/commits" -> [Apps (/apps), sample-app (/apps/sample-app)]
 * An unknown path returns [].
 */
function pathToCrumbs(pathname: string): Crumb[] {
  const [section, id] = pathname.split('/').filter(Boolean).map(decodeURIComponent)

  // top-level pages, then a page inside a section
  if (!section) return [{label: 'Overview', path: '/'}]
  if (section === 'apps') {
    return id ? [{label: 'Apps', path: '/apps'}, {label: id, path: `/apps/${encodeURIComponent(id)}`}] : [{label: 'Apps', path: '/apps'}]
  }
  if (section === 'environments' && id) return [{label: 'Environments', path: null}, {label: id, path: null}]
  if (section === 'history') return [{label: 'History', path: '/history'}]
  return []
}

/** The top bar's breadcrumb (Apps / sample-app), with a back button to the parent page when there is one. */
function PageBreadcrumb() {
  const {pathname} = useLocation()
  const navigate = useNavigate()
  const crumbs = pathToCrumbs(pathname)

  // the closest step above this page that has a page of its own
  const parentPath = crumbs.slice(0, -1).findLast((c) => c.path !== null)?.path ?? null

  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1">
      {parentPath && (
        <Button variant="ghost" size="icon-sm" aria-label="Back" title="Back" onClick={() => navigate(parentPath)}>
          <ArrowLeft/>
        </Button>
      )}
      <ol className="flex min-w-0 items-center gap-1.5 text-sm">
        {crumbs.map((crumb, i) => {
          const isCurrent = i === crumbs.length - 1
          return (
            <Fragment key={i}>
              {i > 0 && <li aria-hidden className="text-muted-foreground">/</li>}
              <li className="min-w-0 truncate">
                {isCurrent ? (
                  <span aria-current="page" className="font-semibold">{crumb.label}</span>
                ) : crumb.path ? (
                  <Link to={crumb.path} className="text-muted-foreground hover:text-foreground hover:underline">{crumb.label}</Link>
                ) : (
                  <span className="text-muted-foreground">{crumb.label}</span>
                )}
              </li>
            </Fragment>
          )
        })}
      </ol>
    </nav>
  )
}

export default PageBreadcrumb
