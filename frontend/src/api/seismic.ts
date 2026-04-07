import api from './client'
import type { ApiResponse, PagedResponse } from '@/types'

export const getLatestEvents = () =>
  api.get<ApiResponse<any[]>>('/seismic/events/latest')

export const getEvents = (since: string, minMag?: number, page = 0, size = 20) =>
  api.get<ApiResponse<PagedResponse>>('/seismic/events', {
    params: { since, minMag, page, size },
  })

export const getNearbyEvents = (lat: number, lon: number, radiusKm = 100) =>
  api.get<ApiResponse<any[]>>('/seismic/events/nearby', {
    params: { lat, lon, radiusKm },
  })
