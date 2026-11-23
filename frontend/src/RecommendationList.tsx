import type { Recommendation } from './recommendationData'

type Props = {
  items: Recommendation[] | null
  onSelect: (item: Recommendation) => void
}

export default function RecommendationList({ items, onSelect }: Props) {
  return (
    <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid #ddd' }}>
      <details>
        <summary style={{ cursor: 'pointer', fontWeight: 700, color: '#222' }}>
          Empfehlungen (Top 10)
        </summary>
        <div style={{ marginTop: '8px', fontSize: '12px', color: '#222' }}>
          {items === null && <div>Noch nicht berechnet</div>}
          {items?.map((item) => (
            <button
              key={item.h3Index}
              type="button"
              onClick={() => onSelect(item)}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                background: 'none',
                border: 'none',
                borderBottom: '0.5px solid #d1d5db',
                padding: '6px 0',
                font: 'inherit',
                cursor: 'pointer',
                color: '#222'
              }}
            >
              <strong>{item.rank}.</strong> {Math.round(item.hectares)} ha · {item.points.toFixed(1)} Punkte
              {item.distanceKm !== null ? ` · ${Math.round(item.distanceKm)} km` : ''}
            </button>
          ))}
        </div>
      </details>
    </div>
  )
}