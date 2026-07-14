<template>
  <div class="context-monitor">
    <div class="page-header">
      <h2>座標查詢監看台</h2>
      <p class="subtitle">輸入場址座標，取得整合環境脈絡與土石流警戒燈號（資料完全公開、不儲存任何場址）</p>
    </div>

    <!-- Query form -->
    <form class="query-bar" @submit.prevent="query">
      <label>緯度 lat
        <input v-model.number="lat" type="number" step="any" placeholder="23.5108" required />
      </label>
      <label>經度 lon
        <input v-model.number="lon" type="number" step="any" placeholder="120.8052" required />
      </label>
      <label>半徑 km（選填）
        <input v-model.number="radiusKm" type="number" step="any" min="0.1" max="100" placeholder="auto" />
      </label>
      <button type="submit" class="btn-query" :disabled="loading">查詢</button>
    </form>

    <p v-if="error" class="err">{{ error }}</p>
    <LoadingSpinner :loading="loading" />

    <template v-if="ctx && !loading">
      <!-- Location + query echo -->
      <AppCard>
        <div class="loc-row">
          <div>
            <span class="loc-place">{{ ctx.location.county || '—' }} {{ ctx.location.township || '' }}</span>
            <StatusBadge v-if="!ctx.location.inTaiwan" level="severe" label="涵蓋區外" />
          </div>
          <div class="loc-meta">
            座標 {{ ctx.query.lat }}, {{ ctx.query.lon }} ·
            半徑 {{ ctx.query.autoRadius ? `auto(≤${ctx.query.maxAutoRadiusKm}km)` : `${ctx.query.radiusKm}km` }} ·
            地震半徑 {{ ctx.query.quakeRadiusKm }}km
          </div>
        </div>
      </AppCard>

      <!-- Rainfall warning light (headline) -->
      <AppCard title="降雨 / 土石流警戒">
        <div v-if="ctx.rainfall" class="rain-block">
          <div class="signal-lamp" :class="signalClass(ctx.rainfall.signal)">
            <span class="lamp-dot"></span>
            <span class="lamp-text">{{ signalText(ctx.rainfall.signal) }}</span>
          </div>
          <p class="signal-basis">{{ ctx.rainfall.signalBasis || '無鄉鎮警戒基準值可比對' }}</p>
          <div class="metric-grid">
            <div class="metric"><span>最大時雨量 I</span><b>{{ fmt(ctx.rainfall.maxHourlyIntensityMm) }} mm</b></div>
            <div class="metric"><span>有效累積雨量 Rt</span><b>{{ fmt(ctx.rainfall.effectiveRainfallMm) }} mm</b></div>
            <div class="metric"><span>RTI = I×Rt</span><b>{{ fmt(ctx.rainfall.rti) }}</b></div>
            <div class="metric" v-if="ctx.rainfall.alertBaseline">
              <span>鄉鎮警戒值 R70</span><b>{{ fmt(ctx.rainfall.alertBaseline.thresholdMm) }} mm</b>
            </div>
          </div>
          <div class="metric-grid accum">
            <div class="metric" v-for="w in accumWindows" :key="w.k">
              <span>{{ w.label }}</span><b>{{ fmt((ctx.rainfall.accumulatedMm as any)[w.k]) }}</b>
            </div>
          </div>
          <ProvFresh :prov="ctx.rainfall.provenance" :fresh="ctx.rainfall.freshness" />
        </div>
        <p v-else class="empty">此半徑內無雨量站</p>
      </AppCard>

      <div class="two-col">
        <!-- Water level -->
        <AppCard title="水位">
          <div v-if="ctx.waterLevel" class="wl-block">
            <div class="wl-head">
              <span class="wl-value">{{ fmt(ctx.waterLevel.waterLevelM) }} m</span>
              <StatusBadge :level="statusClass(ctx.waterLevel.alertStatus)" :label="ctx.waterLevel.alertStatus || 'N/A'" />
            </div>
            <div class="metric-grid" v-if="ctx.waterLevel.alertLevels">
              <div class="metric"><span>一級</span><b>{{ fmt(ctx.waterLevel.alertLevels.level1) }}</b></div>
              <div class="metric"><span>二級</span><b>{{ fmt(ctx.waterLevel.alertLevels.level2) }}</b></div>
              <div class="metric"><span>三級</span><b>{{ fmt(ctx.waterLevel.alertLevels.level3) }}</b></div>
            </div>
            <ProvFresh :prov="ctx.waterLevel.provenance" :fresh="ctx.waterLevel.freshness" />
          </div>
          <p v-else class="empty">此半徑內無水位站</p>
        </AppCard>

        <!-- Seismic -->
        <AppCard title="鄰近地震">
          <div v-if="ctx.seismic" class="seis-block">
            <div v-if="ctx.seismic.strongestNearby" class="seis-strong">
              <span class="seis-mag">M{{ ctx.seismic.strongestNearby.magnitude }}</span>
              <StatusBadge v-if="ctx.seismic.strongestNearby.maxIntensity"
                level="caution" :label="`震度 ${ctx.seismic.strongestNearby.maxIntensity}`" />
              <div class="seis-meta">
                深度 {{ fmt(ctx.seismic.strongestNearby.depthKm) }}km ·
                距 {{ fmt(ctx.seismic.strongestNearby.distanceKm) }}km ·
                {{ ctx.seismic.strongestNearby.locationDesc }}
              </div>
              <div class="seis-time">{{ fmtTime(ctx.seismic.strongestNearby.time) }}</div>
            </div>
            <p v-else class="empty">半徑內近 {{ ctx.seismic.window }} 無地震</p>
            <div class="seis-count">近 {{ ctx.seismic.window }} 內共 {{ ctx.seismic.nearbyCount }} 起</div>
            <ProvFresh :prov="ctx.seismic.provenance" :fresh="ctx.seismic.freshness" />
          </div>
          <p v-else class="empty">—</p>
        </AppCard>
      </div>

      <!-- GNSS quality (global) -->
      <AppCard title="GNSS 品質（太空天氣，全域）">
        <div class="gnss-block">
          <StatusBadge :level="qualityClass(ctx.gnssQuality.qualityLevel)" :label="ctx.gnssQuality.qualityLevel" />
          <span class="gnss-rec">建議：{{ ctx.gnssQuality.recommendation }}</span>
          <div class="metric-grid">
            <div class="metric"><span>Kp</span><b>{{ fmt(ctx.gnssQuality.kpIndex) }}</b></div>
            <div class="metric"><span>Dst</span><b>{{ fmt(ctx.gnssQuality.dstIndex) }} nT</b></div>
            <div class="metric"><span>Bz</span><b>{{ fmt(ctx.gnssQuality.bzComponent) }}</b></div>
            <div class="metric"><span>太陽風</span><b>{{ fmt(ctx.gnssQuality.solarWindSpeed) }} km/s</b></div>
            <div class="metric"><span>G / R / S</span><b>{{ ctx.gnssQuality.gScale }} / {{ ctx.gnssQuality.rScale }} / {{ ctx.gnssQuality.sScale }}</b></div>
          </div>
          <p class="assess">{{ ctx.gnssQuality.assessment }}</p>
          <div class="fresh-line">新鮮度：{{ fmtAge(ctx.gnssQuality.freshness.ageSeconds) }} 前
            <span v-if="ctx.gnssQuality.freshness.stale" class="stale">· 過期</span></div>
        </div>
      </AppCard>

      <!-- Warnings -->
      <AppCard v-if="ctx.warnings.length" title="提示 / 警告">
        <ul class="warn-list">
          <li v-for="(w, i) in ctx.warnings" :key="i"><code>{{ w.code }}</code> [{{ w.domain }}] {{ w.message }}</li>
        </ul>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, h, defineComponent } from 'vue'
