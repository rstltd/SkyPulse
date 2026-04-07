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
      <div class="spacer"></div>
      <button class="btn btn-export" disabled>Export CSV</button>
    </div>

    <LoadingSpinner :loading="loading && !waterLevels.length && !reservoirs.length"
      text="Loading hydrology data..." />

    <template v-if="!loading || waterLevels.length || reservoirs.length">
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
            <div class="reservoir-details">
              <div>Level: {{ r.waterLevel ?? '-' }}m / {{ r.fullLevel ?? '-' }}m</div>
              <div>Inflow: {{ r.inflow ?? '-' }} | Outflow: {{ r.outflow ?? '-' }}</div>
              <div>Daily Rainfall: {{ r.dailyRainfall ?? '-' }} mm</div>
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
      county: null, township: null, isActive: true,
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
const counties = computed(() => {
  const set = new Set(allStationItems.value.map(s => s.county).filter(Boolean) as string[])
  return [...set].sort((a, b) => {
    const ia = COUNTY_ORDER.indexOf(a), ib = COUNTY_ORDER.indexOf(b)
    return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib)
  })
})

const townships = computed(() => {
  if (!selectedCounty.value) return []
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
  if (selectedCounty.value) list = list.filter(s => s.county === selectedCounty.value)
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
      const res = await getWaterLevelByStation(selectedStation.value)
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

const waterLevelChartOption = computed(() => ({
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
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: '#334155' } },
  },
  series: [{
    type: 'line',
    data: [...stationData.value].reverse().map(d => [d.time, d.waterLevel]),
    smooth: true,
    lineStyle: { color: '#38bdf8', width: 2 },
    itemStyle: { color: '#38bdf8' },
    areaStyle: { color: 'rgba(56, 189, 248, 0.1)' },
  }],
  dataZoom: [{ type: 'slider', bottom: 5 }, { type: 'inside' }],
}))

const getReservoirClass = (pct: number) => {
  if (pct < 20) return 'danger'
  if (pct < 50) return 'warning'
  return 'success'
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

.reservoir-details {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  line-height: 1.8;
}
</style>
