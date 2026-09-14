import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './app/App';
import { SesionProvider } from './features/acceso/estado/SesionProvider';
import './shared/styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <SesionProvider>
      <App />
    </SesionProvider>
  </React.StrictMode>,
);
