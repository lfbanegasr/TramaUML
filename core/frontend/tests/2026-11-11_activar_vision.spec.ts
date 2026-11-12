import { expect, test } from '@playwright/test';

for (const falla of [false, true]) {
test(falla ? 'una cuota agotada no se muestra como servicio activo' : 'activa imágenes sin pedir otra clave y solo confirma después de la prueba del servidor', async ({ page }) => {
  let activated = false;
  await page.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/csrf')) return route.fulfill({ json: { token: 'ficticio', header: 'X-CSRF' } });
    if (path.endsWith('/imagenes-gemini/activar')) {
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['x-csrf']).toBe('ficticio');
      expect(route.request().postData() ?? '').not.toContain('clave');
      if (falla) return route.fulfill({ status: 503, json: { mensaje: 'Gemini agotó la cuota disponible.' } });
      activated = true;
      return route.fulfill({ json: { id: 'vision', estado: 'ACTIVO', configuracion: { modelo: 'gemini-3.8-flash' } } });
    }
    return route.fulfill({ json: {
      servicios: activated ? [{ id: 'vision', nombre: 'Gemini imágenes', proveedor: 'GEMINI', tipoServicio: 'IA_IMAGEN',
        estado: 'ACTIVO', tieneCredencial: true, configuracion: { modelo: 'gemini-3.8-flash' } }] : [], pagina: 0, tamano: 20, total: activated ? 1 : 0,
    } });
  });
  await page.route('**/prueba-servicios', route => route.fulfill({ contentType: 'text/html', body: `
    <div id="fixture"></div>
    <script type="module">
      import React from '/node_modules/.vite/deps/react.js';
      import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';
      import RefreshRuntime from '/@react-refresh';
      import '/src/shared/styles.css';
      RefreshRuntime.injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {};
      window.$RefreshSig$ = () => type => type;
      window.__vite_plugin_react_preamble_installed__ = true;
      const {ServiciosPanel} = await import('/src/features/administracion/components/ServiciosPanel.tsx');
      ReactDOM.createRoot(document.getElementById('fixture')).render(React.createElement(ServiciosPanel, {onEdicionChange:()=>{}}));
    </script>` }));
  await page.goto('/prueba-servicios');
  await expect(page.getByText('No hay servicios con esos filtros.')).toBeVisible();
  await page.getByRole('button', { name: 'Habilitar imágenes con Gemini' }).click();
  if (falla) {
    await expect(page.getByRole('alert')).toContainText('cuota');
    await expect(page.getByRole('status')).toHaveCount(0);
    expect(activated).toBe(false);
    return;
  }
  await expect(page.getByRole('status')).toContainText('Gemini analizó correctamente la imagen de prueba');
  await expect(page.locator('tbody .badge.active')).toHaveText('ACTIVO');
  expect(activated).toBe(true);
  expect(await page.locator('input[type=password]').count()).toBe(0);
});
}
