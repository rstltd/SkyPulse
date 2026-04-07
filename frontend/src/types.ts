export interface ApiResponse<T> {
  success: boolean
  data: T | null
  message: string | null
}

export interface LoginResponse {
  username: string
  role: string
}

export interface Station {
  stationCode: string
  stationName: string
  source: string
  stationType: string
  latitude: number | null
  longitude: number | null
  altitude: number | null
  county: string | null
  township: string | null
  alertLevel1: number | null
  alertLevel2: number | null
  alertLevel3: number | null
  isActive: boolean
}

export interface MonitorSummary {
  gnssQuality: GnssQuality | null
  collectors: CollectorStatus[]
  recentEarthquakes: EarthquakeEvent[]
  activeAlerts: HazardAlert[]
}

export interface GnssQuality {
  timestamp: string
  qualityLevel: 'NORMAL' | 'CAUTION' | 'DEGRADED' | 'SEVERE'
  kpIndex: number | null
  dstIndex: number | null
  bzComponent: number | null
  solarWindSpeed: number | null
  gScale: number
  rScale: number
  sScale: number
  assessment: string
  recommendation: string
}

export interface CollectorStatus {
  source: string
  status: string
  lastRunTime: string
  persistedCount: number
  fetchedCount: number
  durationMs: number
  errorMessage: string | null
}

export interface EarthquakeEvent {
  time: string
  eventId: string
  magnitude: number
  depthKm: number
  latitude: number
  longitude: number
  locationDesc: string
  source: string
  maxIntensity: string | null
}

export interface HazardAlert {
  id: number
  alertTime: string
  alertType: string
  severity: string
  source: string
  title: string
  description: string
  affectedArea: string
  expiresAt: string | null
}

export interface PagedResponse {
  content: any[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface KpRecord {
  time: string
  kpValue: number
  source: string
}

export interface DstRecord {
  time: string
  dstValue: number
  source: string
}

export interface SolarWindRecord {
  time: string
  windSpeed: number
  density: number
  bz: number
  bt: number
  source: string
}

export interface EffectiveRainfallResponse {
  stationCode: string
  timestamp: string
  effectiveRainfall: number
  currentIntensity: number
  halfLifeHours: number
  windowHours: number
  eventTotalRainfall: number
  eventStartTime: string | null
  eventDurationHours: number
  timeSeries: EffectiveRainfallPoint[]
}

export interface EffectiveRainfallPoint {
  time: string
  intensity: number
  effectiveAccumulated: number
  eventAccumulated: number
}

export interface RainfallObservation {
  time: string
  stationCode: string
  precipitation: number
  source: string
}

export interface WeatherObservation {
  time: string
  stationCode: string
  temperature: number
  humidity: number
  pressure: number
  windSpeed: number
  windDirection: number
  precipitation: number
  source: string
}

export interface WaterLevelObservation {
  time: string
  stationCode: string
  waterLevel: number
  source: string
}

export interface SystemLog {
  time: string
  category: 'COLLECTOR' | 'SYSTEM' | 'BACKFILL' | 'ERROR'
  level: 'INFO' | 'WARN' | 'ERROR'
  source: string
  message: string | null
  fetchedCount: number | null
  validCount: number | null
  persistedCount: number | null
  durationMs: number | null
  errorDetail: string | null
}

export interface LogStats {
  byLevel: Record<string, number>
  bySource: Record<string, number>
  hourly: Array<{ time: string; info: number; warn: number; error: number }>
}

export interface ReservoirStatus {
  time: string
  reservoirId: string
  reservoirName: string
  waterLevel: number
  fullLevel: number
  storagePct: number
  inflow: number
  outflow: number
  dailyRainfall: number
  source: string
}
