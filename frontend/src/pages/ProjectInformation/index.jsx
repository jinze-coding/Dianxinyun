import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getProjectProfile, updateProjectProfile } from '../../services/project';
import { deleteFile, previewFile, uploadFile } from '../../services/file';
import { getApiErrorMessage } from '../../services/api';
import { profilePayload, validateProjectProfile } from './projectProfile';
import './index.css';

const GROUPS = [
  {
    title: '基本信息',
    layout: 'wide',
    fields: [
      ['projectName', '项目全称', 'text', true, 'wide'], ['shortName', '项目简称', 'text', true],
      ['directCompany', '直属公司', 'text', true], ['manager', '项目经理', 'text', true],
      ['managerPhone', '经理联系方式', 'text', true], ['spaceCapacity', '空间容量'],
      ['engineeringType', '工程类型', 'text', true], ['phase', '工程状态', 'text', true],
      ['description', '项目简介', 'textarea', false, 'wide'],
    ],
  },
  {
    title: '工期与位置',
    layout: 'wide',
    fields: [
      ['startDate', '计划开工日期', 'date', true], ['endDate', '计划竣工日期', 'date', true],
      ['actualStartDate', '实际开工日期', 'date'], ['actualEndDate', '实际竣工日期', 'date'],
      ['address', '项目地点', 'text', true, 'wide'], ['fixedIpAddress', '固定 IP'],
    ],
  },
  {
    title: '参建及合同',
    layout: 'wide',
    fields: [
      ['ownerUnit', '建设单位', 'text', true], ['supervisionUnit', '监理单位', 'text', true],
      ['designUnit', '设计单位', 'text', true], ['contractor', '施工单位', 'text', true],
      ['contractorCreditCode', '施工单位统一社会信用代码'], ['contractorLicenseNumber', '安全生产许可证号'],
      ['generalContractNumber', '总承包合同编号'], ['projectClassification', '项目分类'],
      ['investmentEntity', '投资主体'], ['contractingMode', '承建模式'],
      ['contractAmount', '合同金额（元）', 'number'],
    ],
  },
  {
    title: '规模指标',
    fields: [
      ['buildingArea', '建筑面积（㎡）', 'number', true], ['landArea', '用地面积（㎡）', 'number', true],
      ['buildingHeight', '建筑高度（m）', 'number', true], ['excavationDepth', '开挖深度（m）', 'number'],
      ['undergroundFloorCount', '地下层数', 'integer'], ['abovegroundFloorCount', '地上层数', 'integer'],
      ['projectScale', '项目规模'], ['projectCategory', '项目类别'], ['projectLevel', '项目级别'],
    ],
  },
  {
    title: '目标与人员',
    fields: [
      ['projectTarget', '项目目标', 'textarea', false, 'wide'], ['qualityGoal', '质量目标', 'textarea'],
      ['safetyGoal', '安全目标', 'textarea'], ['greenConstructionGoal', '绿色建造目标', 'textarea', false, 'wide'],
      ['managementStaffCount', '管理人员数', 'integer'], ['attendanceCount', '考勤人数', 'integer'],
      ['partyMemberCount', '党员人数', 'integer'],
    ],
  },
];

const BLANK = '未填写';
const MAX_IMAGE_SIZE = 15 * 1024 * 1024;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

function normalizeProfile(value) {
  const normalized = { ...value };
  GROUPS.flatMap((group) => group.fields).forEach(([key]) => {
    if (normalized[key] === null || normalized[key] === undefined) normalized[key] = '';
  });
  normalized.images = Array.isArray(value?.images) ? value.images.map((item) => ({ ...item })) : [];
  return normalized;
}

function Value({ value }) {
  return <div className={`project-profile-value${value === null || value === undefined || value === '' ? ' empty' : ''}`}>{value === null || value === undefined || value === '' ? BLANK : String(value)}</div>;
}

function ProfileField({ config, value, editing, error, onChange }) {
  const [key, label, type = 'text', required = false, width = ''] = config;
  const inputType = type === 'integer' || type === 'number' ? 'number' : type;
  return (
    <div className={`project-profile-field ${editing ? 'editing' : 'viewing'} ${width}`}>
      <label htmlFor={`profile-${key}`}>{label}{required && <em> *</em>}</label>
      {editing ? (
        type === 'textarea' ? (
          <textarea id={`profile-${key}`} value={value ?? ''} onChange={(event) => onChange(key, event.target.value)} />
        ) : (
          <input id={`profile-${key}`} type={inputType} step={type === 'integer' ? '1' : type === 'number' ? 'any' : undefined}
            value={value ?? ''} onChange={(event) => onChange(key, event.target.value)} />
        )
      ) : <Value value={value} />}
      {editing && error && <span className="project-profile-field-error">{error}</span>}
    </div>
  );
}

