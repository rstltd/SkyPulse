<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-logo">SkyPulse</h1>
        <p class="login-subtitle">GNSS Slope Monitoring Platform</p>
      </div>
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">Username</label>
          <input id="username" v-model="username" type="text" autocomplete="username"
            placeholder="Enter username" required />
        </div>
        <div class="form-group">
          <label for="password">Password</label>
          <input id="password" v-model="password" type="password" autocomplete="current-password"
            placeholder="Enter password" required />
        </div>
        <div class="error-message" v-if="error">{{ error }}</div>
        <button type="submit" class="btn btn-primary login-btn" :disabled="submitting">
          {{ submitting ? 'Logging in...' : 'Login' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const route = useRoute()
const { login } = useAuth()

const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

const handleLogin = async () => {
  error.value = ''
  submitting.value = true
  const err = await login(username.value, password.value)
  submitting.value = false
  if (err) {
    error.value = err
    return
  }
  const redirect = (route.query.redirect as string) || '/dashboard'
  router.push(redirect)
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-primary);
  padding: var(--space-md);
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-2xl) var(--space-xl);
}

.login-header {
  text-align: center;
  margin-bottom: var(--space-xl);
}

.login-logo {
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-accent);
}

.login-subtitle {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  margin-top: var(--space-xs);
}

.form-group {
  margin-bottom: var(--space-lg);
}

.form-group label {
  display: block;
  margin-bottom: var(--space-xs);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.form-group input {
  width: 100%;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  font-size: 1rem;
}

.form-group input:focus {
  outline: none;
  border-color: var(--color-accent);
}

.error-message {
  color: var(--color-danger);
  font-size: 0.875rem;
  margin-bottom: var(--space-md);
  text-align: center;
}

.login-btn {
  width: 100%;
  padding: var(--space-md);
  font-size: 1rem;
  justify-content: center;
}
</style>
