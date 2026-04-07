<template>
  <div class="chart-container" :style="{ height: height }">
    <LoadingSpinner :loading="loading" v-if="loading" />
    <v-chart v-else :option="option" autoresize :theme="'dark'" />
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import LoadingSpinner from './LoadingSpinner.vue'

defineProps<{
  option: Record<string, any>
  height?: string
  loading?: boolean
}>()
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: 350px;
  min-height: 200px;
}
</style>
