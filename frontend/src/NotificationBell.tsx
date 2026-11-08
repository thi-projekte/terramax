import { useState } from 'react'
import type { WeeklyAlerts } from './alerts'
import './NotificationBell.css'

type Props = {
    weekly: WeeklyAlerts
}

function NotificationBell({ weekly }: Props) {
    const [open, setOpen] = useState(false)

    return (
        <div className="bell">
        <button
            className="bell-button"
    aria-expanded={open}
    onClick={() => setOpen(!open)}
>
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        <span>Notifications</span>
        </button>

    {open && (
        <div className="bell-panel">
        <div className="bell-header">
        <span className="bell-heading">Notifications</span>
            <span className="bell-subheading">
        {weekly.territory} · delivered on {weekly.deliveredOn.toDateString()}
        </span>
        </div>
        <ol className="bell-list">
        {weekly.alerts.map((alert, index) => (
                <li key={alert.id} className="bell-item">
            <span className="bell-rank">{index + 1}</span>
            <div>
            <p className="bell-reason">{alert.reason}</p>
                <p className="bell-meta">{alert.territory} · Source: {alert.source}</p>
    </div>
    </li>
    ))}
        </ol>
        </div>
    )}
    </div>
)
}

export default NotificationBell