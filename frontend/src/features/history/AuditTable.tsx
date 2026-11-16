import {ExternalLink} from 'lucide-react'
import {Badge} from '@/components/ui/badge'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import type {HistoryEntry} from '@/api/history'
import StatusBadge from '@/features/build/StatusBadge'
import {formatActionType} from '@/lib/format'

type Props = {
  entries: HistoryEntry[]
  /** Show the Target column; off when every entry has the same target */
  showTarget: boolean
  /** Opens a build or release's build in the details dialog */
  onOpenBuild: (entry: HistoryEntry) => void
}

/**
 * The build an entry started: a deploy build links to the CI tool (it has no tests to show), a build or release's
 * build opens the details dialog; a build that wasn't found is plain text, and no build is "-".
 */
function BuildCell({item, onOpenBuild}: { item: HistoryEntry, onOpenBuild: Props['onOpenBuild'] }) {
  const {entry, build} = item
  if (!entry.buildId) return '-'
  if (!build) return `#${entry.buildId}`

  if (entry.actionType === 'DEPLOY') {
    return (
      <a href={build.url} target="_blank" rel="noreferrer"
         className="inline-flex items-center gap-1 font-medium underline-offset-4 hover:underline">
        #{entry.buildId} <ExternalLink className="size-3.5"/>
      </a>
    )
  }
  return (
    <button type="button" className="font-medium underline-offset-4 hover:underline"
            aria-label={`Build #${entry.buildId} details`} onClick={() => onOpenBuild(item)}>
      #{entry.buildId}
    </button>
  )
}

/** Audit log entries as a table: who did what, to what, when, and how it went. */
function AuditTable({entries, showTarget, onOpenBuild}: Props) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>When</TableHead>
          <TableHead>Who</TableHead>
          <TableHead>What</TableHead>
          {showTarget && <TableHead>Target</TableHead>}
          <TableHead>Build</TableHead>
          <TableHead>Build status</TableHead>
          <TableHead>Action result</TableHead>
          <TableHead>Details</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map((item) => {
          const {entry, build} = item
          return (
            <TableRow key={entry.id}>
              <TableCell className="text-muted-foreground">{new Date(entry.createdAt).toLocaleString()}</TableCell>
              <TableCell className="font-medium">{entry.username}</TableCell>
              <TableCell>{formatActionType(entry.actionType)}</TableCell>
              {showTarget && <TableCell className="font-mono text-xs">{entry.target}</TableCell>}
              <TableCell><BuildCell item={item} onOpenBuild={onOpenBuild}/></TableCell>
              <TableCell>{build ? <StatusBadge status={build.status}/> : '-'}</TableCell>
              <TableCell>
                <Badge variant={entry.actionResult === 'SUCCESS' ? 'outline' : 'destructive'}>
                  {entry.actionResult === 'SUCCESS' ? 'Done' : 'Failed'}
                </Badge>
              </TableCell>
              <TableCell className="max-w-xs truncate text-muted-foreground" title={entry.details ?? undefined}>
                {entry.details ?? ''}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

export default AuditTable
