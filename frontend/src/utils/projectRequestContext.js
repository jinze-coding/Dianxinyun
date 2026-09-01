const projectKey = (projectId) => projectId === null || projectId === undefined
  ? ''
  : String(projectId);

export function createProjectRequestGuard() {
  let latestSequence = 0;

  return {
    begin(projectId) {
      latestSequence += 1;
      return {
        projectKey: projectKey(projectId),
        sequence: latestSequence,
      };
    },
    invalidate() {
      latestSequence += 1;
    },
    isCurrent(ticket, currentProjectId) {
      return Boolean(ticket)
        && ticket.sequence === latestSequence
        && ticket.projectKey === projectKey(currentProjectId);
    },
  };
}
