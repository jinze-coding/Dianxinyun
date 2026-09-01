export function createMeetingInviteRefreshCoordinator() {
  let loadStarted = false;
  let firstShowHandled = false;
  let inFlight: Promise<void> | undefined;

  return {
    markLoadStarted() {
      loadStarted = true;
    },

    shouldRefreshOnShow() {
      if (!firstShowHandled) {
        firstShowHandled = true;
        return false;
      }
      return loadStarted;
    },

    run(task: () => Promise<void>) {
      if (inFlight) return inFlight;
      let tracked: Promise<void>;
      tracked = Promise.resolve().then(task).finally(() => {
        if (inFlight === tracked) inFlight = undefined;
      });
      inFlight = tracked;
      return tracked;
    }
  };
}
