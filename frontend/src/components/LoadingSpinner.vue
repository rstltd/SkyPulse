<template>
  <div class="loading" v-if="loading">
    <div class="spinner"></div>
    <span v-if="text" class="loading-text">{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  loading: boolean
  text?: string
}>()
</script>

<style scoped>
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  padding: var(--space-xl);
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid var(--color-bg-tertiary);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-text {
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
