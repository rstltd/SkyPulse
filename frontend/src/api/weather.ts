import api from './client'
import type { ApiResponse } from '@/types'

export const getLatestRainfall = () =>
  api.get<ApiResponse<any[]>>('/weather/rainfall/latest')

export const getRainfallByStation = (code: string, hours = 24) =>
  api.get<ApiResponse<any[]>>(`/weather/rainfall/station/${code}`, { params: { hours } })

export const getAccumulatedRainfall = (stationCode: string, hours = 24) =>
  api.get<ApiResponse<any>>('/weather/rainfall/accumulated', { params: { stationCode, hours } })

export const getLatestObservations = () =>
  api.get<ApiResponse<any[]>>('/weather/observations/latest')

export const getForecasts = (location: string) =>
  api.get<ApiResponse<any[]>>(`/weather/forecasts/${location}`)

export const getEffectiveRainfall = (stationCode: string, windowHours = 72, endTime?: string) =>
  api.get<ApiResponse<any>>('/weather/rainfall/effective', {
    params: { stationCode, windowHours, endTime: endTime || undefined },
  })
