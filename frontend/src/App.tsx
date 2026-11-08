
import keycloak from './keycloak'
import AustriaMap from './AustriaMap'
import Besuchstour from './tour/Besuchstour'
import { useState } from 'react'
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
  const darfTour = darfTourSehen()
  const [tab, setTab] = useState<Tab>(startTab)

  const wechseln = (neu: Tab) => {
    setTab(neu)
    window.history.replaceState(null, '', `#${neu}`)
  }

  return (
    <main>
      <h1>Terramax</h1>
      <p>Logged in as {keycloak.tokenParsed?.preferred_username}</p>
      <button onClick={() => keycloak.logout()}>Sign Out</button>

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
