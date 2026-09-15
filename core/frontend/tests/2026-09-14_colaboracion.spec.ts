import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const usuario = process.env.UML_E2E_USERNAME;
const clave = process.env.UML_E2E_PASSWORD;
if (!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error('Este recorrido requiere la base ficticia aislada del script de navegador.');

async function entrar(page: Page, nombre = usuario!, contrasena = clave!) {
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(nombre);
  await page.getByLabel('Contraseña', { exact: true }).fill(contrasena);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await expect(page.getByRole('heading', { name: 'Tus proyectos' })).toBeVisible();
}

async function escribir(api: APIRequestContext, ruta: string, datos: unknown, metodo = 'POST') {
  const csrf = await (await api.get('/api/acceso/csrf')).json();
  return api.fetch('/api' + ruta, {
    method: metodo,
    data: datos,
    headers: { [csrf.header]: csrf.token },
  });
}

async function crearProyecto(page: Page) {
  await entrar(page);
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  const nombre = 'Colaboración ' + crypto.randomUUID().slice(0, 8);
  await page.getByLabel('Nombre del proyecto').fill(nombre);
  await page.getByLabel('Código', { exact: true }).fill('COL_' + crypto.randomUUID());
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const proyecto = (await (await respuesta).json()) as {
    id: string;
    idModelo: string;
    nombre: string;
  };
  await expect(page.getByText('Colaboración conectada')).toBeVisible();
  return proyecto;
}

async function crearCuenta(page: Page, nombre: string) {
  const alta = await escribir(page.request, '/administracion/usuarios', {
    usuario: nombre,
    correo: nombre + '@example.test',
    nombre: 'Ingeniero colaborador',
    estado: 'PENDIENTE',
    roles: [],
  });
  expect(alta.status()).toBe(201);
  const datos = await alta.json();
  const nueva = crypto.randomUUID();
  expect(
    (
      await escribir(page.request, '/acceso/activar', {
        identificador: nombre,
        temporal: datos.credencialTemporal,
        nueva,
      })
    ).status(),
  ).toBe(204);
  return { id: datos.id as string, clave: nueva };
}

test('dos integrantes comparten presencia, foco y cambios confirmados; perder edición degrada a lector', async ({
  page,
  browser,
}) => {
  const proyecto = await crearProyecto(page);
  const nombre = 'colab_' + crypto.randomUUID().slice(0, 8);
  const cuenta = await crearCuenta(page, nombre);
  const equipo = await (
    await page.request.get('/api/proyectos/' + proyecto.id + '/miembros')
  ).json();
  const rolEditor = equipo.roles.find((r: { codigo: string }) => r.codigo === 'EDITOR_PROYECTO');
  const rolLector = equipo.roles.find((r: { codigo: string }) => r.codigo === 'LECTOR_PROYECTO');
  expect(rolEditor?.id).toBeTruthy();
  expect(rolLector?.id).toBeTruthy();
  const altaMiembro = await escribir(page.request, '/proyectos/' + proyecto.id + '/miembros', {
    usuario: cuenta.id,
    rol: rolEditor.id,
    estado: 'ACTIVO',
    motivo: '',
    permisos: [],
  });
  expect(altaMiembro.status()).toBe(201);
  let miembro = await altaMiembro.json();

  const contexto = await browser.newContext({ baseURL: process.env.UML_E2E_URL });
  try {
    const colaborador = await contexto.newPage();
    await entrar(colaborador, nombre, cuenta.clave);
    const tarjeta = colaborador.locator('.project-card').filter({ hasText: proyecto.nombre });
    await tarjeta.getByRole('button', { name: /Abrir modelo/ }).click();
    await expect(colaborador.getByText('Colaboración conectada')).toBeVisible();
    await expect(page.getByLabel('Participantes conectados')).toContainText(
      'Ingeniero colaborador',
    );
    await expect(colaborador.getByLabel('Participantes conectados')).toContainText(
      'Administrador ficticio',
    );

    const clase = 'PedidoCompartido';
    await page.getByRole('button', { name: /Nueva clase/ }).click();
    await page.getByLabel('Nombre', { exact: true }).fill(clase);
    await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
    await expect(colaborador.getByRole('button', { name: '◇ ' + clase })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await colaborador.getByRole('button', { name: '◇ ' + clase }).click();
    await expect(
      page.locator('.participant').filter({ hasText: 'Ingeniero colaborador' }),
    ).toContainText('clase');

    const actual = await (
      await page.request.get('/api/proyectos/' + proyecto.id + '/miembros')
    ).json();
    miembro = actual.miembros.find((m: { usuario: string }) => m.usuario === cuenta.id);
    const degradar = await escribir(
      page.request,
      '/proyectos/' + proyecto.id + '/miembros/' + miembro.id,
      {
        usuario: cuenta.id,
        rol: rolLector.id,
        estado: 'ACTIVO',
        motivo: '',
        permisos: [],
        revision: miembro.revision,
      },
      'PUT',
    );
    expect(degradar.status()).toBe(200);
    await expect(colaborador.getByRole('button', { name: /Nueva clase/ })).toHaveCount(0);
    await expect(colaborador.locator('.editor-footer')).toContainText('Solo lectura');

    await colaborador
      .getByRole('button', { name: /Proyectos/ })
      .first()
      .click();
    await expect(colaborador.getByRole('heading', { name: 'Tus proyectos' })).toBeVisible();
    await expect(
      page.locator('.participant').filter({ hasText: 'Ingeniero colaborador' }),
    ).toHaveCount(0);
    await page.screenshot({
      path: 'test-results/incremento4-colaboracion-verificada.png',
      fullPage: true,
    });
  } finally {
    await contexto.close();
  }
});
