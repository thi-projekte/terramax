import { useState } from 'react'
import keycloak from './keycloak'
import { getWeeklyAlerts } from './alerts'
import { loadDeliveryDay, saveDeliveryDay } from './settings'
import NotificationBell from './NotificationBell'
import SettingsMenu from './SettingsMenu'
import ProfileMenu from './ProfileMenu'

function App() {
    const username = keycloak.tokenParsed?.preferred_username ?? ''
    const [deliveryDay, setDeliveryDay] = useState(() => loadDeliveryDay(username))
    const weekly = getWeeklyAlerts(username, deliveryDay)

    function changeDeliveryDay(day: number) {
        setDeliveryDay(day)
        saveDeliveryDay(username, day)
    }

    return (
      <main>
          <header className="top-bar">
              <h1>Terramax</h1>
              <div className="top-bar-actions">
                  <NotificationBell weekly={weekly} />
                  <SettingsMenu deliveryDay={deliveryDay} onDeliveryDayChange={changeDeliveryDay} />
                  <ProfileMenu username={username} onSignOut={() => keycloak.logout()} />
              </div>
          </header>
      </main>
  )
}
export default App
