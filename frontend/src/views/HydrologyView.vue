<template>
  <div>
    <div class="page-header">
      <h1>Hydrology</h1>
      <p class="text-secondary">Water levels and reservoir status</p>
    </div>

    <div class="toolbar">
      <StationSelector
        :county="selectedCounty"
        :township="selectedTownship"
        :station="selectedStation"
        :counties="counties"
        :townships="townships"
        :stations="filteredStations"
        @update:county="onCountyChange"
        @update:township="onTownshipChange"
        @update:station="v => selectedStation = v"
      />
      <select v-if="selectedStation" class="select" v-model="selectedHours" @change="fetchData()">
        <option :value="6">6 小時</option>
        <option :value="12">12 小時</option>
        <option :value="24">24 小時</option>
        <option :value="48">48 小時</option>
        <option :value="72">72 小時</option>
        <option :value="168">7 天</option>
      </select>
      <div class="spacer"></div>
      <button class="btn btn-export" disabled>Export CSV</button>
    </div>

    <LoadingSpinner :loading="loading && !waterLevels.length && !reservoirs.length"
      text="Loading hydrology data..." />

    <template v-if="!loading || waterLevels.length || reservoirs.length">
      <!-- Water Level Alert Summary -->
      <div v-if="alertStations.length" style="margin-bottom: var(--space-lg)">
        <div class="page-header">
          <h2 style="font-size: 1.1rem;">水位警戒</h2>
        </div>
        <div class="grid-3" style="margin-bottom: var(--space-md)">
          <AppCard>
            <div class="alert-stat">
              <span class="alert-stat-value danger">{{ alertCount.level1 }}</span>
              <span class="alert-stat-label">一級警戒</span>
            </div>
          </AppCard>
          <AppCard>
            <div class="alert-stat">
              <span class="alert-stat-value orange">{{ alertCount.level2 }}</span>
              <span class="alert-stat-label">二級警戒</span>
            </div>
          </AppCard>
          <AppCard>
            <div class="alert-stat">
              <span class="alert-stat-value warning">{{ alertCount.level3 }}</span>
              <span class="alert-stat-label">三級警戒</span>
            </div>
          </AppCard>
        </div>
        <AppCard title="警戒站台" :no-padding="true">
          <DataTable :columns="alertCols" :rows="alertStations" empty-text="">
            <template #name="{ row }">{{ row.name }}</template>
            <template #waterLevel="{ row }">{{ row.waterLevel }} m</template>
            <template #level="{ row }">
              <span class="alert-badge" :class="row.levelClass">{{ row.levelLabel }}</span>
            </template>
          </DataTable>
        </AppCard>
      </div>

      <!-- Water Level Chart -->
      <AppCard title="Water Level Trend" style="margin-bottom: var(--space-lg)"
        v-if="selectedStation && stationData.length">
        <AppChart :option="waterLevelChartOption" height="300px" />
      </AppCard>

      <!-- Reservoir Cards -->
      <div class="page-header" v-if="reservoirs.length" style="margin-top: var(--space-lg)">
        <h2 style="font-size: 1.1rem;">Reservoirs</h2>
      </div>
      <div class="grid-3" style="margin-bottom: var(--space-lg)" v-if="reservoirs.length">
        <AppCard v-for="r in reservoirs" :key="r.reservoirId" :title="r.reservoirName">
          <div class="reservoir-card">
            <div class="reservoir-bar-container">
              <div class="reservoir-bar" :style="{ width: (r.storagePct || 0) + '%' }"
                :class="getReservoirClass(r.storagePct)"></div>
            </div>
            <span class="reservoir-pct" :class="getReservoirClass(r.storagePct)">
              {{ (r.storagePct || 0).toFixed(1) }}%
            </span>
            <div class="reservoir-metrics">
              <!-- 水位 -->
              <div class="reservoir-metric">
                <span class="reservoir-metric-label">水位</span>
                <div class="reservoir-level-bar-container">
                  <div class="reservoir-bar"
                    :style="{ width: getLevelPct(r.waterLevel, r.fullLevel) + '%' }"
                    :class="getReservoirClass(r.storagePct)"></div>
                </div>
                <span class="reservoir-metric-value">
                  <span :class="getReservoirClass(r.storagePct)">{{ r.waterLevel ?? '-' }}</span>
                  <span class="muted"> / {{ r.fullLevel ?? '-' }} m</span>
                </span>
              </div>
              <!-- 入流 / 出流 -->
              <div class="reservoir-flow">
                <div class="flow-item">
                  <span class="reservoir-metric-label">入流</span>
                  <span class="reservoir-metric-value">
                    <span class="flow-arrow inflow">▼</span>
                    <span :class="getFlowClass(r.inflow, r.outflow, 'in')">{{ r.inflow ?? '-' }}</span>
                  </span>
                </div>
                <div class="flow-divider"></div>
                <div class="flow-item">
                  <span class="reservoir-metric-label">出流</span>
                  <span class="reservoir-metric-value">
                    <span class="flow-arrow outflow">▲</span>
                    <span :class="getFlowClass(r.inflow, r.outflow, 'out')">{{ r.outflow ?? '-' }}</span>
                  </span>
                </div>
              </div>
              <!-- 日雨量 -->
              <div class="reservoir-metric">
                <span class="reservoir-metric-label">日雨量</span>
                <span class="reservoir-metric-value">
                  <span class="rain-dot" :class="getRainClass(r.dailyRainfall)"></span>
                  <span :class="getRainClass(r.dailyRainfall)">{{ r.dailyRainfall ?? '-' }} mm</span>
                </span>
              </div>
            </div>
          </div>
        </AppCard>
      </div>

      <!-- Water Level Table -->
      <AppCard title="Water Level Readings" :no-padding="true">
        <DataTable :columns="waterCols" :rows="waterLevels.slice(0, 50)" empty-text="No water level data">
          <template #time="{ value }">{{ formatTime(value) }}</template>
          <template #stationCode="{ value }">{{ getLabel(value) }}</template>
          <template #waterLevel="{ value }">{{ value }} m</template>
        </DataTable>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { getLatestWaterLevels, getWaterLevelByStation, getReservoirs } from '@/api/hydrology'
