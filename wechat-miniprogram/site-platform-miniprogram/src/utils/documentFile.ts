import type { ProjectDocument, ProjectDocumentVersion } from '@/types';
import { downloadProjectDocumentFile } from '@/api/document';

export interface LocalDocumentFile {
  path: string;
  name: string;
  size: number;
}

interface ChooseMessageFileResult {
  tempFiles?: Array<{ path: string; name?: string; size?: number }>;
}

declare const wx: {
  chooseMessageFile: (options: {
    count: number;
    type: 'file';
    success: (result: ChooseMessageFileResult) => void;
    fail: (error: { errMsg?: string }) => void;
  }) => void;
};

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'];
const OPEN_DOCUMENT_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt'];

function ensureSize(file: LocalDocumentFile) {
  if (file.size > MAX_FILE_SIZE) throw new Error('单个文件不能超过 50MB');
  return file;
}

export function chooseMessageDocument(): Promise<LocalDocumentFile> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (result) => {
        const file = result.tempFiles?.[0];
        if (!file?.path) { reject(new Error('未选择文件')); return; }
        try { resolve(ensureSize({ path: file.path, name: file.name || `项目资料_${Date.now()}`, size: Number(file.size || 0) })); }
        catch (error) { reject(error); }
      },
      fail: (error) => reject(new Error(error.errMsg?.includes('cancel') ? '已取消选择' : error.errMsg || '文件选择失败'))
    });
    // #endif

    // #ifdef H5
    const h5Api = uni as unknown as { chooseFile?: (options: { count: number; success: (result: ChooseMessageFileResult) => void; fail: (error: { errMsg?: string }) => void }) => void };
    if (!h5Api.chooseFile) { reject(new Error('当前环境暂不支持文件选择')); return; }
    h5Api.chooseFile({
      count: 1,
      success: (result) => {
        const file = result.tempFiles?.[0];
        if (!file?.path) { reject(new Error('未选择文件')); return; }
        try { resolve(ensureSize({ path: file.path, name: file.name || `项目资料_${Date.now()}`, size: Number(file.size || 0) })); }
        catch (error) { reject(error); }
      },
      fail: (error) => reject(new Error(error.errMsg?.includes('cancel') ? '已取消选择' : error.errMsg || '文件选择失败'))
    });
    // #endif
  });
}

export function chooseDocumentImage(sourceType: 'camera' | 'album'): Promise<LocalDocumentFile> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['original', 'compressed'],
      sourceType: [sourceType],
      success: (result) => {
        const path = result.tempFilePaths?.[0];
        const rawFiles = result.tempFiles ? (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) : [];
        const file = rawFiles[0] as { size?: number } | undefined;
        if (!path) { reject(new Error('未选择图片')); return; }
        try { resolve(ensureSize({ path, name: `现场资料_${Date.now()}.jpg`, size: Number(file?.size || 0) })); }
        catch (error) { reject(error); }
      },
      fail: (error) => reject(new Error(error.errMsg?.includes('cancel') ? '已取消选择' : error.errMsg || '图片选择失败'))
    });
  });
}

export function extensionOf(fileName?: string) {
  if (!fileName || !fileName.includes('.')) return '';
  return fileName.split('.').pop()?.toLowerCase() || '';
}

export function formatFileSize(size?: number) {
  const bytes = Number(size || 0);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10240 ? 1 : 0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(bytes < 10485760 ? 1 : 0)} MB`;
}

export async function openProjectDocument(document: ProjectDocument, version?: ProjectDocumentVersion, preview = true) {
  const selected = version || document.currentVersion;
  const filePath = await downloadProjectDocumentFile(document.id, selected?.id, preview);
  const extension = extensionOf(selected?.fileName || selected?.fileExtension);
  if (IMAGE_EXTENSIONS.includes(extension) || filePath.endsWith('.svg')) {
    uni.previewImage({ urls: [filePath], current: filePath });
    return;
  }
  if (OPEN_DOCUMENT_EXTENSIONS.includes(extension)) {
    await new Promise<void>((resolve, reject) => {
      uni.openDocument({ filePath, showMenu: true, success: () => resolve(), fail: (error) => reject(new Error(error.errMsg || '文件打开失败')) });
    });
    return;
  }
  throw new Error('该格式暂不支持在线预览，请使用下载功能后通过对应软件打开');
}

export async function saveProjectDocument(document: ProjectDocument, version?: ProjectDocumentVersion) {
  const selected = version || document.currentVersion;
  const filePath = await downloadProjectDocumentFile(document.id, selected?.id, false);
  return new Promise<string>((resolve, reject) => {
    uni.saveFile({
      tempFilePath: filePath,
      success: (result) => resolve(result.savedFilePath),
      fail: (error) => reject(new Error(error.errMsg || '文件保存失败'))
    });
  });
}
