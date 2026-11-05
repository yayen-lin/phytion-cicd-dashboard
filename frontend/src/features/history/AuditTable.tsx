import {Badge} from '@/components/ui/badge'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import type {AuditEntry} from '@/api/audit'
import type {CiBuild} from '@/api/ci'
import StatusBadge from '@/features/build/StatusBadge'
import {formatActionType} from '@/lib/format'

type Props = {
  entries: AuditEntry[]
  /** Show the Target column; off when every entry has the same target */
  showTarget: boolean
  /** The current state of a build an entry is about, or undefined when unknown (then "-") */
  buildOf: (entry: AuditEntry) => CiBuild | undefined
  /** Opens the build's details. Without it, or for entries canOpenBuild rejects, the build number is plain text */
  onOpenBuild?: (entry: AuditEntry) => void
  /** Whether an entry's build can be opened, e.g. only builds of configured pipelines; all can when left out */
  canOpenBuild?: (entry: AuditEntry) => boolean
}

/** Audit log entries as a table: who did what, to what, when, and how it went. */
function AuditTable({entries, showTarget, buildOf, onOpenBuild, canOpenBuild}: Props) {
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
        {entries.map((entry) => {
          const build = buildOf(entry)
          return (
            <TableRow key={entry.id}>
              <TableCell className="text-muted-foreground">{new Date(entry.createdAt).toLocaleString()}</TableCell>
              <TableCell className="font-medium">{entry.username}</TableCell>
              <TableCell>{formatActionType(entry.actionType)}</TableCell>
              {showTarget && <TableCell className="font-mono text-xs">{entry.target}</TableCell>}
              <TableCell>
                {!entry.buildId ? '-' : onOpenBuild && (canOpenBuild?.(entry) ?? true) ? (
                  <button
                    type="button"
                    className="font-medium underline-offset-4 hover:underline"
                    aria-label={`Build #${entry.buildId} details`}
                    onClick={() => onOpenBuild(entry)}
                  >
                    #{entry.buildId}
                  </button>
                ) : `#${entry.buildId}`}
              </TableCell>
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
