<template>
  <div>
    <div class="page-header">
      <h1>Seismic Events</h1>
      <p class="text-secondary">Earthquake events (M4.0+, Taiwan region)</p>
    </div>

    <div class="toolbar">
      <label class="text-secondary">Min Magnitude:</label>
      <select v-model.number="minMag">
        <option :value="4">M4.0+</option>
        <option :value="4.5">M4.5+</option>
        <option :value="5">M5.0+</option>
        <option :value="6">M6.0+</option>
      </select>
      <label class="text-secondary">Period:</label>
      <select v-model.number="days">
        <option :value="7">7 days</option>
        <option :value="14">14 days</option>
        <option :value="30">30 days</option>
      </select>
      <div class="spacer"></div>
      <button class="btn btn-export" disabled>Export CSV</button>
    </div>

    <LoadingSpinner :loading="loading" text="Loading seismic data..." v-if="loading && !events.length" />

    <template v-if="events.length || !loading">
      <!-- Event Stats -->
      <div class="grid-3" style="margin-bottom: var(--space-lg)">
        <AppCard>
          <div class="stat-card">
            <span class="stat-value">{{ events.length }}</span>
            <span class="stat-label">Total Events</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="stat-card">
            <span class="stat-value text-warning">{{ maxMag }}</span>
            <span class="stat-label">Max Magnitude</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="stat-card">
            <span class="stat-value">{{ avgDepth }} km</span>
            <span class="stat-label">Avg Depth</span>
          </div>
        </AppCard>
      </div>

      <!-- Event Table -->
      <AppCard title="Earthquake Events" :no-padding="true">
        <DataTable :columns="eventCols" :rows="events" empty-text="No earthquake events found"
          :page="page" :total-pages="totalPages" @page-change="p => { page = p; fetchData() }">
          <template #time="{ value }">{{ formatTime(value) }}</template>
          <template #magnitude="{ row }">
            <span class="eq-mag" :class="getMagClass(row.magnitude)">M{{ row.magnitude }}</span>
          </template>
          <template #depthKm="{ value }">{{ value }} km</template>
          <template #source="{ value }">
            <StatusBadge :level="value === 'CWA' ? 'normal' : 'caution'" :label="value" />
          </template>
        </DataTable>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { getEvents } from '@/api/seismic'
import AppCard from '@/components/AppCard.vue'
import DataTable from '@/components/DataTable.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const events = ref<any[]>([])
const loading = ref(false)
const minMag = ref(4)
const days = ref(7)
const page = ref(0)
const totalPages = ref(0)

const eventCols = [
  { key: 'time', label: 'Time' },
  { key: 'magnitude', label: 'Mag' },
  { key: 'depthKm', label: 'Depth' },
  { key: 'locationDesc', label: 'Location' },
  { key: 'source', label: 'Source' },
]

const fetchData = async () => {
  loading.value = true
  try {
    const since = new Date(Date.now() - days.value * 86400000).toISOString()
    const res = await getEvents(since, minMag.value, page.value, 20)
    if (res.data.success && res.data.data) {
      events.value = res.data.data.content || []
      totalPages.value = res.data.data.totalPages || 0
    }
  } catch (e) {
    console.error('Seismic fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
watch([minMag, days], () => { page.value = 0; fetchData() })

const maxMag = computed(() => {
  if (!events.value.length) return '-'
  return Math.max(...events.value.map(e => e.magnitude)).toFixed(1)
})

const avgDepth = computed(() => {
  if (!events.value.length) return '-'
  const avg = events.value.reduce((s, e) => s + (e.depthKm || 0), 0) / events.value.length
  return avg.toFixed(1)
})

const getMagClass = (mag: number) => {
  if (mag >= 6) return 'severe'
  if (mag >= 5) return 'degraded'
  if (mag >= 4.5) return 'caution'
  return 'normal'
}

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  font-family: var(--font-mono);
}

.stat-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  text-transform: uppercase;
}

.eq-mag {
  font-family: var(--font-mono);
  font-weight: 700;
}

.eq-mag.normal { color: var(--color-normal); }
.eq-mag.caution { color: var(--color-caution); }
.eq-mag.degraded { color: var(--color-degraded); }
.eq-mag.severe { color: var(--color-severe); }
</style>
