import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ReviewSectionLayout } from './components/ReviewSectionLayout'
import { LoginPage } from './pages/LoginPage'
import { PlaceholderPage } from './pages/PlaceholderPage'
import { SettingsPage } from './pages/SettingsPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<PlaceholderPage title="홈" />} />
        <Route element={<ReviewSectionLayout />}>
          <Route path="/records" element={<PlaceholderPage title="기록 모아보기" />} />
          <Route path="/changes" element={<PlaceholderPage title="나의 변화" />} />
          <Route path="/coach" element={<PlaceholderPage title="AI 회고" />} />
        </Route>
        <Route path="/records/new" element={<PlaceholderPage title="기록 작성" />} />
        <Route path="/records/:id" element={<PlaceholderPage title="기록 상세" />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/hobbies" element={<PlaceholderPage title="활동 분야 관리" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
