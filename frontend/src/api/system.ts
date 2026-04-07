import api from './client'
import type { ApiResponse } from '@/types'

export const getCollectors = () =>
  api.get<ApiResponse<any[]>>('/system/collectors')

export const triggerBackfill = (source: string, startDate: string, endDate: string) =>
  api.post<ApiResponse<any>>(`/backfill/${source}`, null, {
    params: { startDate, endDate },
  })
