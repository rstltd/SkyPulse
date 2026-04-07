<template>
  <div>
    <div class="page-header">
      <h1>Space Weather</h1>
      <p class="text-secondary">Geomagnetic indices and GNSS quality assessment</p>
    </div>

    <LoadingSpinner :loading="loading && !gnssQuality" text="Loading space weather data..." />

    <template v-if="gnssQuality">
      <!-- GNSS Quality Banner -->
      <AppCard style="margin-bottom: var(--space-lg)">
        <div class="gnss-banner">
          <div class="gnss-quality-main">
            <StatusBadge :level="gnssQuality.qualityLevel" style="font-size: 1rem; padding: 6px 16px;" />
            <p class="gnss-assessment">{{ gnssQuality.assessment }}</p>
          </div>
          <div class="gnss-metrics">
            <div class="gnss-metric">
              <span class="metric-label">Kp</span>
              <span class="metric-value">{{ gnssQuality.kpIndex ?? '-' }}</span>
            </div>
            <div class="gnss-metric">
              <span class="metric-label">Dst</span>
              <span class="metric-value">{{ gnssQuality.dstIndex ?? '-' }} nT</span>
            </div>
            <div class="gnss-metric">
              <span class="metric-label">Bz</span>
              <span class="metric-value">{{ gnssQuality.bzComponent ?? '-' }} nT</span>
            </div>
            <div class="gnss-metric">
              <span class="metric-label">Wind</span>
              <span class="metric-value">{{ gnssQuality.solarWindSpeed ?? '-' }} km/s</span>
            </div>
            <div class="gnss-metric" v-if="gnssQuality.gScale">
              <span class="metric-label">G-Scale</span>
              <span class="metric-value text-warning">G{{ gnssQuality.gScale }}</span>
            </div>
          </div>
        </div>
      </AppCard>

      <!-- Real-Time Indices -->
      <div class="grid-3" style="margin-bottom: var(--space-lg)">
        <AppCard>
          <div class="realtime-card">
            <span class="realtime-label">Kp Index (Real-Time)</span>
            <span class="realtime-value">{{ currentKp?.kpValue ?? '-' }}</span>
            <span class="realtime-time" v-if="currentKp">{{ formatTime(currentKp.time) }}</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="realtime-card">
            <span class="realtime-label">Dst Index (Real-Time)</span>
            <span class="realtime-value">{{ currentDst ? currentDst.dstValue + ' nT' : '-' }}</span>
            <span class="realtime-time" v-if="currentDst">{{ formatTime(currentDst.time) }}</span>
          </div>
        </AppCard>
        <AppCard>
          <div class="realtime-card">
            <span class="realtime-label">Solar Wind (Real-Time)</span>
            <span class="realtime-value">{{ currentSolarWind ? currentSolarWind.windSpeed + ' km/s' : '-' }}</span>
            <span class="realtime-detail" v-if="currentSolarWind">
              Bz: {{ currentSolarWind.bz }} nT
            </span>
            <span class="realtime-time" v-if="currentSolarWind">{{ formatTime(currentSolarWind.time) }}</span>
          </div>
        </AppCard>
      </div>

      <!-- Time range selector -->
      <div class="toolbar">
        <label class="text-secondary">Time Range:</label>
        <select v-model="hours">
          <option :value="24">24 hours</option>
          <option :value="72">3 days</option>
          <option :value="168">7 days</option>
        </select>
        <div class="spacer"></div>
        <button class="btn btn-export" :disabled="!kpData.length" @click="handleExport">Export CSV</button>
      </div>

      <!-- Charts -->
      <div class="grid-2" style="margin-bottom: var(--space-lg)">
        <AppCard title="Kp Index">
          <AppChart :option="kpChartOption" height="300px" />
        </AppCard>
        <AppCard title="Dst Index">
          <AppChart :option="dstChartOption" height="300px" />
        </AppCard>
      </div>

      <!-- Space Weather Alerts -->
      <AppCard title="Space Weather Alerts" :subtitle="'Last 3 days'">
        <DataTable :columns="alertCols" :rows="swAlerts">
          <template #alertTime="{ value }">{{ formatTime(value) }}</template>
          <template #alertType="{ value }"><StatusBadge :level="value" :label="value" /></template>
        </DataTable>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { getGnssQuality, getKpHistory, getDstHistory, getSpaceWeatherAlerts, getKpCurrent, getDstCurrent, getSolarWindCurrent } from '@/api/spaceweather'
