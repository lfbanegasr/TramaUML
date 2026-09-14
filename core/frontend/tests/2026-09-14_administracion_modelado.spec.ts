import { test, expect, type Page, type APIRequestContext } from '@playwright/test';

const usuario = process.env.UML_E2E_USERNAME;
const clave = process.env.UML_E2E_PASSWORD;
if (!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error('Este recorrido requiere la instalación ficticia aislada del script de pruebas.');

async function entrar(page: Page, u = usuario!, c = clave!) {
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(u);
  await page.getByLabel('Contraseña', { exact: true }).fill(c);
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

async function cuentaActiva(page: Page, u: string) {
  const alta = await escribir(page.request, '/administracion/usuarios', {
    usuario: u,
    correo: u + '@example.test',
    nombre: 'Ingeniero ' + u,
    estado: 'PENDIENTE',
    roles: [],
  });
  expect(alta.status()).toBe(201);
  const d = await alta.json();
  const nueva = crypto.randomUUID();
  expect(
    (
      await escribir(page.request, '/acceso/activar', {
        identificador: u,
        temporal: d.credencialTemporal,
        nueva,
      })
    ).status(),
  ).toBe(204);
  return { id: d.id as string, clave: nueva };
}

async function proyecto(page: Page) {
  await entrar(page);
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  const nombre = 'Equipo ficticio ' + crypto.randomUUID().slice(0, 8);
  await page.getByLabel('Nombre del proyecto').fill(nombre);
  await page.getByLabel('Código', { exact: true }).fill('T_' + crypto.randomUUID());
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const p = (await (await respuesta).json()) as { id: string; idModelo: string; nombre: string };
  await expect(page.getByRole('button', { name: /Nueva clase/ })).toBeVisible();
  return p;
}

test('usuarios: alta, activación personal, acceso y revocación desde administración', async ({
  page,
  browser,
}) => {
  await entrar(page);
  await page.getByRole('button', { name: /Administración/ }).click();
  await page.getByRole('button', { name: 'Nuevo usuario', exact: true }).click();
  const u = 'ui_' + crypto.randomUUID().slice(0, 8),
    nueva = crypto.randomUUID();
  await page.getByLabel('Nombre de usuario', { exact: true }).fill(u);
  await page.getByLabel('Correo electrónico', { exact: true }).fill(u + '@example.test');
  await page.getByLabel('Nombre completo', { exact: true }).fill('Ingeniero ficticio');
  await page.getByRole('button', { name: 'Guardar usuario', exact: true }).click();
  const temporal = await page.getByLabel('Credencial temporal', { exact: true }).textContent();
  expect(temporal).toBeTruthy();
  await page.getByRole('button', { name: 'Ya conservé la credencial' }).click();
  await page.getByLabel('Buscar en administración').fill(u);
  const fila = page.locator('tbody tr').filter({ hasText: u + '@example.test' });
  await expect(fila).toContainText('PENDIENTE');
  const contexto = await browser.newContext({ baseURL: process.env.UML_E2E_URL });
  try {
    const invitado = await contexto.newPage();
    await invitado.goto('/');
    await invitado.getByRole('button', { name: 'Activar cuenta con credencial temporal' }).click();
    await invitado.getByLabel('Correo o usuario').fill(u);
    await invitado.getByLabel('Credencial temporal', { exact: true }).fill(temporal!);
    await invitado.getByLabel('Nueva contraseña', { exact: true }).fill(nueva);
    await invitado.getByLabel('Confirmar contraseña nueva', { exact: true }).fill(nueva);
    await invitado.getByRole('button', { name: 'Confirmar activación' }).click();
    await expect(invitado.getByRole('status')).toContainText('Cuenta activada');
    await entrar(invitado, u, nueva);
    await expect(invitado.getByRole('button', { name: /Administración/ })).toHaveCount(0);
    await page.getByLabel('Buscar en administración').fill('');
    await page.getByLabel('Buscar en administración').fill(u);
    await expect(fila).toContainText('ACTIVO');
    await fila.getByRole('button', { name: 'Editar usuario ' + u }).click();
    await page.getByLabel('Estado de la cuenta').selectOption('INACTIVO');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Guardar usuario', exact: true }).click();
    await expect(fila).toContainText('INACTIVO');
    expect((await invitado.request.get('/api/proyectos')).status()).toBe(401);
    await invitado.reload();
    await expect(invitado.getByRole('button', { name: /Entrar a la plataforma/ })).toBeVisible();
  } finally {
    await contexto.close();
  }
});

test('roles: matriz real, revocación y filtros de ámbito', async ({ page }) => {
  await entrar(page);
  await page.getByRole('button', { name: /Administración/ }).click();
  await page.getByRole('button', { name: 'Roles y permisos' }).click();
  await page.getByRole('button', { name: 'Nuevo rol', exact: true }).click();
  const sufijo = crypto.randomUUID().slice(0, 8).toUpperCase(),
    nombre = 'Analista ' + sufijo;
  await page.getByLabel('Código del rol').fill('ANALISTA_' + sufijo);
  await page.getByLabel('Nombre del rol').fill(nombre);
  await page.getByLabel('Ámbito del rol').selectOption('PROYECTO');
  await page.getByRole('checkbox', { name: /^VER_MODELO/ }).check();
  await page.getByRole('checkbox', { name: /^EDITAR_MODELO/ }).check();
  await page.getByRole('button', { name: 'Guardar rol y permisos' }).click();
  await page.getByLabel('Buscar en administración').fill(nombre);
  const fila = page.locator('tbody tr').filter({ hasText: nombre });
  await expect(fila).toContainText('2 permisos');
  await page.getByRole('button', { name: 'Editar ' + nombre, exact: true }).click();
  await page.getByRole('checkbox', { name: /^EDITAR_MODELO/ }).uncheck();
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Guardar rol y permisos' }).click();
  await expect(fila).toContainText('1 permisos');
  await page.getByLabel('Filtrar ámbito de roles').selectOption('GLOBAL');
  await expect(fila).toHaveCount(0);
  await page.getByLabel('Filtrar ámbito de roles').selectOption('PROYECTO');
  await expect(fila).toBeVisible();
  await page.screenshot({ path: 'test-results/administracion-verificada.png', fullPage: true });
});

test('miembros: incorporación, excepción denegada, suspensión y propiedad protegida', async ({
  page,
  browser,
}) => {
  const p = await proyecto(page);
  const u = 'miembro_' + crypto.randomUUID().slice(0, 8),
    cuenta = await cuentaActiva(page, u);
  await page.getByRole('button', { name: /← Proyectos/ }).click();
  const tarjeta = page.locator('.project-card').filter({ hasText: p.nombre });
  await tarjeta.getByRole('button', { name: /Miembros/ }).click();
  const panel = page.getByRole('dialog', { name: 'Miembros del proyecto' });
  expect((await panel.boundingBox())!.width).toBeGreaterThan(900);
  await panel.getByRole('button', { name: 'Agregar miembro', exact: true }).click();
  await page.getByLabel('Buscar cuenta activa').fill(u);
  await expect(page.getByLabel('Cuenta para incorporar').locator('option')).toHaveCount(2);
  await page.getByLabel('Cuenta para incorporar').selectOption(cuenta.id);
  await page.getByLabel('Rol del proyecto').selectOption({ label: 'Ingeniero editor' });
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Confirmar miembro' }).click();
  const fila = panel.locator('tbody tr').filter({ hasText: u + '@example.test' });
  await expect(fila).toContainText('ACTIVO');
  const contexto = await browser.newContext({ baseURL: process.env.UML_E2E_URL });
  try {
    const miembro = await contexto.newPage();
    await entrar(miembro, u, cuenta.clave);
    const modeloAntes = await (await miembro.request.get('/api/modelos/' + p.idModelo)).json();
    expect(modeloAntes.puedeEditar).toBe(true);
    await fila.getByRole('button', { name: 'Editar miembro ' + u }).click();
    await page.getByLabel('Excepción EDITAR_MODELO').selectOption('DENEGAR');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Confirmar miembro' }).click();
    await expect(fila).toBeVisible();
    const modeloDespues = await (await miembro.request.get('/api/modelos/' + p.idModelo)).json();
    expect(modeloDespues.puedeEditar).toBe(false);
    await fila.getByRole('button', { name: 'Editar miembro ' + u }).click();
    await page.getByLabel('Estado del miembro').selectOption('SUSPENDIDO');
    await page.getByLabel('Motivo del estado').fill('Pausa de responsabilidad en la prueba');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Confirmar miembro' }).click();
    await expect(fila).toContainText('SUSPENDIDO');
    expect((await miembro.request.get('/api/modelos/' + p.idModelo)).status()).toBe(404);
    await fila.getByRole('button', { name: 'Editar miembro ' + u }).click();
    await page.getByLabel('Estado del miembro').selectOption('ACTIVO');
    await page.getByLabel('Excepción EDITAR_MODELO').selectOption('');
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Confirmar miembro' }).click();
    await expect(fila).toContainText('ACTIVO');
    page.once('dialog', (d) => d.accept());
    await fila.getByRole('button', { name: 'Transferir propiedad a ' + u }).click();
    await expect(fila).toContainText('Propietario actual');
    await fila.getByRole('button', { name: 'Editar miembro ' + u }).click();
    await expect(page.getByLabel('Estado del miembro')).toBeDisabled();
    page.once('dialog', (d) => d.dismiss());
    await page.getByRole('button', { name: 'Cerrar miembros', exact: true }).click();
    await expect(page.getByLabel('Estado del miembro')).toBeVisible();
    await page.getByRole('button', { name: 'Cancelar edición' }).click();
    await page.screenshot({ path: 'test-results/miembros-verificados.png', fullPage: true });
  } finally {
    await contexto.close();
  }
});

test('paquetes y validación asíncrona: errores localizables, corrección y revisión desactualizada', async ({
  page,
}) => {
  const p = await proyecto(page);
  await page.getByRole('button', { name: 'Nuevo paquete', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('ventas');
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(
    page.locator('.inspector').getByRole('heading', { name: 'ventas', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Nuevo paquete', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('dominio');
  await page.getByLabel('Paquete padre').selectOption({ label: 'ventas' });
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(
    page.locator('.inspector').getByRole('heading', { name: 'dominio', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: /Nueva clase/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Cliente');
  await page.getByLabel('Paquete de la clase').selectOption({ label: 'dominio' });
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(
    page.locator('.inspector').getByRole('heading', { name: 'Cliente', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Informe de validación', exact: true }).click();
  const informe = page.getByRole('region', { name: 'Informe de validación' });
  await informe.getByRole('button', { name: 'Validar modelo', exact: true }).click();
  await expect(informe.getByRole('status')).toContainText('INVALIDO');
  await expect(informe).toContainText('identificador');
  await informe.getByRole('button', { name: 'Localizar elemento' }).first().click();
  await page.getByRole('button', { name: /Añadir atributo/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('id');
  await page.getByLabel('Tipo UML', { exact: true }).fill('UUID');
  await page.getByLabel('Identificador', { exact: true }).check();
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(informe.getByRole('status')).toContainText('DESACTUALIZADO');
  await informe.getByRole('button', { name: 'Validar modelo', exact: true }).click();
  await expect(informe.getByRole('status')).toHaveText(/VALIDO · revisión/);
  await expect(informe).toContainText('0 errores');
  const modelo = await (await page.request.get('/api/modelos/' + p.idModelo)).json();
  expect(modelo.paquetes).toHaveLength(2);
  expect(modelo.clases[0].id_paquete).toBe(
    modelo.paquetes.find((x: { nombre: string }) => x.nombre === 'dominio').id,
  );
  await page.screenshot({ path: 'test-results/validacion-verificada.png', fullPage: true });
  await page.getByRole('button', { name: 'Editar propiedades', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Comprador');
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(informe.getByRole('status')).toContainText('DESACTUALIZADO');
  await page
    .locator('.outline')
    .getByRole('button', { name: /dominio/ })
    .click();
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Eliminar paquete', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  expect((await (await page.request.get('/api/modelos/' + p.idModelo)).json()).revision).toBe(
    modelo.revision + 1,
  );
});
