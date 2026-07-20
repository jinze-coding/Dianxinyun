import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  addProject,
  deleteProject,
  getProjectMapPoints,
  updateProjectLocation,
} from '../../services/project';
import { loadBaiduMap } from '../../utils/loadBaiduMap';

const STATUS_COLORS = {
  normal: '#22c55e',
  warning: '#f59e0b',
  danger: '#ef4444',
  stopped: '#ef4444',
};

const emptyProjectForm = {
  projectName: '',
  shortName: '',
  projectStatus: 'normal',
  phase: '',
  address: '',
  longitude: '',
  latitude: '',
};

function normalizeProjects(list) {
  return list.map((p) => ({
    ...p,
    id: p.id || p.projectId,
    projectName: p.projectName || p.name,
    shortName: p.shortName || p.short,
    projectStatus: p.projectStatus || p.status || 'normal',
    phase: p.phase || p.currentStage,
    longitude: p.longitude,
    latitude: p.latitude,
    province: p.province,
    city: p.city,
    district: p.district,
    address: p.address,
    coordinateType: p.coordinateType || 'BD09',
    hasLocation: p.hasLocation ?? Boolean(p.longitude && p.latitude),
  }));
}

function buildInfoWindowContent(project, onNavigate) {
  const container = document.createElement('div');
  container.style.cssText = 'padding:4px 0;min-width:200px;';

  const nameEl = document.createElement('div');
  nameEl.textContent = project.projectName;
  nameEl.style.cssText = 'font-size:14px;font-weight:600;color:#1e293b;margin-bottom:2px;';
  container.appendChild(nameEl);

  if (project.address) {
    const addrEl = document.createElement('div');
    addrEl.textContent = project.address;
    addrEl.style.cssText = 'font-size:11px;color:#64748b;margin-bottom:10px;';
    container.appendChild(addrEl);
  }

  const btnContainer = document.createElement('div');
  btnContainer.style.cssText = 'display:flex;gap:6px;flex-wrap:wrap;';

  [
    { label: '项目概况', page: 'overview' },
    { label: '人员与安全', page: 'personnel' },
    { label: '设备与监控', page: 'monitor' },
  ].forEach(({ label, page }) => {
    const btn = document.createElement('button');
    btn.textContent = label;
    btn.style.cssText =
      'padding:5px 14px;font-size:12px;border:none;border-radius:4px;background:#1677ff;color:#fff;cursor:pointer;white-space:nowrap;';
    btn.onclick = () => onNavigate(page, project.id);
    btnContainer.appendChild(btn);
  });

  container.appendChild(btnContainer);
  return container;
}

