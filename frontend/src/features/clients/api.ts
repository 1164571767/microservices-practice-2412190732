import { authSession } from '../../app/services'
import { checked, checkedArray, checkedPage, isClient, isPet, isPetDirectory, isVaccination, isAllergy, isSummary, isOperation, isCandidate, type ClientInput, type PetInput } from './model'
const root = '/api/owner'
export const clientKey = ['clinic-archives']
const get = (path: string) => authSession.request<unknown>(`${root}${path}`)
const write = (path: string, body: unknown, method: 'POST' | 'PUT' = 'POST') => authSession.request<unknown>(`${root}${path}`, { method, body })
export const listClients = async (params: URLSearchParams, signal?: AbortSignal) => checkedPage(await authSession.request(`${root}/clients?${params}`, { signal }), isClient)
export const listPets = async (params: URLSearchParams, signal?: AbortSignal) => checkedPage(await authSession.request(`${root}/clinic-pets?${params}`, { signal }), isPetDirectory)
export const getClient = async (id: string) => checked(await get(`/owners/${id}`), isClient)
export const getPet = async (id: string) => checked(await get(`/pets/${id}`), isPet)
export const saveClient = async (details: ClientInput, id?: string, version?: number) => checked(await write(id ? `/clients/${id}` : '/clients', id ? { details, version } : details, id ? 'PUT' : 'POST'), isClient)
export const savePet = async (details: PetInput, ownerId: string, id?: string, version = 0) => checked(await write(id ? `/clinic-pets/${id}` : `/clients/${ownerId}/pets`, { details, birthDateEstimated: details.birthDateEstimated, version }, id ? 'PUT' : 'POST'), isPet)
export const archive = async (kind: 'client' | 'pet', id: string, version: number, reason: string) => {
  const value = await write(`/${kind === 'client' ? 'clients' : 'clinic-pets'}/${id}/archive`, { version, reason })
  return kind === 'client' ? checked(value, isClient) : checked(value, isPet)
}
export const vaccinations = async (id: string) => checkedArray(await get(`/pets/${id}/vaccinations`), isVaccination)
export const allergies = async (id: string) => checkedArray(await get(`/pets/${id}/allergies`), isAllergy)
export const summaries = async (id: string) => checkedArray(await get(`/pets/${id}/medical-records`), isSummary)
export const operations = async (kind: 'client' | 'pet', id: string, page: number) => checkedPage(await get(`/${kind === 'client' ? 'clients' : 'clinic-pets'}/${id}/operations?page=${page}&size=10`), isOperation)
export const findCandidate = async (username: string) => checked(await get(`/clients/binding-candidate?${new URLSearchParams({ username })}`), isCandidate)
export const bindAccount = async (id: string, version: number, username: string, expectedUserId: string) => checked(await write(`/clients/${id}/bind`, { version, username, expectedUserId, confirmed: true }), isClient)
export async function saveHealth(kind: 'vaccinations' | 'allergies', id: string, record: unknown, correctionId?: string, reason?: string) {
  const value = await write(`/pets/${id}/${kind}${correctionId ? `/${correctionId}/corrections` : ''}`, correctionId ? { record, reason } : record)
  return kind === 'vaccinations' ? checked(value, isVaccination) : checked(value, isAllergy)
}
