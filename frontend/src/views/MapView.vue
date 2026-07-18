<template>
  <div class="map-page">
    <div ref="mapEl" class="map"></div>

    <div class="panel">
      <div class="panel-title">圖層</div>
      <label v-for="l in layers" :key="l.id" class="layer-row">
        <input type="checkbox" v-model="l.on" @change="toggle(l)" />
        <span class="mark" :class="l.shape" :style="markStyle(l)"></span>
        <span class="layer-name">{{ l.label }}</span>
        <span class="layer-count">{{ l.count }}</span>
      </label>

      <div class="legend">
        <div class="leg-title">圖例</div>
        <div class="leg-row">
          <span class="leg-label">水庫蓄水率</span>
          <span class="grad-bar"></span>
          <span class="grad-ends"><i>低</i><i>滿</i></span>
        </div>
        <div class="leg-row">
          <span class="leg-label">地震規模</span>
          <span class="quake-scale">
            <span class="qs" style="width:8px;height:8px;background:#fde047"></span>
            <span class="qs" style="width:13px;height:13px;background:#f97316"></span>
            <span class="qs" style="width:19px;height:19px;background:#dc2626"></span>
          </span>
          <span class="grad-ends"><i>M4</i><i>M7+</i></span>
        </div>
      </div>

      <div class="panel-foot">
        <span v-if="lastUpdate" class="upd">更新 {{ lastUpdate }}</span>
        <button class="refresh" @click="refresh" :disabled="loading">{{ loading ? '載入中…' : '重新整理' }}</button>
      </div>
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
import gnssStations from '@/assets/gnss-stations.json'

const mapEl = ref<HTMLElement>()
let map: maplibregl.Map | null = null
const loading = ref(false)
const lastUpdate = ref('')

const layers = reactive([
  { id: 'rainfall', label: '雨量站', color: '#3b82f6', shape: 'dot', on: true, count: 0 },
  { id: 'weather', label: '天氣站', color: '#a855f7', shape: 'dot', on: true, count: 0 },
  { id: 'water', label: '水位站', color: '#14b8a6', shape: 'dot', on: true, count: 0 },
  { id: 'gnss', label: 'GNSS 監測站', color: '#d946ef', shape: 'tri', on: true, count: 0 },
  { id: 'reservoirs', label: '水庫', color: '', shape: 'grad', on: true, count: 0 },
  { id: 'quakes', label: '地震', color: '#f97316', shape: 'ring', on: true, count: 0 },
])
function markStyle(l: { color: string; shape: string }) {
  if (l.shape === 'dot') return { background: l.color }
  if (l.shape === 'tri') return { borderBottomColor: l.color }
  if (l.shape === 'ring') return { borderColor: l.color }
  return {}
}

const empty = () => ({ type: 'FeatureCollection' as const, features: [] as any[] })
const fc = (features: any[]) => ({ type: 'FeatureCollection' as const, features })
const pt = (lon: number, lat: number, properties: any) => ({
  type: 'Feature' as const, geometry: { type: 'Point' as const, coordinates: [lon, lat] }, properties,
})
const num = (v: any) => (v == null ? null : Number(v))
const geo = (arr: any[]) => arr.filter((s) => s.latitude != null && s.longitude != null)
const setData = (id: string, data: any) => (map?.getSource(id) as maplibregl.GeoJSONSource)?.setData(data)

function stationFC(arr: any[]) {
  return fc(geo(arr).map((s) => pt(+s.longitude, +s.latitude, { name: s.stationName || s.stationCode, code: s.stationCode, source: s.source, county: s.county })))
}

