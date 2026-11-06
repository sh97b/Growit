import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { GuestRoute } from './components/GuestRoute'
import { OnboardingRoute } from './components/OnboardingRoute'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ReviewSectionLayout } from './components/ReviewSectionLayout'
import { ChangesPage } from './pages/ChangesPage'
import { CoachHistoryDetailPage } from './pages/CoachHistoryDetailPage'
import { CoachHistoryPage } from './pages/CoachHistoryPage'
import { CoachPage } from './pages/CoachPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { MetricsDetailPage } from './pages/MetricsDetailPage'
import { OnboardingPage } from './pages/OnboardingPage'
import { PrivacyPage } from './pages/PrivacyPage'
import { RecordDetailPage } from './pages/RecordDetailPage'
import { RecordEditPage } from './pages/RecordEditPage'
import { RecordNewPage } from './pages/RecordNewPage'
import { RecordSavedPage } from './pages/RecordSavedPage'
import { RecordsPage } from './pages/RecordsPage'
import { SettingsPage } from './pages/SettingsPage'
import { SignupPage } from './pages/SignupPage'
import { WeekDetailPage } from './pages/WeekDetailPage'
import { AiPage } from './pages/settings/AiPage'
import { DeleteAccountPage } from './pages/settings/DeleteAccountPage'
import { HobbiesPage } from './pages/settings/HobbiesPage'
import { PasswordPage } from './pages/settings/PasswordPage'

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
      <Route path="/privacy" element={<PrivacyPage />} />
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
          <Route path="/changes" element={<ChangesPage />} />
          <Route path="/coach" element={<CoachPage />} />
        </Route>
        <Route path="/coach/history" element={<CoachHistoryPage />} />
        <Route path="/coach/history/:id" element={<CoachHistoryDetailPage />} />
        <Route path="/changes/metrics" element={<MetricsDetailPage />} />
        <Route path="/weeks/:weekStart" element={<WeekDetailPage />} />
        <Route path="/records/new" element={<RecordNewPage />} />
        <Route path="/records/:id/saved" element={<RecordSavedPage />} />
        <Route path="/records/:id" element={<RecordDetailPage />} />
        <Route path="/records/:id/edit" element={<RecordEditPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/hobbies" element={<HobbiesPage />} />
        <Route path="/settings/ai" element={<AiPage />} />
        <Route path="/settings/password" element={<PasswordPage />} />
        <Route path="/settings/delete" element={<DeleteAccountPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
