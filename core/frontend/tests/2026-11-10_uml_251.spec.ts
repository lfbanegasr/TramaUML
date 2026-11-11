import { test, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

async function crearProyecto(page: Page) {
  if (
    !process.env.UML_E2E_USERNAME ||
    !process.env.UML_E2E_PASSWORD ||
    !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_')
  )
    throw new Error('Requiere el lanzador de pruebas aisladas.');
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(process.env.UML_E2E_USERNAME);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.UML_E2E_PASSWORD);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  await page.getByLabel('Nombre del proyecto').fill('Aceptación UML 251');
  await page.getByLabel('Código', { exact: true }).fill('UML251_' + crypto.randomUUID());
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const project = await (await response).json();
  await expect(page.getByText('Colaboración conectada')).toBeVisible();
  return project;
}

test('colecciones UML conservan máximo ilimitado y modificadores al editar; el intercambio moderno es predeterminado', async ({
  page,
}) => {
  const project = await crearProyecto(page);
  await page.getByRole('button', { name: /Nueva clase/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Concepto');
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await page.getByTestId('clase-uml').locator('.uml-class-title').click();
  await page.getByRole('button', { name: /Añadir atributo \(avanzado\)/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('valores');
  await page.getByLabel('Mínimo', { exact: true }).fill('2');
  await page.getByLabel('Máximo (* ilimitado)', { exact: true }).fill('8');
  await page.getByLabel('Colección ordenada {ordered}', { exact: true }).check();
  await page.getByLabel('Valores únicos en la colección UML', { exact: true }).uncheck();
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(page.locator('[data-testid="clase-uml"]')).toContainText('{ordered}');
  await expect(page.locator('[data-testid="clase-uml"]')).toContainText('{nonunique}');
  const row = page.locator('.attribute-row').filter({ hasText: 'valores' });
  await row.getByRole('button', { name: 'Editar', exact: true }).click();
  await expect(page.getByLabel('Máximo (* ilimitado)', { exact: true })).toHaveValue('8');
  await page.getByLabel('Máximo (* ilimitado)', { exact: true }).fill('*');
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(page.getByTestId('clase-uml')).toContainText('2..*');
  const model = await (await page.request.get('/api/modelos/' + project.idModelo)).json();
  expect(model.clases[0].es_persistente).toBe(false);
  expect(model.atributos[0].multiplicidad_maxima).toBeNull();
  expect(model.atributos[0].es_ordenado).toBe(true);
  expect(model.atributos[0].coleccion_unica).toBe(false);
  await page.getByRole('button', { name: /Interoperabilidad/ }).click();
  await page.locator('#btn-exportar-xmi').click();
  await expect(page.locator('#xmi-formato')).toHaveValue('XMI_251_EA');
  await expect(page.locator('#xmi-formato')).toContainText('verificación visual pendiente');
  const descargado = page.waitForEvent('download');
  await page.locator('#btn-confirmar-exportar-xmi').click();
  const archivo = await descargado;
  expect(archivo.suggestedFilename()).toContain('_uml251_xmi251.xmi');
  const xml = await readFile((await archivo.path())!, 'utf8');
  expect(xml).toContain('http://www.omg.org/spec/UML/20161101');
  expect(xml).toContain('isOrdered="true"');
  expect(xml).toContain('isUnique="false"');
  await page.screenshot({ path: 'test-results/2026-11-10_uml251.png' });
});

test('la paleta crea clases conceptuales y representa abstracción sin inventar perfiles UML', async ({
  page,
}) => {
  const project = await crearProyecto(page);
  const canvas = page.locator('.canvas');
  const herramientas = [
    'herramienta-clase',
    'herramienta-clase-abstracta',
    'herramienta-clase-asociacion',
  ];
  for (let i = 0; i < herramientas.length; i++) {
    const herramienta = page.getByTestId(herramientas[i]);
    await expect(herramienta).toHaveAttribute('draggable', 'true');
    await herramienta.dragTo(canvas, { targetPosition: { x: 70 + i * 140, y: 120 } });
    await expect(page.getByTestId('clase-uml')).toHaveCount(i + 1);
  }
  const model = await (await page.request.get('/api/modelos/' + project.idModelo)).json();
  expect(model.clases.every((c: { es_persistente: boolean }) => c.es_persistente === false)).toBe(
    true,
  );
  expect(model.clases.filter((c: { es_abstracto: boolean }) => c.es_abstracto)).toHaveLength(1);
  expect(model.clases.every((c: { estereotipo: string | null }) => !c.estereotipo)).toBe(true);
  expect(model.clases.some((c: { nombre: string }) => c.nombre.startsWith('ClaseIntermedia'))).toBe(
    true,
  );
});