import { getContext } from '@/api/context'
import type { ContextResponse, Provenance, Freshness } from '@/types'
import AppCard from '@/components/AppCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const lat = ref<number | null>(null)
const lon = ref<number | null>(null)
const radiusKm = ref<number | null>(null)
const ctx = ref<ContextResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const accumWindows = [
  { k: 'h3', label: '3h' }, { k: 'h6', label: '6h' }, { k: 'h12', label: '12h' },
  { k: 'h24', label: '24h' }, { k: 'h48', label: '48h' }, { k: 'h72', label: '72h' },
]

const query = async () => {
  if (lat.value == null || lon.value == null) { error.value = '請輸入經緯度'; return }
  loading.value = true
  error.value = null
  try {
    const res = await getContext(lat.value, lon.value,
      radiusKm.value != null ? { radiusKm: radiusKm.value } : {})
    if (res.data.success && res.data.data) {
      ctx.value = res.data.data
    } else {
      error.value = res.data.message || '查詢失敗'
      ctx.value = null
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || '查詢失敗，請確認已登入'
    ctx.value = null
  } finally {
    loading.value = false
  }
}

const fmt = (v: number | null | undefined) => (v == null ? '—' : v)
const fmtAge = (sec: number | null | undefined) =>
  sec == null ? '—' : sec < 60 ? `${sec}s` : sec < 3600 ? `${Math.round(sec / 60)}m` : `${Math.round(sec / 3600)}h`
const fmtTime = (t: string) => new Date(t).toLocaleString('zh-TW', { hour12: false })

const signalClass = (s: string | null) => (s ? s.toLowerCase() : 'none')
const signalText = (s: string | null) =>
  s === 'RED' ? '紅色警戒' : s === 'YELLOW' ? '黃色警戒' : s === 'GREEN' ? '綠燈（正常）' : '無燈號'
const statusClass = (s: string | null) =>
  s === 'LEVEL3' ? 'severe' : s === 'LEVEL2' ? 'degraded' : s === 'LEVEL1' ? 'caution' : 'normal'
const qualityClass = (q: string) => q.toLowerCase()

// Small inline provenance + freshness footer.
const ProvFresh = defineComponent({
  props: { prov: { type: Object as () => Provenance, required: true },
           fresh: { type: Object as () => Freshness, required: true } },
  setup(p) {
    return () => h('div', { class: 'prov-fresh' }, [
      h('span', { class: 'prov' },
        `來源 ${p.prov.source}` +
        (p.prov.stationName ? ` · ${p.prov.stationName}(${p.prov.stationCode})` : '') +
        (p.prov.distanceKm != null ? ` · ${p.prov.distanceKm}km` : '') +
        (p.prov.dataset ? ` · ${p.prov.dataset}` : '')),
      h('span', { class: 'fresh' + (p.fresh.stale ? ' stale' : '') },
        `新鮮度 ${p.fresh.ageSeconds == null ? '—' : fmtAge(p.fresh.ageSeconds) + ' 前'}` +
        (p.fresh.stale ? ' · 過期' : '')),
    ])
  },
})
</script>

<style scoped>
.page-header h2 { margin: 0; }
.subtitle { color: var(--color-text-muted); font-size: 0.85rem; margin: 4px 0 16px; }
.query-bar { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 16px; }
.query-bar label { display: flex; flex-direction: column; font-size: 0.8rem; color: var(--color-text-muted); gap: 4px; }
.query-bar input { background: var(--color-bg-elevated, #1e293b); border: 1px solid #334155; color: var(--color-text, #e2e8f0); padding: 8px 10px; border-radius: 6px; width: 140px; }
.btn-query { background: var(--color-accent, #38bdf8); color: #06283d; border: none; padding: 9px 22px; border-radius: 6px; font-weight: 600; cursor: pointer; }
.btn-query:disabled { opacity: 0.5; cursor: default; }
.err { color: var(--color-severe, #f87171); }
.empty, .assess { color: var(--color-text-muted); font-size: 0.85rem; }
.loc-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.loc-place { font-size: 1.1rem; font-weight: 600; margin-right: 8px; }
.loc-meta { color: var(--color-text-muted); font-size: 0.8rem; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 720px) { .two-col { grid-template-columns: 1fr; } }
.signal-lamp { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 8px; }
.lamp-dot { width: 22px; height: 22px; border-radius: 50%; box-shadow: 0 0 12px currentColor; }
.lamp-text { font-size: 1.3rem; font-weight: 700; }
.signal-lamp.green { background: rgba(74,222,128,.1); color: var(--color-normal, #4ade80); }
.signal-lamp.yellow { background: rgba(250,204,21,.1); color: var(--color-caution, #facc15); }
.signal-lamp.red { background: rgba(248,113,113,.12); color: var(--color-severe, #f87171); }
.signal-lamp.none { background: rgba(148,163,184,.1); color: var(--color-text-muted, #94a3b8); }
.signal-basis { font-size: 0.9rem; margin: 10px 0; }
.metric-grid { display: flex; flex-wrap: wrap; gap: 18px; margin: 10px 0; }
.metric-grid.accum { border-top: 1px solid #334155; padding-top: 10px; }
.metric { display: flex; flex-direction: column; }
.metric span { font-size: 0.72rem; color: var(--color-text-muted); }
.metric b { font-size: 1rem; }
.wl-head { display: flex; align-items: center; gap: 12px; }
.wl-value { font-size: 1.4rem; font-weight: 700; }
.seis-mag { font-size: 1.4rem; font-weight: 700; margin-right: 8px; }
.seis-meta, .seis-time, .seis-count { color: var(--color-text-muted); font-size: 0.8rem; margin-top: 4px; }
.gnss-rec { margin-left: 12px; font-size: 0.85rem; }
.prov-fresh { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; border-top: 1px solid #334155; margin-top: 12px; padding-top: 8px; font-size: 0.72rem; color: var(--color-text-muted); }
.fresh.stale, .stale { color: var(--color-severe, #f87171); }
.fresh-line { font-size: 0.72rem; color: var(--color-text-muted); margin-top: 8px; }
.warn-list { margin: 0; padding-left: 18px; font-size: 0.85rem; }
.warn-list code { color: var(--color-caution, #facc15); }
</style>