async function loadData() {
  if (!map) return
  loading.value = true
  try {
    const [rain, wea, wat, rv, eq] = await Promise.all([
      getStations({ type: 'RAINFALL' }).then((r) => r.data.data ?? []).catch(() => []),
      getStations({ type: 'WEATHER' }).then((r) => r.data.data ?? []).catch(() => []),
      getStations({ type: 'WATER_LEVEL' }).then((r) => r.data.data ?? []).catch(() => []),
      getReservoirs().then((r) => r.data.data ?? []).catch(() => []),
      getLatestEvents().then((r) => r.data.data ?? []).catch(() => []),
    ])
    setData('rainfall', stationFC(rain)); layers[0].count = geo(rain).length
    setData('weather', stationFC(wea)); layers[1].count = geo(wea).length
    setData('water', stationFC(wat)); layers[2].count = geo(wat).length

    const rvF = fc(geo(rv).map((r) => pt(+r.longitude, +r.latitude, { name: r.reservoirName || r.reservoirId, pct: num(r.storagePct), level: num(r.waterLevelM), county: r.county })))
    setData('reservoirs', rvF); layers[4].count = geo(rv).length

    const eqF = fc(geo(eq).map((e) => pt(+e.longitude, +e.latitude, { name: e.locationDesc || '地震', mag: num(e.magnitude), depth: num(e.depthKm), intensity: e.maxIntensity, time: e.time })))
    setData('quakes', eqF); layers[5].count = geo(eq).length

    lastUpdate.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

function toggle(l: { id: string; on: boolean }) {
  map?.setLayoutProperty(l.id, 'visibility', l.on ? 'visible' : 'none')
}
function popup(title: string, lines: string[]) {
  return `<div style="font:13px system-ui;min-width:160px;line-height:1.5"><b>${title}</b><br>${lines.filter(Boolean).join('<br>')}</div>`
}
function triangleIcon(color: string, size = 26): ImageData {
  const c = document.createElement('canvas'); c.width = c.height = size
  const g = c.getContext('2d')!
  g.beginPath(); g.moveTo(size / 2, 3); g.lineTo(size - 3, size - 4); g.lineTo(3, size - 4); g.closePath()
  g.fillStyle = color; g.fill(); g.lineWidth = 2; g.strokeStyle = '#ffffff'; g.stroke()
  return g.getImageData(0, 0, size, size)
}

onMounted(() => {
  map = new maplibregl.Map({
    container: mapEl.value!,
    style: {
      version: 8,
      sources: { osm: { type: 'raster', tiles: ['https://a.tile.openstreetmap.org/{z}/{x}/{y}.png', 'https://b.tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, attribution: '© OpenStreetMap' } },
      layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
    },
    center: [120.95, 23.75], zoom: 6.6,
  })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left')

  map.on('load', () => {
    map!.addImage('gnss-tri', triangleIcon('#d946ef'))
    for (const id of ['rainfall', 'weather', 'water', 'gnss', 'reservoirs', 'quakes']) {
      map!.addSource(id, { type: 'geojson', data: empty() })
    }
    const dot = (id: string, color: string, r = 3) => map!.addLayer({
      id, type: 'circle', source: id,
      paint: { 'circle-radius': r, 'circle-color': color, 'circle-opacity': 0.75, 'circle-stroke-width': 0.5, 'circle-stroke-color': '#0b1220' },
    })
    dot('rainfall', '#3b82f6'); dot('weather', '#a855f7'); dot('water', '#14b8a6', 3.5)
    map!.addLayer({
      id: 'reservoirs', type: 'circle', source: 'reservoirs',
      paint: {
        'circle-radius': 7,
        'circle-color': ['interpolate', ['linear'], ['coalesce', ['get', 'pct'], 50], 0, '#ef4444', 50, '#eab308', 100, '#22c55e'],
        'circle-stroke-width': 2, 'circle-stroke-color': '#ffffff', 'circle-opacity': 0.95,
      },
    })
    map!.addLayer({
      id: 'quakes', type: 'circle', source: 'quakes',
      paint: {
        'circle-radius': ['interpolate', ['linear'], ['coalesce', ['get', 'mag'], 4], 4, 6, 7, 22],
        'circle-color': ['interpolate', ['linear'], ['coalesce', ['get', 'mag'], 4], 4, '#fde047', 5.5, '#f97316', 7, '#dc2626'],
        'circle-opacity': 0.85, 'circle-stroke-width': 1.5, 'circle-stroke-color': '#7f1d1d',
      },
    })
    map!.addLayer({
      id: 'gnss', type: 'symbol', source: 'gnss',
      layout: { 'icon-image': 'gnss-tri', 'icon-size': 0.8, 'icon-allow-overlap': true, 'icon-ignore-placement': true },
    })

    // GNSS station positions are static reference data — set once
    setData('gnss', fc((gnssStations as any[]).map((s) => pt(s.lon, s.lat, { name: `${s.area} / ${s.station}`, area: s.area }))))
    layers[3].count = (gnssStations as any[]).length

    const handlers: Record<string, (p: any) => string> = {
      rainfall: (p) => popup(p.name, [`雨量站 · ${p.source ?? ''}`, p.county, `代碼 ${p.code}`]),
      weather: (p) => popup(p.name, [`天氣站 · ${p.source ?? ''}`, p.county, `代碼 ${p.code}`]),
      water: (p) => popup(p.name, [`水位站 · ${p.source ?? ''}`, p.county, `代碼 ${p.code}`]),
      gnss: (p) => popup(p.name, ['GNSS 監測站']),
      reservoirs: (p) => popup(p.name, [`蓄水率 ${p.pct != null ? p.pct + '%' : '—'}`, `水位 ${p.level != null ? p.level + ' m' : '—'}`, p.county]),
      quakes: (p) => popup(p.name, [`規模 M${p.mag ?? '—'} · 震度 ${p.intensity ?? '—'}`, `深度 ${p.depth ?? '—'} km`, p.time ? new Date(p.time).toLocaleString() : '']),
    }
    for (const id of ['rainfall', 'weather', 'water', 'gnss', 'reservoirs', 'quakes']) {
      map!.on('click', id, (e) => {
        const f = e.features?.[0]; if (!f) return
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
.panel { position: absolute; top: 12px; right: 12px; z-index: 5; width: 224px; background: var(--color-bg-secondary); border: 1px solid var(--color-border); border-radius: 10px; padding: 12px 14px; box-shadow: 0 6px 20px rgba(0,0,0,.28); }
.panel-title, .leg-title { font-size: .72rem; font-weight: 700; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .09em; }
.panel-title { margin-bottom: 8px; }
.layer-row { display: flex; align-items: center; gap: 9px; padding: 4px 0; font-size: .9rem; color: var(--color-text-secondary); cursor: pointer; }
.layer-name { flex: 1; }
.layer-count { font-variant-numeric: tabular-nums; color: var(--color-text-muted); font-size: .8rem; }
.mark { width: 13px; height: 13px; flex: none; display: inline-block; }
.mark.dot { border-radius: 50%; }
.mark.ring { border-radius: 50%; background: transparent; border: 2.5px solid; }
.mark.grad { border-radius: 50%; border: 1.5px solid #fff; background: linear-gradient(135deg, #ef4444, #eab308, #22c55e); }
.mark.tri { width: 0; height: 0; border-left: 7px solid transparent; border-right: 7px solid transparent; border-bottom: 12px solid; }
.legend { margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--color-border); }
.leg-title { margin-bottom: 8px; }
.leg-row { display: flex; align-items: center; gap: 8px; margin: 7px 0; font-size: .76rem; color: var(--color-text-muted); }
.leg-label { width: 66px; flex: none; }
.grad-bar { flex: 1; height: 9px; border-radius: 5px; background: linear-gradient(90deg, #ef4444, #eab308, #22c55e); }
.grad-ends { display: flex; justify-content: space-between; width: 34px; flex: none; }
.grad-ends i { font-style: normal; font-size: .68rem; }
.quake-scale { flex: 1; display: flex; align-items: center; gap: 6px; }
.qs { border-radius: 50%; display: inline-block; }
.panel-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--color-border); }
.upd { font-size: .74rem; color: var(--color-text-muted); }
.refresh { font-size: .78rem; padding: 4px 10px; border-radius: 6px; border: 1px solid var(--color-border); background: var(--color-bg-tertiary); color: var(--color-text-secondary); cursor: pointer; }
.refresh:disabled { opacity: .6; cursor: default; }
:deep(.maplibregl-popup-content) { background: var(--color-bg-secondary); color: var(--color-text-primary); border: 1px solid var(--color-border); border-radius: 8px; }
:deep(.maplibregl-popup-tip) { border-top-color: var(--color-bg-secondary); }
</style>
