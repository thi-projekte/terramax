import keycloak from './keycloak'
import RoleSections from './RoleSections'

function App() {
  return (
      <main>
        <h1>Terramax</h1>
        <p>Logged in as {keycloak.tokenParsed?.preferred_username}</p>
          <button onClick={() => keycloak.logout()}>Sign Out</button>
        <RoleSections />
      </main>
  )
}
export default App
