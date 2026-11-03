import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { GuestRoute } from './components/GuestRoute'
import { OnboardingRoute } from './components/OnboardingRoute'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ReviewSectionLayout } from './components/ReviewSectionLayout'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { OnboardingPage } from './pages/OnboardingPage'
import { PlaceholderPage } from './pages/PlaceholderPage'
import { RecordDetailPage } from './pages/RecordDetailPage'
import { RecordEditPage } from './pages/RecordEditPage'
import { RecordNewPage } from './pages/RecordNewPage'
import { RecordsPage } from './pages/RecordsPage'
import { SettingsPage } from './pages/SettingsPage'
import { SignupPage } from './pages/SignupPage'
import { AiPage } from './pages/settings/AiPage'
import { HobbiesPage } from './pages/settings/HobbiesPage'

function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <GuestRoute>
            <LoginPage />
          </GuestRoute>
        }
      />
      <Route
        path="/signup"
        element={
          <GuestRoute>
            <SignupPage />
          </GuestRoute>
        }
      />
      <Route
        path="/onboarding"
        element={
          <OnboardingRoute>
            <OnboardingPage />
          </OnboardingRoute>
        }
      />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route element={<ReviewSectionLayout />}>
          <Route path="/records" element={<RecordsPage />} />
          <Route path="/changes" element={<PlaceholderPage title="나의 변화" />} />
          <Route path="/coach" element={<PlaceholderPage title="AI 회고" />} />
        </Route>
        <Route path="/records/new" element={<RecordNewPage />} />
        <Route path="/records/:id" element={<RecordDetailPage />} />
        <Route path="/records/:id/edit" element={<RecordEditPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/hobbies" element={<HobbiesPage />} />
        <Route path="/settings/ai" element={<AiPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
