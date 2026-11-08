export type Alert = {
    id: number
    territory: string
    reason: string
    source: string
    priority: number
}

// mock data
const mockAlerts: Alert[] = [
    { id: 1, territory: 'Ingolstadt North', reason: 'New potential customer: Huber Agrar GmbH registered a new farm', source: 'Public business registry', priority: 1 },
    { id: 2, territory: 'Ingolstadt North', reason: 'Existing customer Meier KG viewed 3 tractor models online', source: 'Website analytics', priority: 2 },
    { id: 3, territory: 'Ingolstadt North', reason: 'Service contract of Bauer Landtechnik expires in 4 weeks', source: 'CRM', priority: 3 },
    { id: 4, territory: 'Ingolstadt North', reason: 'Existing customer Schmid ordered spare parts twice this month', source: 'Service records', priority: 4 },
    { id: 5, territory: 'Ingolstadt North', reason: 'Farm Wagner expanded its land by 40 hectares', source: 'Market data', priority: 5 },
    { id: 6, territory: 'Ingolstadt North', reason: 'Customer Fischer has not been contacted for 6 months', source: 'CRM', priority: 6 },
    { id: 7, territory: 'Eichstätt', reason: 'New potential customer: Hofgut Altmühl opened a second site', source: 'Public business registry', priority: 1 },
    { id: 8, territory: 'Eichstätt', reason: 'Existing customer Weber requested a quote online', source: 'CRM', priority: 2 },
]

const territoryByUser: Record<string, string> = {
    'testuser': 'Ingolstadt North',
    'sales': 'Eichstätt',
    'dealer': 'Ingolstadt North'
}

const defaultTerritory = 'Eichstätt'

export function getTerritory(username: string): string {
    return territoryByUser[username] ?? defaultTerritory
}

export function getLastDeliveryDate(deliveryDay: number, today = new Date()): Date {
    const date = new Date(today)
    date.setHours(0, 0, 0, 0)
    const daysSinceDelivery = (date.getDay() - deliveryDay + 7) % 7
    date.setDate(date.getDate() - daysSinceDelivery)
    return date
}

export type WeeklyAlerts = {
    territory: string
    deliveredOn: Date
    alerts: Alert[]
}

export function getWeeklyAlerts(username: string, deliveryDay: number, max = 5): WeeklyAlerts {
    const territory = getTerritory(username)
    const alerts = mockAlerts
        .filter((alert) => alert.territory === territory)
        .sort((a, b) => a.priority - b.priority)
        .slice(0, max)
    return { territory, deliveredOn: getLastDeliveryDate(deliveryDay), alerts }
}