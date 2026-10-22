import keycloak from './keycloak'

function App() {
  return (
      <main>
        <h1>Terramax</h1>
        <p>Angemeldet als {keycloak.tokenParsed?.preferred_username}</p>
      </main>
  )
}
export default App
