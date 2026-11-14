import { useState } from 'react'
import { weekdays } from './settings'
import './NotificationBell.css'
import './SettingsMenu.css'

type Props = {
    deliveryDay: number
    onDeliveryDayChange: (day: number) => void
}

function SettingsMenu({ deliveryDay, onDeliveryDayChange }: Props) {
    const [open, setOpen] = useState(false)

    return (
        <div className="bell">
            <button
                className="bell-button"
                aria-expanded={open}
                onClick={() => setOpen(!open)}
            >
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
                    <circle cx="12" cy="12" r="3" />
                </svg>
                <span>Settings</span>
            </button>

            {open && (
                <div className="bell-panel">
                    <div className="bell-header">
                        <span className="bell-heading">Settings</span>
                    </div>
                    <div className="settings-section">
                        <span className="settings-title">Alerts</span>
                        <label className="settings-row">
                            Delivery day
                            <select
                                value={deliveryDay}
                                onChange={(event) => onDeliveryDayChange(Number(event.target.value))}
                            >
                                {weekdays.map((name, index) => (
                                    <option key={name} value={index}>{name}</option>
                                ))}
                            </select>
                        </label>
                    </div>
                </div>
            )}
        </div>
    )
}

export default SettingsMenu