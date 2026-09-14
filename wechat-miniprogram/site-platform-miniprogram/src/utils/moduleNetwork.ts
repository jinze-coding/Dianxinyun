// Keep only request handles and scope here; credentials and response bodies are never cached.
type Task = { abort: () => void };
type Scope = { projectId: number; module: string | null; task?: Task };
const pending = new Set<Scope>();
let currentProjectId = 0;
export function setNetworkProject(projectId: number) { currentProjectId = projectId; }

export function moduleForRequest(url: string, businessType = ''): string | null {
  const path = url.replace(/^https?:\/\/[^/]+/, '').replace(/^\/api(?:\/v1)?/, '');
  if (/^\/(?:public\/)?(?:site-access|meeting-materials)/.test(path)) return 'SITE_ACCESS';
  if (/^\/(?:project-documents|document-|my-document-|seal)/.test(path)) return 'DOCUMENT';
  if (/^\/(?:public\/)?(?:electric-boxes|inspection|general-inspection|edge-inspections|scan\/electric-box)/.test(path)) return 'INSPECTION';
  if (/^\/quality/.test(path)) return 'QUALITY';
  if (/^\/safety-committee/.test(path)) return 'SAFETY_COMMITTEE';
  if (/^(DOCUMENT_|SEAL_|PROJECT_DOCUMENT)/.test(businessType)) return 'DOCUMENT';
  if (/^QUALITY_/.test(businessType)) return 'QUALITY';
  if (/^(INSPECTION_|EDGE_INSPECTION_|ELECTRIC_BOX)/.test(businessType)) return 'INSPECTION';
  return null;
}

function pageScope() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
  const page = pages[pages.length - 1] as unknown as { route?: string; options?: Record<string, string> } | undefined;
  const route = page?.route || '';
  const module = route.startsWith('pages/documents/') || route.startsWith('pages/seal/') ? 'DOCUMENT'
    : route.startsWith('pages/inspection/') ? 'INSPECTION'
    : route.startsWith('pages/quality/') ? 'QUALITY'
    : route.startsWith('pages/safety-committee/') ? 'SAFETY_COMMITTEE' : null;
  return { projectId: Number(page?.options?.projectId) || currentProjectId, module };
}

function track<T extends Task>(options: { url: string; data?: unknown; formData?: unknown; complete?: (result: any) => void }, create: (complete: (result: any) => void) => T): T {
  const context = pageScope();
  const data = (options.formData || options.data || {}) as Record<string, unknown>;
  const queryProject = options.url.match(/[?&]projectId=(\d+)/)?.[1];
  const scope: Scope = { projectId: Number(data.projectId || queryProject) || context.projectId,
    module: moduleForRequest(options.url, String(data.businessType || '')) || (/\/files\//.test(options.url) ? context.module : null) };
  if (scope.module) pending.add(scope);
  try {
    const task = create((result) => { pending.delete(scope); options.complete?.(result); });
    scope.task = task;
    return task;
  } catch (error) { pending.delete(scope); throw error; }
}

uni.$on('project-module-availability', (state: { projectId: number; enabledBusinessModules: string[] }) => {
  for (const scope of pending) {
    if (scope.projectId === Number(state.projectId) && scope.module && !state.enabledBusinessModules.includes(scope.module)) {
      scope.task?.abort(); pending.delete(scope);
    }
  }
});

export const moduleRequest = (options: UniApp.RequestOptions) => track(options, (complete) => uni.request({ ...options, complete }));
export const moduleUpload = (options: UniApp.UploadFileOption) => track(options, (complete) => uni.uploadFile({ ...options, complete }));
export const moduleDownload = (options: UniApp.DownloadFileOption) => track(options, (complete) => uni.downloadFile({ ...options, complete }));
