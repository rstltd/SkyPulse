<template>
  <div class="card">
    <div class="card-header" v-if="title || $slots.header">
      <slot name="header">
        <h3 class="card-title">{{ title }}</h3>
        <span class="card-subtitle" v-if="subtitle">{{ subtitle }}</span>
      </slot>
    </div>
    <div class="card-body" :class="{ 'no-padding': noPadding }">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
  noPadding?: boolean
}>()
</script>

<style scoped>
.card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.card-title {
  font-size: 1rem;
  font-weight: 600;
}

.card-subtitle {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.card-body {
  padding: var(--space-lg);
}

.card-body.no-padding {
  padding: 0;
}
</style>
