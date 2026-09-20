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
  await page.getByRole('button', { name: /Asistente IA/ }).click();
  await page.getByLabel('Instrucción').fill(instruccion);
  const solicitud = page.waitForResponse(
    (r) => r.url().endsWith('/ia/solicitud/texto') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Generar propuesta', exact: true }).click();
  expect((await solicitud).status()).toBe(201);
  await expect(page.getByRole('heading', { name: 'Revisar cambios interpretados' })).toBeVisible();
}

test('CU21-CU25 permite revisar, aplicar y rechazar sin alterar el modelo antes de decidir', async ({
  page,
}) => {
  const proyecto = await crearProyecto(page);
  await proponer(
    page,
    'Crea una clase Cliente con atributos id: Long, nombre: String y una clase Pedido con atributo fecha: LocalDate, con relación 1 a *',
  );

  await expect(page.locator('.ia-confidence')).toContainText('93%');
  await expect(page.locator('.ia-operation')).toHaveCount(6);
  await expect(page.getByText('Crear Clase · Cliente', { exact: true })).toBeVisible();
  await expect(page.getByText('Crear Relación', { exact: true })).toBeVisible();
  let modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.clases).toHaveLength(0);

  await page.getByText('Crear Clase · Cliente', { exact: true }).click();
  const editor = page.getByLabel('Datos propuestos de operación 1');
  const claseCorregida = JSON.parse(await editor.inputValue()) as Record<string, unknown>;
  claseCorregida.nombre = 'ClientePreferente';
  await editor.fill(JSON.stringify(claseCorregida, null, 2));
  const revision = page.waitForResponse(
    (r) => r.url().includes('/ia/propuestas/') && r.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Guardar y revalidar', exact: true }).click();
  expect((await revision).status()).toBe(200);

  const aplicacion = page.waitForResponse(
    (r) => r.url().endsWith('/aplicar') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Aplicar propuesta', exact: true }).click();
  expect((await aplicacion).status()).toBe(200);
  await expect(page.locator('.outline')).toContainText('ClientePreferente');
  await expect(page.locator('.outline')).toContainText('Pedido');
  modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.revision).toBe(2);
  expect(modelo.clases).toHaveLength(2);
  expect(modelo.atributos).toHaveLength(3);
  expect(modelo.relaciones).toHaveLength(1);

  await proponer(page, 'Crea una clase Temporal');
  await page.getByRole('button', { name: 'Rechazar', exact: true }).click();
  await page.getByLabel('Motivo del rechazo').fill('No corresponde al alcance aprobado');
  const rechazo = page.waitForResponse(
    (r) => r.url().endsWith('/rechazar') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirmar rechazo', exact: true }).click();
  expect((await rechazo).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Revisar cambios interpretados' })).toHaveCount(0);
  modelo = await (await page.request.get('/api/modelos/' + proyecto.idModelo)).json();
  expect(modelo.revision).toBe(2);
  expect(modelo.clases.map((clase: { nombre: string }) => clase.nombre).sort()).toEqual([
    'ClientePreferente',
    'Pedido',
  ]);
});

test('CU22-CU23 expone voz y carga segura de una foto antes de enviarlas', async ({ page }) => {
  await crearProyecto(page);
  await page.getByRole('button', { name: /Asistente IA/ }).click();
  await page.getByRole('button', { name: '◉ Usar voz', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Modificar mediante voz' })).toBeVisible();
  await expect(page.getByLabel('Transcripción')).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar voz' }).click();

  await page.getByRole('button', { name: /Asistente IA/ }).click();
  await page.getByRole('button', { name: '▧ Subir foto de pizarra', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Reconstruir desde una foto' })).toBeVisible();
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
