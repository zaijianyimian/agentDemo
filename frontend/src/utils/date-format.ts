import dayjs from 'dayjs'

/**
 * 格式化时间为 HH:mm 格式
 * @param time 时间字符串或 Date
 * @returns 格式化后的时间字符串
 */
export function formatTime(time: string | Date | number | null | undefined): string {
  if (!time) return '-'
  return dayjs(time).format('HH:mm')
}

/**
 * 格式化时间为完整日期时间格式
 * @param time 时间字符串或 Date
 * @returns 格式化后的日期时间字符串
 */
export function formatDateTime(time: string | Date | number | null | undefined): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

/**
 * 格式化时间为日期格式
 * @param time 时间字符串或 Date
 * @returns 格式化后的日期字符串
 */
export function formatDate(time: string | Date | number | null | undefined): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD')
}

/**
 * 格式化时间为短日期时间格式（MM-DD HH:mm）
 * @param time 时间字符串或 Date
 * @returns 格式化后的短日期时间字符串
 */
export function formatShortDateTime(time: string | Date | number | null | undefined): string {
  if (!time) return '-'
  return dayjs(time).format('MM-DD HH:mm')
}

/**
 * 格式化相对时间（智能显示）
 * - 今天：显示 HH:mm
 * - 昨天：显示"昨天"
 * - 7天内：显示"X天前"
 * - 其他：显示 MM-DD
 * @param time 时间字符串
 * @returns 格式化后的相对时间字符串
 */
export function formatRelativeTime(time: string | null | undefined): string {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()

  if (now.diff(d, 'day') === 0) return d.format('HH:mm')
  if (now.diff(d, 'day') === 1) return '昨天'
  if (now.diff(d, 'day') < 7) return `${now.diff(d, 'day')}天前`
  return d.format('MM-DD')
}

/**
 * 格式化文件更新时间（智能显示）
 * @param time 时间字符串
 * @returns 格式化后的时间字符串
 */
export function formatUpdateTime(time: string | null | undefined): string {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()

  if (now.diff(d, 'day') === 0) return d.format('HH:mm')
  if (now.diff(d, 'day') === 1) return '昨天'
  if (now.diff(d, 'day') < 7) return `${now.diff(d, 'day')}天前`
  return d.format('MM-DD')
}

/**
 * 格式化会话时间（带日期）
 * @param time 时间字符串
 * @returns 格式化后的会话时间
 */
export function formatSessionTime(time?: string): string {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()

  if (now.diff(d, 'day') === 0) return d.format('HH:mm')
  if (now.diff(d, 'day') === 1) return '昨天'
  if (now.diff(d, 'day') < 7) return `${now.diff(d, 'day')}天前`
  return d.format('MM-DD')
}

/**
 * 处理数组格式的时间（后端某些字段返回数组格式 [year, month, day, hour, minute, second]）
 * @param time 时间字符串或数组
 * @returns 格式化后的日期时间字符串
 */
export function formatArrayTime(time: string | number[] | null | undefined): string {
  if (!time) return '-'

  if (Array.isArray(time)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = time
    return dayjs(new Date(year, month - 1, day, hour, minute, second)).format('YYYY-MM-DD HH:mm')
  }

  return dayjs(time).format('YYYY-MM-DD HH:mm')
}