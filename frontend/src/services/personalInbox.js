import { get, put } from './api';

export const getPersonalTodos = (params = {}) => get('/me/todos', params);
export const getPersonalTodoSummary = (params = {}) => get('/me/work-summary', params);
export const getPersonalNotifications = (params = {}) => get('/me/inbox', params);
export const getUnreadNotificationCount = () => get('/me/inbox/unread-count');
export const markNotificationRead = (id) => put(`/me/inbox/${id}/read`);
export const markAllNotificationsRead = (projectId) => {
  const query = projectId == null || projectId === ''
    ? ''
    : `?projectId=${encodeURIComponent(projectId)}`;
  return put(`/me/inbox/read-all${query}`);
};
