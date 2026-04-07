<template>
  <div class="station-selector">
    <div class="selector-group">
      <label class="text-secondary">County:</label>
      <select :value="county" @change="$emit('update:county', ($event.target as HTMLSelectElement).value)">
        <option value="">All Counties</option>
        <option v-for="c in counties" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>
    <div class="selector-group" v-if="county">
      <label class="text-secondary">Township:</label>
      <select :value="township" @change="$emit('update:township', ($event.target as HTMLSelectElement).value)">
        <option value="">All Townships</option>
        <option v-for="t in townships" :key="t" :value="t">{{ t }}</option>
      </select>
    </div>
    <div class="selector-group">
      <label class="text-secondary">Station:</label>
      <select :value="station" @change="$emit('update:station', ($event.target as HTMLSelectElement).value)">
        <option value="">{{ county ? 'Select Station' : 'All Stations' }}</option>
        <option v-for="s in stations" :key="s.stationCode" :value="s.stationCode">
          {{ s.stationName }}
          <template v-if="!county"> ({{ s.county }})</template>
          <template v-else-if="!township"> ({{ s.township }})</template>
        </option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Station } from '@/types'

defineProps<{
  county: string
  township: string
  station: string
  counties: string[]
  townships: string[]
  stations: Station[]
}>()

defineEmits<{
  'update:county': [value: string]
  'update:township': [value: string]
  'update:station': [value: string]
}>()
</script>

<style scoped>
.station-selector {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.selector-group {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.selector-group select {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  min-width: 140px;
}
</style>
