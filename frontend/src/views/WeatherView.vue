<template>
  <div>
    <div class="page-header">
      <h1>Weather / Rainfall</h1>
      <p class="text-secondary">Real-time weather observations and rainfall trends</p>
    </div>

    <div class="toolbar">
      <StationSelector
        :county="sf.selectedCounty.value"
        :township="sf.selectedTownship.value"
        :station="sf.selectedStation.value"
        :counties="sf.counties.value"
        :townships="sf.townships.value"
        :stations="sf.filteredStations.value"
        @update:county="v => { sf.selectedCounty.value = v; sf.onCountyChange() }"
        @update:township="v => { sf.selectedTownship.value = v; sf.onTownshipChange() }"
        @update:station="v => sf.selectedStation.value = v"
      />
      <label class="text-secondary">Time Range:</label>
      <select v-model.number="hours">
        <option :value="6">6 hours</option>
        <option :value="24">24 hours</option>
        <option :value="72">3 days</option>
        <option :value="168">7 days</option>
        <option :value="720">30 days</option>
      </select>
      <div class="spacer"></div>
      <button class="btn btn-export" disabled>Export CSV</button>
    </div>

    <LoadingSpinner :loading="loading && !rainfallData.length" text="Loading weather data..." />

    <template v-if="!loading || rainfallData.length">
      <!-- Hourly Rainfall (precip1hr from CWA API) -->
      <AppCard title="Hourly Rainfall" :subtitle="sf.selectedStationLabel.value || 'All stations (latest 2h)'"
        style="margin-bottom: var(--space-lg)">
        <AppChart :option="hourlyChartOption" height="300px" />
      </AppCard>

      <!-- Daily Accumulated Rainfall -->
      <AppCard v-if="sf.selectedStation.value" title="Daily Accumulated Rainfall"
        style="margin-bottom: var(--space-lg)">
        <AppChart :option="dailyAccChartOption" height="300px" />
      </AppCard>

      <!-- Effective Accumulated Rainfall Section -->
      <template v-if="sf.selectedStation.value && effData">
        <div class="toolbar" style="margin-bottom: var(--space-md)">
          <label class="text-secondary" style="font-weight:600;">R_eff Window:</label>
          <select v-model.number="effWindowHours">
            <option :value="24">24 hours</option>
            <option :value="48">48 hours</option>
            <option :value="72">72 hours (SWCB default)</option>
            <option :value="168">7 days</option>
            <option :value="336">14 days</option>
          </select>
        </div>
        <!-- ETR1 + ETR2 + I Summary -->
        <div class="grid-4" style="margin-bottom: var(--space-lg)">
          <AppCard>
            <div class="metric-card">
              <span class="metric-label">ETR1 Event Rainfall</span>
              <span class="metric-value" :class="etr1Class">{{ effData.eventTotalRainfall }} mm</span>
              <span class="metric-detail" v-if="effData.eventStartTime">
                Duration: {{ effData.eventDurationHours }}h
              </span>
              <span class="metric-detail text-muted" v-else>No active event</span>
            </div>
          </AppCard>
          <AppCard>
            <div class="metric-card">
              <span class="metric-label">ETR2 Effective Rainfall</span>
              <span class="metric-value" :class="rEffClass">{{ effData.effectiveRainfall }} mm</span>
              <span class="metric-detail">T&frac12;={{ effData.halfLifeHours }}h</span>
            </div>
          </AppCard>
          <AppCard>
            <div class="metric-card">
              <span class="metric-label">Current Intensity (I)</span>
              <span class="metric-value">{{ effData.currentIntensity }} mm/h</span>
            </div>
          </AppCard>
          <AppCard>
            <div class="metric-card">
              <span class="metric-label">Event Start</span>
              <span class="metric-detail" v-if="effData.eventStartTime">
                {{ formatTime(effData.eventStartTime) }}
              </span>
              <span class="metric-detail text-muted" v-else>-</span>
              <span class="metric-detail">Split: 6h &lt; 4mm</span>
            </div>
          </AppCard>
        </div>

        <!-- ETR1 + ETR2 Time Series + Intensity -->
        <AppCard title="ETR1 / ETR2 &amp; Rainfall Intensity"
          subtitle="SWCB standard: T½=12h, event split: 6h<4mm" style="margin-bottom: var(--space-lg)">
          <AppChart :option="rEffTimeSeriesOption" height="350px" />
        </AppCard>

        <!-- I-R Scatter Plot -->
        <AppCard title="I-R Diagram (Intensity vs Effective Rainfall)"
          subtitle="Each point = 1 observation; latest point highlighted"
          style="margin-bottom: var(--space-lg)">
          <AppChart :option="irScatterOption" height="350px" />
        </AppCard>
      </template>

      <!-- Current Weather -->
      <div class="grid-2" style="margin-bottom: var(--space-lg)" v-if="sf.selectedStation.value">
        <AppCard title="Current Accumulated Rainfall" v-if="accSummary">
          <div class="accumulated-grid">
            <div class="acc-item" v-for="item in accSummaryItems" :key="item.label">
              <span class="acc-label">{{ item.label }}</span>
              <span class="acc-value" :class="item.class">{{ item.value }} mm</span>
            </div>
          </div>
        </AppCard>
        <AppCard title="Current Weather" v-if="observations.length">
          <div class="obs-grid">
            <div class="obs-item" v-for="obs in observations.slice(0, 1)" :key="obs.stationCode">
              <div>Temp: <strong>{{ obs.temperature }}&deg;C</strong></div>
              <div>Humidity: <strong>{{ obs.humidity }}%</strong></div>
              <div>Pressure: <strong>{{ obs.pressure }} hPa</strong></div>
              <div>Wind: <strong>{{ obs.windSpeed }} m/s</strong></div>
            </div>
          </div>
        </AppCard>
      </div>

      <!-- Rainfall Table -->
      <AppCard title="Observation Records" :no-padding="true">
        <DataTable :columns="rainfallCols" :rows="rainfallData.slice(0, 100)" empty-text="No rainfall data">
          <template #time="{ value }">{{ formatTime(value) }}</template>
          <template #stationCode="{ value }">{{ sf.getStationLabel(value) }}</template>
          <template #precipitation="{ value }">
            <span :class="value > 10 ? 'text-warning' : value > 0 ? 'text-success' : ''">
              {{ value }} mm
            </span>
          </template>
        </DataTable>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { getLatestRainfall, getRainfallByStation, getAccumulatedRainfall, getLatestObservations, getEffectiveRainfall } from '@/api/weather'
