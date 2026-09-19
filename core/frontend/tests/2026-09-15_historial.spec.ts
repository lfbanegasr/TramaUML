import { test, expect, type Page } from '@playwright/test';

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

async function crearProyecto(page: Page) {
  await entrar(page);
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  const nombre = 'Historial ' + crypto.randomUUID().slice(0, 8);
  await page.getByLabel('Nombre del proyecto').fill(nombre);
  await page.getByLabel('Código', { exact: true }).fill('HIS_' + crypto.randomUUID().slice(0, 8));
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const res = (await (await respuesta).json()) as {
    id: string;
    idModelo: string;
    nombre: string;
  };
  await expect(page.getByText('Colaboración conectada')).toBeVisible();
  return res;
}

test.describe('CU19 y CU20 — Historial y restauración de versiones', () => {
  test('Consulta historial, revisa lista de versiones y compara diferencias', async ({ page }) => {
    await crearProyecto(page);

    // 1. Crear una clase "Usuario"
    await page.getByRole('button', { name: '＋ Nueva clase' }).click();
    await page.getByLabel('Nombre').fill('Usuario');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByText('Usuario', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('status')).toContainText(/revisión 2/i);

    // 2. Crear una segunda clase "Pedido"
    await page.getByRole('button', { name: '＋ Nueva clase' }).click();
    await page.getByLabel('Nombre').fill('Pedido');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByText('Pedido', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('status')).toContainText(/revisión 3/i);

    // 3. Abrir el historial
    await page.locator('#btn-historial').click();
    await expect(page.getByRole('heading', { name: 'Historial del modelo' })).toBeVisible();

    // 4. Debe listar las versiones existentes
    await expect(page.getByText(/Versión #2|v2/).first()).toBeVisible();
    await expect(page.getByText(/Versión #1|v1/).first()).toBeVisible();

    // 5. Seleccionar la versión 1 y abrir comparación
    await page
      .getByText(/Versión #1|v1/)
      .first()
      .click();
    await expect(
      page.getByRole('button', { name: /Comparar con (versión )?actual/i }),
    ).toBeVisible();
    await page.getByRole('button', { name: /Comparar con (versión )?actual/i }).click();

    // 6. Verificar que la comparación muestre diferencias respecto a la versión actual
    await expect(page.getByText('Comparación de versiones')).toBeVisible();
    await expect(page.getByText(/Pedido/)).toBeVisible();

    // Cerrar la comparación
    await page.getByRole('button', { name: /Cerrar comparación|×/ }).click();
    await expect(page.getByText('Comparación de versiones')).not.toBeVisible();

    // Cerrar el historial
    await page.getByRole('button', { name: '← Volver al editor' }).click();
    await expect(page.getByRole('heading', { name: 'Historial del modelo' })).not.toBeVisible();
  });

  test('Restaura una versión anterior generando una nueva revisión consistente', async ({
    page,
  }) => {
    await crearProyecto(page);

    // Crear clase "Cliente" (Versión 1)
    await page.getByRole('button', { name: '＋ Nueva clase' }).click();
    await page.getByLabel('Nombre').fill('Cliente');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByText('Cliente', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('status')).toContainText(/revisión 2/i);

    // Crear clase "Factura" (Versión 2)
    await page.getByRole('button', { name: '＋ Nueva clase' }).click();
    await page.getByLabel('Nombre').fill('Factura');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByText('Factura', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('status')).toContainText(/revisión 3/i);

    // Abrir Historial
    await page.locator('#btn-historial').click();
    await expect(page.getByRole('heading', { name: 'Historial del modelo' })).toBeVisible();

    // Seleccionar versión 2 (que contiene solo Cliente)
    await page
      .getByText(/Versión #2|v2/)
      .first()
      .click();

    // Abrir diálogo de restauración
    await page.getByRole('button', { name: /Restaurar esta versión/i }).click();
    await expect(page.getByRole('heading', { name: /Restaurar versión/i })).toBeVisible();

    // Llenar motivo y confirmar
    await page
      .getByPlaceholder(/Describe brevemente|Explica por qué/)
      .fill('Volver al estado inicial con solo Cliente');
    await page.getByLabel(/Entiendo que esta acción/i).check();
    await page.locator('#btn-confirmar-restauracion').click();

    // Tras la restauración, vuelve al editor con la nueva revisión (Revisión 4)
    await expect(page.getByRole('heading', { name: 'Historial del modelo' })).not.toBeVisible();
    await expect(page.getByRole('status')).toContainText(/revisión 4/i);
    await expect(page.getByText('Cliente', { exact: true }).first()).toBeVisible();
  });
});
