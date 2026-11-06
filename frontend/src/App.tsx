import {Navigate, Route, Routes} from 'react-router'
import AppLayout from '@/layout/AppLayout'
import BuildPage from '@/features/build/BuildPage'
import CodePage from '@/features/code/CodePage'
import HistoryPage from '@/features/history/HistoryPage'
import HomePage from '@/features/home/HomePage'
import ReleasePage from '@/features/release/ReleasePage'

/** The pages. Only rendered once logged in (see AuthProvider). */
function App() {
  return (
    <Routes>
      <Route element={<AppLayout/>}>
        {/* Landing page after login */}
        <Route index element={<HomePage/>}/>
        <Route path="code" element={<Navigate to="/code/branches" replace/>}/>
        <Route path="code/:tab" element={<CodePage/>}/>
        <Route path="build" element={<Navigate to="/build/builds" replace/>}/>
        {/* Each tab loads its own data each time it is opened */}
        <Route path="build/:tab" element={<BuildPage/>}/>
        <Route path="release" element={<ReleasePage/>}/>
        <Route path="history" element={<HistoryPage/>}/>
        <Route path="*" element={<Navigate to="/" replace/>}/>
      </Route>
    </Routes>
  )
}

export default App
