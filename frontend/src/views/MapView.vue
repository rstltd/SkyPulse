<template>
  <div class="map-page">
    <div ref="mapEl" class="map"></div>

    <div class="panel">
      <div class="panel-title">圖層</div>
      <label v-for="l in layers" :key="l.id" class="layer-row">
        <input type="checkbox" v-model="l.on" @change="toggle(l)" />
        <span class="swatch" :style="{ background: l.color }"></span>
        <span class="layer-name">{{ l.label }}</span>
        <span class="layer-count">{{ l.count }}</span>
      </label>
      <div class="panel-foot">
        <span v-if="lastUpdate" class="upd">更新 {{ lastUpdate }}</span>
        <button class="refresh" @click="refresh" :disabled="loading">{{ loading ? '載入中…' : '重新整理' }}</button>
      </div>
      <div class="legend">地震圓圈大小＝規模；顏色 黃→紅＝規模愈大。水庫顏色 紅→綠＝蓄水率。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { getStations } from '@/api/stations'
import { getReservoirs } from '@/api/hydrology'
import { getLatestEvents } from '@/api/seismic'
import { usePolling } from '@/composables/usePolling'

const mapEl = ref<HTMLElement>()
let map: maplibregl.Map | null = null
const loading = ref(false)
const lastUpdate = ref('')

const layers = reactive([
  { id: 'stations', label: '測站', color: '#38bdf8', on: true, count: 0 },
  { id: 'reservoirs', label: '水庫', color: '#22c55e', on: true, count: 0 },
  { id: 'quakes', label: '地震', color: '#f97316', on: true, count: 0 },
])

const empty = () => ({ type: 'FeatureCollection' as const, features: [] as any[] })
const fc = (features: any[]) => ({ type: 'FeatureCollection' as const, features })
const pt = (lon: number, lat: number, properties: any) => ({
  type: 'Feature' as const, geometry: { type: 'Point' as const, coordinates: [lon, lat] }, properties,
})
const num = (v: any) => (v == null ? null : Number(v))

