import { test, expect, type Page } from '@playwright/test';

async function abrirComponente(
  page: Page,
  componente: string,
  props: string,
  panelLateral = false,
) {
  page.on('pageerror', (error) => console.error('Error de interfaz:', error.message));
  await page.route('**/prueba-flujo', (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: `
    <meta charset="utf-8"><div id="fixture"></div>
    <script type="module">
      import React from '/node_modules/.vite/deps/react.js';
      import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';
      import RefreshRuntime from '/@react-refresh';
      import '/src/shared/styles.css';
      RefreshRuntime.injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {};
      window.$RefreshSig$ = () => type => type;
      window.__vite_plugin_react_preamble_installed__ = true;
      const {${componente.split('/').at(-1)}} = await import('/src/features/${componente}.tsx');
      const panel = React.createElement(${componente.split('/').at(-1)}, ${props});
      const contenido = ${
        panelLateral
          ? `React.createElement('aside', {
        className:'editor-sidebar-right', style:{height:'calc(100dvh - 96px)',width:'min(420px,100vw)'}},
        React.createElement('div',{className:'sidebar-tabs-right',style:{height:'40px'}},'Propiedades · Asistente IA · Validación'),
        React.createElement('div',{className:'sidebar-right-body'},panel))`
          : 'panel'
      };
      ReactDOM.createRoot(document.getElementById('fixture')).render(contenido);
    </script>`,
    }),
  );
  await page.goto('/prueba-flujo');
  await expect(
    panelLateral
      ? page.getByRole('complementary', { name: 'Asistente Inteligente de Modelado' })
      : page.getByRole('dialog'),
  ).toBeVisible({ timeout: 15000 });
}

const versiones = [
  { id: 'actual', numeroVersion: 2, revision: 2, estadoValidacion: 'VALIDO', versionActual: true },
  {
    id: 'anterior',
    numeroVersion: 1,
    revision: 1,
    estadoValidacion: 'SIN_VALIDAR',
    versionActual: false,
  },
];
function configuracion(version = 'actual', validada = true) {
  return {
    id: null,
    idVersionModelo: version,
    numeroVersion: version === 'actual' ? 2 : 1,
    revisionVersion: version === 'actual' ? 2 : 1,
    versionActual: version === 'actual',
    estadoVersion: validada ? 'VALIDO' : 'SIN_VALIDAR',
    estado: 'BORRADOR',
    nombre: 'Backend de prueba',
    nombreProyecto: 'prueba',
    idGrupo: 'com.empresa',
    idArtefacto: 'prueba',
    paqueteBase: 'com.empresa.prueba',
    versionInicial: '1.0.0',
    versionJava: '21',
    versionSpringBoot: '3.5.16',
    herramientaMigracion: 'FLYWAY',
    generarRepositorios: true,
    generarServicios: true,
    generarControladores: true,
    generarDtos: true,
    exponerRest: true,
    versionesDisponibles: versiones,
    clases: [],
    mapeosTipo: [],
    dependencias: [],
    mensajes: validada
      ? []
      : [
          {
            nivel: 'ADVERTENCIA',
            codigo: 'VERSION_SIN_VALIDAR',
            mensaje: 'Debe validarse esta versión.',
          },
        ],
    puedeGenerar: false,
  };
}

const informe = {
  id: 'validacion',
  idGeneracion: 'g1',
  estado: 'VALIDO',
  puedeDescargar: true,
  comprobacionesAprobadas: 5,
  cantidadErrores: 0,
  cantidadAdvertencias: 0,
  hallazgos: [],
  iniciadoEn: '2026-11-13T10:00:00Z',
  completadoEn: '2026-11-13T10:00:01Z',
  registroCompilacion: 'BUILD SUCCESS',
  sha256: 'prueba',
};

