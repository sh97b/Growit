// Growit 서비스 워커. push·notificationclick만 처리한다.
// fetch를 가로채거나 캐시를 만들지 않는다 — Vercel 배포 뒤에 낡은 화면이 남는 문제를 피하기 위해서다.

self.addEventListener('push', (event) => {
  let data = { title: 'Growit', body: '' }
  try {
    if (event.data) {
      data = { ...data, ...event.data.json() }
    }
  } catch {
    // JSON이 아니면 기본 문구만 보여준다.
  }

  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: 'growit-reminder',
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  event.waitUntil(
    (async () => {
      const clientsList = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const client of clientsList) {
        if ('focus' in client) {
          await client.focus()
          if ('navigate' in client) {
            await client.navigate('/records/new')
          }
          return
        }
      }
      await self.clients.openWindow('/records/new')
    })(),
  )
})
