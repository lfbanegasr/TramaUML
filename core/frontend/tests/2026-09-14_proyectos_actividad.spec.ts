import { test, expect, type Page, type APIRequestContext } from '@playwright/test';

const usuario = process.env.UML_E2E_USERNAME,
  clave = process.env.UML_E2E_PASSWORD;
if (!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error('Usa el arnés nativo de navegador con una base ficticia aislada.');
async function entrar(page: Page, u = usuario!, c = clave!) {
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(u);
  await page.getByLabel('Contraseña', { exact: true }).fill(c);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await expect(page.getByRole('heading', { name: 'Tus proyectos' })).toBeVisible();
}
async function escribir(api: APIRequestContext, ruta: string, datos: unknown, method = 'POST') {
  const csrf = await (await api.get('/api/acceso/csrf')).json();
  return api.fetch('/api' + ruta, { method, data: datos, headers: { [csrf.header]: csrf.token } });
}
async function proyecto(page: Page) {
  await entrar(page);
  const alta = await escribir(page.request, '/proyectos', {
    codigo: 'T_' + crypto.randomUUID(),
    nombre: 'Proyecto 03 ' + crypto.randomUUID().slice(0, 8),
    descripcion: 'Ficticio',
  });
  expect(alta.status()).toBe(201);
  const p = await alta.json();
  await page.reload();
  await page.getByLabel('Buscar proyectos').fill(p.codigo);
  await expect(page.locator('.project-card')).toHaveCount(1);
  return p as { id: string; idModelo: string; codigo: string; nombre: string; revision: string };
}
async function resumen(page: Page) {
  await page.getByRole('button', { name: 'Ver resumen y actividad' }).click();
  await expect(page.getByRole('button', { name: 'Actualizar datos', exact: true })).toBeVisible();
}

test('filtros y vista personal, actualización, archivo de solo lectura y reactivación reales', async ({
  page,
}) => {
  const p = await proyecto(page);
  const hoy = new Date().toISOString().slice(0, 10);
  await page.getByLabel('Rol en proyectos').selectOption('PROPIETARIO_PROYECTO');
  await page.getByLabel('Proyectos creados desde').fill(hoy);
  await page.getByLabel('Proyectos creados hasta').fill(hoy);
  await expect(page.locator('.project-card')).toHaveCount(1);
  await page.getByRole('button', { name: /Abrir modelo/ }).click();
  await expect(page.getByRole('button', { name: /Nueva clase/ })).toBeVisible();
  await page
    .getByRole('button', { name: /Proyectos/, exact: false })
    .first()
    .click();
  await expect(page.getByLabel('Buscar proyectos')).toHaveValue(p.codigo);
  await expect(page.getByLabel('Rol en proyectos')).toHaveValue('PROPIETARIO_PROYECTO');
  await expect(page.getByLabel('Proyectos creados hasta')).toHaveValue(hoy);
  await resumen(page);
  await page.getByRole('button', { name: 'Actualizar datos' }).click();
  const nombre = p.nombre + ' actualizado';
  await page.getByLabel('Nombre del proyecto').fill(nombre);
  const descripcion =
    'Revisión humana del proyecto.\n' + 'Detalle de alcance y decisiones. '.repeat(15);
  await page.getByLabel('Descripción', { exact: true }).fill(descripcion);
  await page.getByRole('button', { name: 'Guardar proyecto', exact: true }).click();
  await expect(
    page.getByRole('dialog').getByRole('heading', { name: nombre, exact: true }),
  ).toBeVisible();
  await expect(page.locator('.project-summary .project-description')).toHaveText(descripcion);
  expect(
    (await page.locator('.project-summary .project-description').boundingBox())!.height,
  ).toBeGreaterThan(44);
  expect((await page.getByRole('dialog').boundingBox())!.width).toBeGreaterThan(800);
  await page.screenshot({
    path: 'test-results/incremento3-resumen-verificado.png',
    fullPage: true,
  });
  await page.getByRole('button', { name: 'Archivar proyecto', exact: true }).click();
  await page.getByLabel('Comprendo y confirmo el cambio de estado').check();
  await page.getByRole('button', { name: 'Confirmar cambio de estado', exact: true }).click();
  await expect(page.locator('.project-facts')).toContainText('ARCHIVADO');
  await page.getByRole('button', { name: 'Abrir modelo ↗', exact: true }).last().click();
  await expect(page.locator('.editor-footer')).toContainText('Solo lectura');
  await expect(page.getByRole('button', { name: /Nueva clase/ })).toHaveCount(0);
  await page
    .getByRole('button', { name: /Proyectos/ })
    .first()
    .click();
  await page.getByRole('button', { name: 'Ver resumen y actividad' }).click();
  await page.getByRole('button', { name: 'Reactivar proyecto', exact: true }).click();
  await page.getByLabel('Comprendo y confirmo el cambio de estado').check();
  await page.getByRole('button', { name: 'Confirmar cambio de estado', exact: true }).click();
  await expect(page.locator('.project-facts')).toContainText('ACTIVO');
  await expect(page.getByRole('button', { name: 'Actualizar datos', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Actividad', exact: true }).click();
  await page.getByRole('combobox', { name: 'Acción', exact: true }).selectOption('ACTUALIZAR');
  await expect(page.locator('.activity-panel tbody tr')).toHaveCount(1);
  await page.getByRole('button', { name: /Detalle del evento/ }).click();
  await expect(page.locator('.activity-values')).toContainText(p.nombre);
  await expect(page.locator('.activity-values')).toContainText(nombre);
  await page.locator('.activity-detail').scrollIntoViewIfNeeded();
  await page.screenshot({
    path: 'test-results/incremento3-proyecto-actividad.png',
    fullPage: true,
  });
});

test('conflicto conserva el borrador y una respuesta perdida reutiliza el UUID', async ({
  page,
}) => {
  const p = await proyecto(page);
  await resumen(page);
  await page.getByRole('button', { name: 'Actualizar datos' }).click();
  await page.getByLabel('Nombre del proyecto').fill('Borrador conservado');
  expect(
    (
      await escribir(
        page.request,
        '/proyectos/' + p.id,
        {
          codigo: p.codigo,
          nombre: 'Cambio desde otra sesión',
          descripcion: 'Actual',
          revision: p.revision,
          idSolicitudCliente: crypto.randomUUID(),
        },
        'PUT',
      )
    ).status(),
  ).toBe(200);
  await page.getByRole('button', { name: 'Guardar proyecto', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('proyecto cambió');
  await expect(page.getByLabel('Nombre del proyecto')).toHaveValue('Borrador conservado');
  await page.getByRole('button', { name: 'Recargar revisión y conservar borrador' }).click();
  await expect(page.locator('.current-project')).toContainText('Cambio desde otra sesión');
  await expect(page.getByLabel('Nombre del proyecto')).toHaveValue('Borrador conservado');
  const cuerpos: { idSolicitudCliente: string }[] = [];
  let primera = true;
  await page.route('**/api/proyectos/' + p.id, async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.continue();
      return;
    }
    cuerpos.push(route.request().postDataJSON());
    if (primera) {
      primera = false;
      const respuesta = await route.fetch();
      expect(respuesta.status()).toBe(200);
      await route.fulfill({ response: respuesta, body: '{"respuestaIncompleta":' });
    } else await route.continue();
  });
  await page.getByRole('button', { name: 'Guardar proyecto', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Reintentar la misma solicitud' })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('respuesta quedó incompleta');
  await expect(page.getByLabel('Nombre del proyecto')).toHaveValue('Borrador conservado');
  await page.getByRole('button', { name: 'Reintentar la misma solicitud' }).click();
  await expect(
    page.getByRole('dialog').getByRole('heading', { name: 'Borrador conservado', exact: true }),
  ).toBeVisible();
  expect(cuerpos).toHaveLength(2);
  expect(cuerpos[0].idSolicitudCliente).toBe(cuerpos[1].idSolicitudCliente);
  const actividad = await (
    await page.request.get('/api/proyectos/' + p.id + '/actividad?accion=ACTUALIZAR')
  ).json();
  expect(actividad.total).toBe(2);
});

test('actividad paginada, filtros, valores UML y versión vinculada consultan persistencia real', async ({
  page,
}) => {
  const p = await proyecto(page);
  let revision = p.revision;
  for (let i = 0; i < 11; i++) {
    const r = await escribir(
      page.request,
      '/proyectos/' + p.id,
      {
        codigo: p.codigo,
        nombre: p.nombre,
        descripcion: 'Cambio ' + i,
        revision,
        idSolicitudCliente: crypto.randomUUID(),
      },
      'PUT',
    );
    expect(r.status()).toBe(200);
    revision = (await r.json()).revision;
  }
  const apertura = page.waitForResponse(
    (r) => r.url().endsWith('/sesiones') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: /Abrir modelo/ }).click();
  const sesion = await (await apertura).json();
  await expect(page.getByText('Colaboración conectada')).toBeVisible();
  const entidad = crypto.randomUUID();
  const cambio = await escribir(page.request, '/modelos/' + p.idModelo + '/cambios', {
    idSesionEdicion: sesion.id,
    revisionBase: 1,
    idCambioCliente: crypto.randomUUID(),
    acciones: [
      {
        operacion: 'CREAR',
        entidad: 'CLASE',
        idEntidad: entidad,
        datos: { nombre: 'Entidad auditada', x: 0, y: 0 },
      },
    ],
  });
  expect(cambio.status()).toBe(200);
  const version = (await cambio.json()).idVersion;
  await page
    .getByRole('button', { name: /Proyectos/, exact: false })
    .first()
    .click();
  await resumen(page);
  await page.getByRole('button', { name: 'Actividad', exact: true }).click();
  await page.getByLabel('Eventos por página').selectOption('10');
  const actividadCompleta = await (
    await page.request.get('/api/proyectos/' + p.id + '/actividad?tamano=10')
  ).json();
  expect(actividadCompleta.total).toBeGreaterThan(10);
  await expect(page.locator('.activity-panel tbody tr')).toHaveCount(10);
  const ids = await page.getByRole('button', { name: /Detalle del evento/ }).allTextContents();
  expect(ids).toHaveLength(10);
  await page.getByRole('button', { name: 'Actividad siguiente' }).click();
  await expect(page.locator('.activity-panel .pagination')).toContainText('Página 2');
  await expect(page.locator('.activity-panel tbody tr')).toHaveCount(
    Math.min(10, actividadCompleta.total - 10),
  );
  let falloConsulta = true;
  await page.route('**/api/proyectos/' + p.id + '/actividad?*', async (route) => {
    if (falloConsulta) {
      falloConsulta = false;
      await route.abort('connectionfailed');
    } else await route.continue();
  });
  await page.getByLabel('UUID de la entidad').fill(entidad);
  await expect(page.getByRole('button', { name: 'Reintentar actividad' })).toBeVisible();
  await page.getByRole('button', { name: 'Reintentar actividad' }).click();
  await expect(page.locator('.activity-panel tbody tr')).toHaveCount(1);
  await page.getByRole('button', { name: /Detalle del evento/ }).click();
  await expect(page.locator('.activity-detail')).toContainText(version);
  await expect(page.locator('.activity-values')).toContainText('Entidad auditada');
  await page.getByLabel('UUID de la entidad').fill(crypto.randomUUID());
  await expect(page.locator('.activity-panel')).toContainText('No hay actividad que coincida');
});

test('consulta fallida permite recuperar la vista sin perder los filtros', async ({ page }) => {
  const p = await proyecto(page);
  let fallo = true;
  await page.route('**/api/proyectos?*', async (route) => {
    if (fallo) {
      fallo = false;
      await route.abort('connectionfailed');
    } else await route.continue();
  });
  await page.getByLabel('Estado de proyectos').selectOption('ACTIVO');
  await expect(page.getByRole('button', { name: 'Reintentar consulta' })).toBeVisible();
  await page.getByRole('button', { name: 'Reintentar consulta' }).click();
  await expect(page.locator('.project-card')).toHaveCount(1);
  await expect(page.getByLabel('Buscar proyectos')).toHaveValue(p.codigo);
  await expect(page.getByLabel('Estado de proyectos')).toHaveValue('ACTIVO');
});

test('una membresía retirada tras listar impide abrir y recupera la vista autorizada', async ({
  page,
  browser,
}) => {
  const p = await proyecto(page),
    u = 'consulta_' + crypto.randomUUID().slice(0, 8),
    nueva = crypto.randomUUID();
  const alta = await escribir(page.request, '/administracion/usuarios', {
    usuario: u,
    correo: u + '@example.test',
    nombre: 'Consulta ficticia',
    estado: 'PENDIENTE',
    roles: [],
  });
  expect(alta.status()).toBe(201);
  const cuenta = await alta.json();
  expect(
    (
      await escribir(page.request, '/acceso/activar', {
        identificador: u,
        temporal: cuenta.credencialTemporal,
        nueva,
      })
    ).status(),
  ).toBe(204);
  const equipo = await (await page.request.get('/api/proyectos/' + p.id + '/miembros')).json();
  const rol = equipo.roles.find((r: { codigo: string }) => r.codigo === 'LECTOR_PROYECTO').id;
  const ingreso = await escribir(page.request, '/proyectos/' + p.id + '/miembros', {
    usuario: cuenta.id,
    rol,
    estado: 'ACTIVO',
    permisos: [],
  });
  expect(ingreso.status()).toBe(201);
  const miembro = await ingreso.json();
  const contexto = await browser.newContext({ baseURL: process.env.UML_E2E_URL });
  try {
    const lector = await contexto.newPage();
    await entrar(lector, u, nueva);
    await lector.getByLabel('Buscar proyectos').fill(p.codigo);
    await expect(lector.locator('.project-card')).toHaveCount(1);
    expect(
      (
        await escribir(
          page.request,
          '/proyectos/' + p.id + '/miembros/' + miembro.id,
          {
            usuario: cuenta.id,
            rol,
            estado: 'RETIRADO',
            motivo: 'Prueba de autorización renovada',
            permisos: [],
            revision: miembro.revision,
          },
          'PUT',
        )
      ).status(),
    ).toBe(200);
    await lector.getByRole('button', { name: 'Ver resumen y actividad' }).click();
    await expect(lector.getByRole('dialog').getByRole('alert')).toContainText(
      'Proyecto no disponible',
    );
    await expect(
      lector.getByRole('dialog').getByRole('button', { name: 'Actualizar datos' }),
    ).toHaveCount(0);
    await lector.getByRole('button', { name: 'Volver a mis proyectos' }).click();
    await expect(lector.locator('.project-card')).toHaveCount(0);
    await expect(lector.getByRole('heading', { name: 'No hay coincidencias' })).toBeVisible();
    await expect(lector.getByLabel('Buscar proyectos')).toHaveValue(p.codigo);
  } finally {
    await contexto.close();
  }
});
