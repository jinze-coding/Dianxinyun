const COORDINATE_TYPES = new Set(['BD09', 'GCJ02', 'WGS84']);
const ROUTE_IMAGE_ACTIONS = new Set(['KEEP', 'REPLACE', 'REMOVE']);
const ROUTE_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
export const MAX_ROUTE_IMAGE_SIZE = 15 * 1024 * 1024;

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

export function hasProjectCoordinates(value) {
  const longitude = Number(value?.longitude);
  const latitude = Number(value?.latitude);
  return text(value?.longitude) !== ''
    && text(value?.latitude) !== ''
    && Number.isFinite(longitude)
    && longitude >= -180
    && longitude <= 180
    && Number.isFinite(latitude)
    && latitude >= -90
    && latitude <= 90;
}

export function normalizeGeographicPoint(value) {
  const rawLongitude = value?.lng ?? value?.longitude;
  const rawLatitude = value?.lat ?? value?.latitude;
  if (text(rawLongitude) === '' || text(rawLatitude) === '') return null;
  const longitude = Number(rawLongitude);
  const latitude = Number(rawLatitude);
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180
    || !Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    return null;
  }
  return { lng: longitude, lat: latitude };
}

export function extractBaiduEventLngLat(event, markerPosition) {
  const candidates = [event?.latlng, event?.latLng, markerPosition, event?.point];
  for (const candidate of candidates) {
    const normalized = normalizeGeographicPoint(candidate);
    if (normalized) return normalized;
  }
  return null;
}

export function normalizeProjectLocation(profile, mapDetail) {
  const source = mapDetail || profile || {};
  return {
    province: text(source.province ?? profile?.province),
    city: text(source.city ?? profile?.city),
    district: text(source.district ?? profile?.district),
    address: text(source.address ?? profile?.address),
    longitude: text(source.longitude ?? profile?.longitude),
    latitude: text(source.latitude ?? profile?.latitude),
    coordinateType: COORDINATE_TYPES.has(text(source.coordinateType ?? profile?.coordinateType).toUpperCase())
      ? text(source.coordinateType ?? profile?.coordinateType).toUpperCase()
      : 'BD09',
  };
}

export function buildGeocoderAddress(value) {
  const sections = [value?.province, value?.city, value?.district, value?.address]
    .map(text)
    .filter(Boolean);
  return sections.filter((section, index) => index === 0 || section !== sections[index - 1]).join('');
}

export function validateProjectCoordinates(value) {
  const errors = {};
  const longitudeText = text(value?.longitude);
  const latitudeText = text(value?.latitude);
  if (!longitudeText || !latitudeText) {
    errors.coordinates = '请输入完整的经度和纬度';
  } else {
    const longitude = Number(longitudeText);
    const latitude = Number(latitudeText);
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      errors.coordinates = '经度格式不正确，应在 -180 到 180 之间';
    } else if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      errors.coordinates = '纬度格式不正确，应在 -90 到 90 之间';
    }
  }
  if (!COORDINATE_TYPES.has(text(value?.coordinateType).toUpperCase())) {
    errors.coordinateType = '坐标来源必须是 BD09、GCJ02 或 WGS84';
  }
  return errors;
}

export function validateProjectLocation(value, pointConfirmed = true) {
  const errors = validateProjectCoordinates(value);
  if (!text(value?.address)) errors.address = '请填写地标及到访说明';
  if (!errors.coordinates && !pointConfirmed) {
    errors.coordinates = '请预览后在地图上确认，或明确确认输入的经纬度';
  }
  return errors;
}

export function normalizeProjectRouteImage(location) {
  const source = location?.routeImage || {};
  const fileId = source.fileId ?? source.id ?? location?.routeImageFileId;
  if (fileId === null || fileId === undefined || fileId === '') return null;
  return {
    fileId,
    fileName: source.fileName || location?.routeImageFileName || '到访路线图',
    fileSize: source.fileSize ?? location?.routeImageFileSize,
    mimeType: source.mimeType || location?.routeImageMimeType || '',
    pending: Boolean(source.pending),
  };
}

export function validateProjectRouteImageFile(file) {
  if (!file) return '请选择到访路线图';
  if (!ROUTE_IMAGE_TYPES.has(String(file.type || '').toLowerCase())) {
    return '路线图仅支持 JPEG、PNG、WebP';
  }
  if (!Number.isFinite(Number(file.size)) || Number(file.size) <= 0 || Number(file.size) > MAX_ROUTE_IMAGE_SIZE) {
    return '路线图大小必须在 15MB 以内';
  }
  return '';
}

export function projectLocationPayload(value, expectedVersion, routeImage = {}) {
  const action = text(routeImage.action).toUpperCase() || 'KEEP';
  const payload = {
    province: text(value?.province),
    city: text(value?.city),
    district: text(value?.district),
    address: text(value?.address),
    longitude: Number(value?.longitude).toFixed(6),
    latitude: Number(value?.latitude).toFixed(6),
    coordinateType: text(value?.coordinateType).toUpperCase() || 'BD09',
    expectedVersion,
    routeImageAction: ROUTE_IMAGE_ACTIONS.has(action) ? action : 'KEEP',
  };
  if (payload.routeImageAction === 'REPLACE' && routeImage.fileId !== null && routeImage.fileId !== undefined) {
    payload.routeImageFileId = routeImage.fileId;
  }
  return payload;
}
