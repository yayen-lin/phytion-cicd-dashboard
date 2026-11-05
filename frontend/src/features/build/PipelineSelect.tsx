import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import type {CiPipeline} from '@/api/ci'

const toolNames: Record<string, string> = {
  'jenkins': 'Jenkins',
  'github-actions': 'GitHub Actions',
}

/** "jenkins:sample-app" -> "Jenkins · sample-app" */
function pipelineLabel(pipeline: CiPipeline): string {
  return `${toolNames[pipeline.tool] ?? pipeline.tool} · ${pipeline.job}`
}

type Props = {
  pipelines: CiPipeline[]
  /** The selected pipeline's id, e.g. "jenkins:sample-app" */
  value: string
  onChange: (pipelineId: string) => void
}

/** Picks one of the configured CI pipelines. Used by the Build & Test page. */
function PipelineSelect({pipelines, value, onChange}: Props) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-64" aria-label="Pipeline">
        <SelectValue/>
      </SelectTrigger>
      <SelectContent>
        {pipelines.map((p) => (
          <SelectItem key={p.id} value={p.id}>{pipelineLabel(p)}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export default PipelineSelect
