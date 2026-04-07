import api from './client'
import type { ApiResponse, MonitorSummary } from '@/types'

export const getMonitorSummary = () =>
  api.get<ApiResponse<MonitorSummary>>('/monitor/summary')