import { getStations } from '@/api/stations'
import AppCard from '@/components/AppCard.vue'
import AppChart from '@/components/AppChart.vue'
import StationSelector from '@/components/StationSelector.vue'
import DataTable from '@/components/DataTable.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import type { Station } from '@/types'

// County ordering (same as useStationFilter)
const COUNTY_ORDER = [
  '基隆市','臺北市','新北市','桃園市','新竹市','新竹縣',
  '苗栗縣','臺中市','彰化縣','南投縣','雲林縣',
  '嘉義市','嘉義縣','臺南市','高雄市','屏東縣',
  '宜蘭縣','花蓮縣','臺東縣',
  '澎湖縣','金門縣','連江縣',
]

const waterLevels = ref<any[]>([])
const stationData = ref<any[]>([])
const reservoirs = ref<any[]>([])
const selectedCounty = ref('')
const selectedTownship = ref('')
const selectedStation = ref('')
const selectedHours = ref(24)
const stationMeta = ref<Map<string, Station>>(new Map())
// Combined station list: stations table + observation-derived codes
const allStationItems = ref<Station[]>([])
const loading = ref(false)

const waterCols = [
  { key: 'time', label: 'Time' },
  { key: 'stationCode', label: 'Station' },
  { key: 'waterLevel', label: 'Level' },
  { key: 'source', label: 'Source' },
]

const loadMeta = async () => {
  try {
    const res = await getStations()
    if (res.data.success) {
      const map = new Map<string, Station>()
      for (const s of res.data.data || []) {
        map.set(s.stationCode, s)
      }
      stationMeta.value = map
    }
  } catch (e) {
    console.error('Station meta error:', e)
  }
}

