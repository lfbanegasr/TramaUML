import { test,expect,type Page } from '@playwright/test';
const usuario=process.env.UML_E2E_USERNAME, clave=process.env.UML_E2E_PASSWORD;
if(!usuario || !clave || !process.env.UML_TEST_DB_URL?.includes('/uml_collab_test_'))
  throw new Error('Ejecuta el script de navegador: requiere base y credenciales ficticias aisladas.');
async function entrar(page:Page) {
  await page.goto('/');await page.getByLabel('Correo o usuario').fill(usuario!);await page.getByLabel('Contraseña',{exact:true}).fill(clave!);
  await page.getByRole('button',{name:/Entrar a la plataforma/}).click();await expect(page.getByRole('heading',{name:'Tus proyectos'})).toBeVisible();
}
async function proyecto(page:Page) {
  await entrar(page);await page.getByRole('button',{name:/Nuevo proyecto/}).click();
  const nombre='Proyecto ficticio '+crypto.randomUUID().slice(0,8);
  await page.getByLabel('Nombre del proyecto').fill(nombre);await page.getByLabel('Código',{exact:true}).fill('T_'+crypto.randomUUID());
  const respuesta=page.waitForResponse(r=>r.url().endsWith('/api/proyectos') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Crear proyecto',exact:true}).click();const p=await (await respuesta).json();
  await expect(page.getByRole('button',{name:/Nueva clase/})).toBeVisible();return p as {idModelo:string;nombre:string};
}
async function crearClase(page:Page,nombre:string) {
  await page.getByRole('button',{name:/Nueva clase/}).click();await page.getByLabel('Nombre',{exact:true}).fill(nombre);
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.locator('.inspector').getByRole('heading',{name:nombre,exact:true})).toBeVisible();
}
test('acceso, proyectos autorizados y cierre de sesión real',async({page})=>{
  await page.goto('/');expect((await page.request.get('/api/proyectos')).status()).toBe(401);
  await entrar(page);await page.getByRole('button',{name:/Cerrar sesión/}).click();
  await expect(page.getByRole('button',{name:/Entrar a la plataforma/})).toBeVisible();
  expect((await page.request.get('/api/proyectos')).status()).toBe(401);
});
test('clases, atributos corregibles, operaciones, parámetros y relaciones persisten',async({page})=>{
  const p=await proyecto(page);await crearClase(page,'Cliente');
  await page.getByRole('button',{name:/Añadir atributo/}).click();
  await page.getByLabel('Nombre',{exact:true}).fill('id');await page.getByLabel('Tipo UML',{exact:true}).fill('UUID');
  await page.getByLabel('Identificador',{exact:true}).check();await page.getByLabel('Máximo (* ilimitado)',{exact:true}).fill('*');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Multiplicidad inválida');
  await expect(page.getByLabel('Nombre',{exact:true})).toHaveValue('id');
  await page.getByLabel('Máximo (* ilimitado)',{exact:true}).fill('1');await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByRole('button',{name:'Editar atributo id',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Editar atributo id',exact:true}).click();await page.getByLabel('Nombre',{exact:true}).fill('codigo');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await page.getByRole('button',{name:/Añadir operación/}).click();await page.getByLabel('Nombre',{exact:true}).fill('buscar');
  await page.getByLabel('Tipo de retorno',{exact:true}).fill('String');await page.getByRole('button',{name:/Añadir parámetro/}).click();
  await page.getByLabel('Nombre del parámetro',{exact:true}).fill('texto');await page.getByLabel('Tipo del parámetro',{exact:true}).fill('String');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByTestId('clase-uml')).toContainText('buscar(texto: String): String');
  await crearClase(page,'Pedido');await page.getByRole('button',{name:/Nueva relación/}).click();
  await page.getByLabel('Nombre',{exact:true}).fill('pedidos');await page.getByLabel('Clase origen',{exact:true}).selectOption({label:'Cliente'});
  await page.getByLabel('Clase destino',{exact:true}).selectOption({label:'Pedido'});await page.getByLabel('Máximo destino',{exact:true}).fill('*');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByRole('button',{name:'Editar relación',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Editar relación',exact:true}).click();await page.getByLabel('Rol destino',{exact:true}).fill('compras');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await page.reload();await page.locator('.project-card').filter({has:page.getByRole('heading',{name:p.nombre,exact:true})}).getByRole('button',{name:/Abrir modelo/}).click();
  await expect(page.getByTestId('clase-uml').filter({hasText:'Cliente'})).toContainText('codigo: UUID');
  const modelo=await (await page.request.get('/api/modelos/'+p.idModelo)).json();
  expect(modelo.clases).toHaveLength(2);expect(modelo.parametros[0].nombre).toBe('texto');
  expect(modelo.relaciones[0].maximo_destino).toBeNull();expect(modelo.relaciones[0].rol_destino).toBe('compras');
  await page.getByRole('button',{name:'Fit View',exact:true}).click();
  const title=page.getByTestId('clase-uml').filter({hasText:'Cliente'}).locator('.uml-class-title');
  const caja=await title.boundingBox();expect(caja).not.toBeNull();
  const movimiento=page.waitForResponse(r=>r.url().endsWith('/cambios') && r.request().method()==='POST');
  await page.mouse.move(caja!.x+caja!.width/2,caja!.y+caja!.height/2);await page.mouse.down();
  await page.mouse.move(caja!.x+caja!.width/2+60,caja!.y+caja!.height/2+45,{steps:8});await page.mouse.up();
  expect((await movimiento).status()).toBe(200);
  const movido=await (await page.request.get('/api/modelos/'+p.idModelo)).json();
  expect(movido.clases.find((c:{nombre:string})=>c.nombre==='Cliente').x).not.toBe(modelo.clases.find((c:{nombre:string})=>c.nombre==='Cliente').x);
  await page.screenshot({path:'test-results/editor-verificado.png',fullPage:true});
});
test('respuesta perdida se reintenta con el mismo UUID sin duplicar una clase',async({page})=>{
  const p=await proyecto(page);const cuerpos:{idCambioCliente:string}[]=[];let primera=true;
  await page.route('**/api/modelos/*/cambios',async route=>{
    cuerpos.push(route.request().postDataJSON());
    if(primera) {primera=false;const respuesta=await route.fetch();expect(respuesta.status()).toBe(200);await route.abort('connectionfailed');}
    else await route.continue();
  });
  await page.getByRole('button',{name:/Nueva clase/}).click();await page.getByLabel('Nombre',{exact:true}).fill('Entrega');
  await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByRole('button',{name:'Reintentar el mismo envío',exact:true})).toBeVisible();
  const reenvio=page.waitForResponse(r=>r.url().endsWith('/cambios') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Reintentar el mismo envío',exact:true}).click();
  expect((await (await reenvio).json()).repetido).toBe(true);
  await expect(page.locator('.inspector').getByRole('heading',{name:'Entrega',exact:true})).toBeVisible();
  expect(cuerpos).toHaveLength(2);expect(cuerpos[0].idCambioCliente).toBe(cuerpos[1].idCambioCliente);
  const modelo=await (await page.request.get('/api/modelos/'+p.idModelo)).json();expect(modelo.clases).toHaveLength(1);expect(modelo.revision).toBe(2);
});
test('conflicto conserva el borrador y salir exige decisión del usuario',async({page})=>{
  const p=await proyecto(page);await page.getByRole('button',{name:/Nueva clase/}).click();await page.getByLabel('Nombre',{exact:true}).fill('Local');
  page.once('dialog',d=>d.dismiss());await page.locator('.sidebar').getByRole('button',{name:/Proyectos/}).click();
  await expect(page.getByLabel('Nombre',{exact:true})).toHaveValue('Local');
  const csrf=await (await page.request.get('/api/acceso/csrf')).json();
  const externa=await page.request.post('/api/modelos/'+p.idModelo+'/cambios',{headers:{[csrf.header]:csrf.token},
    data:{idCambioCliente:crypto.randomUUID(),revisionBase:1,acciones:[{operacion:'CREAR',entidad:'CLASE',idEntidad:crypto.randomUUID(),datos:{nombre:'Remota',x:400,y:100}}]}});
  expect(externa.status()).toBe(200);await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'Tu borrador se conserva'})).toBeVisible();await expect(page.getByLabel('Nombre',{exact:true})).toHaveValue('Local');
  await page.getByRole('button',{name:/Recargar modelo/}).click();await expect(page.getByRole('button',{name:'Guardar elemento',exact:true})).toBeEnabled();
  await page.getByLabel('Nombre',{exact:true}).fill('LocalRevisada');await page.getByRole('button',{name:'Guardar elemento',exact:true}).click();
  await expect(page.locator('.inspector').getByRole('heading',{name:'LocalRevisada',exact:true})).toBeVisible();
  const modelo=await (await page.request.get('/api/modelos/'+p.idModelo)).json();expect(modelo.clases.map((c:{nombre:string})=>c.nombre).sort()).toEqual(['LocalRevisada','Remota']);
});
