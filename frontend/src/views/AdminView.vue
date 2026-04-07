<template>
  <div>
    <div class="page-header">
      <h1>System Admin</h1>
      <p class="text-secondary">Collector management and data backfill</p>
    </div>

    <!-- Collectors -->
    <AppCard title="Data Collectors" :no-padding="true" style="margin-bottom: var(--space-lg)">
      <LoadingSpinner :loading="loading" text="Loading collectors..." v-if="loading && !collectors.length" />
      <DataTable :columns="collectorCols" :rows="collectors" empty-text="No collector data">
        <template #status="{ value }">
          <StatusBadge :level="value" />
        </template>
        <template #lastRunTime="{ value }">{{ formatTime(value) }}</template>
        <template #durationMs="{ value }">
          {{ value ? (value / 1000).toFixed(1) + 's' : '-' }}
        </template>
        <template #errorMessage="{ value }">
          <span class="text-danger" v-if="value">{{ value }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </DataTable>
    </AppCard>

    <!-- Backfill -->
    <AppCard title="Data Backfill">
      <form @submit.prevent="triggerBackfillAction" class="backfill-form">
        <div class="form-row">
          <div class="form-group">
            <label>Source</label>
            <select v-model="backfillSource" required>
              <option value="">Select source...</option>
              <option value="cwa-rainfall">CWA Rainfall</option>
              <option value="cwa-weather">CWA Weather</option>
              <option value="usgs-earthquake">USGS Earthquake</option>
              <option value="swpc-kp">SWPC Kp Index</option>
              <option value="swpc-dst">SWPC Dst Index</option>
            </select>
          </div>
          <div class="form-group">
            <label>Start Date</label>
            <input type="date" v-model="backfillStart" required />
          </div>
          <div class="form-group">
            <label>End Date</label>
            <input type="date" v-model="backfillEnd" required />
          </div>
          <div class="form-group form-action">
            <button type="submit" class="btn btn-primary" :disabled="backfillRunning">
              {{ backfillRunning ? 'Running...' : 'Start Backfill' }}
            </button>
          </div>
        </div>
        <div class="backfill-result" v-if="backfillResult">
          <pre>{{ JSON.stringify(backfillResult, null, 2) }}</pre>
        </div>
        <div class="text-danger" v-if="backfillError" style="margin-top: var(--space-md)">
          {{ backfillError }}
        </div>
      </form>
    </AppCard>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCollectors, triggerBackfill } from '@/api/system'
import AppCard from '@/components/AppCard.vue'
import DataTable from '@/components/DataTable.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const collectors = ref<any[]>([])
const loading = ref(false)
const backfillSource = ref('')
const backfillStart = ref('')
const backfillEnd = ref('')
const backfillRunning = ref(false)
const backfillResult = ref<any>(null)
const backfillError = ref('')

const collectorCols = [
  { key: 'source', label: 'Source' },
  { key: 'status', label: 'Status' },
  { key: 'lastRunTime', label: 'Last Run' },
  { key: 'persistedCount', label: 'Persisted' },
  { key: 'fetchedCount', label: 'Fetched' },
  { key: 'durationMs', label: 'Duration' },
  { key: 'errorMessage', label: 'Error' },
]

const fetchCollectors = async () => {
  loading.value = true
  try {
    const res = await getCollectors()
    if (res.data.success) collectors.value = res.data.data || []
  } catch (e) {
    console.error('Collectors fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchCollectors)

const triggerBackfillAction = async () => {
  backfillRunning.value = true
  backfillResult.value = null
  backfillError.value = ''
  try {
    const res = await triggerBackfill(backfillSource.value, backfillStart.value, backfillEnd.value)
    if (res.data.success) {
      backfillResult.value = res.data.data
    } else {
      backfillError.value = res.data.message || 'Backfill failed'
    }
  } catch (e: any) {
    backfillError.value = e.response?.data?.message || 'Backfill request failed'
  } finally {
    backfillRunning.value = false
  }
}

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
</script>

<style scoped>
.backfill-form {
  max-width: 800px;
}

.form-row {
  display: flex;
  gap: var(--space-md);
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  flex: 1;
  min-width: 150px;
}

.form-group label {
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.form-group select,
.form-group input {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
}

.form-action {
  flex: 0;
}

.backfill-result {
  margin-top: var(--space-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-sm);
  padding: var(--space-md);
  overflow-x: auto;
}

.backfill-result pre {
  color: var(--color-success);
  font-size: 0.8rem;
  font-family: var(--font-mono);
  white-space: pre-wrap;
}
</style>
