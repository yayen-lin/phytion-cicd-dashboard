import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import type {App} from '@/api/apps'

type Props = {
  apps: App[]
  /** The selected app */
  value: App
  onChange: (appId: string) => void
  /** Which part of the app the page is about, shown under the dropdown, e.g. "Repo: andy-test-app/sample-app (Bitbucket)" */
  part: (app: App) => string
}

/** Picks one of the apps, and says which of its parts (repo, pipeline, image) the page shows. Used by every stage page. */
function AppSelect({apps, value, onChange, part}: Props) {
  return (
    <div className="flex flex-col items-end gap-1">
      <Select value={value.id} onValueChange={onChange}>
        <SelectTrigger className="w-64" aria-label="App">
          <SelectValue/>
        </SelectTrigger>
        <SelectContent>
          {apps.map((a) => <SelectItem key={a.id} value={a.id}>{a.id}</SelectItem>)}
        </SelectContent>
      </Select>
      <p className="text-xs text-muted-foreground">{part(value)}</p>
    </div>
  )
}

export default AppSelect
