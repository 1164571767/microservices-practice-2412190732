import { authSession } from '../../app/services'
import { normalizeEmployee, validateCreatedEmployee, type CreateEmployeeRequest } from './model'

export async function createEmployee(input: CreateEmployeeRequest) {
  const request = normalizeEmployee(input)
  // This endpoint has no idempotency contract. The session/client never replay POST.
  const response = await authSession.request<unknown>('/api/identity/users', { method: 'POST', body: request })
  return validateCreatedEmployee(response, request)
}
