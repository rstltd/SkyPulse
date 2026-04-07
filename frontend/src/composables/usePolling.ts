import { onMounted, onUnmounted, ref } from 'vue'

export function usePolling(fn: () => Promise<void>, intervalMs = 60000) {
  const loading = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  const execute = async () => {
    loading.value = true
    try {
      await fn()
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    execute()
    timer = setInterval(execute, intervalMs)
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { loading, refresh: execute }
}
