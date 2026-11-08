import { useState } from 'react'
import './NotificationBell.css'
import './ProfileMenu.css'

type Props = {
    username: string
    onSignOut: () => void
}

function ProfileMenu({ username, onSignOut }: Props) {
    const [open, setOpen] = useState(false)

    return (
        <div className="bell">
            <button
                className="bell-button"
                aria-expanded={open}
                onClick={() => setOpen(!open)}
            >
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="8" r="5" />
                    <path d="M20 21a8 8 0 0 0-16 0" />
                </svg>
                <span>Me</span>
            </button>

            {open && (
                <div className="bell-panel">
                    <div className="bell-header">
                        <span className="bell-subheading">Logged in as</span>
                        <span className="bell-heading">{username}</span>
                    </div>
                    <div className="profile-actions">
                        <button className="profile-signout" onClick={onSignOut}>
                            Sign Out
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}

export default ProfileMenu