import { ref, computed, watch } from 'vue'
import { getStations } from '@/api/stations'
import type { Station } from '@/types'

// Counties ordered: North → Central → South → East → Islands
const COUNTY_ORDER: string[] = [
  // North
  '基隆市', '臺北市', '新北市', '桃園市', '新竹市', '新竹縣',
  // Central
  '苗栗縣', '臺中市', '彰化縣', '南投縣', '雲林縣',
  // South
  '嘉義市', '嘉義縣', '臺南市', '高雄市', '屏東縣',
  // East
  '宜蘭縣', '花蓮縣', '臺東縣',
  // Islands
  '澎湖縣', '金門縣', '連江縣',
]

function countyIndex(county: string): number {
  const idx = COUNTY_ORDER.indexOf(county)
  return idx >= 0 ? idx : 999
}

export function useStationFilter(filterParams?: { type?: string; source?: string }) {
  const allStations = ref<Station[]>([])
  const selectedCounty = ref('')
  const selectedTownship = ref('')
  const selectedStation = ref('')
  const loading = ref(false)

  const loadStations = async () => {
    loading.value = true
    try {
      const res = await getStations(filterParams)
      if (res.data.success) {
        allStations.value = res.data.data || []
        // Auto-select first county → township → station after loading
        autoSelectDefaults()
      }
    } catch (e) {
      console.error('Station fetch error:', e)
    } finally {
      loading.value = false
    }
  }

  // Sorted unique counties
  const counties = computed(() => {
    const set = new Set(allStations.value.map(s => s.county).filter(Boolean) as string[])
    return [...set].sort((a, b) => countyIndex(a) - countyIndex(b))
  })

  // Townships filtered by selected county
  const townships = computed(() => {
    if (!selectedCounty.value) return []
    const set = new Set(
      allStations.value
        .filter(s => s.county === selectedCounty.value)
        .map(s => s.township)
        .filter(Boolean) as string[]
    )
    return [...set].sort()
  })

  // Stations filtered by county + township
  const filteredStations = computed(() => {
    let list = allStations.value
    if (selectedCounty.value) {
      list = list.filter(s => s.county === selectedCounty.value)
    }
    if (selectedTownship.value) {
      list = list.filter(s => s.township === selectedTownship.value)
    }
    return list.sort((a, b) => a.stationName.localeCompare(b.stationName))
  })

  // Auto-select first available defaults
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

  // When county changes: auto-select first township and station
  const onCountyChange = () => {
    selectedTownship.value = ''
    selectedStation.value = ''
    if (townships.value.length) {
      selectedTownship.value = townships.value[0]
    }
    if (filteredStations.value.length) {
      selectedStation.value = filteredStations.value[0].stationCode
    }
  }

  // When township changes: auto-select first station
  const onTownshipChange = () => {
    selectedStation.value = ''
    if (filteredStations.value.length) {
      selectedStation.value = filteredStations.value[0].stationCode
    }
  }

  // Label for display
  const selectedStationLabel = computed(() => {
    if (!selectedStation.value) return ''
    const s = allStations.value.find(s => s.stationCode === selectedStation.value)
    return s ? `${s.stationName} (${s.county} ${s.township})` : selectedStation.value
  })

  // Get station name by code
  const getStationLabel = (code: string) => {
    const s = allStations.value.find(s => s.stationCode === code)
    return s ? s.stationName : code
  }

  return {
    allStations,
    selectedCounty,
    selectedTownship,
    selectedStation,
    counties,
    townships,
    filteredStations,
    selectedStationLabel,
    getStationLabel,
    onCountyChange,
    onTownshipChange,
    loadStations,
    loading,
  }
}
