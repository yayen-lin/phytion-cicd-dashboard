import {Navigate, Route, Routes} from 'react-router'
import AppLayout from '@/layout/AppLayout'
import BuildPage from '@/features/build/BuildPage'

/** The pages. Only rendered once logged in (see AuthProvider). */
function App() {
  return (
    <Routes>
      <Route element={<AppLayout/>}>
        {/* Landing page after login: blank for now, just the sidebar and top bar */}
        <Route index element={null}/>
        <Route path="build" element={<Navigate to="/build/builds" replace/>}/>
        {/* Each tab loads its own data each time it is opened */}
        <Route path="build/:tab" element={<BuildPage/>}/>
        <Route path="*" element={<Navigate to="/" replace/>}/>
      </Route>
    </Routes>
  )
}

export default App
