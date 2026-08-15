import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { updateProjectLocation } from '../../services/project';
import { getApiErrorMessage } from '../../services/api';
import { deleteFile, previewFile, uploadFile } from '../../services/file';
import { loadBaiduMap } from '../../utils/loadBaiduMap';
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
} from './projectLocation';

const DEFAULT_POINT = { longitude: 121.4737, latitude: 31.2304 };

const ProjectLocationEditor = forwardRef(function ProjectLocationEditor({
  projectId,
  profile,
  location,
  editing = false,
  onSaved,
  onStateChange,
  onReload,
}, ref) {
  const initialLocation = normalizeProjectLocation(profile, location);
  const [form, setForm] = useState(initialLocation);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('');
  const [mapState, setMapState] = useState('idle');
  const [pointConfirmed, setPointConfirmed] = useState(hasProjectCoordinates(initialLocation));
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [routeImageDraft, setRouteImageDraft] = useState(() => normalizeProjectRouteImage(location));
  const [routeImageAction, setRouteImageAction] = useState('KEEP');
  const [routeImageUrl, setRouteImageUrl] = useState('');
  const [routeImageError, setRouteImageError] = useState('');
  const [routeImageUploading, setRouteImageUploading] = useState(false);
  const [dirty, setDirty] = useState(false);
  const mapContainerRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const BMapGLRef = useRef(null);
  const formRef = useRef(form);
  const mapClickHandlerRef = useRef(null);
  const editorSessionRef = useRef(0);
  const selectMapPointRef = useRef(null);
  const pendingRouteImageIdRef = useRef(null);
  const routeImageObjectUrlRef = useRef('');
  const routePreviewRequestRef = useRef(0);
  const routeUploadRequestRef = useRef(0);
  const coordinatePreviewRequestRef = useRef(0);

  const revokeRouteImageUrl = useCallback(() => {
    routePreviewRequestRef.current += 1;
    if (routeImageObjectUrlRef.current) URL.revokeObjectURL(routeImageObjectUrlRef.current);
    routeImageObjectUrlRef.current = '';
    setRouteImageUrl('');
  }, []);

  const loadRouteImagePreview = useCallback(async (image) => {
    const requestId = routePreviewRequestRef.current + 1;
    routePreviewRequestRef.current = requestId;
    if (routeImageObjectUrlRef.current) URL.revokeObjectURL(routeImageObjectUrlRef.current);
    routeImageObjectUrlRef.current = '';
    setRouteImageUrl('');
    if (!image?.fileId) return;
    try {
      const blob = await previewFile(image.fileId);
      if (requestId !== routePreviewRequestRef.current) return;
      const url = URL.createObjectURL(blob);
      routeImageObjectUrlRef.current = url;
      setRouteImageUrl(url);
      setRouteImageError('');
    } catch (error) {
      if (requestId === routePreviewRequestRef.current) {
        setRouteImageError(getApiErrorMessage(error, '到访路线图预览失败'));
      }
    }
  }, []);

  const cleanupPendingRouteImage = useCallback(async () => {
    const pendingId = pendingRouteImageIdRef.current;
    pendingRouteImageIdRef.current = null;
    if (pendingId) await deleteFile(pendingId).catch(() => {});
  }, []);

  const resetEditor = useCallback(async () => {
    editorSessionRef.current += 1;
    coordinatePreviewRequestRef.current += 1;
    routeUploadRequestRef.current += 1;
    await cleanupPendingRouteImage();
    const currentLocation = normalizeProjectLocation(profile, location);
    const currentRouteImage = normalizeProjectRouteImage(location);
    setForm(currentLocation);
    setRouteImageDraft(currentRouteImage);
    setRouteImageAction('KEEP');
    setRouteImageError('');
    setErrors({});
    setMessage('');
    setMessageType('');
    setConflict(false);
    setPointConfirmed(hasProjectCoordinates(currentLocation));
    setDirty(false);
    void loadRouteImagePreview(currentRouteImage);
  }, [cleanupPendingRouteImage, loadRouteImagePreview, location, profile]);

  useImperativeHandle(ref, () => ({
    discardChanges: async () => {
      if (saving || routeImageUploading) return false;
      await resetEditor();
      return true;
    },
  }), [resetEditor, routeImageUploading, saving]);

  useEffect(() => {
    formRef.current = form;
  }, [form]);

  const previousEditingRef = useRef(editing);

  useEffect(() => {
    if (!editing) {
      void resetEditor();
    } else if (!previousEditingRef.current) {
      setErrors({});
      setMessage('');
      setMessageType('');
      setConflict(false);
      setDirty(false);
    }
    previousEditingRef.current = editing;
  }, [editing, resetEditor]);

  const previousProjectIdRef = useRef(projectId);

  useEffect(() => {
    if (previousProjectIdRef.current !== projectId) {
      editorSessionRef.current += 1;
      routeUploadRequestRef.current += 1;
      void resetEditor();
      setConflict(false);
      previousProjectIdRef.current = projectId;
    }
  }, [projectId, resetEditor]);

  useEffect(() => () => {
    routePreviewRequestRef.current += 1;
    routeUploadRequestRef.current += 1;
    if (routeImageObjectUrlRef.current) URL.revokeObjectURL(routeImageObjectUrlRef.current);
    const pendingId = pendingRouteImageIdRef.current;
    pendingRouteImageIdRef.current = null;
    if (pendingId) deleteFile(pendingId).catch(() => {});
  }, []);

  useEffect(() => {
    onStateChange?.({ dirty, busy: saving || routeImageUploading });
  }, [dirty, onStateChange, routeImageUploading, saving]);

  useEffect(() => () => onStateChange?.({ dirty: false, busy: false }), [onStateChange]);

  const setStatus = useCallback((text, type = '') => {
    setMessage(text);
    setMessageType(type);
  }, []);

  const placeMarker = useCallback((point) => {
    const geographicPoint = normalizeGeographicPoint(point);
    if (!geographicPoint) return;
    const map = mapRef.current;
    const BMapGL = BMapGLRef.current;
    if (!map || !BMapGL) return;
    const markerPoint = new BMapGL.Point(geographicPoint.lng, geographicPoint.lat);
    if (!markerRef.current) {
      const marker = new BMapGL.Marker(markerPoint);
      marker.enableDragging?.();
      marker.addEventListener('dragend', (event) => {
        const dragPoint = extractBaiduEventLngLat(event, marker.getPosition?.());
        selectMapPointRef.current?.(dragPoint, '已拖动并确认新的导航点', true);
      });
      map.addOverlay(marker);
      markerRef.current = marker;
    } else {
      markerRef.current.setPosition(markerPoint);
    }
  }, []);

  const selectMapPoint = useCallback((point, statusText = '已在地图上确认导航点', confirmed = true) => {
    const geographicPoint = normalizeGeographicPoint(point);
    if (!geographicPoint) {
      setPointConfirmed(false);
      setErrors((current) => ({ ...current, coordinates: '地图未返回有效经纬度，请重新选点' }));
      setStatus('地图返回了非经纬度坐标，已阻止写入，请重新选点', 'error');
      return;
    }
    editorSessionRef.current += 1;
    coordinatePreviewRequestRef.current += 1;
    const longitude = geographicPoint.lng.toFixed(6);
    const latitude = geographicPoint.lat.toFixed(6);
    setForm((current) => ({ ...current, longitude, latitude, coordinateType: 'BD09' }));
    setPointConfirmed(confirmed);
    setErrors((current) => ({
      ...current,
      coordinates: confirmed ? undefined : '请在地图上点击、拖动标记，或明确确认当前导航点',
      coordinateType: undefined,
    }));
    setConflict(false);
    setDirty(true);
    placeMarker(geographicPoint);
    setStatus(statusText, confirmed ? 'success' : 'warning');
  }, [placeMarker, setStatus]);

  useEffect(() => {
    selectMapPointRef.current = selectMapPoint;
  }, [selectMapPoint]);

  const previewCoordinateOnMap = useCallback((value, options = {}) => new Promise((resolve, reject) => {
    const requestId = coordinatePreviewRequestRef.current + 1;
    coordinatePreviewRequestRef.current = requestId;
    const BMapGL = BMapGLRef.current;
    const map = mapRef.current;
    if (!BMapGL || !map) {
      reject(new Error('百度地图不可用或尚未加载，请检查浏览器端 AK 配置'));
      return;
    }
    const validation = validateProjectCoordinates(value);
    if (Object.keys(validation).length) {
      reject(new Error(validation.coordinates || validation.coordinateType));
      return;
    }
    const point = new BMapGL.Point(Number(value.longitude), Number(value.latitude));
    const finish = (baiduPoint) => {
      if (requestId !== coordinatePreviewRequestRef.current) {
        resolve(null);
        return;
      }
      placeMarker(baiduPoint);
      map.centerAndZoom(baiduPoint, 17);
      setPointConfirmed(Boolean(options.confirmed));
      setErrors((current) => ({
        ...current,
        coordinates: options.confirmed ? undefined : '请核对地图标记，或明确确认输入的经纬度',
        coordinateType: undefined,
      }));
      setStatus(options.message || '经纬度已预览，请核对地图标记后确认', options.confirmed ? 'success' : 'warning');
      resolve(baiduPoint);
    };
    const coordinateType = String(value.coordinateType || 'BD09').toUpperCase();
    if (coordinateType === 'BD09') {
      finish(point);
      return;
    }
    if (!BMapGL.Convertor) {
      reject(new Error('当前地图版本无法转换该坐标来源，请检查百度地图配置'));
      return;
    }
    const convertor = new BMapGL.Convertor();
    convertor.translate([point], coordinateType === 'WGS84' ? 1 : 3, 5, (result) => {
      const convertedPoint = Number(result?.status) === 0 ? result?.points?.[0] : null;
      if (!convertedPoint) {
        reject(new Error('坐标转换失败，请检查坐标来源和数值'));
        return;
      }
      finish(convertedPoint);
    });
  }), [placeMarker, setStatus]);

  const resolveAddress = useCallback((automatic = false) => {
    editorSessionRef.current += 1;
    coordinatePreviewRequestRef.current += 1;
    const session = editorSessionRef.current;
    const current = formRef.current;
    const query = buildGeocoderAddress(current);
    if (!current.address.trim()) {
      setErrors((value) => ({ ...value, address: '请填写地标及到访说明' }));
      setStatus('请先填写地标及到访说明', 'error');
      return;
    }
    if (mapState !== 'ready' || !BMapGLRef.current?.Geocoder) {
      setStatus(mapState === 'error' ? '百度地图不可用，请检查浏览器端 AK 配置' : '地图正在加载，请稍后再试', 'error');
      return;
    }

    setStatus(automatic ? '正在根据已有位置说明尝试预定位…' : '正在根据位置说明尝试定位…');
    setPointConfirmed(false);
    const geocoder = new BMapGLRef.current.Geocoder();
    geocoder.getPoint(query, (point) => {
      if (session !== editorSessionRef.current) return;
      if (!point) {
        setStatus('位置说明无法检索，可直接输入经纬度，或在地图上选点', 'error');
        return;
      }
      mapRef.current?.centerAndZoom(point, 17);
      selectMapPoint(point, '位置说明已预定位，请在地图上点击、拖动标记或点击确认按钮', false);
    }, current.city || current.province || undefined);
  }, [mapState, selectMapPoint, setStatus]);

  useEffect(() => {
    if (!editing) return undefined;
    let cancelled = false;
    setMapState('loading');
    setStatus('正在加载百度地图…');
    loadBaiduMap()
      .then((BMapGL) => {
        if (cancelled || !mapContainerRef.current) return;
        BMapGLRef.current = BMapGL;
        const map = new BMapGL.Map(mapContainerRef.current, { enableMapClick: true });
        map.enableScrollWheelZoom(true);
        mapRef.current = map;
        const current = formRef.current;
        const point = hasProjectCoordinates(current)
          ? new BMapGL.Point(Number(current.longitude), Number(current.latitude))
          : new BMapGL.Point(DEFAULT_POINT.longitude, DEFAULT_POINT.latitude);
        map.centerAndZoom(point, hasProjectCoordinates(current) ? 17 : 11);
        mapClickHandlerRef.current = (event) => {
          const clickPoint = extractBaiduEventLngLat(event);
          selectMapPointRef.current?.(clickPoint, '已在地图上选择并确认新的导航点', true);
        };
        map.addEventListener('click', mapClickHandlerRef.current);
        setMapState('ready');
        if (hasProjectCoordinates(current)) {
          void previewCoordinateOnMap(current, {
            confirmed: true,
            message: '当前导航点已加载，可输入坐标、点击地图或拖动标记调整',
          }).catch((error) => {
            if (!cancelled) setStatus(getApiErrorMessage(error, '现有坐标预览失败，请重新输入并确认'), 'error');
          });
        } else if (current.address) {
          const session = editorSessionRef.current;
          setTimeout(() => {
            if (!cancelled && session === editorSessionRef.current) {
              const query = buildGeocoderAddress(formRef.current);
              const geocoder = new BMapGL.Geocoder();
              setStatus('正在根据已有位置说明尝试预定位…');
              geocoder.getPoint(query, (resolvedPoint) => {
                if (cancelled || session !== editorSessionRef.current) return;
                if (!resolvedPoint) {
                  setStatus('已有位置说明无法检索，可直接输入经纬度，或在地图上选点', 'error');
                  return;
                }
                map.centerAndZoom(resolvedPoint, 17);
                selectMapPointRef.current?.(resolvedPoint, '位置说明已预定位，请在地图上点击、拖动标记或点击确认按钮', false);
              }, formRef.current.city || formRef.current.province || undefined);
            }
          }, 0);
        }
      })
      .catch((error) => {
        if (cancelled) return;
        setMapState('error');
        setStatus(getApiErrorMessage(error, '百度地图加载失败，请检查浏览器端 AK 和域名白名单'), 'error');
      });

    return () => {
      cancelled = true;
      if (mapRef.current && mapClickHandlerRef.current) {
        mapRef.current.removeEventListener?.('click', mapClickHandlerRef.current);
      }
      mapClickHandlerRef.current = null;
      markerRef.current = null;
      mapRef.current = null;
      BMapGLRef.current = null;
      coordinatePreviewRequestRef.current += 1;
      setMapState('idle');
    };
  }, [editing, previewCoordinateOnMap, setStatus]);

  const updateField = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
    setConflict(false);
    setDirty(true);
    if (['province', 'city', 'district', 'address', 'longitude', 'latitude', 'coordinateType'].includes(key)) {
      editorSessionRef.current += 1;
      coordinatePreviewRequestRef.current += 1;
      setPointConfirmed(false);
      setErrors((current) => ({ ...current, coordinates: '定位信息已修改，请预览或明确确认输入坐标' }));
    }
  };

  const previewManualCoordinates = async () => {
    const validation = validateProjectCoordinates(form);
    setErrors((current) => ({
      ...current,
      coordinates: validation.coordinates,
      coordinateType: validation.coordinateType,
    }));
    if (Object.keys(validation).length) {
      setStatus(validation.coordinates || validation.coordinateType, 'error');
      return;
    }
    editorSessionRef.current += 1;
    setPointConfirmed(false);
    try {
      await previewCoordinateOnMap(form, { confirmed: false, message: '经纬度已预览，请核对标记后确认' });
    } catch (error) {
      setStatus(getApiErrorMessage(error, '经纬度预览失败'), 'error');
    }
  };

  const confirmManualCoordinates = () => {
    const validation = validateProjectCoordinates(form);
    setErrors((current) => ({
      ...current,
      coordinates: validation.coordinates,
      coordinateType: validation.coordinateType,
    }));
    if (Object.keys(validation).length) {
      setStatus(validation.coordinates || validation.coordinateType, 'error');
      return;
    }
    editorSessionRef.current += 1;
    coordinatePreviewRequestRef.current += 1;
    setPointConfirmed(true);
    setErrors((current) => ({ ...current, coordinates: undefined, coordinateType: undefined }));
    setStatus(
      mapState === 'error'
        ? '已明确确认手工坐标；当前未加载地图，系统将按输入坐标保存'
        : '已明确确认输入的经纬度和坐标来源',
      mapState === 'error' ? 'warning' : 'success',
    );
  };

  const uploadRouteImage = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    const validationMessage = validateProjectRouteImageFile(file);
    if (validationMessage) {
      setRouteImageError(validationMessage);
      return;
    }
    setRouteImageUploading(true);
    setRouteImageError('');
    const requestId = routeUploadRequestRef.current + 1;
    routeUploadRequestRef.current = requestId;
    try {
      const result = await uploadFile({
        file,
        projectId,
        fileName: file.name,
        fileType: '到访路线图',
        businessType: 'PROJECT_ROUTE_IMAGE_PENDING',
      });
      if (Number(result?.code) !== 200 || !result?.data?.id) throw new Error(result?.message || '路线图上传失败');
      if (requestId !== routeUploadRequestRef.current) {
        await deleteFile(result.data.id).catch(() => {});
        return;
      }
      const previousPendingId = pendingRouteImageIdRef.current;
      pendingRouteImageIdRef.current = result.data.id;
      if (previousPendingId) await deleteFile(previousPendingId).catch(() => {});
      revokeRouteImageUrl();
      const localUrl = URL.createObjectURL(file);
      routeImageObjectUrlRef.current = localUrl;
      setRouteImageUrl(localUrl);
      setRouteImageDraft({
        fileId: result.data.id,
        fileName: result.data.fileName || file.name,
        fileSize: file.size,
        mimeType: file.type,
        pending: true,
      });
      setRouteImageAction('REPLACE');
      setDirty(true);
      setStatus('到访路线图已暂存，保存项目位置后正式生效', 'success');
    } catch (error) {
      setRouteImageError(getApiErrorMessage(error, '路线图上传失败'));
    } finally {
      setRouteImageUploading(false);
    }
  };

  const removeRouteImage = async () => {
    await cleanupPendingRouteImage();
    revokeRouteImageUrl();
    setRouteImageDraft(null);
    setRouteImageAction('REMOVE');
    setDirty(true);
    setRouteImageError('');
    setStatus('保存后将移除当前到访路线图', 'warning');
  };

  const save = async () => {
    const validation = validateProjectLocation(form, pointConfirmed);
    setErrors(validation);
    if (Object.keys(validation).length) {
      setStatus('请先修正定位信息再保存', 'error');
      return;
    }
    if (routeImageAction === 'REPLACE' && !pendingRouteImageIdRef.current) {
      setRouteImageError('路线图暂存文件已失效，请重新上传');
      setStatus('请重新上传到访路线图', 'error');
      return;
    }

    setSaving(true);
    setConflict(false);
    setStatus('正在保存项目地址与导航点…');
    try {
      const expectedVersion = Math.max(
        Number(location?.profileVersion) || 0,
        Number(profile?.profileVersion) || 0,
      );
      const result = await updateProjectLocation(projectId, projectLocationPayload(form, expectedVersion, {
        action: routeImageAction,
        fileId: pendingRouteImageIdRef.current,
      }));
      if (Number(result?.code) !== 200 || !result?.data) {
        const responseError = new Error(result?.message || '项目定位保存失败');
        responseError.businessCode = Number(result?.code);
        throw responseError;
      }
      pendingRouteImageIdRef.current = null;
      setRouteImageAction('KEEP');
      const savedLocation = result.data;
      const savedRouteImage = normalizeProjectRouteImage(savedLocation);
      setForm(normalizeProjectLocation(profile, savedLocation));
      setRouteImageDraft(savedRouteImage);
      setRouteImageError('');
      setPointConfirmed(hasProjectCoordinates(savedLocation));
      setDirty(false);
      void loadRouteImagePreview(savedRouteImage);
      setStatus('项目地址与导航点已保存', 'success');
      await onSaved?.(savedLocation);
    } catch (error) {
      const isConflict = error?.response?.status === 409
        || Number(error?.response?.data?.code) === 409
        || Number(error?.businessCode) === 409;
      setConflict(isConflict);
      setStatus(
        isConflict ? '项目信息已被其他管理员更新，请重新加载后再定位' : getApiErrorMessage(error, '项目定位保存失败'),
        'error',
      );
    } finally {
      setSaving(false);
    }
  };

  const displayLocation = normalizeProjectLocation(profile, location);
  const hasLocation = hasProjectCoordinates(displayLocation);

  return (
    <section className="project-profile-card project-location-card">
      <div className="project-profile-section-title">
        <div>
          <span className="project-profile-section-index">02</span>
          <h2>项目位置与导航点</h2>
          <p>可使用地标说明或直接输入经纬度，并配置独立到访路线图</p>
        </div>
      </div>

      {!editing ? (
        <div className="project-location-summary">
          <div>
            <span>地标及到访说明</span>
            <strong>{displayLocation.address || '未填写'}</strong>
          </div>
          <div>
            <span>导航状态</span>
            <strong className={hasLocation ? 'ready' : 'missing'}>{hasLocation ? '已配置导航点' : '尚未配置导航坐标'}</strong>
          </div>
          {hasLocation && <div>
            <span>坐标</span>
            <strong>{Number(displayLocation.longitude).toFixed(6)}, {Number(displayLocation.latitude).toFixed(6)} · {displayLocation.coordinateType}</strong>
          </div>}
          <div className="project-route-image-summary">
            <span>到访路线图</span>
            {routeImageDraft ? <>
              {routeImageUrl ? <a href={routeImageUrl} target="_blank" rel="noopener noreferrer"><img src={routeImageUrl} alt="到访路线图" /></a> : <strong>{routeImageError || '正在加载路线图…'}</strong>}
              <strong>{routeImageDraft.fileName}</strong>
            </> : <strong className="missing">未配置</strong>}
          </div>
          {profile?.canEdit && <p>位置说明不要求是可检索门牌地址；点击页面顶部“编辑项目信息”可同步修改说明、坐标和路线图。</p>}
        </div>
      ) : (
        <div className="project-location-editor">
          <div className="project-location-form">
            <div className="project-location-region-grid">
              <label>省/直辖市<input value={form.province} onChange={(event) => updateField('province', event.target.value)} placeholder="例如：上海市" /></label>
              <label>城市<input value={form.city} onChange={(event) => updateField('city', event.target.value)} placeholder="例如：上海市" /></label>
              <label>区/县<input value={form.district} onChange={(event) => updateField('district', event.target.value)} placeholder="例如：浦东新区" /></label>
            </div>
            <label className="project-location-address">地标及到访说明 <em>*</em>
              <textarea value={form.address} onChange={(event) => updateField('address', event.target.value)}
                placeholder="例如：XX桥北侧工地东门，沿蓝色围挡进入；无需填写可检索门牌地址" />
              {errors.address && <span className="project-profile-field-error">{errors.address}</span>}
            </label>
            <div className="project-location-coordinate-row">
              <label><span>经度</span><input type="number" step="any" value={form.longitude}
                onChange={(event) => updateField('longitude', event.target.value)} placeholder="例如：121.473701" /></label>
              <label><span>纬度</span><input type="number" step="any" value={form.latitude}
                onChange={(event) => updateField('latitude', event.target.value)} placeholder="例如：31.230416" /></label>
              <label><span>坐标来源</span><select value={form.coordinateType}
                onChange={(event) => updateField('coordinateType', event.target.value)}>
                <option value="BD09">BD09（百度坐标）</option>
                <option value="GCJ02">GCJ02（高德/腾讯坐标）</option>
                <option value="WGS84">WGS84（GPS 坐标）</option>
              </select></label>
            </div>
            {errors.coordinateType && <span className="project-profile-field-error">{errors.coordinateType}</span>}
            {errors.coordinates && <span className="project-profile-field-error">{errors.coordinates}</span>}
            <div className="project-location-coordinate-actions">
              <button type="button" className="project-location-resolve-button" onClick={() => resolveAddress(false)} disabled={mapState !== 'ready' || saving}>
                尝试按位置说明定位
              </button>
              <button type="button" className="project-location-preview-button" onClick={previewManualCoordinates} disabled={mapState !== 'ready' || saving}>
                按经纬度预览
              </button>
              <button type="button" className="project-location-confirm-button" onClick={confirmManualCoordinates}
                disabled={!hasProjectCoordinates(form) || saving}>确认输入坐标</button>
            </div>
            <p className="project-location-help">地址无法检索时可直接输入经纬度；地图不可用时，仍可严格校验并显式确认手工坐标。</p>
          </div>
          <div className="project-location-map-panel">
            <div ref={mapContainerRef} className="project-location-map" aria-label="项目导航点地图" />
            {mapState === 'loading' && <div className="project-location-map-cover">正在加载百度地图…</div>}
            {mapState === 'error' && <div className="project-location-map-cover error">地图不可用<br /><small>请配置 VITE_BAIDU_MAP_AK；也可在左侧确认并保存手工坐标</small></div>}
          </div>
          <div className="project-route-image-editor">
            <div className="project-route-image-copy">
              <strong>到访路线图（选填）</strong>
              <span>独立于项目效果图，仅支持 JPEG、PNG、WebP，单张不超过 15MB。</span>
            </div>
            <div className="project-route-image-preview">
              {routeImageUrl ? <a href={routeImageUrl} target="_blank" rel="noopener noreferrer"><img src={routeImageUrl} alt="到访路线图预览" /></a>
                : <span>{routeImageDraft ? '路线图预览加载失败' : '暂未配置路线图'}</span>}
            </div>
            <div className="project-route-image-actions">
              <label className={routeImageUploading || saving ? 'disabled' : ''}>{routeImageUploading ? '上传中…' : routeImageDraft ? '替换路线图' : '上传路线图'}
                <input type="file" accept="image/jpeg,image/png,image/webp" disabled={routeImageUploading || saving} onChange={uploadRouteImage} />
              </label>
              {routeImageDraft && <button type="button" onClick={() => void removeRouteImage()} disabled={routeImageUploading || saving}>移除</button>}
            </div>
            {routeImageDraft && <span className="project-route-image-name">{routeImageDraft.fileName}</span>}
            {routeImageError && <span className="project-profile-field-error">{routeImageError}</span>}
          </div>
          <div className={`project-location-message ${messageType}`} role={messageType === 'error' ? 'alert' : 'status'}>
            <span>{message || '请确认位置说明、导航点和路线图'}</span>
            {conflict && <button type="button" onClick={() => { void resetEditor().then(() => onReload?.()); }}>重新加载</button>}
          </div>
          <div className="project-location-actions">
            <button type="button" onClick={() => void resetEditor()} disabled={saving || routeImageUploading || !dirty}>撤销位置修改</button>
            <button type="button" className="primary" onClick={save} disabled={saving || routeImageUploading || !pointConfirmed || !dirty}>{saving ? '保存中…' : '保存项目位置'}</button>
          </div>
        </div>
      )}
    </section>
  );
});

export default ProjectLocationEditor;
