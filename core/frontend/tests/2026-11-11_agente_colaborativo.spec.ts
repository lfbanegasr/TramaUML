import { expect, test, type Page } from '@playwright/test';

async function iniciar(page: Page) {
  if (!process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_')) throw new Error('Requiere una base ficticia aislada.');
  await page.goto('/');
  await page.getByLabel('Correo o usuario').fill(process.env.UML_E2E_USERNAME!);
  await page.getByLabel('Contraseña', { exact: true }).fill(process.env.UML_E2E_PASSWORD!);
  await page.getByRole('button', { name: /Entrar a la plataforma/ }).click();
  await page.getByRole('button', { name: /Nuevo proyecto/ }).click();
  await page.getByLabel('Nombre del proyecto').fill('Agente ' + crypto.randomUUID().slice(0, 8));
  await page.getByLabel('Código', { exact: true }).fill('AG_' + crypto.randomUUID());
  const creada = page.waitForResponse(r => r.url().endsWith('/api/proyectos') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Crear proyecto', exact: true }).click();
  const proyecto = await (await creada).json();
  await page.getByRole('button', { name: /Asistente IA/ }).click();
  return proyecto as { idModelo: string };
}

test('consulta compartida sobrevive a pestañas y recarga sin alterar el modelo', async ({ page }) => {
  const { idModelo } = await iniciar(page);
  const panel = page.getByLabel('Asistente Inteligente de Modelado', { exact: true });
  await panel.getByLabel('Intención del mensaje').selectOption('CONSULTAR');
  await panel.getByLabel('Mensaje al asistente').fill('¿Cómo funciona la composición?');
  await panel.getByRole('button', { name: /Enviar/ }).click();
  await expect(panel.getByText(/En composición el origen es el conjunto/)).toBeVisible();
  const modelo = await (await page.request.get('/api/modelos/' + idModelo)).json();
  expect(modelo.revision).toBe(1); expect(modelo.clases).toHaveLength(0);
  await page.reload();
  await page.getByRole('button', { name: /Asistente IA/ }).click();
  await expect(panel.getByText('¿Cómo funciona la composición?', { exact: true })).toBeVisible();
  await expect(panel.getByText(/En composición el origen es el conjunto/)).toBeVisible();
  expect((await (await page.request.get(`/api/modelos/${idModelo}/ia/conversacion`)).json()).mensajes).toHaveLength(2);
});

test('acuerdo confirmado conserva autor y se recupera en una consulta posterior', async ({ page }) => {
  await iniciar(page);
  const panel = page.getByLabel('Asistente Inteligente de Modelado', { exact: true });
  await panel.getByLabel('Intención del mensaje').selectOption('CONSULTAR');
  await panel.getByLabel('Mensaje al asistente').fill('Las reservas no requieren anticipo.');
  await panel.getByRole('button', { name: /Enviar/ }).click();
  await expect(panel.getByRole('button', { name: 'Confirmar acuerdo' })).toBeVisible();
  page.once('dialog', d => d.accept('Las reservas no requieren anticipo.'));
  await panel.getByRole('button', { name: 'Confirmar acuerdo' }).click();
  await expect(panel.getByText('Acuerdos del equipo (1 vigentes)')).toBeVisible();
  await panel.getByLabel('Mensaje al asistente').fill('¿Qué acordamos sobre reservas?');
  await panel.getByRole('button', { name: /Enviar/ }).click();
  await expect(panel.getByText(/Acuerdo confirmado por.*Las reservas no requieren anticipo/)).toBeVisible();
  await expect(panel.getByText('Fuentes del contexto (1)')).toBeVisible();
});
