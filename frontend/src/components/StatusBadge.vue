<template>
  <span class="badge" :class="level.toLowerCase()">{{ label || level }}</span>
</template>

<script setup lang="ts">
defineProps<{
  level: string
  label?: string
}>()
</script>

<style scoped>
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.normal, .success, .up {
  background: rgba(74, 222, 128, 0.15);
  color: var(--color-normal);
}

.caution, .warning {
  background: rgba(250, 204, 21, 0.15);
  color: var(--color-caution);
}

.degraded, .orange {
  background: rgba(251, 146, 60, 0.15);
  color: var(--color-degraded);
}

.severe, .danger, .failure, .error {
  background: rgba(248, 113, 113, 0.15);
  color: var(--color-severe);
}

.empty, .unknown {
  background: rgba(148, 163, 184, 0.15);
  color: var(--color-text-muted);
}
</style>
