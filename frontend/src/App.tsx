import { Navigate, Route, Routes } from 'react-router'
import AppLayout from '@/layout/AppLayout'
import AppPage from '@/pages/app/AppPage'
import AppsPage from '@/pages/apps/AppsPage'
import EnvironmentPage from '@/pages/environment/EnvironmentPage'
import HistoryPage from '@/pages/history/HistoryPage'
import OverviewPage from '@/pages/overview/OverviewPage'
import UsersPage from '@/pages/users/UsersPage'

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
        <Route path="users" element={<UsersPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
