export function Footer() {
  return (
    <footer className="hidden h-[52px] flex-none items-center border-t border-line bg-paper lg:flex">
      <div className="mx-auto flex w-full max-w-[1200px] items-center justify-between px-10 text-[13px] text-ink-3">
        <span>© 2026 Growit</span>
        <nav className="flex gap-5">
          <span>이용약관</span>
          <span>개인정보 처리방침</span>
          <span>문의</span>
        </nav>
      </div>
    </footer>
  )
}
