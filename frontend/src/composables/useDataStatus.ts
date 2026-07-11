import { ref, computed } from 'vue'

export interface ApiFailure {
  url: string
  status: number | null
  at: Date
}

// Module-level singleton state shared across the app (survives component remounts).
const lastFailure = ref<ApiFailure | null>(null)
const dismissedAt = ref(0)

/**
 * Record a failed data request. Called from the axios interceptor so that a page never
 * renders "couldn't load" as if it were "all clear" — a dangerous silent-failure mode for
 * an early-warning monitor. Phase 4 will extend this into a per-source freshness dashboard.
 */
export function reportApiFailure(url: string, status: number | null) {
  lastFailure.value = { url, status, at: new Date() }
}

export function useDataStatus() {
  const activeFailure = computed<ApiFailure | null>(() =>
    lastFailure.value && lastFailure.value.at.getTime() > dismissedAt.value
      ? lastFailure.value
      : null,
  )
  const dismiss = () => {
    dismissedAt.value = Date.now()
  }
  return { activeFailure, dismiss }
}
