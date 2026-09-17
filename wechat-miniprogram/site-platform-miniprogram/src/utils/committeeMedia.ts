import { committeeApi } from '@/api/safetyCommittee';
import { ApiRequestError } from '@/api/request';
import { moduleDownload } from '@/utils/moduleNetwork';

interface MediaOptions {
  thumbnail?: boolean;
  video?: boolean;
  forceDownload?: boolean;
  onProgress?: (percent: number) => void;
}

/** One component-owned media file; no persistent cache or shared login credentials. */
export function createCommitteeMediaLoader() {
  let generation = 0;
  let task: UniApp.DownloadTask | undefined;
  let temporary = '';
  const cancelled = () => new Error('附件读取已取消');
  function unlink(path: string) {
    // #ifdef MP-WEIXIN
    if (path) uni.getFileSystemManager().unlink({ filePath: path, fail: () => undefined });
    // #endif
  }
  function clear() {
    generation++;
    task?.abort(); task = undefined;
    unlink(temporary); temporary = '';
  }
  async function load(id: number, options: MediaOptions = {}) {
    clear();
    const ticket = generation;
    const source = await committeeApi.read(id, true, Boolean(options.thumbnail));
    if (ticket !== generation) throw cancelled();
    // Native images use the same checked download channel as documents, then render a wxfile path.
    // HTTPS video keeps Range playback; local development HTTP and explicit retries use a local file.
    // #ifdef MP-WEIXIN
    if (!options.video || !/^https:\/\//i.test(source) || options.forceDownload) {
      const path = await new Promise<string>((resolve, reject) => {
        task = moduleDownload({
          url: source,
          timeout: options.video ? 600000 : 120000,
          success: result => {
            if (ticket !== generation) { unlink(result.tempFilePath); reject(cancelled()); return; }
            if (result.statusCode !== 200) {
              unlink(result.tempFilePath);
              reject(new ApiRequestError(result.statusCode === 409 ? '附件已更新，请重试加载' : '附件读取失败，请重试', result.statusCode, result.statusCode));
              return;
            }
            resolve(result.tempFilePath);
          },
          fail: () => reject(ticket !== generation ? cancelled() : new Error('附件下载失败，请检查网络后重试')),
        });
        task.onProgressUpdate?.(result => { if (ticket === generation) options.onProgress?.(result.progress); });
      });
      if (ticket !== generation) { unlink(path); throw cancelled(); }
      task = undefined; temporary = path;
      return path;
    }
    // #endif
    return source;
  }
  return { load, clear };
}
