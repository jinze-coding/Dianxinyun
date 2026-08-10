export interface VisitorProfileAwarePayload {
  profileAction?: 'NONE' | 'CREATE' | 'UPDATE';
  profileCode?: string;
  profileName?: string;
  profileRetentionAgreed?: boolean;
  profileVersion?: number;
}

export interface VisitorSessionErrorLike {
  code?: number;
  statusCode?: number;
}

export interface VisitorSessionLike {
  visitorSessionToken: string;
}

export type OptionalVisitorProfilesResult<TSession extends VisitorSessionLike, TProfile> =
  | { available: true; session: TSession; profiles: TProfile[] }
  | { available: false };

export type VisitorSubmitResult<TResult> =
  | { status: 'submitted'; data: TResult }
  | { status: 'submitted-manually'; data: TResult }
  | { status: 'cancelled' };

export interface LatestSelectionTicket {
  id: number;
  key: string;
}

/** Keeps only the latest asynchronous profile selection eligible to update the form. */
export class LatestProfileSelectionGuard {
  private latestId = 0;
  private busyKey = '';

  begin(key: string): LatestSelectionTicket | null {
    if (!key || this.busyKey === key) return null;
    const ticket = { id: ++this.latestId, key };
    this.busyKey = key;
    return ticket;
  }

  isCurrent(ticket: LatestSelectionTicket): boolean {
    return ticket.id === this.latestId;
  }

  finish(ticket: LatestSelectionTicket): boolean {
    if (!this.isCurrent(ticket)) return false;
    this.busyKey = '';
    return true;
  }

  invalidate(): void {
    this.latestId += 1;
    this.busyKey = '';
  }

  activeKey(): string {
    return this.busyKey;
  }
}

/** Profile history is optional: any identity/session failure leaves manual entry available. */
export async function loadOptionalVisitorProfiles<
  TSession extends VisitorSessionLike,
  TProfile
>(
  createSession: () => Promise<TSession>,
  listProfiles: (visitorSessionToken: string) => Promise<TProfile[]>
): Promise<OptionalVisitorProfilesResult<TSession, TProfile>> {
  try {
    const session = await createSession();
    const profiles = await listProfiles(session.visitorSessionToken);
    return { available: true, session, profiles };
  } catch {
    return { available: false };
  }
}

export function isVisitorSessionAuthorizationError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as VisitorSessionErrorLike;
  return candidate.code === 401 || candidate.code === 403
    || candidate.statusCode === 401 || candidate.statusCode === 403;
}

export function hasVisitorProfileContext(payload: VisitorProfileAwarePayload): boolean {
  return Boolean(payload.profileCode)
    || payload.profileAction === 'CREATE'
    || payload.profileAction === 'UPDATE';
}

/** Removes only profile/session semantics; all visitor-entered trip data remains untouched. */
export function withoutVisitorProfileContext<TPayload extends VisitorProfileAwarePayload>(
  payload: TPayload
): TPayload {
  const result = { ...payload };
  delete result.profileAction;
  delete result.profileCode;
  delete result.profileName;
  delete result.profileRetentionAgreed;
  delete result.profileVersion;
  return result;
}

export async function submitWithVisitorSessionFallback<
  TPayload extends VisitorProfileAwarePayload,
  TResult
>(options: {
  payload: TPayload;
  visitorSessionToken?: string;
  submit: (payload: TPayload, visitorSessionToken?: string) => Promise<TResult>;
  confirmManualFallback: () => Promise<boolean>;
  onSessionExpired: () => void;
}): Promise<VisitorSubmitResult<TResult>> {
  try {
    return {
      status: 'submitted',
      data: await options.submit(options.payload, options.visitorSessionToken)
    };
  } catch (error) {
    const mayFallback = Boolean(options.visitorSessionToken)
      && isVisitorSessionAuthorizationError(error);
    if (!mayFallback) throw error;

    options.onSessionExpired();
    if (!await options.confirmManualFallback()) return { status: 'cancelled' };

    const manualPayload = withoutVisitorProfileContext(options.payload);
    return {
      status: 'submitted-manually',
      data: await options.submit(manualPayload, undefined)
    };
  }
}
