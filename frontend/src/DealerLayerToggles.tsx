import { useState } from 'react'
import type { Map as MapboxMap } from 'mapbox-gl'

type Group = {
    key: string
    label: string
    color: string
    layers: string[]
}

const GROUPS: Group[] = [
    {
        key: 'territories',
        label: 'Vertriebsgebiete',
        color: '#3b82f6',
        layers: ['territories-fill', 'territories-lines', 'territories-labels']
    },
    {
        key: 'customers',
        label: 'Kunden (CRM-Export)',
        color: '#facc15',
        layers: ['customer-cells-fill', 'customer-cells-lines', 'customers-circles']
    },
    {
        key: 'whiteSpots',
        label: 'Weiße Flecken (Ackerfläche ohne Kunden)',
        color: '#ffffff',
        layers: ['white-spots-fill', 'white-spots-lines']
    }
]

type Props = { mapRef: { current: MapboxMap | null } }

export default function DealerLayerToggles({ mapRef }: Props) {
    const [visible, setVisible] = useState<Record<string, boolean>>({
        territories: true,
        customers: true,
        whiteSpots: true
    })

    const toggle = (group: Group, checked: boolean) => {
        setVisible((current) => ({ ...current, [group.key]: checked }))
        const map = mapRef.current
        if (!map) return
        for (const id of group.layers) {
            if (map.getLayer(id)) {
                map.setLayoutProperty(id, 'visibility', checked ? 'visible' : 'none')
            }
        }
    }

    return (
        <>
            <div style={{ marginTop: '12px' }}>
                <strong>Dealer Principal: Mein Gebiet</strong>
            </div>
            {GROUPS.map((group) => (
                <label
                    key={group.key}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        marginTop: '7px',
                        cursor: 'pointer'
                    }}
                >
                    <input
                        type="checkbox"
                        checked={visible[group.key]}
                        onChange={(event) => toggle(group, event.target.checked)}
                        style={{ margin: 0 }}
                    />
                    <span
                        style={{
                            display: 'inline-block',
                            width: '12px',
                            height: '12px',
                            flexShrink: 0,
                            borderRadius: '50%',
                            background: group.color,
                            border: '2px solid #fff',
                            boxShadow: '0 0 0 1px #aaa'
                        }}
                    />
                    {group.label}
                </label>
            ))}
        </>
    )
}