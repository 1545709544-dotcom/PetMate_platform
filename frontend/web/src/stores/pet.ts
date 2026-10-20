import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Pet, PetSavePayload } from '@/api/modules/pet'
import { petApi } from '@/api/modules/pet'

/**
 * 宠物 Store
 */
export const usePetStore = defineStore('pet', () => {
  const pets = ref<Pet[]>([])
  const currentPetId = ref<number | null>(null)
  const loading = ref(false)

  const currentPet = computed<Pet | null>(() => {
    return pets.value.find((p) => p.id === currentPetId.value) || null
  })

  async function fetch() {
    loading.value = true
    try {
      const data = await petApi.list()
      pets.value = data || []
    } finally {
      loading.value = false
    }
  }

  function setCurrent(id: number | null) {
    currentPetId.value = id
  }

  async function save(pet: PetSavePayload) {
    const saved = await petApi.save(pet)
    if (pet.id) {
      const idx = pets.value.findIndex((p) => p.id === pet.id)
      if (idx >= 0) pets.value[idx] = saved
    } else {
      pets.value.unshift(saved)
    }
    return saved
  }

  async function remove(id: number) {
    await petApi.remove(id)
    pets.value = pets.value.filter((p) => p.id !== id)
    if (currentPetId.value === id) currentPetId.value = null
  }

  return {
    pets,
    currentPetId,
    currentPet,
    loading,
    fetch,
    setCurrent,
    save,
    remove,
  }
})