import { useStationFilter } from '@/composables/useStationFilter'
import AppCard from '@/components/AppCard.vue'
import AppChart from '@/components/AppChart.vue'
import StationSelector from '@/components/StationSelector.vue'
import DataTable from '@/components/DataTable.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const sf = useStationFilter({ type: 'RAINFALL' })

const rainfallData = ref<any[]>([])
const observations = ref<any[]>([])
const accSummary = ref<any>(null)
const effData = ref<any>(null)
const hours = ref(24)
const effWindowHours = ref(72)
const loading = ref(false)

const rainfallCols = [
  { key: 'time', label: 'Time' },
  { key: 'stationCode', label: 'Station' },
  { key: 'precip1hr', label: '1hr (mm)' },
  { key: 'precip3hr', label: '3hr (mm)' },
  { key: 'precip24hr', label: '24hr (mm)' },
  { key: 'precipitation', label: 'Daily Acc.' },
]

const fetchData = async () => {
  loading.value = true
  try {
    if (sf.selectedStation.value) {
      const [rfRes, accRes, obsRes, effRes] = await Promise.all([
        getRainfallByStation(sf.selectedStation.value, hours.value),
        getAccumulatedRainfall(sf.selectedStation.value, 72),
        getLatestObservations(),
        getEffectiveRainfall(sf.selectedStation.value, effWindowHours.value),
      ])
      if (rfRes.data.success) rainfallData.value = rfRes.data.data || []
      if (accRes.data.success) accSummary.value = accRes.data.data
      if (obsRes.data.success) observations.value = obsRes.data.data || []
      if (effRes.data.success) effData.value = effRes.data.data
    } else {
      const [rfRes, obsRes] = await Promise.all([
        getLatestRainfall(),
        getLatestObservations(),
      ])
      if (rfRes.data.success) rainfallData.value = rfRes.data.data || []
      if (obsRes.data.success) observations.value = obsRes.data.data || []
      accSummary.value = null
      effData.value = null
    }
  } catch (e) {
    console.error('Weather fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await sf.loadStations()
  fetchData()
})
watch([() => sf.selectedStation.value, hours, effWindowHours], () => {
  if (sf.selectedStation.value) fetchData()
})

// Accumulated rainfall summary items
const accSummaryItems = computed(() => {
  if (!accSummary.value) return []
  const a = accSummary.value
  const classify = (val: number) =>
    val >= 200 ? 'text-danger' : val >= 80 ? 'text-warning' : val > 0 ? 'text-success' : ''
  return [
    { label: '1h', value: a.hourly ?? 0, class: classify(a.hourly ?? 0) },
    { label: '3h', value: a.accumulated3h ?? 0, class: classify(a.accumulated3h ?? 0) },
    { label: '24h', value: a.accumulated24h ?? 0, class: classify(a.accumulated24h ?? 0) },
    { label: '48h', value: a.accumulated48h ?? 0, class: classify(a.accumulated48h ?? 0) },
    { label: '72h', value: a.accumulated72h ?? 0, class: classify(a.accumulated72h ?? 0) },
  ]
})

// Hourly rainfall bar chart (uses precip1hr from CWA API)
const hourlyChartOption = computed(() => ({
  grid: { top: 20, right: 20, bottom: 60, left: 50 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'time',
    axisLabel: { color: '#94a3b8', fontSize: 11 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: 'mm/h',
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: '#334155' } },
  },
  series: [{
    type: 'bar',
    name: '1hr Rainfall',
    data: rainfallData.value.map(d => [d.time, d.precip1hr ?? 0]),
    itemStyle: { color: '#38bdf8' },
  }],
  dataZoom: [{ type: 'slider', bottom: 5 }, { type: 'inside' }],
}))

