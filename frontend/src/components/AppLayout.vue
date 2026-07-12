<template>
  <div class="layout">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <span class="logo" v-if="!sidebarCollapsed">SkyPulse</span>
        <span class="logo logo-short" v-else>SP</span>
        <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          {{ sidebarCollapsed ? '\u25B6' : '\u25C0' }}
        </button>
      </div>
      <nav class="sidebar-nav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path"
          class="nav-item" :class="{ active: $route.path === item.path }">
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label" v-if="!sidebarCollapsed">
            {{ item.label }}
            <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
          </span>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <button class="nav-item logout-btn" @click="handleLogout">
          <span class="nav-icon">&#x23FB;</span>
          <span class="nav-label" v-if="!sidebarCollapsed">Logout</span>
        </button>
      </div>
    </aside>
    <main class="main-content">
      <header class="top-bar">
        <div class="top-bar-left">
          <button class="mobile-menu-btn" @click="mobileMenuOpen = !mobileMenuOpen">&#x2630;</button>
          <h2 class="current-page">{{ currentPageTitle }}</h2>
        </div>
        <div class="top-bar-right">
          <span class="user-info">{{ user?.username }}</span>
        </div>
      </header>
      <div v-if="activeFailure" class="data-status-banner" role="alert">
        <span class="dsb-icon">&#x26A0;</span>
        <span class="dsb-text">
          資料更新失敗（{{ failureTime }}）——畫面顯示的可能是舊資料，不代表一切正常。
        </span>
        <button class="dsb-dismiss" @click="dismiss" aria-label="關閉此提示">&#x2715;</button>
      </div>
      <div class="content-area">
        <slot />
      </div>
    </main>
    <div class="mobile-overlay" v-if="mobileMenuOpen" @click="mobileMenuOpen = false"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { useDataStatus } from '@/composables/useDataStatus'

const route = useRoute()
const router = useRouter()
const { user, logout } = useAuth()
const { activeFailure, dismiss } = useDataStatus()

const failureTime = computed(() =>
  activeFailure.value ? activeFailure.value.at.toLocaleTimeString() : ''
)

const sidebarCollapsed = ref(false)
const mobileMenuOpen = ref(false)

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '\u25A3' },
  { path: '/weather', label: '\u5929\u6C23/\u96E8\u91CF', icon: '\u2602' },
  { path: '/seismic', label: '\u5730\u9707\u4E8B\u4EF6', icon: '\u2746' },
  { path: '/hydrology', label: '\u6C34\u6587\u8CC7\u6599', icon: '\u2B29' },
  { path: '/spaceweather', label: '\u592A\u7A7A\u5929\u6C23', icon: '\u2600' },
  { path: '/alerts', label: '\u8B66\u5831\u4E2D\u5FC3', icon: '\u26A0' },
  { path: '/logs', label: '\u65E5\u8A8C\u7D00\u9304', icon: '\u2263', badge: 'Soon' },
  { path: '/admin', label: '\u7CFB\u7D71\u7BA1\u7406', icon: '\u2699' },
]

const currentPageTitle = computed(() => {
  const item = navItems.find(n => n.path === route.path)
  return item?.label || ''
})

const handleLogout = async () => {
  await logout()
  router.push({ name: 'Login' })
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md);
  border-bottom: 1px solid var(--color-border);
  min-height: 56px;
}

.logo {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-accent);
  white-space: nowrap;
}

.logo-short {
  font-size: 1.1rem;
  margin: 0 auto;
}

.toggle-btn {
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  padding: var(--space-xs);
}

.sidebar-nav {
  flex: 1;
  padding: var(--space-sm) 0;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
  border: none;
  background: none;
  width: 100%;
  text-align: left;
  font-size: 0.9rem;
}

.nav-item:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-tertiary);
}

.nav-item.active {
  color: var(--color-accent);
  background: rgba(56, 189, 248, 0.1);
  border-right: 3px solid var(--color-accent);
}

.nav-icon {
  font-size: 1.1rem;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.nav-badge {
  font-size: 0.65rem;
  background: var(--color-bg-tertiary);
  color: var(--color-text-muted);
  padding: 1px 6px;
  border-radius: 8px;
}

.sidebar-footer {
  border-top: 1px solid var(--color-border);
  padding: var(--space-sm) 0;
}

.logout-btn {
  color: var(--color-text-muted);
}

.logout-btn:hover {
  color: var(--color-danger);
}

.main-content {
  flex: 1;
  margin-left: var(--sidebar-width);
  transition: margin-left var(--transition-normal);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.sidebar.collapsed ~ .main-content,
.sidebar.collapsed + .main-content {
  margin-left: var(--sidebar-collapsed-width);
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border);
  min-height: 56px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.current-page {
  font-size: 1.1rem;
  font-weight: 600;
}

.mobile-menu-btn {
  display: none;
  background: none;
  border: none;
  color: var(--color-text-primary);
  font-size: 1.25rem;
}

.user-info {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.data-status-banner {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-lg);
  background: rgba(234, 179, 8, 0.12);
  border-bottom: 1px solid var(--color-warning, #eab308);
  color: var(--color-warning, #eab308);
  font-size: 0.85rem;
}

.dsb-icon {
  flex-shrink: 0;
}

.dsb-text {
  flex: 1;
}

.dsb-dismiss {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0 var(--space-xs);
}

.content-area {
  flex: 1;
  padding: var(--space-lg);
  overflow-y: auto;
}

.mobile-overlay {
  display: none;
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
    width: var(--sidebar-width);
  }

  .sidebar.collapsed {
    width: var(--sidebar-width);
  }

  .layout:has(.mobile-overlay) .sidebar {
    transform: translateX(0);
  }

  .main-content {
    margin-left: 0 !important;
  }

  .mobile-menu-btn {
    display: block;
  }

  .toggle-btn {
    display: none;
  }

  .mobile-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 99;
  }

  .content-area {
    padding: var(--space-md);
  }
}
</style>
