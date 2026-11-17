import {ExternalLink} from 'lucide-react'
import type {CodeCommit} from '@/api/code'

/** The short commit hash, linking to the commit in the code tool, in a new tab. */
function CommitLink({commit}: { commit: Pick<CodeCommit, 'hash' | 'url'> }) {
  return (
    <a
      href={commit.url}
      target="_blank"
      rel="noreferrer"
      title={commit.hash}
      className="inline-flex items-center gap-1 rounded bg-muted px-1.5 py-0.5 font-mono text-xs hover:underline"
    >
      {commit.hash.slice(0, 7)}
      <ExternalLink className="size-3 text-muted-foreground"/>
    </a>
  )
}

export default CommitLink