for (const guardarAntes of [false, true]) {
  test(`prepara entidades y genera ${guardarAntes ? 'tras guardar' : 'directamente'} sin editar el diagrama`, async ({
    page,
  }) => {
    const clase = {
      idClase: 'clase',
      nombre: 'Cliente',
      seleccionada: true,
      esPersistente: false,
      exponerRest: true,
      nombreTabla: 'cliente',
      nombreEntidad: 'Cliente',
      operaciones: [],
    };
    let cfg = {
      ...configuracion('actual', false),
      clases: [clase],
      mensajes: [
        {
          nivel: 'ERROR',
          codigo: 'SIN_ENTIDADES_PERSISTENTES',
          mensaje: 'Selecciona al menos una clase persistente para generar código.',
        },
      ],
    };
    let guardados = 0,
      generado = false;
    const inesperadas: string[] = [];
    await page.route('**/api/**', async (route) => {
      const req = route.request(),
        url = new URL(req.url());
      if (!url.pathname.startsWith('/api/')) return route.continue();
      if (url.pathname.endsWith('/csrf'))
        return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
      if (url.pathname.endsWith('/configuracion')) {
        if (req.method() === 'PUT') {
          guardados++;
          const cuerpo = req.postDataJSON();
          expect(cuerpo.clases[0].esPersistente).toBe(true);
          cfg = {
            ...cfg,
            ...cuerpo,
            estado: 'VALIDO',
            mensajes: [
              {
                nivel: 'ADVERTENCIA',
                codigo: 'CLASE_SIN_ID',
                mensaje: 'Se generará un identificador técnico Long en el backend.',
              },
            ],
          };
        }
        return route.fulfill({
          json: { ...cfg, id: 'cfg-' + guardados, puedeGenerar: guardados > 0 },
        });
      }
      if (url.pathname.endsWith('/generar')) {
        generado = true;
        return route.fulfill({
          json: {
            idGeneracion: 'g',
            idConfiguracion: 'cfg',
            idVersionModelo: 'actual',
            nombreArchivo: 'backend.zip',
            totalClases: 1,
            totalOperaciones: 0,
            totalArchivosGenerados: 12,
          },
        });
      }
      if (url.pathname.endsWith('/generacion'))
        return route.fulfill({
          json: generado
            ? [
                {
                  idGeneracion: 'g',
                  idConfiguracion: 'cfg',
                  idVersionModelo: 'actual',
                  numeroVersion: 2,
                  nombreArchivo: 'backend.zip',
                  creadoEn: '2026-11-13T10:00:00Z',
                  validacion: null,
                  puedeValidar: true,
                },
              ]
            : [],
        });
      inesperadas.push(url.pathname);
      return route.fulfill({
        status: 500,
        json: { mensaje: 'No se esperaba editar ni validar UML.' },
      });
    });
    await abrirComponente(
      page,
      'interoperabilidad/components/GenerarBackendDialog',
      `{modelo:'m',nombreModelo:'Prueba',cerrar:()=>{}}`,
    );
    if (guardarAntes) {
      await page.getByRole('button', { name: 'Guardar ajustes', exact: true }).click();
      await expect(page.getByText(/Ajustes guardados para la versi/)).toBeVisible();
    }
    await page.getByRole('button', { name: 'Generar backend', exact: true }).click();
    await expect(page.getByText('Proyecto generado desde una versión UML congelada')).toBeVisible();
    expect(guardados).toBe(1);
    expect(inesperadas).toEqual([]);
  });
}

