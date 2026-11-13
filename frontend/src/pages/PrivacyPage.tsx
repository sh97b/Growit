import { Link } from 'react-router-dom'
import { CONTACT_EMAIL } from '../config'

export function PrivacyPage() {
  return (
    <div className="app-shell-container flex flex-col gap-6 py-10">
      {/* LAYOUT-V2: 본문을 80rem 컨테이너 안의 8열(약 53rem)에 왼쪽 정렬한다(가운데 좁은 칼럼 금지). */}
      <div className="flex w-full max-w-[53rem] flex-col gap-6">
      <Link to="/" className="text-sm font-medium text-base-ink no-underline">
        ← Growit으로 가기
      </Link>

      <h1 className="font-serif text-2xl font-semibold">개인정보 처리방침</h1>

      <section className="flex flex-col gap-2">
        <h2 className="text-card-title">수집 항목</h2>
        <p className="text-body-min leading-[1.75] text-ink-2">
          이메일, 이름, 비밀번호(암호화해 저장), 활동 분야·기록·어려웠던 점·느낌·연습 시간, AI 호출 기록을 수집해요.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-card-title">이용 목적</h2>
        <p className="text-body-min leading-[1.75] text-ink-2">
          서비스 제공, 그리고 AI 정리·나의 변화·AI 회고를 만드는 데 써요.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-card-title">외부 전송</h2>
        <p className="text-body-min leading-[1.75] text-ink-2">
          AI 정리가 켜져 있으면 기록 내용이 서비스 기능 제공 목적으로 OpenAI API에 전송돼요.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-card-title">보관</h2>
        <p className="text-body-min leading-[1.75] text-ink-2">
          수업 프로젝트 기간 동안 보관하고, 이후 삭제될 수 있어요.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-card-title">내 권리</h2>
        <p className="text-body-min leading-[1.75] text-ink-2">
          설정에서 AI 정리를 끌 수 있고, AI 정리 결과를 지울 수 있어요. 비밀번호를 바꾸거나 계정을 삭제할 수도 있어요.
        </p>
      </section>

      {CONTACT_EMAIL ? (
        <section className="flex flex-col gap-2">
          <h2 className="text-card-title">문의</h2>
          <p className="text-body-min leading-[1.75] text-ink-2">{CONTACT_EMAIL}</p>
        </section>
      ) : null}

      <p className="border-t border-line pt-4 text-caption-min text-ink-3">
        이 안내는 법률 자문이 아닌, 수업 프로젝트를 위한 간이 안내예요.
      </p>
      </div>
    </div>
  )
}