async function loadData() {
  if (!map) return
  loading.value = true
  try {
    const [st, rv, eq] = await Promise.all([
      getStations().then((r) => r.data.data ?? []).catch(() => []),
      getReservoirs().then((r) => r.data.data ?? []).catch(() => []),
      getLatestEvents().then((r) => r.data.data ?? []).catch(() => []),
    ])
    const stF = st.filter((s: any) => s.latitude != null && s.longitude != null)
      .map((s: any) => pt(+s.longitude, +s.latitude, { name: s.station_name || s.station_code, source: s.source, code: s.station_code }))
    const rvF = rv.filter((s: any) => s.latitude != null && s.longitude != null)
      .map((s: any) => pt(+s.longitude, +s.latitude, { name: s.reservoir_name || s.reservoir_id, pct: num(s.storage_pct) }))
    const eqF = eq.filter((s: any) => s.latitude != null && s.longitude != null)
      .map((s: any) => pt(+s.longitude, +s.latitude, { name: s.location_desc || '地震', mag: num(s.magnitude), depth: num(s.depth_km), time: s.time }))

    layers[0].count = stF.length
    layers[1].count = rvF.length
    layers[2].count = eqF.length
    ;(map.getSource('stations') as maplibregl.GeoJSONSource)?.setData(fc(stF))
    ;(map.getSource('reservoirs') as maplibregl.GeoJSONSource)?.setData(fc(rvF))
    ;(map.getSource('quakes') as maplibregl.GeoJSONSource)?.setData(fc(eqF))
    lastUpdate.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

function toggle(l: { id: string; on: boolean }) {
  map?.setLayoutProperty(l.id, 'visibility', l.on ? 'visible' : 'none')
}

function popup(title: string, lines: string[]) {
  return `<div style="font:13px system-ui;min-width:150px"><b>${title}</b><br>${lines.filter(Boolean).join('<br>')}</div>`
}

onMounted(() => {
  map = new maplibregl.Map({
    container: mapEl.value!,
    style: {
      version: 8,
      sources: {
        osm: {
          type: 'raster',
          tiles: ['https://a.tile.openstreetmap.org/{z}/{x}/{y}.png', 'https://b.tile.openstreetmap.org/{z}/{x}/{y}.png'],
          tileSize: 256,
          attribution: '© OpenStreetMap contributors',
        },
      },
      layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
    },
    center: [120.95, 23.75],
    zoom: 6.6,
  })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left')

  map.on('load', () => {
    for (const id of ['stations', 'reservoirs', 'quakes']) {
      map!.addSource(id, { type: 'geojson', data: empty() })
    }
    map!.addLayer({
      id: 'stations', type: 'circle', source: 'stations',
      paint: { 'circle-radius': 3, 'circle-color': '#38bdf8', 'circle-opacity': 0.7, 'circle-stroke-width': 0.5, 'circle-stroke-color': '#0b1220' },
    })
    map!.addLayer({
      id: 'reservoirs', type: 'circle', source: 'reservoirs',
      paint: {
        'circle-radius': 6,
        'circle-color': ['interpolate', ['linear'], ['coalesce', ['get', 'pct'], 50], 0, '#ef4444', 50, '#eab308', 100, '#22c55e'],
        'circle-stroke-width': 1.5, 'circle-stroke-color': '#0b1220',
      },
    })
    map!.addLayer({
      id: 'quakes', type: 'circle', source: 'quakes',
      paint: {
        'circle-radius': ['interpolate', ['linear'], ['coalesce', ['get', 'mag'], 4], 4, 5, 7, 22],
        'circle-color': ['interpolate', ['linear'], ['coalesce', ['get', 'mag'], 4], 4, '#fde047', 5.5, '#f97316', 7, '#dc2626'],
        'circle-opacity': 0.8, 'circle-stroke-width': 1, 'circle-stroke-color': '#0b1220',
      },
    })

    const handlers: Record<string, (p: any) => string> = {
      stations: (p) => popup(p.name, [`來源 ${p.source ?? '—'}`, `代碼 ${p.code}`]),
      reservoirs: (p) => popup(p.name, [`蓄水率 ${p.pct != null ? p.pct + '%' : '—'}`]),
      quakes: (p) => popup(p.name, [`規模 M${p.mag ?? '—'}`, `深度 ${p.depth ?? '—'} km`, p.time ? new Date(p.time).toLocaleString() : '']),
    }
    for (const id of ['stations', 'reservoirs', 'quakes']) {
      map!.on('click', id, (e) => {
        const f = e.features?.[0]
        if (!f) return
        new maplibregl.Popup().setLngLat((f.geometry as any).coordinates).setHTML(handlers[id](f.properties)).addTo(map!)
      })
      map!.on('mouseenter', id, () => { map!.getCanvas().style.cursor = 'pointer' })
      map!.on('mouseleave', id, () => { map!.getCanvas().style.cursor = '' })
    }
    loadData()
  })
})

const { refresh } = usePolling(loadData, 60000)

onUnmounted(() => { map?.remove(); map = null })
</script>

<style scoped>
.map-page { position: relative; height: calc(100vh - 140px); min-height: 500px; border-radius: 10px; overflow: hidden; border: 1px solid var(--color-border); }
.map { position: absolute; inset: 0; }
.panel {
  position: absolute; top: 12px; right: 12px; z-index: 5; width: 210px;
  background: var(--color-bg-secondary); border: 1px solid var(--color-border);
  border-radius: 10px; padding: 12px 14px; box-shadow: 0 6px 20px rgba(0,0,0,.25);
}
.panel-title { font-size: .8rem; font-weight: 700; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .08em; margin-bottom: 8px; }
.layer-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: .9rem; color: var(--color-text-secondary); cursor: pointer; }
.swatch { width: 11px; height: 11px; border-radius: 3px; flex: none; }
.layer-name { flex: 1; }
.layer-count { font-variant-numeric: tabular-nums; color: var(--color-text-muted); font-size: .82rem; }
.panel-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--color-border); }
.upd { font-size: .75rem; color: var(--color-text-muted); }
.refresh { font-size: .78rem; padding: 4px 10px; border-radius: 6px; border: 1px solid var(--color-border); background: var(--color-bg-tertiary); color: var(--color-text-secondary); cursor: pointer; }
.refresh:disabled { opacity: .6; cursor: default; }
.legend { margin-top: 10px; font-size: .72rem; line-height: 1.5; color: var(--color-text-muted); }
:deep(.maplibregl-popup-content) { background: var(--color-bg-secondary); color: var(--color-text-primary); border: 1px solid var(--color-border); border-radius: 8px; }
:deep(.maplibregl-popup-tip) { border-top-color: var(--color-bg-secondary); }
</style>