// Daily accumulated rainfall line chart
const dailyAccChartOption = computed(() => ({
  grid: { top: 30, right: 20, bottom: 60, left: 50 },
  tooltip: { trigger: 'axis' },
  legend: {
    data: ['Daily Acc.', '3hr', '24hr'],
    textStyle: { color: '#94a3b8' },
    top: 0,
  },
  xAxis: {
    type: 'time',
    axisLabel: { color: '#94a3b8', fontSize: 11 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: 'mm',
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: '#334155' } },
  },
  series: [
    {
      type: 'line',
      name: 'Daily Acc.',
      data: rainfallData.value.map(d => [d.time, d.precipitation]),
      smooth: true,
      lineStyle: { color: '#38bdf8', width: 2 },
      itemStyle: { color: '#38bdf8' },
      areaStyle: { color: 'rgba(56, 189, 248, 0.08)' },
      showSymbol: false,
    },
    {
      type: 'line',
      name: '3hr',
      data: rainfallData.value.map(d => [d.time, d.precip3hr ?? 0]),
      smooth: true,
      lineStyle: { color: '#4ade80', width: 2 },
      itemStyle: { color: '#4ade80' },
      showSymbol: false,
    },
    {
      type: 'line',
      name: '24hr',
      data: rainfallData.value.map(d => [d.time, d.precip24hr ?? 0]),
      smooth: true,
      lineStyle: { color: '#fb923c', width: 2 },
      itemStyle: { color: '#fb923c' },
      showSymbol: false,
    },
  ],
  dataZoom: [{ type: 'slider', bottom: 5 }, { type: 'inside' }],
}))

// Color classes for rainfall values
const classifyRainfall = (v: number) =>
  v >= 200 ? 'text-danger' : v >= 100 ? 'text-warning' : v > 0 ? 'text-success' : ''

const rEffClass = computed(() => classifyRainfall(effData.value?.effectiveRainfall ?? 0))
const etr1Class = computed(() => classifyRainfall(effData.value?.eventTotalRainfall ?? 0))