export default function ProjectInformationPage({ projectId, onBack, onSaved }) {
  const [profile, setProfile] = useState(null);
  const [draft, setDraft] = useState(null);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadRequired, setReloadRequired] = useState(false);
  const [errors, setErrors] = useState({});
  const [imageUrls, setImageUrls] = useState({});
  const [previewIndex, setPreviewIndex] = useState(null);
  const pendingIdsRef = useRef(new Set());
  const objectUrlsRef = useRef(new Set());
  const mountedRef = useRef(true);

  const releaseObjectUrls = useCallback(() => {
    setPreviewIndex(null);
    objectUrlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    objectUrlsRef.current.clear();
    setImageUrls({});
  }, []);

  const loadImageUrls = useCallback(async (images) => {
    releaseObjectUrls();
    const entries = await Promise.all((images || []).map(async (image) => {
      try {
        const blob = await previewFile(image.fileId);
        const url = URL.createObjectURL(blob);
        objectUrlsRef.current.add(url);
        return [image.fileId, url];
      } catch {
        return [image.fileId, ''];
      }
    }));
    if (mountedRef.current) setImageUrls(Object.fromEntries(entries));
  }, [releaseObjectUrls]);

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError('');
    setNotice('');
    setReloadRequired(false);
    setEditing(false);
    setErrors({});
    try {
      const result = await getProjectProfile(projectId);
      if (Number(result?.code) !== 200 || !result?.data) throw new Error(result?.message || '项目信息加载失败');
      const normalized = normalizeProfile(result.data);
      setProfile(normalized);
      setDraft(normalized);
      await loadImageUrls(normalized.images);
    } catch (requestError) {
      setProfile(null);
      setDraft(null);
      setError(getApiErrorMessage(requestError, '项目信息加载失败'));
    } finally {
      setLoading(false);
    }
  }, [loadImageUrls, projectId]);

  useEffect(() => {
    mountedRef.current = true;
    load();
    return () => {
      mountedRef.current = false;
      objectUrlsRef.current.forEach((url) => URL.revokeObjectURL(url));
      objectUrlsRef.current.clear();
      pendingIdsRef.current.forEach((id) => { deleteFile(id).catch(() => {}); });
    };
  }, [load]);

  const setField = (key, value) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const cancel = async () => {
    const ids = [...pendingIdsRef.current];
    pendingIdsRef.current.clear();
    await Promise.allSettled(ids.map((id) => deleteFile(id)));
    setDraft(normalizeProfile(profile));
    setEditing(false);
    setErrors({});
    setError('');
    setNotice('已取消本次修改');
    await loadImageUrls(profile?.images || []);
  };

  const handleBack = async () => {
    if (editing) await cancel();
    onBack?.();
  };

  const reloadProfile = async () => {
    const ids = [...pendingIdsRef.current];
    pendingIdsRef.current.clear();
    await Promise.allSettled(ids.map((id) => deleteFile(id)));
    await load();
  };

  const uploadImages = async (event) => {
    const files = [...(event.target.files || [])];
    event.target.value = '';
    if (!files.length) return;
    if ((draft?.images?.length || 0) + files.length > 20) {
      setErrors((current) => ({ ...current, images: '项目效果图最多20张' }));
      return;
    }
    const invalid = files.find((file) => !IMAGE_TYPES.has(file.type) || file.size > MAX_IMAGE_SIZE);
    if (invalid) {
      setErrors((current) => ({ ...current, images: '仅支持 JPEG、PNG、WebP，单张不超过15MB' }));
      return;
    }
    setUploading(true);
    setError('');
    const added = [];
    try {
      for (const file of files) {
        const result = await uploadFile({ file, projectId, fileName: file.name, fileType: '项目效果图', businessType: 'PROJECT_PROFILE_IMAGE_PENDING' });
        if (Number(result?.code) !== 200 || !result?.data?.id) throw new Error(result?.message || '效果图上传失败');
        pendingIdsRef.current.add(result.data.id);
        const url = URL.createObjectURL(file);
        objectUrlsRef.current.add(url);
        setImageUrls((current) => ({ ...current, [result.data.id]: url }));
        added.push({ fileId: result.data.id, fileName: result.data.fileName || file.name, fileSize: file.size, pending: true });
      }
      setDraft((current) => ({ ...current, images: [...(current.images || []), ...added] }));
      setErrors((current) => ({ ...current, images: undefined }));
    } catch (uploadError) {
      const uploadedIds = added.map((item) => item.fileId);
      uploadedIds.forEach((id) => pendingIdsRef.current.delete(id));
      await Promise.allSettled(uploadedIds.map((id) => deleteFile(id)));
      setImageUrls((current) => {
        const next = { ...current };
        uploadedIds.forEach((id) => {
          const url = next[id];
          if (url) {
            URL.revokeObjectURL(url);
            objectUrlsRef.current.delete(url);
          }
          delete next[id];
        });
        return next;
      });
      setError(getApiErrorMessage(uploadError, '效果图上传失败'));
    } finally {
      setUploading(false);
    }
  };

  const removeImage = async (image) => {
    setDraft((current) => ({ ...current, images: current.images.filter((item) => item.fileId !== image.fileId) }));
    if (pendingIdsRef.current.delete(image.fileId)) {
      await deleteFile(image.fileId).catch(() => {});
    }
  };

  const save = async () => {
    const validation = validateProjectProfile(draft);
    setErrors(validation);
    if (Object.keys(validation).length) {
      setError('请先修正标红字段再保存');
      return;
    }
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const result = await updateProjectProfile(projectId, profilePayload(draft));
      if (Number(result?.code) !== 200 || !result?.data) throw new Error(result?.message || '保存失败');
      pendingIdsRef.current.clear();
      const normalized = normalizeProfile(result.data);
      setProfile(normalized);
      setDraft(normalized);
      setEditing(false);
      setNotice('项目信息已保存');
      await loadImageUrls(normalized.images);
      await onSaved?.();
    } catch (saveError) {
      const conflict = saveError?.response?.status === 409 || Number(saveError?.response?.data?.code) === 409;
      setReloadRequired(conflict);
      setError(conflict ? '项目信息已被其他管理员更新，请重新加载后再编辑' : getApiErrorMessage(saveError, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const displayedImages = editing ? draft?.images || [] : profile?.images || [];
  const coverImage = displayedImages[0];
  const secondaryImages = displayedImages.slice(1);
  const title = profile?.projectName || '项目信息';
  const updatedText = useMemo(() => profile?.updateTime ? String(profile.updateTime).replace('T', ' ') : '', [profile?.updateTime]);
  const previewedImage = previewIndex === null ? null : displayedImages[previewIndex];

  const movePreview = useCallback((offset) => {
    setPreviewIndex((current) => {
      if (current === null || displayedImages.length < 2) return current;
      return (current + offset + displayedImages.length) % displayedImages.length;
    });
  }, [displayedImages.length]);

  useEffect(() => {
    if (previewIndex === null) return undefined;
    if (!displayedImages[previewIndex]) {
      setPreviewIndex(null);
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setPreviewIndex(null);
      if (event.key === 'ArrowLeft') movePreview(-1);
      if (event.key === 'ArrowRight') movePreview(1);
    };
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [displayedImages, movePreview, previewIndex]);

  const renderImage = (image, index, className = '') => (
    <figure key={image.fileId} className={className}>
      {imageUrls[image.fileId] ? <button type="button" className="project-profile-image-trigger"
        aria-label={`放大查看${image.fileName || `效果图${index + 1}`}`} onClick={() => setPreviewIndex(index)}>
        <img src={imageUrls[image.fileId]} alt={`${title}效果图${index + 1}`} />
        <span className="project-profile-image-zoom-hint" aria-hidden="true">点击查看大图</span>
      </button> : <div className="project-profile-image-fallback">图片加载失败</div>}
      <figcaption><span>{index === 0 ? '首图 · ' : ''}{image.fileName || `效果图${index + 1}`}</span>{editing && <button type="button" onClick={() => removeImage(image)}>移除</button>}</figcaption>
    </figure>
  );

  return (
    <main className="project-profile-page">
      <div className="project-profile-shell">
        <header className="project-profile-header">
          <div className="project-profile-heading">
            <button type="button" className="project-profile-back" onClick={handleBack}>← 返回上一页</button>
            <span className="project-profile-eyebrow">PROJECT PROFILE</span>
            <h1>{title}</h1>
            <p>集中查看项目基础资料、参建单位、规模指标和建设目标{updatedText && ` · 更新于 ${updatedText}`}</p>
            {!loading && profile && <div className="project-profile-summary">
              <span className="phase">{profile.phase || '工程状态未填写'}</span>
              <span>简称：{profile.shortName || '未填写'}</span>
              <span>项目经理：{profile.manager || '未填写'}</span>
              <span>{profile.startDate || '计划开工未填写'} 至 {profile.endDate || '计划竣工未填写'}</span>
            </div>}
          </div>
          {!loading && profile && <div className="project-profile-actions">
            {editing ? <>
              <button type="button" onClick={cancel} disabled={saving || uploading}>取消</button>
              <button type="button" className="primary" onClick={save} disabled={saving || uploading}>{saving ? '保存中…' : '保存修改'}</button>
            </> : profile.canEdit && <button type="button" className="primary" onClick={() => { setDraft(normalizeProfile(profile)); setEditing(true); setNotice(''); setReloadRequired(false); }}>编辑项目信息</button>}
          </div>}
        </header>

        {notice && <div className="project-profile-notice" role="status">{notice}</div>}
        {error && <div className="project-profile-error" role="alert"><span>{error}</span>{(!profile || reloadRequired) && <button type="button" onClick={reloadProfile}>重新加载</button>}</div>}
        {loading ? <div className="project-profile-state">正在加载项目信息…</div> : !profile ? (
          <div className="project-profile-state">暂时无法显示项目信息</div>
        ) : <>
          <section className="project-profile-card project-profile-gallery-section">
            <div className="project-profile-section-title"><div><span className="project-profile-section-index">01</span><h2>项目效果图</h2><p>首张作为项目首图，共 {displayedImages.length} 张</p></div>
              {editing && <label className={`project-profile-upload${uploading ? ' disabled' : ''}`}>＋ {uploading ? '上传中…' : '上传效果图'}<input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={uploading || saving} onChange={uploadImages} /></label>}
            </div>
            {errors.images && <div className="project-profile-inline-error">{errors.images}</div>}
            {displayedImages.length ? <div className={`project-profile-gallery${secondaryImages.length ? '' : ' single'}`}>
              {renderImage(coverImage, 0, 'project-profile-gallery-cover')}
              {secondaryImages.length > 0 && <div className="project-profile-thumbnails">
                {secondaryImages.map((image, index) => renderImage(image, index + 1))}
              </div>}
            </div> : <div className="project-profile-empty-gallery">未上传项目效果图</div>}
          </section>
          <div className="project-profile-sections">
            {GROUPS.map((group, index) => <section className={`project-profile-card project-profile-info-card ${group.layout || ''}`} key={group.title}>
              <div className="project-profile-section-title"><div><span className="project-profile-section-index">{String(index + 2).padStart(2, '0')}</span><h2>{group.title}</h2></div></div>
              <div className="project-profile-grid">
                {group.fields.map((config) => <ProfileField key={config[0]} config={config} value={(editing ? draft : profile)?.[config[0]]}
                  editing={editing} error={errors[config[0]]} onChange={setField} />)}
              </div>
            </section>)}
          </div>
        </>}
      </div>
      {previewedImage && imageUrls[previewedImage.fileId] && <div className="project-profile-lightbox"
        role="dialog" aria-modal="true" aria-labelledby="project-profile-lightbox-title"
        onMouseDown={(event) => { if (event.target === event.currentTarget) setPreviewIndex(null); }}>
        <div className="project-profile-lightbox-dialog">
          <div className="project-profile-lightbox-header">
            <div>
              <strong id="project-profile-lightbox-title">{previewedImage.fileName || `项目效果图${previewIndex + 1}`}</strong>
              <span>{previewIndex + 1} / {displayedImages.length}</span>
            </div>
            <button type="button" autoFocus aria-label="关闭大图" onClick={() => setPreviewIndex(null)}>×</button>
          </div>
          <div className="project-profile-lightbox-stage">
            {displayedImages.length > 1 && <button type="button" className="project-profile-lightbox-nav previous"
              aria-label="查看上一张" onClick={() => movePreview(-1)}>‹</button>}
            <img src={imageUrls[previewedImage.fileId]} alt={`${title}效果图${previewIndex + 1}`} />
            {displayedImages.length > 1 && <button type="button" className="project-profile-lightbox-nav next"
              aria-label="查看下一张" onClick={() => movePreview(1)}>›</button>}
          </div>
          <div className="project-profile-lightbox-footer">可使用键盘 ← → 切换，按 Esc 关闭</div>
        </div>
      </div>}
    </main>
  );
}
