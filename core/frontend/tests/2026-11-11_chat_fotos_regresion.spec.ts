import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  // La regresión verifica la aplicación sin depender de la descarga de fuentes externas.
  await page.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
});

test('el sondeo y un mensaje entrante conservan la lectura incluso cerca del final', async ({ page }) => {
  let calls = 0;
  let incoming = false;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    let data: unknown = {};
    if (url.pathname.endsWith('/capacidades')) {
      data = { proveedor: 'GEMINI', modelo: 'prueba', configurado: true, contextoRemoto: true };
    } else if (url.pathname.endsWith('/conversacion')) {
      calls++;
      const initial = url.searchParams.get('cursor') === '0';
      data = {
        idConversacion: 'prueba', cursor: incoming ? 41 : 40, hayMas: false,
        ejecuciones: [], acuerdos: [],
        mensajes: initial ? Array.from({ length: 40 }, (_, i) => ({
          id: `m${i}`, secuencia: i + 1, autor: 'ana', nombreAutor: 'Ana', emisor: 'USUARIO',
          texto: `Consulta histórica ${i}: ` + 'Descripción del modelo para revisar en equipo. '.repeat(5),
          modalidad: 'TEXTO', creadoEn: '2026-11-11T00:00:00Z',
        })) : incoming && url.searchParams.get('cursor') === '40' ? [{
          id: 'nuevo', secuencia: 41, autor: 'luis', nombreAutor: 'Luis', emisor: 'USUARIO',
          texto: 'Consulta nueva de Luis visible para todos.', modalidad: 'TEXTO', creadoEn: '2026-11-11T00:01:00Z',
        }] : [],
      };
    }
    await route.fulfill({ json: data });
  });
  await page.route('**/prueba-chat', route => route.fulfill({ contentType: 'text/html', body: `
    <div id="fixture" style="height:850px;width:600px;display:flex;flex-direction:row"></div>
    <script type="module">
      import React from '/node_modules/.vite/deps/react.js';
      import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';
      import RefreshRuntime from '/@react-refresh';
      import '/src/shared/styles.css';
      RefreshRuntime.injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {};
      window.$RefreshSig$ = () => type => type;
      window.__vite_plugin_react_preamble_installed__ = true;
      const {PanelAsistenteIa} = await import('/src/features/ia/components/PanelAsistenteIa.tsx');
      ReactDOM.createRoot(document.getElementById('fixture')).render(React.createElement(PanelAsistenteIa, {
        modelo:'m', revision:1, disabled:false, propuestaPendiente:null,
        cerrar:()=>{}, alAplicarPropuesta:async()=>{}, abrirImagen:()=>{}
      }));
    </script>` }));
  await page.goto('/prueba-chat');
  const list = page.locator('.ia-agent-messages');
  await expect(list.locator('.ia-chat-text')).toHaveCount(40);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
  });
  expect(await list.evaluate(el => el.scrollHeight - el.clientHeight)).toBeGreaterThan(500);
  await list.evaluate(el => el.scrollTop = el.scrollHeight - el.clientHeight - 35);
  const before = await list.evaluate(el => el.scrollTop);
  const previousCalls = calls;
  incoming = true;
  await expect.poll(() => calls).toBeGreaterThan(previousCalls + 1);
  await expect(list.getByText('Consulta nueva de Luis visible para todos.')).toHaveCount(1);
  expect(Math.abs(await list.evaluate(el => el.scrollTop) - before)).toBeLessThan(2);
  await expect(list.getByText('Luis', { exact: true })).toHaveCount(1);
  await list.evaluate(el => el.scrollTop = 120);
  const callsAtTop = calls;
  await expect.poll(() => calls).toBeGreaterThan(callsAtTop + 1);
  expect(Math.abs(await list.evaluate(el => el.scrollTop) - 120)).toBeLessThan(2);
  await page.getByRole('button', { name: 'Ver últimos mensajes' }).click();
  await expect.poll(() => list.evaluate(el => el.scrollHeight - el.clientHeight - el.scrollTop)).toBeLessThan(2);
});

test('una foto original de 12 MB se prepara y se envía reducida', async ({ page }) => {
  let receivedBytes = 0;
  await page.route('**/api/**', async route => {
    if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue();
    if (route.request().url().endsWith('/csrf')) {
      await route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    } else if (route.request().method() === 'POST') {
      receivedBytes = route.request().postDataBuffer()?.length ?? 0;
      await route.fulfill({ json: { ejecucion: { id: 'trabajo' } } });
    } else {
      await route.fulfill({ json: { estado: 'COMPLETADO', resultado: { idPropuesta: 'p', estado: 'LISTO' } } });
    }
  });
  await page.route('**/prueba-foto', route => route.fulfill({ contentType: 'text/html', body: `
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
      const {SubirFotoPizarraModal} = await import('/src/features/ia/components/SubirFotoPizarraModal.tsx');
      ReactDOM.createRoot(document.getElementById('fixture')).render(React.createElement(SubirFotoPizarraModal, {
        modelo:'m', cerrar:()=>{}, alGenerar:async()=>{}
      }));
    </script>` }));
  await page.goto('/prueba-foto');
  const encoded = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 100; canvas.height = 100;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#176b52'; context.fillRect(0, 0, 100, 100);
    return canvas.toDataURL('image/png').split(',')[1];
  });
  const png = Buffer.from(encoded, 'base64');
  const original = Buffer.alloc(12 * 1024 * 1024);
  png.copy(original);
  await page.locator('input[type=file]').setInputFiles({ name: 'camara.png', mimeType: 'image/png', buffer: original });
  const send = page.getByRole('button', { name: 'Analizar y generar propuesta' });
  await expect(send).toBeEnabled();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await send.click();
  await expect.poll(() => receivedBytes).toBeGreaterThan(0);
  expect(receivedBytes).toBeLessThan(4 * 1024 * 1024);
});
