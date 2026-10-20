import { request as pwRequest } from '@playwright/test'
const ctx = await pwRequest.newContext()
const reg = await ctx.post('http://localhost:8080/api/v1/auth/register', {
  headers: { 'Content-Type': 'application/json' },
  data: { phone: '139' + String(Math.floor(Math.random()*1e8)).padStart(8,'0'), nickname: 'petmate_test_repro', password: 'abc123456' },
})
console.log('REG', reg.status(), await reg.json())
const regJ = await reg.json()
const token = regJ.data.token
const p = await ctx.post('http://localhost:8080/api/v1/pet', {
  headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
  data: { name: 'petmate_test_repro_pet', categoryId: 1, breedId: 3, gender: 1 },
})
console.log('PET', p.status(), await p.json())
await ctx.dispose()