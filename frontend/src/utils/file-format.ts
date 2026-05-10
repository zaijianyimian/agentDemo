/**
 * 格式化文件大小
 * @param size 文件大小（字节）
 * @returns 格式化后的文件大小字符串
 */
export function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 格式化大数字（如 Token 使用量）
 * @param num 数字
 * @returns 格式化后的数字字符串
 */
export function formatLargeNumber(num: number): string {
  if (num < 1000) return num.toString()
  if (num < 1000000) return `${(num / 1000).toFixed(1)}K`
  return `${(num / 1000000).toFixed(1)}M`
}

/**
 * 格式化百分比
 * @param value 数值（0-1）
 * @returns 格式化后的百分比字符串
 */
export function formatPercent(value: number): string {
  return `${(value * 100).toFixed(0)}%`
}

/**
 * 格式化相似度分数
 * @param score 相似度分数（0-1）
 * @returns 格式化后的相似度字符串
 */
export function formatScore(score: number): string {
  return `相似度 ${(score * 100).toFixed(0)}%`
}