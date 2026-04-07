<template>
  <div>
    <div class="page-header">
      <h1>System Logs</h1>
      <p class="text-secondary">Enhanced logging and visualization</p>
    </div>

    <!-- Filters -->
    <AppCard title="Filters" style="margin-bottom: var(--space-lg)">
      <div class="filter-row">
        <div class="filter-group">
          <label>Category</label>
          <select v-model="filters.category">
            <option value="">All</option>
            <option value="COLLECTOR">Collector</option>
            <option value="SYSTEM">System</option>
            <option value="BACKFILL">Backfill</option>
            <option value="ERROR">Error</option>
          </select>
        </div>
        <div class="filter-group">
          <label>Level</label>
          <select v-model="filters.level">
            <option value="">All</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
          </select>
        </div>
        <div class="filter-group">
          <label>Source</label>
          <select v-model="filters.source">
            <option value="">All</option>
            <option v-for="s in sources" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div class="filter-group filter-keyword">
          <label>Keyword</label>
          <input type="text" v-model="keywordInput" placeholder="Search messages..." />
        </div>
        <div class="filter-group">
          <label>Time Range</label>
          <div class="time-presets">
            <button v-for="p in timePresets" :key="p.label"
                    class="btn btn-sm" :class="{ active: activePreset === p.label }"
                    @click="applyPreset(p)">{{ p.label }}</button>
          </div>
        </div>
        <div class="filter-group filter-action">
          <button class="btn btn-primary btn-sm" @click="fetchAll">Refresh</button>
        </div>
      </div>
    </AppCard>

    <!-- Stats Summary -->
    <div class="stats-row">
      <AppCard class="stat-card">
        <div class="stat-value">{{ totalCount }}</div>
        <div class="stat-label">Total Logs</div>
      </AppCard>
      <AppCard class="stat-card stat-warn">
        <div class="stat-value">{{ warnCount }}</div>
        <div class="stat-label">Warnings</div>
      </AppCard>
      <AppCard class="stat-card stat-error">
        <div class="stat-value">{{ errorCount }}</div>
        <div class="stat-label">Errors</div>
      </AppCard>
    </div>

    <!-- Volume Chart -->
    <AppCard title="Log Volume" style="margin-bottom: var(--space-lg)">
      <AppChart :option="chartOption" :loading="statsLoading" height="250px" />
    </AppCard>

    <!-- Log Table -->
    <AppCard title="Log Entries" :no-padding="true">
      <LoadingSpinner :loading="logsLoading" text="Loading logs..." v-if="logsLoading && !logs.length" />
      <DataTable :columns="columns" :rows="logs" empty-text="No logs found"
                 :page="page" :total-pages="totalPages" @page-change="onPageChange">
        <template #time="{ value }">{{ formatTime(value) }}</template>
        <template #category="{ value }">
          <span class="category-tag">{{ value }}</span>
        </template>
        <template #level="{ value }">
          <StatusBadge :level="mapLevel(value)" :label="value" />
        </template>
        <template #durationMs="{ value }">
          {{ value != null ? (value / 1000).toFixed(1) + 's' : '-' }}
        </template>
        <template #message="{ row }">
          <div class="message-cell">
            <span>{{ row.message || '-' }}</span>
            <button v-if="row.errorDetail" class="btn-detail"
                    @click="toggleDetail(row)">
              {{ expandedRow === row ? 'Hide' : 'Detail' }}
            </button>
          </div>
          <pre v-if="expandedRow === row && row.errorDetail" class="error-detail">{{ row.errorDetail }}</pre>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { getLogs, getLogStats, getLogSources } from '@/api/logs'
import type { SystemLog, LogStats } from '@/types'
import AppCard from '@/components/AppCard.vue'
import AppChart from '@/components/AppChart.vue'
import DataTable from '@/components/DataTable.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const logs = ref<SystemLog[]>([])
const stats = ref<LogStats | null>(null)
const sources = ref<string[]>([])
const logsLoading = ref(false)
const statsLoading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const expandedRow = ref<SystemLog | null>(null)
const keywordInput = ref('')
let keywordTimer: ReturnType<typeof setTimeout> | null = null

const filters = ref({
  category: '',
  level: '',
  source: '',
  keyword: '',
})

const activePreset = ref('24h')

const timePresets = [
  { label: '1h', hours: 1 },
  { label: '6h', hours: 6 },
  { label: '24h', hours: 24 },
  { label: '7d', hours: 168 },
  { label: '30d', hours: 720 },
]

const timeRange = ref({
  start: new Date(Date.now() - 24 * 3600000).toISOString(),
  end: new Date().toISOString(),
})

const columns = [
  { key: 'time', label: 'Time', width: '160px' },
  { key: 'category', label: 'Category', width: '100px' },
  { key: 'level', label: 'Level', width: '80px' },
  { key: 'source', label: 'Source', width: '150px' },
  { key: 'message', label: 'Message' },
  { key: 'durationMs', label: 'Duration', width: '90px' },
]