test('muestra los bloqueos concretos y permite generar después de corregir los ajustes', async ({
  page,
}) => {
  let guardados = 0;
  let generados = 0;
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.endsWith('/csrf'))
      return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    if (url.pathname.endsWith('/configuracion')) {
      if (req.method() !== 'PUT') return route.fulfill({ json: configuracion() });
      guardados++;
      const cfg = req.postDataJSON();
      const correcto = cfg.generarRepositorios;
      return route.fulfill({
        json: {
          ...configuracion(),
          ...cfg,
          id: 'cfg',
          estado: correcto ? 'VALIDO' : 'INVALIDO',
          puedeGenerar: correcto,
          mensajes: correcto
            ? []
            : [
                {
                  nivel: 'ERROR',
                  codigo: 'CAPAS_INCOMPATIBLES',
                  mensaje: 'La capa de servicios requiere repositorios en este generador.',
                },
                {
                  nivel: 'ERROR',
                  codigo: 'REST_SIN_CONTROLADORES',
                  mensaje: 'OpenAPI/REST requiere la capa de controladores.',
                },
                {
                  nivel: 'ADVERTENCIA',
                  codigo: 'NEGOCIO_PENDIENTE',
                  mensaje: 'Las reglas de negocio requieren implementación.',
                },
              ],
        },
      });
    }
    if (url.pathname.endsWith('/generar')) {
      generados++;
      return route.fulfill({
        json: {
          idGeneracion: 'g',
          idConfiguracion: 'cfg',
          idVersionModelo: 'actual',
          nombreArchivo: 'backend.zip',
          totalClases: 1,
          totalOperaciones: 0,
          totalArchivosGenerados: 12,
        },
      });
    }
    return route.fulfill({ json: [] });
  });
  await abrirComponente(
    page,
    'interoperabilidad/components/GenerarBackendDialog',
    `{modelo:'m',nombreModelo:'Prueba',cerrar:()=>{}}`,
  );
  await page.getByText('Opciones avanzadas: capas, clases y tipos', { exact: true }).click();
  await page.getByLabel('Repositorios JPA', { exact: true }).uncheck();
  await page.getByRole('button', { name: 'Guardar ajustes', exact: true }).click();
  await expect(page.getByText(/Hay bloqueos pendientes en la configuración/)).toBeVisible();
  await expect(page.getByText(/Ya puedes generar el backend/)).toHaveCount(0);
  await page.getByRole('button', { name: 'Generar backend', exact: true }).click();
  const alerta = page.getByRole('alert').filter({ hasText: 'Los ajustes se guardaron' });
  await expect(alerta).toBeVisible();
  await expect(alerta).toBeFocused();
  await expect(alerta.getByRole('listitem')).toHaveCount(2);
  await expect(alerta).toContainText('CAPAS_INCOMPATIBLES');
  await expect(alerta).toContainText('La capa de servicios requiere repositorios');
  await expect(alerta).toContainText('REST_SIN_CONTROLADORES');
  await expect(alerta).not.toContainText('NEGOCIO_PENDIENTE');
  expect(generados).toBe(0);
  await page.getByLabel('Repositorios JPA', { exact: true }).check();
  await page.getByRole('button', { name: 'Generar backend', exact: true }).click();
  await expect(page.getByText('Proyecto generado desde una versión UML congelada')).toBeVisible();
  await expect(alerta).toHaveCount(0);
  expect(generados).toBe(1);
  expect(guardados).toBe(3);
});

test('un ZIP aprobado se descarga durante otra comprobación y después de un fallo', async ({
  page,
}) => {
  let estado = 'VALIDO';
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.endsWith('/csrf'))
      return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    if (url.pathname.endsWith('/configuracion')) return route.fulfill({ json: configuracion() });
    if (url.pathname.endsWith('/generacion'))
      return route.fulfill({
        json: [
          {
            idGeneracion: 'aprobado',
            idConfiguracion: 'cfg',
            idVersionModelo: 'actual',
            numeroVersion: 2,
            nombreArchivo: 'backend-aprobado.zip',
            creadoEn: '2026-11-13T10:00:00Z',
            puedeValidar: true,
            validacion: informe,
          },
        ],
      });
    if (url.pathname.endsWith('/validaciones')) estado = 'EN_EJECUCION';
    if (url.pathname.endsWith('/descargar'))
      return route.fulfill({
        contentType: 'application/zip',
        body: Buffer.from('PK\x03\x04'),
        headers: { 'Content-Disposition': 'attachment; filename="backend-aprobado.zip"' },
      });
    return route.fulfill({ json: { ...informe, estado, puedeDescargar: true } });
  });
  await abrirComponente(
    page,
    'interoperabilidad/components/GenerarBackendDialog',
    `{modelo:'m',nombreModelo:'Prueba',cerrar:()=>{}}`,
  );
  await page.getByRole('button', { name: 'Volver a validar backend' }).click();
  await expect(page.getByRole('button', { name: 'Descargar ZIP validado' })).toBeEnabled();
  let descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar ZIP validado' }).click();
  expect((await descarga).suggestedFilename()).toBe('backend-aprobado.zip');
  estado = 'FALLIDO';
  await expect(page.getByText('Validación: FALLIDO', { exact: true })).toBeVisible();
  descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar ZIP validado' }).click();
  expect((await descarga).suggestedFilename()).toBe('backend-aprobado.zip');
});

