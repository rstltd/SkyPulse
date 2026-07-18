export interface ApiResponse<T> {
  success: boolean
  data: T | null
  message: string | null
  errorCode: string | null
}

export interface LoginResponse {
  username: string
  role: string
}

export interface Station {
  stationCode: string
  stationName: string
  source: string
  latitude: number | null
  longitude: number | null
  altitude: number | null
  county: string | null
  township: string | null
  isActive: boolean
}

// Water-level station dimension (thresholds moved off Station in P2a).
export interface WaterLevelStation {
  stationCode: string
  riverName: string | null
  basin: string | null
  alertLevel1: number | null
  alertLevel2: number | null
  alertLevel3: number | null
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

export interface WeatherForecast {
  locationName: string
  forecastTime: string
  issuedTime: string
  weatherDesc: string
  minTemp: number
  maxTemp: number
  rainProb: number
  source: string
}

export interface AccumulatedRainfallResponse {
  stationCode: string
  hours: number
  accumulatedPrecipitation: number
}

export interface RainfallObservation {
  time: string
  stationCode: string
  rain10minMm: number | null
  dailyAccumMm: number | null
  trailing1hrMm: number | null
  trailing3hrMm: number | null
  trailing6hrMm: number | null
  trailing12hrMm: number | null
  trailing24hrMm: number | null
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

export interface SiteInfo {
  siteId: string
  name: string
  latitude: number
  longitude: number
  associatedStationCode: string
}

// Joined reservoir read model (dimension + latest status) — backend ReservoirView.
export interface ReservoirStatus {
  time: string
  reservoirId: string
  reservoirName: string | null
  waterLevelM: number | null
  fullLevelM: number | null
  effectiveStorageM3: number | null
  designCapacityM3: number | null
  storagePct: number | null
  inflowCms: number | null
  outflowCms: number | null
  catchmentRainMm: number | null
  latitude: number | null
  longitude: number | null
  basin: string | null
  county: string | null
}

// --- Coordinate-query context API (Direction B main product) ---
export interface Provenance {
  source: string
  dataset: string | null
  stationCode: string | null
  stationName: string | null
  stationLat: number | null
  stationLon: number | null
  distanceKm: number | null
}

export interface Freshness {
  observedAt: string | null
  ageSeconds: number | null
  stale: boolean | null
  expectedMaxAgeSeconds: number | null
}

export interface Warning {
  domain: string
  code: string
  message: string
  searchedRadiusKm: number | null
}

export interface QueryEcho {
  lat: number
  lon: number
  radiusKm: number | null
  quakeRadiusKm: number
  autoRadius: boolean
  maxAutoRadiusKm: number
}

export interface LocationInfo {
  county: string | null
  township: string | null
  inTaiwan: boolean
}

export interface RainfallContext {
  provenance: Provenance
  freshness: Freshness
  accumulatedMm: { h3: number; h6: number; h12: number; h24: number; h48: number; h72: number }
  maxHourlyIntensityMm: number | null
  effectiveRainfallMm: number | null
  rti: number | null
  alertBaseline: {
    township: string
    thresholdMm: number | null
    source: string
    dataset: string
    effectiveFrom: string | null
  } | null
  signal: 'GREEN' | 'YELLOW' | 'RED' | null
  signalBasis: string | null
}

export interface WaterLevelContext {
  provenance: Provenance
  freshness: Freshness
  waterLevelM: number | null
  alertLevels: { level1: number | null; level2: number | null; level3: number | null } | null
  alertStatus: 'NORMAL' | 'LEVEL1' | 'LEVEL2' | 'LEVEL3' | null
}

export interface SeismicContext {
  strongestNearby: {
    eventId: string
    time: string
    magnitude: number
    depthKm: number | null
    epicenterLat: number
    epicenterLon: number
    distanceKm: number | null
    maxIntensity: string | null
    locationDesc: string | null
    source: string
  } | null
  nearbyCount: number
  window: string
  provenance: Provenance
  freshness: Freshness
}

export interface GnssQualityContext {
  qualityLevel: 'NORMAL' | 'CAUTION' | 'DEGRADED' | 'SEVERE'
  kpIndex: number | null
  dstIndex: number | null
  bzComponent: number | null
  solarWindSpeed: number | null
  gScale: number | null
  rScale: number | null
  sScale: number | null
  assessment: string
  recommendation: string
  global: boolean
  provenance: Array<{ index: string; source: string; observedAt: string | null }>
  freshness: Freshness
}

export interface ContextResponse {
  query: QueryEcho
  location: LocationInfo
  rainfall: RainfallContext | null
  seismic: SeismicContext | null
  gnssQuality: GnssQualityContext
  waterLevel: WaterLevelContext | null
  warnings: Warning[]
  meta: { generatedAt: string; contractVersion: string; partial: boolean }
}

export interface CoverageResponse {
  query: QueryEcho
  location: LocationInfo
  rainfall: Provenance | null
  waterLevel: Provenance | null
  warnings: Warning[]
}
