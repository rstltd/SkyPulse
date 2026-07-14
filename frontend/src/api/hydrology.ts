import api from './client'
import type { ApiResponse, WaterLevelStation } from '@/types'

export const getLatestWaterLevels = () =>
  api.get<ApiResponse<any[]>>('/hydrology/water-level/latest')

export const getWaterLevelStations = () =>
  api.get<ApiResponse<WaterLevelStation[]>>('/hydrology/water-level/stations')

export const getWaterLevelByStation = (code: string, hours: number = 24) =>
  api.get<ApiResponse<any[]>>(`/hydrology/water-level/station/${code}`, { params: { hours } })

export const getReservoirs = () =>
  api.get<ApiResponse<any[]>>('/hydrology/reservoirs')
