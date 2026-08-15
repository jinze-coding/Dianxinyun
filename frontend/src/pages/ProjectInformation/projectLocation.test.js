import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildGeocoderAddress,
  extractBaiduEventLngLat,
  hasProjectCoordinates,
  normalizeGeographicPoint,
  normalizeProjectLocation,
  normalizeProjectRouteImage,
  projectLocationPayload,
  validateProjectCoordinates,
  validateProjectLocation,
  validateProjectRouteImageFile,
} from './projectLocation.js';

test('uses BMapGL geographic event coordinates instead of Mercator event.point', () => {
  const result = extractBaiduEventLngLat({
    latlng: { lng: 121.473701, lat: 31.230416 },
    point: { lng: 13534988.46, lat: 3635740.98212 },
  });
  assert.deepEqual(result, { lng: 121.473701, lat: 31.230416 });
});

test('uses marker position for drag events when event.point is Mercator', () => {
  const result = extractBaiduEventLngLat(
    { point: { lng: 13534988.46, lat: 3635740.98212 } },
    { lng: 121.474, lat: 31.231 },
  );
  assert.deepEqual(result, { lng: 121.474, lat: 31.231 });
});

test('rejects Baidu Mercator values and keeps geographic event.point fallback compatibility', () => {
  assert.equal(normalizeGeographicPoint({ lng: 13534988.46, lat: 3635740.98212 }), null);
  assert.equal(extractBaiduEventLngLat({ point: { lng: 13534988.46, lat: 3635740.98212 } }), null);
  assert.equal(normalizeGeographicPoint(null), null);
  assert.equal(normalizeGeographicPoint({ lng: '', lat: '' }), null);
  assert.equal(normalizeGeographicPoint({ lng: Number.NaN, lat: 31.2 }), null);
  assert.deepEqual(extractBaiduEventLngLat({ point: { lng: 121.5, lat: 31.2 } }), { lng: 121.5, lat: 31.2 });
});

test('normalizes location from map detail and keeps BD09 as safe default', () => {
  const value = normalizeProjectLocation(
    { address: '旧地址', profileVersion: 3 },
    { province: '上海市', city: '上海市', district: '浦东新区', address: '新地址', longitude: 121.5, latitude: 31.2 },
  );
  assert.deepEqual(value, {
    province: '上海市', city: '上海市', district: '浦东新区', address: '新地址',
    longitude: '121.5', latitude: '31.2', coordinateType: 'BD09',
  });
});

test('detects complete coordinates and rejects partial or out of range points', () => {
  assert.equal(hasProjectCoordinates({ longitude: '121.5', latitude: '31.2' }), true);
  assert.equal(hasProjectCoordinates({ longitude: '', latitude: '31.2' }), false);
  assert.equal(hasProjectCoordinates({ longitude: '181', latitude: '31.2' }), false);
});

test('builds a geocoder query without repeating adjacent region names', () => {
  assert.equal(buildGeocoderAddress({ province: '上海市', city: '上海市', district: '浦东新区', address: '世纪大道1号' }), '上海市浦东新区世纪大道1号');
});

test('requires an address and a confirmed map point', () => {
  const errors = validateProjectLocation({ address: '', longitude: '', latitude: '', coordinateType: 'BD09' });
  assert.ok(errors.address);
  assert.ok(errors.coordinates);
  assert.equal(validateProjectLocation({ address: '世纪大道1号', longitude: 121.5, latitude: 31.2, coordinateType: 'BD09' }).coordinates, undefined);
  assert.ok(validateProjectLocation({ address: '世纪大道1号', longitude: 121.5, latitude: 31.2, coordinateType: 'BD09' }, false).coordinates);
});

test('accepts direct coordinates from all supported source systems', () => {
  for (const coordinateType of ['BD09', 'GCJ02', 'WGS84']) {
    assert.deepEqual(validateProjectCoordinates({ longitude: '121.5', latitude: '31.2', coordinateType }), {});
  }
  assert.ok(validateProjectCoordinates({ longitude: '181', latitude: '31.2', coordinateType: 'BD09' }).coordinates);
  assert.ok(validateProjectCoordinates({ longitude: '121.5', latitude: '31.2', coordinateType: 'UNKNOWN' }).coordinateType);
});

test('location payload sends fixed coordinates and optimistic profile version', () => {
  assert.deepEqual(projectLocationPayload({
    province: ' 上海市 ', city: '上海市', district: '浦东新区', address: ' 世纪大道1号 ',
    longitude: '121.5000004', latitude: '31.2000004', coordinateType: 'bd09',
  }, 7, { action: 'REPLACE', fileId: 19 }), {
    province: '上海市', city: '上海市', district: '浦东新区', address: '世纪大道1号',
    longitude: '121.500000', latitude: '31.200000', coordinateType: 'BD09', expectedVersion: 7,
    routeImageAction: 'REPLACE', routeImageFileId: 19,
  });
});

test('route image contract normalizes internal metadata and validates image safety', () => {
  assert.deepEqual(normalizeProjectRouteImage({ routeImage: { id: 8, fileName: '路线.webp', fileSize: 100, mimeType: 'image/webp' } }), {
    fileId: 8, fileName: '路线.webp', fileSize: 100, mimeType: 'image/webp', pending: false,
  });
  assert.equal(validateProjectRouteImageFile({ type: 'image/png', size: 1024 }), '');
  assert.ok(validateProjectRouteImageFile({ type: 'image/svg+xml', size: 1024 }));
  assert.ok(validateProjectRouteImageFile({ type: 'image/png', size: 16 * 1024 * 1024 }));
  assert.equal(projectLocationPayload({
    address: '地标', longitude: 121.5, latitude: 31.2, coordinateType: 'BD09',
  }, 2).routeImageAction, 'KEEP');
  assert.deepEqual(projectLocationPayload({
    address: '地标', longitude: 121.5, latitude: 31.2, coordinateType: 'GCJ02',
  }, 2, { action: 'REMOVE' }), {
    province: '', city: '', district: '', address: '地标', longitude: '121.500000', latitude: '31.200000',
    coordinateType: 'GCJ02', expectedVersion: 2, routeImageAction: 'REMOVE',
  });
});