import { usePolling } from '@/composables/usePolling'
import { useExport } from '@/composables/useExport'
import AppCard from '@/components/AppCard.vue'
import AppChart from '@/components/AppChart.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import DataTable from '@/components/DataTable.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import type { GnssQuality, KpRecord, DstRecord, SolarWindRecord } from '@/types'

const { exportCsv } = useExport()

const gnssQuality = ref<GnssQuality | null>(null)
const kpData = ref<KpRecord[]>([])
const dstData = ref<DstRecord[]>([])
const swAlerts = ref<any[]>([])
const currentKp = ref<KpRecord | null>(null)
const currentDst = ref<DstRecord | null>(null)
const currentSolarWind = ref<SolarWindRecord | null>(null)
const hours = ref(72)

const alertCols = [
  { key: 'alertTime', label: 'Time' },
  { key: 'alertType', label: 'Type' },
  { key: 'message', label: 'Message' },
]

const fetchData = async () => {
  try {
    const [qualityRes, kpRes, dstRes, alertRes, kpCurRes, dstCurRes, swCurRes] = await Promise.all([
      getGnssQuality(),
      getKpHistory(hours.value, 0, 500),
      getDstHistory(hours.value, 0, 500),
      getSpaceWeatherAlerts(),
      getKpCurrent(),
      getDstCurrent(),
      getSolarWindCurrent(),
    ])
    if (qualityRes.data.success) gnssQuality.value = qualityRes.data.data
    if (kpRes.data.success && kpRes.data.data) kpData.value = kpRes.data.data.content || []
    if (dstRes.data.success && dstRes.data.data) dstData.value = dstRes.data.data.content || []
    if (alertRes.data.success) swAlerts.value = alertRes.data.data || []
    currentKp.value = kpCurRes.data.success ? kpCurRes.data.data : null
    currentDst.value = dstCurRes.data.success ? dstCurRes.data.data : null
    currentSolarWind.value = swCurRes.data.success ? swCurRes.data.data : null
  } catch (e) {
    console.error('Space weather fetch error:', e)
  }
}

const handleExport = () => {
  const date = new Date().toISOString().slice(0, 10)
  exportCsv(`kp-index-${date}.csv`, [
    { key: 'time', label: 'Time' },
    { key: 'kpValue', label: 'Kp' },
    { key: 'source', label: 'Source' },
  ], kpData.value)
}

const { loading } = usePolling(fetchData, 120000)

watch(hours, () => fetchData())

const makeMarkLines = (thresholds: Array<{ value: number; label: string; color: string }>) =>
  thresholds.map(t => ({
    yAxis: t.value,
    lineStyle: { color: t.color, type: 'dashed' as const, width: 1 },
    label: { formatter: t.label, color: t.color, fontSize: 10 },
  }))

const kpChartOption = computed(() => ({
  grid: { top: 30, right: 20, bottom: 60, left: 50 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'time',
    axisLabel: { color: '#94a3b8', fontSize: 11 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: 'Kp',
    min: 0, max: 9,
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
    markLine: {
      silent: true,
      data: makeMarkLines([
        { value: 4, label: 'Kp=4 (Caution)', color: '#facc15' },
        { value: 5, label: 'Kp=5 (Degraded)', color: '#fb923c' },
        { value: 7, label: 'Kp=7 (Severe)', color: '#f87171' },
      ]),
    },
  }],
  dataZoom: [{ type: 'slider', bottom: 10 }, { type: 'inside' }],
}))

const dstChartOption = computed(() => ({
  grid: { top: 30, right: 20, bottom: 60, left: 60 },
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
    markLine: {
      silent: true,
      data: makeMarkLines([
        { value: -30, label: '-30nT (Caution)', color: '#facc15' },
        { value: -50, label: '-50nT (Degraded)', color: '#fb923c' },
        { value: -100, label: '-100nT (Severe)', color: '#f87171' },
      ]),
    },
  }],
  dataZoom: [{ type: 'slider', bottom: 10 }, { type: 'inside' }],
}))

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
.gnss-banner {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.gnss-quality-main {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.gnss-assessment {
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.gnss-metrics {
  display: flex;
  gap: var(--space-xl);
  flex-wrap: wrap;
}

.gnss-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
}

.metric-label {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
}

.metric-value {
  font-family: var(--font-mono);
  font-size: 1.1rem;
  font-weight: 600;
}

.realtime-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-sm) 0;
}

.realtime-label {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.realtime-value {
  font-family: var(--font-mono);
  font-size: 1.3rem;
  font-weight: 700;
}

.realtime-detail {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.realtime-time {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
</style>