// R_eff time series chart (dual axis: R_eff line + intensity bars)
const rEffTimeSeriesOption = computed(() => {
  const ts = effData.value?.timeSeries || []
  return {
    grid: { top: 40, right: 60, bottom: 60, left: 60 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!params.length) return ''
        const t = new Date(params[0].value[0]).toLocaleString('zh-TW')
        let html = `${t}<br/>`
        for (const p of params) {
          html += `${p.marker} ${p.seriesName}: <strong>${p.value[1]}</strong><br/>`
        }
        return html
      },
    },
    legend: {
      data: ['ETR1 (mm)', 'ETR2/R_eff (mm)', 'Intensity (mm/h)'],
      textStyle: { color: '#94a3b8' },
      top: 0,
    },
    xAxis: {
      type: 'time',
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { show: false },
    },
    yAxis: [
      {
        type: 'value',
        name: 'R_eff (mm)',
        position: 'left',
        axisLabel: { color: '#fb923c' },
        splitLine: { lineStyle: { color: '#334155' } },
      },
      {
        type: 'value',
        name: 'I (mm/h)',
        position: 'right',
        axisLabel: { color: '#38bdf8' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        type: 'line',
        name: 'ETR1 (mm)',
        yAxisIndex: 0,
        data: ts.map((p: any) => [p.time, p.eventAccumulated]),
        smooth: false,
        lineStyle: { color: '#4ade80', width: 2.5 },
        itemStyle: { color: '#4ade80' },
        areaStyle: { color: 'rgba(74, 222, 128, 0.08)' },
        showSymbol: false,
      },
      {
        type: 'line',
        name: 'ETR2/R_eff (mm)',
        yAxisIndex: 0,
        data: ts.map((p: any) => [p.time, p.effectiveAccumulated]),
        smooth: true,
        lineStyle: { color: '#fb923c', width: 2.5 },
        itemStyle: { color: '#fb923c' },
        showSymbol: false,
      },
      {
        type: 'bar',
        name: 'Intensity (mm/h)',
        yAxisIndex: 1,
        data: ts.map((p: any) => [p.time, p.intensity]),
        itemStyle: { color: 'rgba(56, 189, 248, 0.6)' },
        barMaxWidth: 8,
      },
    ],
    dataZoom: [{ type: 'slider', bottom: 5 }, { type: 'inside' }],
  }
})

// I-R scatter plot
const irScatterOption = computed(() => {
  const ts = effData.value?.timeSeries || []
  if (!ts.length) return {}

  const points = ts.map((p: any) => [p.effectiveAccumulated, p.intensity])
  const lastPoint = points[points.length - 1]

  return {
    grid: { top: 30, right: 30, bottom: 50, left: 60 },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        return `R_eff: <strong>${params.value[0]} mm</strong><br/>I: <strong>${params.value[1]} mm/h</strong>`
      },
    },
    xAxis: {
      type: 'value',
      name: 'R_eff (mm)',
      nameLocation: 'middle',
      nameGap: 30,
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#334155' } },
    },
    yAxis: {
      type: 'value',
      name: 'I (mm/h)',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#334155' } },
    },
    series: [
      {
        type: 'scatter',
        name: 'History',
        data: points.slice(0, -1),
        symbolSize: 6,
        itemStyle: { color: 'rgba(148, 163, 184, 0.5)' },
      },
      {
        type: 'scatter',
        name: 'Current',
        data: [lastPoint],
        symbolSize: 14,
        itemStyle: { color: '#f87171', borderColor: '#fff', borderWidth: 2 },
      },
      {
        type: 'line',
        name: 'Trajectory',
        data: points,
        smooth: false,
        lineStyle: { color: 'rgba(148, 163, 184, 0.3)', width: 1 },
        showSymbol: false,
      },
    ],
  }
})

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
.accumulated-grid {
  display: flex;
  gap: var(--space-lg);
  flex-wrap: wrap;
}

.acc-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
}

.acc-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.acc-value {
  font-family: var(--font-mono);
  font-size: 1.2rem;
  font-weight: 600;
}

.obs-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.obs-item div {
  padding: var(--space-xs) 0;
  color: var(--color-text-secondary);
}

.obs-item strong {
  color: var(--color-text-primary);
}

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
  text-align: center;
}

.metric-value {
  font-size: 1.5rem;
  font-weight: 700;
  font-family: var(--font-mono);
}

.metric-detail {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
}
</style>
