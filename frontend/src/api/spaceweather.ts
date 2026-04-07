import api from './client'
import type { ApiResponse, PagedResponse } from '@/types'

export const getKpCurrent = () =>
  api.get<ApiResponse<any>>('/spaceweather/kp/current')

export const getKpHistory = (hours = 72, page = 0, size = 500) =>
  api.get<ApiResponse<PagedResponse>>('/spaceweather/kp/history', { params: { hours, page, size } })

export const getDstCurrent = () =>
  api.get<ApiResponse<any>>('/spaceweather/dst/current')

export const getDstHistory = (hours = 72, page = 0, size = 500) =>
  api.get<ApiResponse<PagedResponse>>('/spaceweather/dst/history', { params: { hours, page, size } })

export const getSolarWindCurrent = () =>
  api.get<ApiResponse<any>>('/spaceweather/solar-wind/current')

export const getSpaceWeatherAlerts = () =>
  api.get<ApiResponse<any[]>>('/spaceweather/alerts')

export const getGnssQuality = () =>
  api.get<ApiResponse<any>>('/spaceweather/gnss-quality')

export const getGnssQualityHistory = (hours = 24) =>
  api.get<ApiResponse<any[]>>('/spaceweather/gnss-quality/history', { params: { hours } })
