import { useState } from 'react'
import keycloak from './keycloak'
import { getWeeklyAlerts } from './alerts'
import { loadDeliveryDay, saveDeliveryDay } from './settings'
import NotificationBell from './NotificationBell'
import SettingsMenu from './SettingsMenu'
import ProfileMenu from './ProfileMenu'
import RoleSections from './RoleSections'
import AustriaMap from './AustriaMap'
import Besuchstour from './tour/Besuchstour'
import './AppTabs.css'

type Tab = 'karte' | 'tour'

// Reiter oben: Marktkarte und Besuchstour liegen nicht untereinander, man sieht immer nur einen Bereich.
// Der gewählte Reiter steht im URL-Hash (#karte / #tour), damit er nach dem Neuladen erhalten bleibt.
// Erst nach dem Login abfragen (App wird erst nach keycloak.init gerendert, siehe main.tsx)
const darfTourSehen = () => keycloak.hasRealmRole('sales_professional')

function startTab(): Tab {
  const hash = window.location.hash.slice(1)
  if (hash === 'karte' || (hash === 'tour' && darfTourSehen())) return hash
  // Reine Sales Professionals starten in der Besuchstour, alle anderen auf der Karte
  return darfTourSehen() && !keycloak.hasRealmRole('dealer_principal') ? 'tour' : 'karte'
}

function App() {
    const username = keycloak.tokenParsed?.preferred_username ?? ''
    const [deliveryDay, setDeliveryDay] = useState(() => loadDeliveryDay(username))
    const weekly = getWeeklyAlerts(username, deliveryDay)

    function changeDeliveryDay(day: number) {
        setDeliveryDay(day)
        saveDeliveryDay(username, day)
    }
  const darfTour = darfTourSehen()
  const [tab, setTab] = useState<Tab>(startTab)

  const wechseln = (neu: Tab) => {
    setTab(neu)
    window.history.replaceState(null, '', `#${neu}`)
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

      <RoleSections />

      {darfTour && (
        <nav className="app-tabs" aria-label="Bereiche">
          <button type="button" aria-current={tab === 'karte'} onClick={() => wechseln('karte')}>
            Marktkarte
          </button>
          <button type="button" aria-current={tab === 'tour'} onClick={() => wechseln('tour')}>
            Besuchstour
          </button>
        </nav>
      )}

      {tab === 'karte' && (
        <>
          <h2>Agricultural Market – Austria</h2>
          <AustriaMap />
        </>
      )}

      {tab === 'tour' && darfTour && <Besuchstour />}
    </main>
  )
}
export default App