for (const movil of [false, true]) {
  test(`el chat reserva espacio para mensajes y permite ampliarlo${movil ? ' en móvil' : ''}`, async ({
    page,
  }) => {
    await page.setViewportSize(movil ? { width: 390, height: 740 } : { width: 1280, height: 720 });
    await page.route('https://fonts.googleapis.com/**', (route) =>
      route.fulfill({ contentType: 'text/css', body: '' }),
    );
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      if (!url.pathname.startsWith('/api/')) return route.continue();
      if (url.pathname.endsWith('/capacidades'))
        return route.fulfill({
          json: { proveedor: 'GEMINI', modelo: 'prueba', configurado: true, contextoRemoto: true },
        });
      await route.fulfill({
        json: {
          idConversacion: 'chat',
          cursor: 20,
          hayMas: false,
          acuerdos: [],
          ejecuciones: Array.from({ length: 12 }, (_, i) => ({
            id: 'error' + i,
            estado: 'FALLIDO',
            error: 'Proveedor no disponible. Revisa la conexión.',
          })),
          mensajes:
            url.searchParams.get('cursor') === '0'
              ? Array.from({ length: 20 }, (_, i) => ({
                  id: 'm' + i,
                  secuencia: i + 1,
                  autor: 'ana',
                  nombreAutor: 'Ana',
                  emisor: i % 2 ? 'ASISTENTE' : 'USUARIO',
                  texto: 'Mensaje ' + i + ': ' + 'Revisamos el diseño y sus relaciones. '.repeat(4),
                  modalidad: 'TEXTO',
                  creadoEn: '2026-11-13T10:00:00Z',
                }))
              : [],
        },
      });
    });
    await abrirComponente(
      page,
      'ia/components/PanelAsistenteIa',
      `{modelo:'m',revision:12,disabled:false,propuestaPendiente:null,cerrar:()=>{},alAplicarPropuesta:async()=>{},abrirImagen:()=>{}}`,
      true,
    );
    const lista = page.getByRole('log', { name: 'Mensajes del asistente' });
    await expect(lista.locator('.ia-chat-text')).toHaveCount(20);
    const altoPanel = (await page.locator('.editor-sidebar-right').boundingBox())!.height;
    const altoMensajes = (await lista.boundingBox())!.height;
    expect(altoMensajes).toBeGreaterThan(altoPanel * 0.5);
    await expect(page.getByText('12 solicitudes con error · Ver detalles')).toBeVisible();
    await expect(
      page.getByText('Proveedor no disponible. Revisa la conexión.', { exact: true }).first(),
    ).toBeHidden();
    await page.getByLabel('Mensaje al asistente').fill('Borrador que se conserva al ampliar.');
    await lista.evaluate((el) => {
      el.scrollTop = 120;
    });
    await page.screenshot({ path: `../../.qa/2026-11-13_chat_${movil ? 'movil' : 'panel'}.png` });
    await page.getByRole('button', { name: 'Ampliar chat', exact: true }).click();
    const dialogo = page.getByRole('dialog', { name: 'Asistente Inteligente de Modelado' });
    await expect(dialogo).toBeVisible();
    await expect(dialogo.getByLabel('Mensaje al asistente')).toHaveValue(
      'Borrador que se conserva al ampliar.',
    );
    expect((await dialogo.getByRole('log').boundingBox())!.height).toBeGreaterThan(
      altoMensajes + 40,
    );
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      movil ? 390 : 1280,
    );
    await page.screenshot({
      path: `../../.qa/2026-11-13_chat_ampliado_${movil ? 'movil' : 'escritorio'}.png`,
    });
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByLabel('Mensaje al asistente')).toHaveValue(
      'Borrador que se conserva al ampliar.',
    );
    expect(Math.abs((await lista.evaluate((el) => el.scrollTop)) - 120)).toBeLessThan(2);
  });
}

