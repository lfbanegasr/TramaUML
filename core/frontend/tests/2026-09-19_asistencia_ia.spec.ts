import { expect, test, type Page } from '@playwright/test';

const usuario = process.env.UML_E2E_USERNAME;
const clave = process.env.UML_E2E_PASSWORD;

if (!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error(
    'Ejecuta el script de navegador: requiere base y credenciales ficticias aisladas.',
  );

async function crearProyecto(page: Page) {
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(usuario!);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave!);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  await page.getByLabel('Nombre del proyecto').fill('IA ' + crypto.randomUUID().slice(0, 8));
  await page.getByLabel('Código', { exact: true }).fill('IA_' + crypto.randomUUID());
  const creada = page.waitForResponse(
    (r) => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const proyecto = (await creada).json() as Promise<{ idModelo: string }>;
  await expect(page.getByRole('button', { name: /Asistente IA/ })).toBeVisible();
  return proyecto;
}

async function proponer(page: Page, instruccion: string) {
  const panel = page.getByLabel('Asistente Inteligente de Modelado', { exact: true });
  if (!(await panel.isVisible())) await page.getByRole('button', { name: /Asistente IA/ }).click();
  const anteriores = await panel.locator('.ia-inline-proposal-card').count();
  await panel.locator('textarea').fill(instruccion);
  const solicitud = page.waitForResponse(
    (r) => r.url().endsWith('/ia/conversacion/mensajes') && r.request().method() === 'POST',
  );
  await panel.getByRole('button', { name: /Enviar/ }).click();
  expect((await solicitud).status()).toBe(202);
  await expect(panel.locator('.ia-inline-proposal-card')).toHaveCount(anteriores + 1);
  await expect(panel.locator('.ia-inline-proposal-card').last()).toBeVisible();
}

test('CU21-CU25 permite revisar, aplicar y rechazar sin alterar el modelo antes de decidir', async ({
  page,
}) => {
  const proyecto = await crearProyecto(page);
  await proponer(
    page,
    'Crea una clase Cliente con atributos id: Long, nombre: String y una clase Pedido con atributo fecha: LocalDate, con relación 1 a *',
  );

  const propuesta = page.locator('.ia-inline-proposal-card').last();
  await expect(propuesta.locator('.ia-inline-confidence-badge')).toContainText('Requiere revisión');
  await expect(propuesta.locator('.ia-op-card')).toHaveCount(6);
  await expect(propuesta.locator('.ia-op-name').filter({ hasText: /^Cliente$/ })).toBeVisible();
  await expect(propuesta).toContainText('ASOCIACION');
  let modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.clases).toHaveLength(0);

  const atributo = propuesta.locator('.ia-op-card').filter({
    has: page.locator('.ia-op-name').filter({ hasText: /^nombre$/ }),
  });
  await atributo.getByRole('checkbox').uncheck();
  await expect(propuesta.getByRole('button', { name: /Aplicar cambios \(5\)/ })).toBeEnabled();
  const revision = page.waitForResponse(
    (r) => r.url().includes('/ia/propuestas/') && r.request().method() === 'PUT',
  );
  const aplicacion = page.waitForResponse(
    (r) => r.url().endsWith('/aplicar') && r.request().method() === 'POST',
  );
  await propuesta.getByRole('button', { name: /Aplicar cambios \(5\)/ }).click();
  expect((await revision).status()).toBe(200);
  expect((await aplicacion).status()).toBe(200);
  await expect(propuesta.locator('.ia-inline-state-badge')).toContainText('Propuesta aplicada');
  await expect(page.getByTestId('clase-uml').filter({ hasText: 'Cliente' })).toBeVisible();
  await expect(page.getByTestId('clase-uml').filter({ hasText: 'Pedido' })).toBeVisible();
  modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.revision).toBe(2);
  expect(modelo.clases).toHaveLength(2);
  expect(modelo.atributos).toHaveLength(2);
  expect(modelo.atributos.some((a: { nombre: string }) => a.nombre === 'nombre')).toBe(false);
  expect(modelo.relaciones).toHaveLength(1);

  await proponer(page, 'Crea una clase Temporal');
  const rechazo = page.waitForResponse(
    (r) => r.url().endsWith('/rechazar') && r.request().method() === 'POST',
  );
  await page
    .locator('.ia-inline-proposal-card')
    .last()
    .getByRole('button', { name: 'Descartar propuesta', exact: true })
    .click();
  expect((await rechazo).status()).toBe(200);
  await expect(page.locator('.ia-inline-state-badge.rejected')).toContainText(
    'Propuesta descartada',
  );
  modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.revision).toBe(2);
  expect(modelo.clases.map((clase: { nombre: string }) => clase.nombre).sort()).toEqual([
    'Cliente',
    'Pedido',
  ]);
});

test('CU22-CU23 expone voz y carga segura de una foto antes de enviarlas', async ({ page }) => {
  await crearProyecto(page);
  await page.getByRole('button', { name: /Asistente IA/ }).click();
  await expect(
    page.getByTitle('Hablar o dictar instrucción por voz', { exact: true }),
  ).toBeEnabled();
  await expect(page.locator('.ia-agent-textarea')).toBeVisible();
  await page.getByTitle('Imagen', { exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Reconstruir o actualizar desde imagen / pizarra / papel' }),
  ).toBeVisible();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'pizarra.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    ),
  });
  await expect(page.getByAltText('Vista previa del diagrama')).toBeVisible();
  await expect(page.getByText('pizarra.png', { exact: false })).toBeVisible();
});
