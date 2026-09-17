import type { SelectedFile } from '@/api/safetyCommittee';

export function committeeLocalMediaKind(file?: SelectedFile): 'image' | 'video' | undefined {
  const extension = file?.name.split('.').pop()?.toLowerCase();
  if (extension && ['jpg','jpeg','png','gif','bmp','webp','heic','heif'].includes(extension)) return 'image';
  if (extension && ['mp4','mov','m4v','webm','mkv','avi'].includes(extension)) return 'video';
}

// Only files created by our capture page are owned by the draft.
export function cleanupCommitteeCapture(file?: SelectedFile) {
  if (!file?.capturedTemporary) return;
  // #ifdef MP-WEIXIN
  for (const path of new Set([file.path, file.thumbnailPath].filter(Boolean))) {
    try { uni.getFileSystemManager().unlink({filePath:path!,fail:()=>undefined}); } catch { /* Native temporary files also expire. */ }
  }
  // #endif
}
