import { Outlet } from 'react-router-dom'
import { Header } from './Header'
import { RecordFab } from './RecordFab'
import { TabBar } from './TabBar'
import { useOfflineSync } from '../hooks/useOfflineSync'

export function AppLayout() {
  useOfflineSync()

  return (
    // Q1 5번: 모바일은 min-h-dvh(최소 높이)가 아니라 h-dvh(고정 높이)여야 한다 — flex 컨테이너의
    // 높이가 "auto"면(최소값만 있고 정해진 높이가 없으면) flex-1인 main이 실제로 남는 공간만큼만
    // 차지하도록 계산되지 않고 내용 높이만큼 늘어나 버려서(overflow-y-auto가 안 걸리고) 문서(body)
    // 자체가 스크롤되고 탭바는 그 긴 문서의 맨 끝에 있었다. h-dvh로 고정해야 main이 "남은 공간"
    // 이라는 정해진 높이를 받아 그 안에서만 스크롤하고, 탭바가 항상 화면 아래에 머문다.
    <div className="flex h-dvh flex-col overflow-hidden bg-paper text-ink lg:grid lg:grid-rows-[auto_minmax(0,1fr)]">
      <Header />
      {/*
        S1 C-1단계: 프로필 아이콘만 있던 모바일 상단 바를 삭제한다(계정 메뉴 항목은 전부 설정 탭
        안에 있다). 본문은 그 자리 대신 safe-area-inset-top만큼만 위 여백을 갖는다.
      */}
      <main
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain lg:flex lg:flex-col lg:overflow-hidden"
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        {/*
          O1(C.1): 데스크톱에서는 main도 app-shell-main도 스크롤하지 않는다. 전에는 app-shell-main이
          페이지 전체를 스크롤했는데, 그 안의 개별 열(예: 돌아보기 상세 열)도 자기 스크롤을 갖고 있어서
          스크롤바가 두 개(바깥 페이지 + 안쪽 열) 겹쳐 보였다. 이제 스크롤 책임은 각 페이지의 열에게만
          있고, app-shell-main은 패딩만 제공하는 고정 높이 컨테이너다.
          S1 C-3단계: 모바일은 아래쪽에 FAB(+탭바)가 떠 있으므로 마지막 내용이 가려지지 않게
          아래 패딩을 더 둔다(데스크톱은 그대로 py-6).
        */}
        <div className="app-shell-main app-shell-container pb-28 pt-6 lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:overflow-hidden lg:py-6">
          <Outlet />
        </div>
      </main>
      <RecordFab />
      <TabBar />
    </div>
  )
}
