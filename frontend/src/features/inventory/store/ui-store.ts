import { create } from 'zustand'

// Only client-side UI preferences live here. Vehicles remain in TanStack Query.
export const useInventoryUi = create<{
  selectedId: string | null
  managerId: string
  selectVehicle: (id: string | null) => void
  setManagerId: (id: string) => void
}>((set) => ({
  selectedId: null,
  managerId: import.meta.env.VITE_MANAGER_ID || '',
  selectVehicle: (selectedId) => set({ selectedId }),
  setManagerId: (managerId) => set({ managerId }),
}))
