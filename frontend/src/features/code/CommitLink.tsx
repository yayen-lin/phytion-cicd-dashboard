import type {CodeCommit} from '@/api/code'

/** The short commit hash, linking to the commit in the code tool. */
function CommitLink({commit}: { commit: CodeCommit }) {
  return (
    <a
      href={commit.url}
      target="_blank"
      rel="noreferrer"
      title={commit.hash}
      className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs hover:underline"
    >
      {commit.hash.slice(0, 7)}
    </a>
  )
}

export default CommitLink
