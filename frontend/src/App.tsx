
import keycloak from './keycloak'
import AustriaMap from './AustriaMap'

function App() {
  return (
    <main>
      <h1>Terramax</h1>
      <p>Logged in as {keycloak.tokenParsed?.preferred_username}</p>
      <button onClick={() => keycloak.logout()}>Sign Out</button>

      <h2>Agricultural Market – Austria</h2>
      <AustriaMap />
    </main>
  )
}

export default App
