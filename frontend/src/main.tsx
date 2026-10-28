import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import keycloak from './keycloak'

keycloak.onTokenExpired = () => {
    console.log('Token expired, refreshing…')
    keycloak.updateToken(30)
        .then(() => console.log('Token refreshed'))
        .catch(() => keycloak.login())
}

keycloak.init({ onLoad: 'login-required' }).then((authenticated) => {
    if (authenticated) {
        createRoot(document.getElementById('root')!).render(
            <StrictMode>
                <App />
            </StrictMode>,
        )
    }
})
