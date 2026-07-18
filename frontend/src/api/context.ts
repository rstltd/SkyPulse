import api from './client'
import type { ApiResponse, ContextResponse, CoverageResponse } from '@/types'

export interface ContextParams {
  radiusKm?: number
  rainRadiusKm?: number
  waterRadiusKm?: number
  quakeRadiusKm?: number
  include?: string
}

/** Integrated environmental context for a single coordinate (Direction B main product). */
export const getContext = (lat: number, lon: number, opts: ContextParams = {}) =>
  api.get<ApiResponse<ContextResponse>>('/context', { params: { lat, lon, ...opts } })

/** Which station each coordinate-dependent domain would use, no indicators. */
export const getCoverage = (lat: number, lon: number, opts: ContextParams = {}) =>
  api.get<ApiResponse<CoverageResponse>>('/context/coverage', { params: { lat, lon, ...opts } })
