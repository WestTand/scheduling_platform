export const isPreferenceDateInRange = (preferenceDate: string, from: string, to: string) =>
  (!from || preferenceDate >= from) && (!to || preferenceDate <= to)

export const isDateRangeInverted = (from: string, to: string) => Boolean(from) && Boolean(to) && from > to