export default function MapDashboard({ theme: T, onNavigate, projectList = [], onRefreshProjects }) {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeProjectId, setActiveProjectId] = useState(null);
  const [editingProject, setEditingProject] = useState(null);
  const [locationForm, setLocationForm] = useState({
    province: '',
    city: '',
    district: '',
    address: '',
    longitude: '',
    latitude: '',
    coordinateType: 'BD09',
  });
  const [locationMessage, setLocationMessage] = useState('');
  const [savingLocation, setSavingLocation] = useState(false);
  const [showProjectManage, setShowProjectManage] = useState(false);
  const [projectSearch, setProjectSearch] = useState('');
  const [manageSearch, setManageSearch] = useState('');
  const [showProjectForm, setShowProjectForm] = useState(false);
  const [projectForm, setProjectForm] = useState(emptyProjectForm);
  const [projectMessage, setProjectMessage] = useState('');
  const [savingProject, setSavingProject] = useState(false);
  const [mapMode, setMapMode] = useState('normal');
  const [mapReady, setMapReady] = useState(false);

  const mapRef = useRef(null);
  const mapContainerRef = useRef(null);
  const markersRef = useRef([]);

  const projectsWithLocation = useMemo(
    () => projects.filter((p) => p.longitude && p.latitude),
    [projects],
  );

  const filteredManageProjects = useMemo(() => {
    const keyword = manageSearch.trim();
    if (!keyword) return projects;
    return projects.filter((project) => (
      project.projectName?.includes(keyword)
      || project.shortName?.includes(keyword)
      || project.address?.includes(keyword)
    ));
  }, [manageSearch, projects]);

  const filteredProjects = useMemo(() => {
    const keyword = projectSearch.trim();
    if (!keyword) return projects;
    return projects.filter((project) => (
      project.projectName?.includes(keyword)
      || project.shortName?.includes(keyword)
      || project.address?.includes(keyword)
    ));
  }, [projectSearch, projects]);

  const showInfoWindow = useCallback(
    (project, map) => {
      if (!map || !project.longitude || !project.latitude || !window.BMapGL) return;
      const point = new window.BMapGL.Point(Number(project.longitude), Number(project.latitude));
      const infoWindow = new window.BMapGL.InfoWindow(buildInfoWindowContent(project, onNavigate), {
        width: 240,
        title: '',
      });
      map.openInfoWindow(infoWindow, point);
    },
    [onNavigate],
  );

  const focusProjectOnMap = useCallback((project, zoom = 15) => {
    if (!project?.longitude || !project?.latitude || !mapRef.current || !window.BMapGL) return;
    const point = new window.BMapGL.Point(Number(project.longitude), Number(project.latitude));
    mapRef.current.centerAndZoom(point, zoom);
    setTimeout(() => showInfoWindow(project, mapRef.current), 300);
  }, [showInfoWindow]);

  const getSatelliteMapType = () => window.BMAP_SATELLITE_MAP || window.BMAP_EARTH_MAP;

  const refreshProjectData = useCallback(async () => {
    const res = await getProjectMapPoints();
    if (res.code === 200 && Array.isArray(res.data)) {
      const nextProjects = normalizeProjects(res.data);
      setProjects(nextProjects);
      setError(null);
      return nextProjects;
    }
    throw new Error(res.message || '获取项目列表失败，请确认后端服务和登录状态正常');
  }, []);

  useEffect(() => {
    let cancelled = false;

    if (projectList.length > 0) {
      setProjects(normalizeProjects(projectList));
      setError(null);
    }

    setLoading(true);
    refreshProjectData()
      .catch((e) => {
        if (!cancelled && projectList.length === 0) {
          setProjects([]);
          setError('获取项目列表失败，请确认后端服务和登录状态正常');
        }
        console.error('获取项目列表失败', e);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [projectList, refreshProjectData]);

  const handleRetry = async () => {
    setError(null);
    setLoading(true);
    try {
      if (typeof onRefreshProjects === 'function') {
        await onRefreshProjects();
      }
      await refreshProjectData();
    } catch (e) {
      setProjects([]);
      setError(e?.message || '获取项目列表失败，请确认后端服务和登录状态正常');
      console.error('获取项目列表失败', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    loadBaiduMap()
      .then((BMapGL) => {
        if (cancelled || !mapContainerRef.current) return;
        const map = new BMapGL.Map(mapContainerRef.current, { enableMapClick: true });
        map.centerAndZoom(new BMapGL.Point(116.404, 39.915), 5);
        map.enableScrollWheelZoom(true);
        if (window.BMAP_NORMAL_MAP) {
          map.setMapType(window.BMAP_NORMAL_MAP);
        }
        mapRef.current = map;
        setMapReady(true);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || '地图加载失败');
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!mapReady || !map || !window.BMapGL) return;

    markersRef.current.forEach((item) => map.removeOverlay(item.marker));
    markersRef.current = [];

    const points = [];
    projects.forEach((project) => {
      if (!project.longitude || !project.latitude) return;

      const point = new window.BMapGL.Point(Number(project.longitude), Number(project.latitude));
      points.push(point);

      const marker = new window.BMapGL.Marker(point);
      marker.addEventListener('click', () => {
        setActiveProjectId(project.id);
        showInfoWindow(project, map);
      });
      map.addOverlay(marker);
      markersRef.current.push({ marker, projectId: project.id });
    });

    if (points.length === 1) {
      map.centerAndZoom(points[0], 16);
    } else if (points.length > 1) {
      try {
        map.setViewport(points, { margins: [80, 80, 80, 80] });
      } catch {
        map.centerAndZoom(points[0], 12);
      }
    }
  }, [projects, showInfoWindow, mapReady]);

  const syncAfterProjectChanged = async () => {
    if (typeof onRefreshProjects === 'function') {
      await onRefreshProjects();
    }
    await refreshProjectData();
  };

  const handleSelectProject = (projectId) => {
    const project = projects.find((p) => p.id === projectId);
    setActiveProjectId(projectId);
    focusProjectOnMap(project);
  };

  const openLocationEditor = (project, event) => {
    event.stopPropagation();
    setEditingProject(project);
    setLocationForm({
      province: project.province || '',
      city: project.city || '',
      district: project.district || '',
      address: project.address || '',
      longitude: project.longitude || '',
      latitude: project.latitude || '',
      coordinateType: project.coordinateType || 'BD09',
    });
    setLocationMessage('');
  };

  const handleResolveAddress = () => {
    const address = locationForm.address.trim();
    if (!address) {
      setLocationMessage('请先输入详细地址');
      return;
    }
    if (!window.BMapGL?.Geocoder) {
      setLocationMessage('百度地图还没有加载完成，请稍后再试');
      return;
    }

    setLocationMessage('正在根据地址解析坐标...');
    const geocoder = new window.BMapGL.Geocoder();
    geocoder.getPoint(address, (point) => {
      if (!point) {
        setLocationMessage('没有解析到坐标，请把地址写得更具体，或手动输入经纬度');
        return;
      }
      setLocationForm((prev) => ({
        ...prev,
        longitude: Number(point.lng).toFixed(6),
        latitude: Number(point.lat).toFixed(6),
      }));
      setLocationMessage('已解析出坐标，确认无误后点击保存');
      if (mapRef.current) mapRef.current.centerAndZoom(point, 15);
    });
  };

  const validateLngLat = (longitudeValue, latitudeValue, requireBoth = true) => {
    const hasLongitude = String(longitudeValue || '').trim() !== '';
    const hasLatitude = String(latitudeValue || '').trim() !== '';
    if (!hasLongitude && !hasLatitude && !requireBoth) return null;
    if (hasLongitude !== hasLatitude) return '经度和纬度需要同时填写';

    const longitude = Number(longitudeValue);
    const latitude = Number(latitudeValue);
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      return '经度格式不正确，应在 -180 到 180 之间';
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      return '纬度格式不正确，应在 -90 到 90 之间';
    }
    return null;
  };

  const handleSaveLocation = async () => {
    if (!editingProject) return;

    if (!locationForm.address.trim()) {
      setLocationMessage('请填写真实地址');
      return;
    }

    const validateMessage = validateLngLat(locationForm.longitude, locationForm.latitude);
    if (validateMessage) {
      setLocationMessage(validateMessage);
      return;
    }

    setSavingLocation(true);
    setLocationMessage('正在保存定位信息...');
    const longitude = Number(locationForm.longitude);
    const latitude = Number(locationForm.latitude);

    try {
      const res = await updateProjectLocation(editingProject.id, {
        longitude: longitude.toFixed(6),
        latitude: latitude.toFixed(6),
        province: locationForm.province.trim(),
        city: locationForm.city.trim(),
        district: locationForm.district.trim(),
        address: locationForm.address.trim(),
        coordinateType: locationForm.coordinateType || 'BD09',
      });
      if (res.code !== 200) {
        setLocationMessage(res.message || '保存失败');
        return;
      }

      const nextProject = normalizeProjects([res.data || {
        ...editingProject,
        longitude: longitude.toFixed(6),
        latitude: latitude.toFixed(6),
        province: locationForm.province.trim(),
        city: locationForm.city.trim(),
        district: locationForm.district.trim(),
        address: locationForm.address.trim(),
        coordinateType: locationForm.coordinateType || 'BD09',
      }])[0];
      setProjects((prev) => prev.map((item) => (item.id === nextProject.id ? nextProject : item)));
      setActiveProjectId(nextProject.id);
      setEditingProject(null);
      setLocationMessage('');
      focusProjectOnMap(nextProject);
      await syncAfterProjectChanged();
    } catch (e) {
      console.error('保存定位信息失败', e);
      setLocationMessage('保存失败，请确认后端服务正常');
    } finally {
      setSavingLocation(false);
    }
  };

  const openAddProjectForm = () => {
    setProjectForm(emptyProjectForm);
    setProjectMessage('');
    setShowProjectForm(true);
  };

  const handleAddProject = async () => {
    const projectName = projectForm.projectName.trim();
    if (!projectName) {
      setProjectMessage('请填写项目名称');
      return;
    }

    const validateMessage = validateLngLat(projectForm.longitude, projectForm.latitude, false);
    if (validateMessage) {
      setProjectMessage(validateMessage);
      return;
    }

    const payload = {
      projectName,
      shortName: projectForm.shortName.trim(),
      projectStatus: projectForm.projectStatus,
      phase: projectForm.phase.trim(),
      address: projectForm.address.trim(),
    };

    if (projectForm.longitude && projectForm.latitude) {
      payload.longitude = Number(projectForm.longitude).toFixed(6);
      payload.latitude = Number(projectForm.latitude).toFixed(6);
    }

    setSavingProject(true);
    setProjectMessage('正在新增项目...');
    try {
      const res = await addProject(payload);
      if (res.code !== 200) {
        setProjectMessage(res.message || '新增失败');
        return;
      }
      setShowProjectForm(false);
      setProjectForm(emptyProjectForm);
      setProjectMessage('');
      await syncAfterProjectChanged();
    } catch (e) {
      console.error('新增项目失败', e);
      const message = e?.response?.data?.message || (e?.request ? '后端服务未连接，请先启动后端' : '新增失败，请稍后重试');
      setProjectMessage(message);
    } finally {
      setSavingProject(false);
    }
  };

  const handleDeleteProject = async (project, event) => {
    event.stopPropagation();
    if (!window.confirm(`确认删除项目“${project.projectName}”？`)) return;

    try {
      const res = await deleteProject(project.id);
      if (res.code !== 200) {
        window.alert(res.message || '删除失败');
        return;
      }
      setProjects((prev) => prev.filter((item) => item.id !== project.id));
      if (activeProjectId === project.id) setActiveProjectId(null);
      await syncAfterProjectChanged();
    } catch (e) {
      console.error('删除项目失败', e);
      window.alert('删除失败，请确认后端服务正常');
    }
  };

  const handleToggleMapMode = () => {
    const map = mapRef.current;
    if (!map || !window.BMapGL) return;

    const nextMode = mapMode === 'satellite' ? 'normal' : 'satellite';
    const targetMapType = nextMode === 'satellite'
      ? getSatelliteMapType()
      : window.BMAP_NORMAL_MAP;

    if (!targetMapType) {
      window.alert('当前百度地图版本暂不支持卫星图层切换');
      return;
    }

    map.setMapType(targetMapType);
    setMapMode(nextMode);
    if (projectsWithLocation.length === 1) {
      const project = projectsWithLocation[0];
      const point = new window.BMapGL.Point(Number(project.longitude), Number(project.latitude));
      map.centerAndZoom(point, 16);
    } else if (projectsWithLocation.length > 1) {
      const points = projectsWithLocation.map(
        (project) => new window.BMapGL.Point(Number(project.longitude), Number(project.latitude)),
      );
      map.setViewport(points, { margins: [80, 80, 80, 80] });
    }
  };

  if (error) {
    return (
      <div style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: T.pageBg,
        flexDirection: 'column',
        gap: 12,
      }}>
        <span style={{ fontSize: 40 }}>!</span>
        <span style={{ fontSize: 15, color: T.danger, fontWeight: 500 }}>{error}</span>
        <button
          onClick={handleRetry}
          style={{
            padding: '8px 20px',
            borderRadius: 6,
            border: 'none',
            background: T.accent,
            color: '#fff',
            cursor: 'pointer',
            fontSize: 13,
          }}
        >
          重试
        </button>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', width: '100%', height: '100%', background: T.pageBg }}>
      <div style={{
        width: 340,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        borderRight: `1px solid ${T.borderColor}`,
        background: T.cardBg,
      }}>
        <div style={{ padding: '16px 16px 12px', borderBottom: `1px solid ${T.borderColor}` }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: T.textPrimary, letterSpacing: 0.5 }}>
              项目列表
            </div>
            <button
              onClick={() => setShowProjectManage((value) => !value)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                border: `1px solid ${T.accent}`,
                background: showProjectManage ? T.accent : T.activeItemBg,
                color: showProjectManage ? '#fff' : T.accent,
                borderRadius: 7,
                padding: '6px 11px',
                fontSize: 12,
                cursor: 'pointer',
                fontWeight: 700,
              }}
            >
              项目管理
            </button>
          </div>
          <div style={{ fontSize: 12, color: T.textMuted, marginTop: 6 }}>
            共 {projects.length} 个项目
            {projectsWithLocation.length < projects.length
              ? `，${projects.length - projectsWithLocation.length} 个缺少坐标`
              : '，坐标已完善'}
          </div>
          <input
            value={projectSearch}
            onChange={(event) => setProjectSearch(event.target.value)}
            placeholder="搜索项目名称、简称或地址..."
            style={{
              width: '100%',
              boxSizing: 'border-box',
              marginTop: 12,
              background: T.surface2,
              border: `1px solid ${T.borderColor}`,
              borderRadius: 7,
              color: T.textPrimary,
              padding: '8px 10px',
              outline: 'none',
              fontSize: 12,
            }}
          />
        </div>

        {showProjectManage && (
          <div style={{ borderBottom: `1px solid ${T.borderColor}`, padding: 12, background: T.surface2 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
              <input
                value={manageSearch}
                onChange={(event) => setManageSearch(event.target.value)}
                placeholder="搜索项目名称、简称或地址..."
                style={{
                  flex: 1,
                  background: T.cardBg,
                  border: `1px solid ${T.borderColor}`,
                  borderRadius: 7,
                  color: T.textPrimary,
                  padding: '8px 10px',
                  outline: 'none',
                  fontSize: 12,
                }}
              />
              <button
                onClick={openAddProjectForm}
                style={{
                  border: 'none',
                  background: T.accent,
                  color: '#fff',
                  borderRadius: 7,
                  padding: '0 12px',
                  cursor: 'pointer',
                  fontWeight: 700,
                  whiteSpace: 'nowrap',
                }}
              >
                + 新增
              </button>
            </div>
            <div style={{ maxHeight: 180, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
              {filteredManageProjects.length === 0 ? (
                <div style={{ padding: 12, textAlign: 'center', color: T.textMuted, fontSize: 12 }}>
                  没有匹配项目
                </div>
              ) : filteredManageProjects.map((project) => (
                <div
                  key={project.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 10,
                    padding: '8px 9px',
                    borderRadius: 7,
                    background: T.cardBg,
                    border: `1px solid ${T.borderColor}`,
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{
                      color: T.textPrimary,
                      fontSize: 12,
                      fontWeight: 700,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
                      {project.projectName}
                    </div>
                    <div style={{
                      color: T.textMuted,
                      fontSize: 11,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
                      {project.address || '未填写地址'}
                    </div>
                  </div>
                  <button
                    onClick={(event) => handleDeleteProject(project, event)}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      color: T.danger,
                      cursor: 'pointer',
                      fontSize: 12,
                      flexShrink: 0,
                    }}
                  >
                    删除
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
          {loading ? (
            <div style={{ padding: 24, textAlign: 'center', color: T.textMuted, fontSize: 13 }}>
              加载中...
            </div>
          ) : projects.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: T.textMuted, fontSize: 13 }}>
              暂无项目
            </div>
          ) : filteredProjects.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: T.textMuted, fontSize: 13 }}>
              没有匹配项目
            </div>
          ) : (
            filteredProjects.map((project) => {
              const isActive = activeProjectId === project.id;
              const hasLocation = project.longitude && project.latitude;
              const statusColor = STATUS_COLORS[project.projectStatus] || STATUS_COLORS.normal;

              return (
                <div
                  key={project.id}
                  onClick={() => hasLocation && handleSelectProject(project.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 10,
                    padding: '11px 16px',
                    cursor: hasLocation ? 'pointer' : 'default',
                    background: isActive ? T.activeItemBg : 'transparent',
                    borderLeft: isActive ? `3px solid ${T.accent}` : '3px solid transparent',
                    transition: 'background 0.15s',
                    opacity: hasLocation ? 1 : 0.72,
                  }}
                  onMouseEnter={(e) => {
                    if (hasLocation && !isActive) e.currentTarget.style.background = T.hoverBg;
                  }}
                  onMouseLeave={(e) => {
                    if (hasLocation && !isActive) e.currentTarget.style.background = 'transparent';
                  }}
                >
                  <span style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: statusColor,
                    flexShrink: 0,
                    marginTop: 7,
                  }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: 14,
                      fontWeight: isActive ? 700 : 500,
                      color: T.textPrimary,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}>
                      {project.projectName}
                    </div>
                    <div style={{
                      fontSize: 12,
                      color: T.textMuted,
                      marginTop: 3,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}>
                      {project.address || '未填写真实地址'}
                    </div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 6 }}>
                      <span style={{
                        fontSize: 11,
                        color: hasLocation ? T.success : T.warning,
                        background: hasLocation ? 'rgba(34,197,94,0.12)' : 'rgba(245,158,11,0.12)',
                        borderRadius: 999,
                        padding: '2px 7px',
                      }}>
                        {hasLocation ? '已定位' : '缺少坐标'}
                      </span>
                      <button
                        onClick={(event) => openLocationEditor(project, event)}
                        style={{
                          border: `1px solid ${T.accent}`,
                          background: 'transparent',
                          color: T.accent,
                          borderRadius: 4,
                          padding: '2px 8px',
                          fontSize: 11,
                          cursor: 'pointer',
                        }}
                      >
                        编辑定位
                      </button>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      <div style={{ flex: 1, position: 'relative' }}>
        <div ref={mapContainerRef} style={{ width: '100%', height: '100%' }} />

        <button
          onClick={handleToggleMapMode}
          style={{
            position: 'absolute',
            top: 18,
            right: 18,
            zIndex: 20,
            border: `1px solid ${T.borderColor}`,
            background: mapMode === 'satellite' ? T.accent : T.cardBg,
            color: mapMode === 'satellite' ? '#fff' : T.textPrimary,
            borderRadius: 8,
            padding: '9px 14px',
            cursor: 'pointer',
            fontSize: 13,
            fontWeight: 800,
            boxShadow: '0 8px 24px rgba(0,0,0,0.24)',
          }}
        >
          {mapMode === 'satellite' ? '普通地图' : '卫星地图'}
        </button>

        {loading && (
          <div style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'rgba(0,0,0,0.3)',
            zIndex: 10,
          }}>
            <div style={{
              padding: '16px 32px',
              borderRadius: 8,
              background: T.cardBg,
              color: T.textPrimary,
              fontSize: 14,
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
            }}>
              地图加载中...
            </div>
          </div>
        )}

        {!loading && projectsWithLocation.length === 0 && projects.length > 0 && (
          <div style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            pointerEvents: 'none',
          }}>
            <div style={{
              padding: '12px 24px',
              borderRadius: 8,
              background: T.cardBg,
              color: T.textMuted,
              fontSize: 13,
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
            }}>
              所有项目暂无坐标数据，无法在地图上定位
            </div>
          </div>
        )}
      </div>

      {editingProject && (
        <div
          onClick={() => !savingLocation && setEditingProject(null)}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            background: 'rgba(2, 6, 23, 0.72)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24,
          }}
        >
          <div
            onClick={(event) => event.stopPropagation()}
            style={{
              width: 520,
              maxWidth: '100%',
              background: T.modalBg || T.cardBg,
              border: `1px solid ${T.borderColor}`,
              borderRadius: 14,
              boxShadow: '0 24px 80px rgba(0,0,0,0.45)',
              padding: 22,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
              <div>
                <div style={{ fontSize: 18, fontWeight: 800, color: T.textPrimary }}>编辑项目定位</div>
                <div style={{ fontSize: 12, color: T.textMuted, marginTop: 5 }}>{editingProject.projectName}</div>
              </div>
              <button
                onClick={() => !savingLocation && setEditingProject(null)}
                style={{ border: 'none', background: 'transparent', color: T.textMuted, fontSize: 22, cursor: 'pointer' }}
              >
                x
              </button>
            </div>

            <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  省
                  <input
                    value={locationForm.province}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, province: event.target.value }))}
                    placeholder="例如：江苏省"
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  市
                  <input
                    value={locationForm.city}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, city: event.target.value }))}
                    placeholder="例如：苏州市"
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  区县
                  <input
                    value={locationForm.district}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, district: event.target.value }))}
                    placeholder="例如：昆山市"
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  />
                </label>
              </div>

              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                真实地址
                <textarea
                  value={locationForm.address}
                  onChange={(event) => setLocationForm((prev) => ({ ...prev, address: event.target.value }))}
                  placeholder="例如：江苏省苏州市昆山市某某路某某号"
                  rows={3}
                  style={{
                    resize: 'vertical',
                    minHeight: 74,
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>

              <button
                onClick={handleResolveAddress}
                disabled={savingLocation}
                style={{
                  alignSelf: 'flex-start',
                  border: `1px solid ${T.accent}`,
                  background: T.activeItemBg,
                  color: T.accent,
                  borderRadius: 6,
                  padding: '7px 12px',
                  fontSize: 12,
                  cursor: savingLocation ? 'not-allowed' : 'pointer',
                }}
              >
                根据地址解析坐标
              </button>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 150px', gap: 12 }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  经度 longitude
                  <input
                    value={locationForm.longitude}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, longitude: event.target.value }))}
                    placeholder="例如：120.980737"
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  纬度 latitude
                  <input
                    value={locationForm.latitude}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, latitude: event.target.value }))}
                    placeholder="例如：31.384624"
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                  坐标系
                  <select
                    value={locationForm.coordinateType}
                    onChange={(event) => setLocationForm((prev) => ({ ...prev, coordinateType: event.target.value }))}
                    style={{
                      background: T.surface2,
                      border: `1px solid ${T.borderColor}`,
                      borderRadius: 8,
                      color: T.textPrimary,
                      padding: '10px 12px',
                      outline: 'none',
                      fontSize: 13,
                    }}
                  >
                    <option value="BD09">BD09</option>
                    <option value="GCJ02">GCJ02</option>
                    <option value="WGS84">WGS84</option>
                  </select>
                </label>
              </div>

              <div style={{ fontSize: 12, color: locationMessage ? T.warning : T.textMuted, minHeight: 18 }}>
                {locationMessage || '提示：地址用于展示，经纬度用于真正打点。地址解析不准时，可以手动修正经纬度。'}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button
                onClick={() => !savingLocation && setEditingProject(null)}
                disabled={savingLocation}
                style={{
                  border: `1px solid ${T.borderColor}`,
                  background: 'transparent',
                  color: T.textSecondary,
                  borderRadius: 7,
                  padding: '9px 18px',
                  cursor: savingLocation ? 'not-allowed' : 'pointer',
                }}
              >
                取消
              </button>
              <button
                onClick={handleSaveLocation}
                disabled={savingLocation}
                style={{
                  border: 'none',
                  background: T.accent,
                  color: '#fff',
                  borderRadius: 7,
                  padding: '9px 20px',
                  cursor: savingLocation ? 'not-allowed' : 'pointer',
                  fontWeight: 700,
                }}
              >
                {savingLocation ? '保存中...' : '保存定位'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showProjectForm && (
        <div
          onClick={() => !savingProject && setShowProjectForm(false)}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            background: 'rgba(2, 6, 23, 0.72)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24,
          }}
        >
          <div
            onClick={(event) => event.stopPropagation()}
            style={{
              width: 560,
              maxWidth: '100%',
              background: T.modalBg || T.cardBg,
              border: `1px solid ${T.borderColor}`,
              borderRadius: 14,
              boxShadow: '0 24px 80px rgba(0,0,0,0.45)',
              padding: 22,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
              <div>
                <div style={{ fontSize: 18, fontWeight: 800, color: T.textPrimary }}>新增项目</div>
                <div style={{ fontSize: 12, color: T.textMuted, marginTop: 5 }}>新增后会进入真实项目列表，填写坐标即可在地图打点。</div>
              </div>
              <button
                onClick={() => !savingProject && setShowProjectForm(false)}
                style={{ border: 'none', background: 'transparent', color: T.textMuted, fontSize: 22, cursor: 'pointer' }}
              >
                x
              </button>
            </div>

            <div style={{ marginTop: 18, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                项目名称 *
                <input
                  value={projectForm.projectName}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, projectName: event.target.value }))}
                  placeholder="请输入项目名称"
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                项目简称
                <input
                  value={projectForm.shortName}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, shortName: event.target.value }))}
                  placeholder="例如：A区"
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                项目状态
                <select
                  value={projectForm.projectStatus}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, projectStatus: event.target.value }))}
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                >
                  <option value="normal">正常</option>
                  <option value="warning">预警</option>
                  <option value="danger">异常</option>
                  <option value="stopped">停工</option>
                </select>
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                项目阶段
                <input
                  value={projectForm.phase}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, phase: event.target.value }))}
                  placeholder="例如：施工中"
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>
            </div>

            <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary, marginTop: 12 }}>
              真实地址
              <textarea
                value={projectForm.address}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, address: event.target.value }))}
                placeholder="例如：江苏省苏州市昆山市某某路某某号"
                rows={3}
                style={{
                  resize: 'vertical',
                  minHeight: 70,
                  background: T.surface2,
                  border: `1px solid ${T.borderColor}`,
                  borderRadius: 8,
                  color: T.textPrimary,
                  padding: '10px 12px',
                  outline: 'none',
                  fontSize: 13,
                }}
              />
            </label>

            <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                经度 longitude
                <input
                  value={projectForm.longitude}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, longitude: event.target.value }))}
                  placeholder="可先不填，后续编辑定位"
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 12, color: T.textSecondary }}>
                纬度 latitude
                <input
                  value={projectForm.latitude}
                  onChange={(event) => setProjectForm((prev) => ({ ...prev, latitude: event.target.value }))}
                  placeholder="可先不填，后续编辑定位"
                  style={{
                    background: T.surface2,
                    border: `1px solid ${T.borderColor}`,
                    borderRadius: 8,
                    color: T.textPrimary,
                    padding: '10px 12px',
                    outline: 'none',
                    fontSize: 13,
                  }}
                />
              </label>
            </div>

            <div style={{ fontSize: 12, color: projectMessage ? T.warning : T.textMuted, minHeight: 18, marginTop: 12 }}>
              {projectMessage || '提示：只新增项目可以不填坐标；要出现在地图上，需要补齐经纬度。'}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button
                onClick={() => !savingProject && setShowProjectForm(false)}
                disabled={savingProject}
                style={{
                  border: `1px solid ${T.borderColor}`,
                  background: 'transparent',
                  color: T.textSecondary,
                  borderRadius: 7,
                  padding: '9px 18px',
                  cursor: savingProject ? 'not-allowed' : 'pointer',
                }}
              >
                取消
              </button>
              <button
                onClick={handleAddProject}
                disabled={savingProject}
                style={{
                  border: 'none',
                  background: T.accent,
                  color: '#fff',
                  borderRadius: 7,
                  padding: '9px 20px',
                  cursor: savingProject ? 'not-allowed' : 'pointer',
                  fontWeight: 700,
                }}
              >
                {savingProject ? '新增中...' : '新增项目'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