test('genera sin validar UML, conserva ajustes y permite regenerar y descargar', async ({
  page,
}) => {
  let validada = false;
  let generaciones: Record<string, unknown>[] = [];
  const guardadas: Record<string, unknown>[] = [];
  const versionesValidadas: string[] = [];
  await page.route('**/api/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.endsWith('/csrf'))
      return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    let datos: unknown = {};
    if (url.pathname.endsWith('/generacion/configuracion')) {
      if (req.method() === 'PUT') {
        const cfg = req.postDataJSON();
        guardadas.push(cfg);
        datos = {
          ...configuracion(cfg.idVersionModelo, validada),
          ...cfg,
          id: 'cfg',
          estado: 'VALIDO',
          puedeGenerar: true,
        };
      } else {
        const version = url.searchParams.get('version') ?? 'actual';
        datos = configuracion(version, version === 'actual' || validada);
      }
    } else if (url.pathname.endsWith('/validaciones') && !url.pathname.includes('/generacion/')) {
      versionesValidadas.push(url.searchParams.get('version') ?? '');
      validada = true;
      datos = { id: 'tarea', revision: 1 };
    } else if (url.pathname.endsWith('/validaciones/tarea')) {
      datos = { estado: 'DESACTUALIZADO', estado_resultado: 'VALIDO', hallazgos: [] };
    } else if (url.pathname.endsWith('/generacion/generar')) {
      const id = 'g' + (generaciones.length + 1);
      datos = {
        idGeneracion: id,
        idConfiguracion: 'cfg',
        idVersionModelo: 'anterior',
        idArchivoSalida: id,
        nombreArchivo: 'prueba-v1.zip',
        totalClases: 1,
        totalOperaciones: 0,
        totalArchivosGenerados: 12,
      };
      generaciones.unshift({
        ...(datos as object),
        numeroVersion: 1,
        creadoEn: '2026-11-13T10:00:00Z',
        validacion: null,
        puedeValidar: true,
      });
    } else if (url.pathname.endsWith('/generacion')) datos = generaciones;
    else if (url.pathname.endsWith('/validaciones'))
      datos = { ...informe, estado: 'EN_EJECUCION', puedeDescargar: false };
    else if (url.pathname.endsWith('/validacion')) datos = informe;
    else if (url.pathname.endsWith('/descargar')) {
      return route.fulfill({
        contentType: 'application/zip',
        body: Buffer.from('PK\x03\x04prueba'),
        headers: { 'Content-Disposition': 'attachment; filename="prueba-v1.zip"' },
      });
    }
    await route.fulfill({ json: datos });
  });
  await abrirComponente(
    page,
    'interoperabilidad/components/GenerarBackendDialog',
    `{modelo:'m', nombreModelo:'Prueba', modeloObj:{revision:2,clases:[],atributos:[],operaciones:[],relaciones:[],parametros:[]}, cerrar:()=>{}}`,
  );
  await page.getByLabel('Versión del modelo').selectOption('anterior');
  await page.getByLabel('Group ID').fill('bo.edu.personalizado');
  await page.getByRole('button', { name: 'Generar backend', exact: true }).click();
  await expect(page.getByText('Proyecto generado desde una versión UML congelada')).toBeVisible();
  expect(versionesValidadas).toEqual([]);
  expect(guardadas[0]).toMatchObject({
    idVersionModelo: 'anterior',
    idGrupo: 'bo.edu.personalizado',
  });
  await expect(page.getByLabel('Versión del modelo')).toHaveValue('anterior');
  await page.getByRole('button', { name: 'Volver a generar esta versión' }).click();
  await expect.poll(() => generaciones.length).toBe(2);
  expect(guardadas).toHaveLength(1);
  const descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Validar y descargar ZIP', exact: true }).click();
  expect((await descarga).suggestedFilename()).toBe('prueba-v1.zip');
  await expect(page.getByRole('button', { name: 'Descargar ZIP validado' })).toBeEnabled();
  await expect(page.locator('#gen-historial option:checked')).toContainText('ZIP APROBADO');
  await page.screenshot({ path: '../../.qa/2026-11-13_generacion_web.png', fullPage: true });
});

