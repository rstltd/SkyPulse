<template>
  <div>
    <div class="page-header">
      <h1>Alert Center</h1>
      <p class="text-secondary">Active and historical hazard alerts</p>
    </div>

    <div class="toolbar">
      <label class="text-secondary">Type:</label>
      <select v-model="filterType">
        <option value="">All Types</option>
        <option v-for="t in alertTypes" :key="t" :value="t">{{ t }}</option>
      </select>
      <label class="text-secondary">Period:</label>
      <select v-model.number="days">
        <option :value="1">24 hours</option>
        <option :value="7">7 days</option>
        <option :value="30">30 days</option>
      </select>
      <div class="spacer"></div>
      <button class="btn btn-export" disabled>Export CSV</button>
    </div>

    <LoadingSpinner :loading="loading && !activeAlerts.length" text="Loading alerts..." />

    <template v-if="!loading || activeAlerts.length || alerts.length">
      <!-- Active Alerts -->
      <div v-if="activeAlerts.length" style="margin-bottom: var(--space-lg)">
        <h2 class="section-title">Active Alerts ({{ activeAlerts.length }})</h2>
        <div class="active-alerts">
          <div v-for="alert in activeAlerts" :key="alert.id" class="alert-banner"
            :class="(alert.severity || '').toLowerCase()">
            <div class="alert-header">
              <StatusBadge :level="alert.severity || alert.alertType" :label="alert.alertType" />
              <span class="alert-title">{{ alert.title }}</span>
              <span class="alert-time">{{ formatTime(alert.alertTime) }}</span>
            </div>
            <p class="alert-desc" v-if="alert.description">{{ alert.description }}</p>
            <p class="alert-area" v-if="alert.affectedArea">Area: {{ alert.affectedArea }}</p>
          </div>
        </div>
      </div>

      <!-- Historical Alerts -->
      <AppCard title="Alert History" :no-padding="true">
        <DataTable :columns="alertCols" :rows="alerts" empty-text="No alerts found"
          :page="page" :total-pages="totalPages" @page-change="p => { page = p; fetchAlerts() }">
          <template #alertTime="{ value }">{{ formatTime(value) }}</template>
          <template #alertType="{ value }">
            <StatusBadge :level="value" :label="value" />
          </template>
          <template #severity="{ value }">
            <StatusBadge :level="value || 'unknown'" />
          </template>
        </DataTable>
      </AppCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { getActiveAlerts, getAlerts } from '@/api/alerts'
import AppCard from '@/components/AppCard.vue'
import DataTable from '@/components/DataTable.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const activeAlerts = ref<any[]>([])
const alerts = ref<any[]>([])
const loading = ref(false)
const filterType = ref('')
const days = ref(7)
const page = ref(0)
const totalPages = ref(0)
const alertTypes = ref<string[]>([])

const alertCols = [
  { key: 'alertTime', label: 'Time' },
  { key: 'alertType', label: 'Type' },
  { key: 'severity', label: 'Severity' },
  { key: 'title', label: 'Title' },
  { key: 'source', label: 'Source' },
]

const fetchActive = async () => {
  try {
    const res = await getActiveAlerts()
    if (res.data.success) {
      activeAlerts.value = res.data.data || []
      const types = [...new Set(activeAlerts.value.map(a => a.alertType))]
      if (types.length > alertTypes.value.length) alertTypes.value = types.sort()
    }
  } catch (e) {
    console.error('Active alerts fetch error:', e)
  }
}

const fetchAlerts = async () => {
  loading.value = true
  try {
    const since = new Date(Date.now() - days.value * 86400000).toISOString()
    const res = await getAlerts({
      type: filterType.value || undefined,
      since,
      page: page.value,
      size: 20,
    })
    if (res.data.success && res.data.data) {
      alerts.value = res.data.data.content || []
      totalPages.value = res.data.data.totalPages || 0
      const types = [...new Set(alerts.value.map((a: any) => a.alertType))]
      if (types.length > alertTypes.value.length) alertTypes.value = types.sort()
    }
  } catch (e) {
    console.error('Alerts fetch error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => { fetchActive(); fetchAlerts() })
watch([filterType, days], () => { page.value = 0; fetchAlerts() })

const formatTime = (iso: string) => {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-TW', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
.section-title {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: var(--space-md);
  color: var(--color-text-secondary);
}

.active-alerts {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.alert-banner {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-text-muted);
  border-radius: var(--radius-sm);
  padding: var(--space-md);
}

.alert-banner.warning { border-left-color: var(--color-warning); }
.alert-banner.severe, .alert-banner.danger { border-left-color: var(--color-danger); }
.alert-banner.caution { border-left-color: var(--color-orange); }

.alert-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.alert-title {
  font-weight: 600;
  flex: 1;
}

.alert-time {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.alert-desc {
  margin-top: var(--space-sm);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.alert-area {
  margin-top: var(--space-xs);
  color: var(--color-text-muted);
  font-size: 0.8rem;
}
</style>
