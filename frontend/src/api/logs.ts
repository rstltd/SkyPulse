import api from './client'
import type { ApiResponse, PagedResponse, LogStats } from '@/types'

export interface LogQueryParams {
  category?: string
  level?: string
  source?: string
  keyword?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export const getLogs = (params: LogQueryParams) =>
  api.get<ApiResponse<PagedResponse>>('/system/logs', { params })

export const getLogStats = (params: { start?: string; end?: string }) =>
  api.get<ApiResponse<LogStats>>('/system/logs/stats', { params })

export const getLogSources = () =>
  api.get<ApiResponse<string[]>>('/system/logs/sources')