const totalCount = computed(() => {
  if (!stats.value?.byLevel) return 0
  return Object.values(stats.value.byLevel).reduce((a, b) => a + b, 0)
})

const warnCount = computed(() => stats.value?.byLevel?.WARN ?? 0)
const errorCount = computed(() => stats.value?.byLevel?.ERROR ?? 0)

const chartOption = computed(() => {
  const hourly = stats.value?.hourly ?? []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['INFO', 'WARN', 'ERROR'], textStyle: { color: '#94a3b8' } },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: hourly.map(h => {
        const d = new Date(h.time)
        return d.toLocaleString('zh-TW', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
      }),
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      axisLine: { lineStyle: { color: '#334155' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#1e293b' } },
    },
    series: [
      {
        name: 'INFO',
        type: 'bar',
        stack: 'total',
        data: hourly.map(h => h.info),
        itemStyle: { color: '#4ade80' },
      },
      {
        name: 'WARN',
        type: 'bar',
        stack: 'total',
        data: hourly.map(h => h.warn),
        itemStyle: { color: '#facc15' },
      },
      {
        name: 'ERROR',
        type: 'bar',
        stack: 'total',
        data: hourly.map(h => h.error),
        itemStyle: { color: '#f87171' },
      },
    ],
  }
})

function applyPreset(preset: { label: string; hours: number }) {
  activePreset.value = preset.label
  timeRange.value = {
    start: new Date(Date.now() - preset.hours * 3600000).toISOString(),
    end: new Date().toISOString(),
  }
}

function mapLevel(level: string): string {
  switch (level) {
    case 'INFO': return 'success'
    case 'WARN': return 'warning'
    case 'ERROR': return 'error'
    default: return 'unknown'
  }
}

function formatTime(iso: string) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function toggleDetail(row: SystemLog) {
  expandedRow.value = expandedRow.value === row ? null : row
}

async function fetchLogs() {
  logsLoading.value = true
  try {
    const res = await getLogs({
      category: filters.value.category || undefined,
      level: filters.value.level || undefined,
      source: filters.value.source || undefined,
      keyword: filters.value.keyword || undefined,
      start: timeRange.value.start,
      end: timeRange.value.end,
      page: page.value,
      size: 20,
    })
    if (res.data.success && res.data.data) {
      logs.value = res.data.data.content
      totalPages.value = res.data.data.totalPages
    }
  } catch (e) {
    console.error('Failed to fetch logs:', e)
  } finally {
    logsLoading.value = false
  }
}

async function fetchStats() {
  statsLoading.value = true
  try {
    const res = await getLogStats({
      start: timeRange.value.start,
      end: timeRange.value.end,
    })
    if (res.data.success && res.data.data) {
      stats.value = res.data.data
    }
  } catch (e) {
    console.error('Failed to fetch log stats:', e)
  } finally {
    statsLoading.value = false
  }
}

async function fetchSources() {
  try {
    const res = await getLogSources()
    if (res.data.success && res.data.data) {
      sources.value = res.data.data
    }
  } catch (e) {
    console.error('Failed to fetch sources:', e)
  }
}

function fetchAll() {
  timeRange.value.end = new Date().toISOString()
  fetchLogs()
  fetchStats()
}

function onPageChange(newPage: number) {
  page.value = newPage
}

// Debounce keyword input
watch(keywordInput, (val) => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    filters.value.keyword = val
  }, 300)
})

// Reset page and refetch when filters or time range change
watch([() => filters.value.category, () => filters.value.level,
       () => filters.value.source, () => filters.value.keyword,
       () => timeRange.value.start], () => {
  page.value = 0
  fetchLogs()
  fetchStats()
})

watch(page, () => {
  fetchLogs()
})

// Auto-refresh every 30 seconds
let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchSources()
  fetchAll()
  refreshTimer = setInterval(fetchAll, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (keywordTimer) clearTimeout(keywordTimer)
})
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: var(--space-md);
  align-items: flex-end;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-width: 120px;
}

.filter-group label {
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.filter-group select,
.filter-group input {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
}

.filter-keyword {
  flex: 1;
  min-width: 180px;
}

.filter-action {
  min-width: auto;
}

.time-presets {
  display: flex;
  gap: 4px;
}

.time-presets .btn-sm {
  padding: 4px 10px;
  font-size: 0.75rem;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.time-presets .btn-sm.active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.stats-row {
  display: flex;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.stat-card {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

.stat-warn .stat-value {
  color: var(--color-caution);
}

.stat-error .stat-value {
  color: var(--color-severe);
}

.stat-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  margin-top: var(--space-xs);
}

.category-tag {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
}

.message-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.message-cell span {
  flex: 1;
  word-break: break-word;
}

.btn-detail {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-accent);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  cursor: pointer;
  white-space: nowrap;
}

.btn-detail:hover {
  background: var(--color-accent);
  color: #fff;
}

.error-detail {
  margin-top: var(--space-sm);
  padding: var(--space-sm);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--color-severe);
  font-family: var(--font-mono);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>
