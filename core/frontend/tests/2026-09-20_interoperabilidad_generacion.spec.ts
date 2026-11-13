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

async function modeloConClase(page: Page, clase: string, persistente = false) {
  await entrar(page);
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  await page.getByLabel('Nombre del proyecto').fill('Interoperabilidad ' + crypto.randomUUID());
  await page.getByLabel('Código', { exact: true }).fill('INT_' + crypto.randomUUID());
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  await expect(page.getByRole('button', { name: /Nueva clase/ })).toBeVisible();
  await page.getByRole('button', { name: /Nueva clase/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill(clase);
  if (persistente) await page.getByLabel('Entidad persistente', { exact: true }).check();
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await expect(page.getByTestId('clase-uml').filter({ hasText: clase })).toBeVisible();
}

test('exporta y descarga un XMI desde el editor', async ({ page }) => {
  await modeloConClase(page, 'Exportable');
  await page.getByRole('button', { name: /Interoperabilidad/ }).click();
  await page.locator('#btn-exportar-xmi').click();
  await expect(
    page.getByRole('heading', { name: 'Exportar para Enterprise Architect y UML' }),
  ).toBeVisible();
  await expect(page.locator('#xmi-formato')).toHaveValue('XMI_251_EA');
  await page.getByLabel('Descripción del intercambio').fill('Prueba de navegador');
  const descarga = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar y descargar', exact: true }).click();
  expect((await descarga).suggestedFilename()).toMatch(/\.xmi$/);
  const resultado = page.getByRole('dialog').getByRole('status');
  await expect(resultado).toContainText('Exportación completada');
  await expect(resultado).toContainText('1 clases exportadas');
});

test('importa un XMI mediante previsualización y confirmación explícita', async ({ page }) => {
  await modeloConClase(page, 'Existente');
  await page.getByRole('button', { name: /Interoperabilidad/ }).click();
  await page.locator('#btn-importar-xmi').click();
  await expect(
    page.getByRole('heading', { name: 'Importar XMI de Enterprise Architect' }),
  ).toBeVisible();
  const xmi = `<?xml version="1.0" encoding="UTF-8"?>
  <xmi:XMI xmi:version="2.1"
      xmlns:xmi="http://schema.omg.org/spec/XMI/2.1"
      xmlns:uml="http://schema.omg.org/spec/UML/2.1">
    <xmi:Documentation exporter="Enterprise Architect" exporterVersion="16.1"/>
    <uml:Model xmi:id="modelo" name="Importado">
      <packagedElement xmi:type="uml:Class" xmi:id="cliente_importado" name="Importada">
        <ownedAttribute xmi:type="uml:Property" xmi:id="codigo" name="codigo"
            visibility="private" type="String"/>
      </packagedElement>
    </uml:Model>
  </xmi:XMI>`;
  await page.locator('.xmi-drop-zone input[type=file]').setInputFiles({
    name: 'modelo-ea.xmi',
    mimeType: 'application/xml',
    buffer: Buffer.from(xmi),
  });
  await expect(page.getByText('modelo-ea.xmi', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Analizar archivo', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('CLASE Importada');
  await expect(page.getByTestId('clase-uml').filter({ hasText: 'Importada' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Aplicar importación', exact: true }).click();
  await expect(page.getByRole('dialog').locator('.dialog-success')).toContainText(
    'Importación aplicada y versionada',
  );
  await page.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(page.getByTestId('clase-uml').filter({ hasText: 'Importada' })).toBeVisible();
  await expect(page.getByTestId('clase-uml').filter({ hasText: 'Existente' })).toBeVisible();
});

test('configura, genera, valida y descarga un backend desde el editor', async ({ page }) => {
  test.setTimeout(120_000);
  await modeloConClase(page, 'Generable', true);
  await page
    .getByTestId('clase-uml')
    .filter({ hasText: 'Generable' })
    .locator('.uml-class-title')
    .click();
  await page.getByRole('button', { name: /Añadir atributo/ }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('id');
  await page.getByRole('combobox', { name: /^Tipo de dato UML/ }).fill('UUID');
  await page.getByRole('checkbox', { name: /^Identificador/ }).check();
  await page.getByRole('button', { name: 'Guardar elemento', exact: true }).click();
  await page.getByRole('button', { name: /Calidad & Control/ }).click();
  await page.getByRole('button', { name: /Informe de validación/ }).click();
  await page.getByRole('button', { name: 'Validar modelo' }).click();
  await expect(page.getByLabel('Informe de validación').getByRole('status')).toContainText(
    /VALIDO/,
    { timeout: 15_000 },
  );
  await page.getByRole('button', { name: /Interoperabilidad/ }).click();
  await page.locator('#btn-generar-backend').click();
  await expect(
    page.getByRole('heading', { name: 'Configuración avanzada del backend' }),
  ).toBeVisible();
  await expect(page.getByLabel('Group ID')).toBeVisible();
  await page.getByLabel('Group ID').fill('bo.edu.prueba');
  await page.getByLabel('Artifact ID').fill('backend-prueba');
  await page.getByLabel('Paquete base Java').fill('bo.edu.prueba.backend');
  await page.getByRole('button', { name: 'Generar backend', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Validación del backend' });
  await expect(panel.getByRole('button', { name: 'Descargar ZIP validado' })).toBeDisabled();
  await panel.getByRole('button', { name: 'Validar backend', exact: true }).click();
  await expect(panel.getByRole('status')).toContainText('Validación: VALIDO', { timeout: 100_000 });
  const descarga = page.waitForEvent('download');
  await panel.getByRole('button', { name: 'Descargar ZIP validado' }).click();
  expect((await descarga).suggestedFilename()).toMatch(/\.zip$/);
  const resultado = page.getByRole('dialog').locator('.dialog-success');
  await expect(resultado).toContainText('Proyecto generado desde una versión UML congelada');
  await expect(resultado).toContainText('1 clases');
});
