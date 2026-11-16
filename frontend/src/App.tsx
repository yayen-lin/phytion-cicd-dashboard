import { Navigate, Route, Routes } from 'react-router'
import AppLayout from '@/layout/AppLayout'
import AppPage from '@/features/apps/AppPage'
import AppsPage from '@/features/apps/AppsPage'
import EnvironmentPage from '@/features/environments/EnvironmentPage'
import HistoryPage from '@/features/history/HistoryPage'
import OverviewPage from '@/features/overview/OverviewPage'

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        {/* landing page after login */}
        <Route index element={<OverviewPage />} />
        <Route path="environments/:environment" element={<EnvironmentPage />} />
        <Route path="apps" element={<AppsPage />} />
        <Route path="apps/:appId" element={<Navigate to="commits" replace />} />
        <Route path="apps/:appId/:tab" element={<AppPage />} />
        <Route path="history" element={<HistoryPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
