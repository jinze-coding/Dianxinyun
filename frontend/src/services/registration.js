import { post } from './api';

export function submitRegistrationApplication(data) {
  return post('/registration-applications', data);
}

export function queryRegistrationApplicationStatus(data) {
  return post('/registration-applications/status', data);
}

export function cancelRegistrationApplication(data) {
  return post('/registration-applications/cancel', data);
}
