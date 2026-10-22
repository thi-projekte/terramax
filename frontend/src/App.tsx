import keycloak from './keycloak'

function App() {
  return (
      <main>
        <h1>Terramax</h1>
        <p>Logged in as {keycloak.tokenParsed?.preferred_username}</p>
          <button onClick={() => keycloak.logout()}>Sign Out</button>
      </main>
  )
}
export default App
