import api from './client'
import type { ApiResponse, Station } from '@/types'

export const getStations = (params?: { type?: string; source?: string }) =>
  api.get<ApiResponse<Station[]>>('/stations', { params })
