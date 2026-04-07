<template>
  <router-view v-if="$route.meta.public" />
  <AppLayout v-else>
    <router-view />
  </AppLayout>
</template>

<script setup lang="ts">
import AppLayout from '@/components/AppLayout.vue'
</script>
