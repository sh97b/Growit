import type { SVGProps } from 'react'

function Icon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    />
  )
}

export function HomeIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M3 11l9-8 9 8" />
      <path d="M5 10v10h14V10" />
    </Icon>
  )
}

export function ListIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M8 6h13M8 12h13M8 18h13" />
      <path d="M3 6h.01M3 12h.01M3 18h.01" />
    </Icon>
  )
}

export function PlusIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  )
}

export function ChartIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M4 19V10M12 19V5M20 19v-7" />
    </Icon>
  )
}

export function ChatIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M4 5h16v11H8l-4 4V5z" />
    </Icon>
  )
}

export function DotsIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <Icon {...props}>
      <path d="M4 7h10M18 7h2M4 17h2M10 17h10" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="8" cy="17" r="2" />
    </Icon>
  )
}
