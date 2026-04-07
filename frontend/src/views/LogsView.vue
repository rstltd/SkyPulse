<template>
  <div>
    <div class="page-header">
      <h1>System Logs</h1>
      <p class="text-secondary">Enhanced logging and visualization</p>
    </div>

    <div class="coming-soon">
      <div class="coming-soon-icon">&#x2263;</div>
      <h2>Coming Soon</h2>
      <p>The enhanced logging system will be available in the next release.</p>
      <ul>
        <li>Real-time log streaming</li>
        <li>Log level filtering</li>
        <li>Time range queries</li>
        <li>Keyword search</li>
        <li>Log volume statistics charts</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2xl);
  text-align: center;
  min-height: 400px;
}

.coming-soon-icon {
  font-size: 3rem;
  opacity: 0.3;
  margin-bottom: var(--space-lg);
}

.coming-soon h2 {
  font-size: 1.5rem;
  margin-bottom: var(--space-md);
  color: var(--color-text-secondary);
}

.coming-soon p {
  color: var(--color-text-muted);
  margin-bottom: var(--space-lg);
}

.coming-soon ul {
  list-style: none;
  color: var(--color-text-muted);
}

.coming-soon li {
  padding: var(--space-xs) 0;
}

.coming-soon li::before {
  content: '\2022 ';
  color: var(--color-accent);
  margin-right: var(--space-sm);
}
</style>
