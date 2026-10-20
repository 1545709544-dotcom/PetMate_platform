/**
 * 直接执行 SQL 的辅助 —— 仅供测试用
 */
import { spawn } from 'child_process'
import { MYSQL } from './constants.js'

function quote(v: string): string {
  return `"${v.replace(/"/g, '\\"')}"`
}

export async function execDb(sql: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const args = [
      `-h${MYSQL.host}`,
      `-P${MYSQL.port}`,
      `-u${MYSQL.user}`,
      `-p${MYSQL.password}`,
      MYSQL.database,
      `-e`,
      sql,
    ]
    const proc = spawn('mysql', args, { windowsHide: true })
    let stdout = ''
    let stderr = ''
    proc.stdout.on('data', (d) => (stdout += d.toString()))
    proc.stderr.on('data', (d) => (stderr += d.toString()))
    proc.on('close', (code) => {
      if (code !== 0) return reject(new Error(`mysql exit=${code} stderr=${stderr}`))
      resolve(stdout)
    })
    proc.on('error', reject)
  })
}