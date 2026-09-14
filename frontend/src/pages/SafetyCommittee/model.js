/** Keep older pages stable while new submissions would shift offset pagination. */
export function mergeCommitteePage(current, incoming, page, latestId) {
  if (page > 1 && latestId !== null && latestId !== incoming.latestId) return { data: current, latestId, hasNew: true };
  return { data: incoming, latestId: incoming.latestId, hasNew: false };
}
