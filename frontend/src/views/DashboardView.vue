<template>
  <div>
    <div class="page-header">
      <h1>Dashboard</h1>
      <p class="text-secondary">
        System overview &middot; Last updated: {{ lastUpdated || '-' }}
      </p>
    </div>

    <LoadingSpinner :loading="loading" text="Loading dashboard data..." v-if="loading && !data" />

    <template v-if="data">
      <!-- GNSS Quality -->
      <div class="grid-4" style="margin-bottom: var(--space-lg)">
        <AppCard>
          <div class="metric-card">
            <span class="metric-label">GNSS Quality</span>
            <StatusBadge :level="data.gnssQuality?.qualityLevel || 'UNKNOWN'" />
          </div>
        </AppCard>
        <AppCard>
          <div class="metric-card">
            <span class="metric-label">Kp Index</span>
            <span class="metric-value">{{ data.gnssQuality?.kpIndex ?? '-' }}</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="metric-card">
            <span class="metric-label">Dst Index</span>
            <span class="metric-value">{{ data.gnssQuality?.dstIndex ?? '-' }} nT</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="metric-card">
            <span class="metric-label">Solar Wind</span>
            <span class="metric-value">{{ data.gnssQuality?.solarWindSpeed ?? '-' }} km/s</span>
          </div>
        </AppCard>
      </div>

      <!-- Charts Row -->
      <div class="grid-2" style="margin-bottom: var(--space-lg)">
        <AppCard title="Kp Index (24h)">
          <AppChart :option="kpChartOption" height="250px" :loading="chartLoading" />
        </AppCard>
        <AppCard title="Dst Index (24h)">
          <AppChart :option="dstChartOption" height="250px" :loading="chartLoading" />
        </AppCard>
      </div>

      <!-- Collectors -->
      <AppCard title="Data Collectors" :subtitle="`${data.collectors?.length || 0} sources`"
        style="margin-bottom: var(--space-lg)">
        <DataTable :columns="collectorCols" :rows="data.collectors || []" :no-padding="true">
          <template #status="{ value }">
            <StatusBadge :level="value" />
          </template>
          <template #lastRunTime="{ value }">
            {{ formatTime(value) }}
          </template>
          <template #durationMs="{ value }">
            {{ value ? (value / 1000).toFixed(1) + 's' : '-' }}
          </template>
        </DataTable>
      </AppCard>

      <!-- Earthquakes & Alerts -->
      <div class="grid-2">
        <AppCard title="Recent Earthquakes" :subtitle="'Last 7 days'">
          <div v-if="!data.recentEarthquakes?.length" class="text-muted" style="padding: var(--space-md)">
            No recent earthquake events
          </div>
          <div v-for="eq in data.recentEarthquakes" :key="eq.eventId" class="list-item">
            <div class="list-item-main">
              <span class="eq-magnitude" :class="getMagnitudeClass(eq.magnitude)">
                M{{ eq.magnitude }}
              </span>
              <span>{{ eq.locationDesc }}</span>
            </div>
            <div class="list-item-meta">
              {{ formatTime(eq.time) }} &middot; {{ eq.source }}
            </div>
          </div>
        </AppCard>

        <AppCard title="Active Alerts" :subtitle="'Last 24h'">
          <div v-if="!data.activeAlerts?.length" class="text-muted" style="padding: var(--space-md)">
            No active alerts
          </div>
          <div v-for="alert in data.activeAlerts" :key="alert.id" class="list-item">
            <div class="list-item-main">
              <StatusBadge :level="alert.severity || alert.alertType" :label="alert.alertType" />
              <span>{{ alert.title }}</span>
            </div>
            <div class="list-item-meta">{{ formatTime(alert.alertTime) }}</div>
          </div>
        </AppCard>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getMonitorSummary } from '@/api/monitor'
import { getKpHistory, getDstHistory } from '@/api/spaceweather'
import { usePolling } from '@/composables/usePolling'
import AppCard from '@/components/AppCard.vue'
import AppChart from '@/components/AppChart.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import DataTable from '@/components/DataTable.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import type { MonitorSummary, KpRecord, DstRecord } from '@/types'

const data = ref<MonitorSummary | null>(null)
const kpData = ref<KpRecord[]>([])
const dstData = ref<DstRecord[]>([])
const lastUpdated = ref('')
const chartLoading = ref(false)

const collectorCols = [
  { key: 'source', label: 'Source' },
  { key: 'status', label: 'Status' },
  { key: 'lastRunTime', label: 'Last Run' },
  { key: 'persistedCount', label: 'Persisted' },
  { key: 'fetchedCount', label: 'Fetched' },
  { key: 'durationMs', label: 'Duration' },
]

const fetchData = async () => {
  try {
    const [summaryRes, kpRes, dstRes] = await Promise.all([
      getMonitorSummary(),
      getKpHistory(24, 0, 200).catch(() => null),
      getDstHistory(24, 0, 200).catch(() => null),
    ])
    if (summaryRes.data.success) {
      data.value = summaryRes.data.data
    }
    if (kpRes?.data?.success && kpRes.data.data) {
      kpData.value = kpRes.data.data.content || []
    }
    if (dstRes?.data?.success && dstRes.data.data) {
      dstData.value = dstRes.data.data.content || []
    }
    lastUpdated.value = new Date().toLocaleTimeString('zh-TW')
  } catch (e) {
    console.error('Dashboard fetch error:', e)
  }
}

const { loading } = usePolling(fetchData, 60000)

const kpChartOption = computed(() => ({
  grid: { top: 30, right: 20, bottom: 40, left: 50 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'time',
    axisLabel: { color: '#94a3b8', fontSize: 11 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: 'Kp',
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: '#334155' } },
  },
  series: [{
    type: 'line',
    data: [...kpData.value].reverse().map(d => [d.time, d.kpValue]),
    smooth: true,
    lineStyle: { color: '#38bdf8', width: 2 },
    itemStyle: { color: '#38bdf8' },
    areaStyle: { color: 'rgba(56, 189, 248, 0.1)' },
  }],
  dataZoom: [{ type: 'inside' }],
}))

const dstChartOption = computed(() => ({
  grid: { top: 30, right: 20, bottom: 40, left: 50 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'time',
    axisLabel: { color: '#94a3b8', fontSize: 11 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: 'Dst (nT)',
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: '#334155' } },
  },
  series: [{
    type: 'line',
    data: [...dstData.value].reverse().map(d => [d.time, d.dstValue]),
    smooth: true,
    lineStyle: { color: '#fb923c', width: 2 },
    itemStyle: { color: '#fb923c' },
    areaStyle: { color: 'rgba(251, 146, 60, 0.1)' },
  }],
  dataZoom: [{ type: 'inside' }],
}))

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

const getMagnitudeClass = (mag: number) => {
  if (mag >= 6) return 'severe'
  if (mag >= 5) return 'degraded'
  if (mag >= 4.5) return 'caution'
  return 'normal'
}
</script>

<style scoped>
.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
}

.metric-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.metric-value {
  font-size: 1.25rem;
  font-weight: 600;
  font-family: var(--font-mono);
}

.list-item {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-border);
}

.list-item:last-child {
  border-bottom: none;
}

.list-item-main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.list-item-meta {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  padding-left: var(--space-xl);
}

.eq-magnitude {
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 0.9rem;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.eq-magnitude.normal { color: var(--color-normal); }
.eq-magnitude.caution { color: var(--color-caution); }
.eq-magnitude.degraded { color: var(--color-degraded); }
.eq-magnitude.severe { color: var(--color-severe); }
</style>
