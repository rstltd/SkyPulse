import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './plugins/echarts'
import './assets/css/variables.css'
import './assets/css/base.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
