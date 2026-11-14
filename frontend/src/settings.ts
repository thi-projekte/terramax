export const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

const defaultDeliveryDay = 1

function storageKey(username: string): string {
    return `deliveryDay:${username}`
}

export function loadDeliveryDay(username: string): number {
    const saved = localStorage.getItem(storageKey(username))
    return saved === null ? defaultDeliveryDay : Number(saved)
}

export function saveDeliveryDay(username: string, day: number): void {
    localStorage.setItem(storageKey(username), String(day))
}