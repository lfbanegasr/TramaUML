import { test, expect, type Page } from '@playwright/test';

const usuario = process.env.UML_E2E_USERNAME;
const clave = process.env.UML_E2E_PASSWORD;
if (!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error('Este recorrido requiere la instalación ficticia aislada del script de pruebas.');

async function entrar(page: Page) {
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(usuario!);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave!);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await expect(page.getByRole('heading', { name: 'Tus proyectos' })).toBeVisible();
}

async function modeloConClase(page: Page, clase: string) {
  await entrar(page);
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  await page.getByLabel('Nombre del proyecto').fill('Interoperabilidad ' + crypto.randomUUID());
  await page.getByLabel('Código', { exact: true }).fill('INT_' + crypto.randomUUID());
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  await expect(page.getByRole('button', { name: /Nueva clase/ })).toBeVisible();
  await page.getByRole('button', { name: /Nueva clase/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill(clase);
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(page.getByTestId('clase-uml').filter({ hasText: clase })).toBeVisible();
}

test('exporta y descarga un XMI desde el editor', async ({ page }) => {
  await modeloConClase(page, 'Exportable');
  await page.getByRole('button', { name: 'Exportar XMI', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Exportar XMI 2.1' })).toBeVisible();
  await page.getByLabel('Descripción del intercambio').fill('Prueba de navegador');
  const descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar y descargar', exact: true }).click();
  expect((await descarga).suggestedFilename()).toMatch(/\.xmi$/);
  const resultado = page.getByRole('dialog').getByRole('status');
  await expect(resultado).toContainText('Exportación completada');
  await expect(resultado).toContainText('1 clases exportadas');
});

test('configura, genera y descarga un backend desde el editor', async ({ page }) => {
  await modeloConClase(page, 'Generable');
  await page.getByRole('button', { name: 'Generar Backend', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Generar Backend Spring Boot' })).toBeVisible();
  await expect(page.getByLabel('Group ID (Maven)')).toBeVisible();
  await page.getByLabel('Group ID (Maven)').fill('bo.edu.prueba');
  await page.getByLabel('Artifact ID (Maven)').fill('backend-prueba');
  await page.getByLabel('Paquete base Java').fill('bo.edu.prueba.backend');
  const descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Generar y descargar ZIP', exact: true }).click();
  expect((await descarga).suggestedFilename()).toMatch(/\.zip$/);
  const resultado = page.getByRole('dialog').getByRole('status');
  await expect(resultado).toContainText('Proyecto generado y descargado');
  await expect(resultado).toContainText('1 clases procesadas');
});
