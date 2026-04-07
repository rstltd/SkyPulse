export function useExport() {
  const exportCsv = (filename: string, columns: { key: string; label: string }[], rows: any[]) => {
    if (!rows.length) return

    const bom = '\uFEFF'
    const header = columns.map(c => escapeCsvField(c.label)).join(',')
    const body = rows.map(row =>
      columns.map(c => escapeCsvField(row[c.key])).join(',')
    ).join('\n')

    const blob = new Blob([bom + header + '\n' + body], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }

  return { exportCsv }
}

function escapeCsvField(value: unknown): string {
  if (value == null) return ''
  const str = String(value)
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return '"' + str.replace(/"/g, '""') + '"'
  }
  return str
}
