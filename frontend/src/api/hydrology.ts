import api from './client'
import type { ApiResponse } from '@/types'

export const getLatestWaterLevels = () =>
  api.get<ApiResponse<any[]>>('/hydrology/water-level/latest')

export const getWaterLevelByStation = (code: string) =>
  api.get<ApiResponse<any[]>>(`/hydrology/water-level/station/${code}`)

export const getReservoirs = () =>
  api.get<ApiResponse<any[]>>('/hydrology/reservoirs')