const buildStationItems = () => {
  const codes = [...new Set(waterLevels.value.map((w: any) => w.stationCode))]
  const items: Station[] = codes.map(code => {
    const meta = stationMeta.value.get(code)
    if (meta) return meta
    return {
      stationCode: code,
      stationName: code,
      source: 'WRA',
      stationType: 'WATER_LEVEL',
      latitude: null, longitude: null, altitude: null,
      county: null, township: null,
      alertLevel1: null, alertLevel2: null, alertLevel3: null,
      isActive: true,
    }
  })
  allStationItems.value = items
  autoSelectDefaults()
}

const autoSelectDefaults = () => {
  if (!selectedCounty.value && counties.value.length) {
    selectedCounty.value = counties.value[0]
  }
  if (!selectedTownship.value && townships.value.length) {
    selectedTownship.value = townships.value[0]
  }
  if (!selectedStation.value && filteredStations.value.length) {
    selectedStation.value = filteredStations.value[0].stationCode
  }
}

// Cascading filter
const OTHER_COUNTY = '其他'

const counties = computed(() => {
  const set = new Set(allStationItems.value.map(s => s.county).filter(Boolean) as string[])
  const sorted = [...set].sort((a, b) => {
    const ia = COUNTY_ORDER.indexOf(a), ib = COUNTY_ORDER.indexOf(b)
    return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib)
  })
  // Add "其他" if there are stations without county
  if (allStationItems.value.some(s => !s.county)) {
    sorted.push(OTHER_COUNTY)
  }
  return sorted
})

const townships = computed(() => {
  if (!selectedCounty.value) return []
  if (selectedCounty.value === OTHER_COUNTY) return []
  const set = new Set(
    allStationItems.value
      .filter(s => s.county === selectedCounty.value)
      .map(s => s.township)
      .filter(Boolean) as string[]
  )
  return [...set].sort()
})

const filteredStations = computed(() => {
  let list = allStationItems.value
  if (selectedCounty.value === OTHER_COUNTY) {
    list = list.filter(s => !s.county)
  } else if (selectedCounty.value) {
    list = list.filter(s => s.county === selectedCounty.value)
  }
  if (selectedTownship.value) list = list.filter(s => s.township === selectedTownship.value)
  return list.sort((a, b) => a.stationName.localeCompare(b.stationName))
})

const onCountyChange = (v: string) => {
  selectedCounty.value = v
  selectedTownship.value = ''
  selectedStation.value = ''
  if (townships.value.length) selectedTownship.value = townships.value[0]
  if (filteredStations.value.length) selectedStation.value = filteredStations.value[0].stationCode
}

const onTownshipChange = (v: string) => {
  selectedTownship.value = v
  selectedStation.value = ''
  if (filteredStations.value.length) selectedStation.value = filteredStations.value[0].stationCode
}

const getLabel = (code: string) => {
  const s = stationMeta.value.get(code)
  return s ? s.stationName : code
}

