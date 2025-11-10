// CommonJS copy of quantity helpers for a lightweight node test runner
function parseQuantityString(s) {
  const m = s.trim().match(/^([0-9]+)\s+([0-9]+)\/([0-9]+)\b/)
  if (m) {
    const whole = parseInt(m[1], 10)
    const num = parseInt(m[2], 10)
    const den = parseInt(m[3], 10)
    if (den !== 0) return { value: whole + num / den, raw: m[0] }
  }
  const m2 = s.trim().match(/^([0-9]+)\/([0-9]+)\b/)
  if (m2) {
    const num = parseInt(m2[1], 10)
    const den = parseInt(m2[2], 10)
    if (den !== 0) return { value: num / den, raw: m2[0] }
  }
  const m3 = s.trim().match(/^([0-9]*\.?[0-9]+)\b/)
  if (m3) return { value: parseFloat(m3[1]), raw: m3[0] }
  return { value: null, raw: null }
}

function formatQuantity(n) {
  if (!isFinite(n)) return String(n)
  const absN = Math.abs(n)
  const sign = n < 0 ? '-' : ''
  if (Number.isInteger(absN)) return sign + String(Math.round(absN))
  const whole = Math.floor(absN)
  const frac = absN - whole
  const denominators = [2, 3, 4, 8, 16]
  let best = { den: 1, num: 0, err: 1 }
  for (const d of denominators) {
    const num = Math.round(frac * d)
    const approx = num / d
    const err = Math.abs(frac - approx)
    if (err < best.err) best = { den: d, num, err }
  }
  const ERR_THRESHOLD = 0.035
  if (best.num !== 0 && best.err <= ERR_THRESHOLD) {
    const num = best.num
    const den = best.den
    const gcd = (a, b) => (b === 0 ? a : gcd(b, a % b))
    const g = gcd(num, den)
    const rnum = num / g
    const rden = den / g
    if (whole === 0) return sign + `${rnum}/${rden}`
    return sign + `${whole} ${rnum}/${rden}`
  }
  return sign + parseFloat(n.toFixed(2)).toString()
}

function scaleIngredient(ing, multiplier) {
  if (ing == null) return ing
  if (typeof ing === 'string') {
    const { value, raw } = parseQuantityString(ing)
    if (value != null && raw) {
      const scaled = value * multiplier
      const scaledStr = formatQuantity(scaled)
      return ing.replace(raw, scaledStr)
    }
    return ing
  }
  if (typeof ing === 'object') {
    const copy = { ...ing }
    if (typeof copy.amount === 'number') copy.amount = copy.amount * multiplier
    if (typeof copy.quantity === 'number') copy.quantity = copy.quantity * multiplier
    if (typeof copy.value === 'number') copy.value = copy.value * multiplier
    return copy
  }
  return ing
}

module.exports = { parseQuantityString, formatQuantity, scaleIngredient }
