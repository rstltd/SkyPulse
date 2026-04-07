<template>
  <div class="empty-state">
    <span class="empty-icon">{{ icon }}</span>
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  message: string
  icon?: string
}>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2xl);
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 2rem;
  margin-bottom: var(--space-md);
  opacity: 0.5;
}
</style>
