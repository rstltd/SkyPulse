import api from './client'
import type { ApiResponse, PagedResponse } from '@/types'

export const getActiveAlerts = () =>
  api.get<ApiResponse<any[]>>('/alerts/active')

export const getAlerts = (params: { type?: string; since?: string; page?: number; size?: number }) =>
  api.get<ApiResponse<PagedResponse>>('/alerts', { params })