const fetchData = async () => {
  loading.value = true
  try {
    if (selectedStation.value) {
      const res = await getWaterLevelByStation(selectedStation.value, selectedHours.value)
      if (res.data.success) {
        stationData.value = res.data.data || []
        waterLevels.value = stationData.value
      }
    } else {
      const [wlRes, rvRes] = await Promise.all([
        getLatestWaterLevels(),
        getReservoirs(),
      ])
      if (wlRes.data.success) {
        waterLevels.value = wlRes.data.data || []
        buildStationItems()
      }
      if (rvRes.data.success) reservoirs.value = rvRes.data.data || []
      stationData.value = []
    }
  } catch (e) {
    console.error('Hydrology fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => { await loadMeta(); fetchData() })
watch(selectedStation, () => {
  if (selectedStation.value) fetchData()
})

// Alert level analysis
const alertCols = [
  { key: 'name', label: '測站' },
  { key: 'waterLevel', label: '水位 (m)' },
  { key: 'level', label: '警戒等級' },
]

const getStationAlertLevel = (wl: number, meta: Station | undefined) => {
  if (!meta || wl == null) return null
  if (meta.alertLevel1 != null && wl >= meta.alertLevel1) return { level: 1, label: '一級警戒', cls: 'danger' }
  if (meta.alertLevel2 != null && wl >= meta.alertLevel2) return { level: 2, label: '二級警戒', cls: 'orange' }
  if (meta.alertLevel3 != null && wl >= meta.alertLevel3) return { level: 3, label: '三級警戒', cls: 'warning' }
  return null
}

const alertStations = computed(() => {
  // Use latest reading per station from waterLevels
  const latestByStation = new Map<string, any>()
  for (const w of waterLevels.value) {
    const existing = latestByStation.get(w.stationCode)
    if (!existing || w.time > existing.time) {
      latestByStation.set(w.stationCode, w)
    }
  }

  const results: Array<{ name: string; waterLevel: number; levelLabel: string; levelClass: string; sortOrder: number }> = []
  for (const [code, w] of latestByStation) {
    const meta = stationMeta.value.get(code)
    const alert = getStationAlertLevel(w.waterLevel, meta)
    if (alert) {
      results.push({
        name: meta?.stationName || code,
        waterLevel: w.waterLevel,
        levelLabel: alert.label,
        levelClass: alert.cls,
        sortOrder: alert.level,
      })
    }
  }
  return results.sort((a, b) => a.sortOrder - b.sortOrder)
})

const alertCount = computed(() => ({
  level1: alertStations.value.filter(a => a.sortOrder === 1).length,
  level2: alertStations.value.filter(a => a.sortOrder === 2).length,
  level3: alertStations.value.filter(a => a.sortOrder === 3).length,
}))

// Water level chart with alert markLines
const waterLevelChartOption = computed(() => {
  const meta = selectedStation.value ? stationMeta.value.get(selectedStation.value) : undefined
  const markLines: any[] = []
  if (meta?.alertLevel3 != null) {
    markLines.push({ yAxis: meta.alertLevel3, lineStyle: { color: '#facc15', type: 'dashed', width: 1 }, label: { formatter: `三級警戒 ${meta.alertLevel3}m`, color: '#facc15', fontSize: 10 } })
  }
  if (meta?.alertLevel2 != null) {
    markLines.push({ yAxis: meta.alertLevel2, lineStyle: { color: '#fb923c', type: 'dashed', width: 1 }, label: { formatter: `二級警戒 ${meta.alertLevel2}m`, color: '#fb923c', fontSize: 10 } })
  }
  if (meta?.alertLevel1 != null) {
    markLines.push({ yAxis: meta.alertLevel1, lineStyle: { color: '#f87171', type: 'dashed', width: 1 }, label: { formatter: `一級警戒 ${meta.alertLevel1}m`, color: '#f87171', fontSize: 10 } })
  }

  // Calculate Y-axis range from data + alert levels
  const dataValues = stationData.value.map(d => d.waterLevel).filter((v: any) => v != null) as number[]
  const allValues = [...dataValues]
  if (meta?.alertLevel1 != null) allValues.push(meta.alertLevel1)
  if (meta?.alertLevel2 != null) allValues.push(meta.alertLevel2)
  if (meta?.alertLevel3 != null) allValues.push(meta.alertLevel3)

  let yMin: number | undefined
  let yMax: number | undefined
  if (allValues.length) {
    const lo = Math.min(...allValues)
    const hi = Math.max(...allValues)
    const padding = Math.max((hi - lo) * 0.15, 0.5)
    yMin = Math.floor((lo - padding) * 10) / 10
    yMax = Math.ceil((hi + padding) * 10) / 10
  }

  return {
    grid: { top: 20, right: 20, bottom: 50, left: 60 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'time',
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      name: 'Level (m)',
      min: yMin,
      max: yMax,
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#334155' } },
    },
    series: [{
      type: 'line',
      data: stationData.value.map(d => [d.time, d.waterLevel]),
      smooth: true,
      lineStyle: { color: '#38bdf8', width: 2 },
      itemStyle: { color: '#38bdf8' },
      areaStyle: { color: 'rgba(56, 189, 248, 0.1)' },
      ...(markLines.length ? { markLine: { silent: true, data: markLines } } : {}),
    }],
    dataZoom: [{ type: 'slider', bottom: 5 }, { type: 'inside' }],
  }
})

const getReservoirClass = (pct: number) => {
  if (pct < 20) return 'danger'
  if (pct < 50) return 'warning'
  return 'success'
}

const getLevelPct = (waterLevel: number, fullLevel: number) => {
  if (!waterLevel || !fullLevel || fullLevel === 0) return 0
  return Math.min(100, (waterLevel / fullLevel) * 100)
}

const getFlowClass = (inflow: number, outflow: number, type: 'in' | 'out') => {
  if (inflow == null || outflow == null) return ''
  if (type === 'in' && inflow > outflow * 1.5) return 'accent'
  if (type === 'out' && outflow > inflow * 1.5) return 'danger'
  return ''
}

const getRainClass = (mm: number) => {
  if (mm == null || mm === 0) return 'muted'
  if (mm < 40) return 'success'
  if (mm < 80) return 'warning'
  if (mm < 200) return 'orange'
  return 'danger'
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
.reservoir-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.reservoir-bar-container {
  width: 100%;
  height: 8px;
  background: var(--color-bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
}

.reservoir-bar {
  height: 100%;
  border-radius: 4px;
  transition: width var(--transition-normal);
}

.reservoir-bar.success { background: var(--color-success); }
.reservoir-bar.warning { background: var(--color-warning); }
.reservoir-bar.danger { background: var(--color-danger); }

.reservoir-pct {
  font-family: var(--font-mono);
  font-size: 1.25rem;
  font-weight: 700;
}

.reservoir-pct.success { color: var(--color-success); }
.reservoir-pct.warning { color: var(--color-warning); }
.reservoir-pct.danger { color: var(--color-danger); }

.reservoir-metrics {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-border);
}

.reservoir-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.reservoir-metric-label {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.05em;
}

.reservoir-metric-value {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.reservoir-metric-value .accent { color: var(--color-accent); }
.reservoir-metric-value .success { color: var(--color-success); }
.reservoir-metric-value .warning { color: var(--color-warning); }
.reservoir-metric-value .orange { color: var(--color-orange); }
.reservoir-metric-value .danger { color: var(--color-danger); }
.reservoir-metric-value .muted { color: var(--color-text-muted); }

/* Water level mini bar */
.reservoir-level-bar-container {
  width: 100%;
  height: 4px;
  background: var(--color-bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
}

/* Inflow / Outflow */
.reservoir-flow {
  display: flex;
  justify-content: space-around;
  align-items: center;
}

.flow-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.flow-divider {
  width: 1px;
  height: 28px;
  background: var(--color-border);
}

.flow-arrow { font-size: 0.6rem; }
.flow-arrow.inflow { color: var(--color-accent); }
.flow-arrow.outflow { color: var(--color-orange); }

/* Rainfall dot */
.rain-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.rain-dot.muted { background: var(--color-text-muted); }
.rain-dot.success { background: var(--color-success); }
.rain-dot.warning { background: var(--color-warning); }
.rain-dot.orange { background: var(--color-orange); }
.rain-dot.danger { background: var(--color-danger); }

/* Alert summary */
.alert-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-sm);
}

.alert-stat-value {
  font-family: var(--font-mono);
  font-size: 1.5rem;
  font-weight: 700;
}

.alert-stat-value.danger { color: var(--color-danger); }
.alert-stat-value.orange { color: var(--color-orange); }
.alert-stat-value.warning { color: var(--color-warning); }

.alert-stat-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.alert-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
}

.alert-badge.danger { background: rgba(248, 113, 113, 0.15); color: var(--color-danger); }
.alert-badge.orange { background: rgba(251, 146, 60, 0.15); color: var(--color-orange); }
.alert-badge.warning { background: rgba(250, 204, 21, 0.15); color: var(--color-warning); }
</style>