test('recupera una importación fallida, cambia la solicitud y cierra después de aplicar', async ({
  page,
}) => {
  const claves: string[] = [];
  let canceladas = 0;
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.endsWith('/csrf'))
      return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    if (url.pathname.endsWith('/cancelar')) canceladas++;
    if (url.pathname.endsWith('/aplicar'))
      return route.fulfill({ json: { estado: 'COMPLETADO', revision: 2 } });
    claves.push(url.searchParams.get('idSolicitud') ?? '');
    await route.fulfill({
      json: {
        idIntercambio: 'importacion-' + claves.length,
        estado: claves.length === 1 ? 'FALLIDO' : 'LISTO_PARA_REVISION',
        nombreArchivo: 'diagrama.xmi',
        estrategia: 'FUSIONAR',
        revisionBase: 1,
        revisionActual: 1,
        totalPaquetes: 0,
        totalClases: 1,
        totalAtributos: 0,
        totalOperaciones: 0,
        totalRelaciones: 0,
        advertencias: [],
        bloqueadores: [],
        operaciones: [],
        puedeAplicar: claves.length > 1,
        detalleError: claves.length === 1 ? 'Archivo XMI inválido.' : null,
      },
    });
  });
  await abrirComponente(
    page,
    'interoperabilidad/components/ImportarXmiDialog',
    `{modelo:'m', sesion:'s', cerrar:()=>document.getElementById('fixture').textContent='Cerrado', alAplicar:async()=>{}}`,
  );
  const subir = () =>
    page
      .locator('input[type=file]')
      .setInputFiles({ name: 'diagrama.xmi', mimeType: 'text/xml', buffer: Buffer.from('<xmi/>') });
  await subir();
  await page.getByRole('button', { name: 'Analizar archivo' }).click();
  await expect(page.getByRole('alert')).toContainText('Archivo XMI inválido');
  await page.getByRole('button', { name: 'Cambiar archivo o estrategia' }).click();
  await subir();
  await page.getByLabel('Estrategia de importación').selectOption('REEMPLAZAR');
  await page.getByRole('button', { name: 'Analizar archivo' }).click();
  await page.getByRole('button', { name: 'Aplicar importación' }).click();
  await expect(page.getByText('Importación aplicada y versionada')).toBeVisible();
  expect(new Set(claves).size).toBe(2);
  await page.getByRole('button', { name: 'Cerrar diálogo' }).click();
  await expect(page.getByText('Cerrado')).toBeVisible();
  expect(canceladas).toBe(0);
});

test('conserva la clave de una imagen al recuperar un fallo de red y muestra su capacidad', async ({
  page,
}) => {
  const claves: string[] = [];
  let consultas = 0;
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.endsWith('/csrf'))
      return route.fulfill({ json: { token: 'prueba', header: 'X-CSRF' } });
    if (url.pathname.endsWith('/capacidades'))
      return route.fulfill({
        json: {
          imagen: {
            proveedor: 'GEMINI',
            modelo: 'vision-prueba',
            configurado: true,
            estado: 'CONFIGURADO',
          },
        },
      });
    if (url.pathname.endsWith('/imagen')) {
      claves.push(
        route
          .request()
          .postData()
          ?.match(/name="clave"\r\n\r\n([^\r]+)/)?.[1] ?? '',
      );
      return route.fulfill({ json: { ejecucion: { id: 'e1' } } });
    }
    consultas++;
    if (consultas === 1) return route.abort('failed');
    return route.fulfill({
      json: {
        id: 'e1',
        estado: 'COMPLETADO',
        revisionBase: 1,
        resultado: { texto: 'Consulta resuelta en el chat.', idPropuesta: null },
      },
    });
  });
  await abrirComponente(
    page,
    'ia/components/SubirFotoPizarraModal',
    `{modelo:'m', cerrar:()=>document.getElementById('fixture').textContent='Cerrado', alGenerar:async()=>{}}`,
  );
  await expect(page.getByRole('status')).toContainText('GEMINI · vision-prueba');
  await page.locator('input[type=file]').setInputFiles({
    name: 'diagrama.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==',
      'base64',
    ),
  });
  await page.getByRole('radio', { name: /Consultar con el mentor/ }).check();
  await page.getByRole('button', { name: 'Consultar imagen con el mentor' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Consultar imagen con el mentor' }).click();
  await expect(page.getByText('Cerrado')).toBeVisible();
  expect(claves).toHaveLength(2);
  expect(claves[0]).not.toBe('');
  expect(claves[1]).toBe(claves[0]);
});
