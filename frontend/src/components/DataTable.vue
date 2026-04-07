<template>
  <div class="table-wrapper">
    <table>
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="col.width ? { width: col.width } : {}">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td :colspan="columns.length" class="empty-row">
            {{ emptyText }}
          </td>
        </tr>
        <tr v-for="(row, i) in rows" :key="i">
          <td v-for="col in columns" :key="col.key">
            <slot :name="col.key" :row="row" :value="row[col.key]">
              {{ row[col.key] ?? '-' }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pagination" v-if="totalPages && totalPages > 1">
      <button class="btn" :disabled="(page ?? 0) <= 0" @click="$emit('page-change', (page ?? 0) - 1)">&laquo; Prev</button>
      <span class="page-info">{{ (page ?? 0) + 1 }} / {{ totalPages }}</span>
      <button class="btn" :disabled="(page ?? 0) >= totalPages - 1" @click="$emit('page-change', (page ?? 0) + 1)">Next &raquo;</button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  columns: Array<{ key: string; label: string; width?: string }>
  rows: any[]
  emptyText?: string
  page?: number
  totalPages?: number
}>()

defineEmits<{
  'page-change': [page: number]
}>()
</script>

<style scoped>
.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
}

thead th {
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: var(--space-sm) var(--space-md);
  white-space: nowrap;
}

tbody td {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-border);
  font-size: 0.875rem;
}

tbody tr:hover {
  background: rgba(56, 189, 248, 0.05);
}

.empty-row {
  text-align: center;
  color: var(--color-text-muted);
  padding: var(--space-xl) !important;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border-top: 1px solid var(--color-border);
}

.page-info {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}
</style>
