/**
 * When and by whom a deploy build ran, for a table cell.
 * e.g. ("2026-11-14T10:00:00Z", "andy") -> "11/14/2026, 4:00:00 AM by andy"; no user -> just the date;
 * no date (no deploy build found, or the CI tool is down) -> "Unknown"
 */
export function deployedLabel(deployedAt: string | null, deployedBy: string | null): string {
  if (!deployedAt) return 'Unknown'
  const date = new Date(deployedAt).toLocaleString()
  return deployedBy ? `${date} by ${deployedBy}` : date
}
