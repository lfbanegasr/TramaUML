import 'package:flutter/material.dart';
import 'api.dart';
import 'app.dart';
import 'workspace.dart';

class ProjectsPage extends StatefulWidget {
  const ProjectsPage({super.key, required this.api});
  final Api api;
  @override
  State<ProjectsPage> createState() => _ProjectsPageState();
}

class _ProjectsPageState extends State<ProjectsPage> {
  final search = TextEditingController();
  List<Json> projects = [];
  int page = 0, total = 0, generation = 0;
  bool loading = true;
  String? error;
  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  Future<void> load({int target = 0}) async {
    final ticket = ++generation;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final data =
          await widget.api.request(
                'GET',
                '/proyectos?filtro=${Uri.encodeQueryComponent(search.text.trim())}&pagina=$target&tamano=20',
              )
              as Json;
      if (mounted && ticket == generation) {
        setState(() {
          projects = rows(data['proyectos']);
          page = target;
          total = data['total'] as int;
        });
      }
    } catch (e) {
      if (mounted && ticket == generation) setState(() => error = e.toString());
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
  }

  Future<void> create() async {
    final created = await showModalBottomSheet<Json>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => CreateProject(api: widget.api),
    );
    if (!mounted || created == null) return;
    await open(created, agent: true);
    if (mounted) load();
  }

  Future<void> open(Json p, {bool agent = false}) async {
    await Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) =>
            Workspace(api: widget.api, project: p, startWithAgent: agent),
      ),
    );
  }

  Future<void> account() async {
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              widget.api.session?['usuario']?['nombreCompleto']?.toString() ??
                  'Mi cuenta',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            const Text(
              'Puedes usar esta cuenta también en escritorio. Cada dispositivo conserva su propia sesión de acceso.',
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              icon: const Icon(Icons.logout),
              label: const Text('Cerrar sesión en este dispositivo'),
              onPressed: () async {
                Navigator.pop(sheetContext);
                try {
                  await widget.api.logout();
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(
                      context,
                    ).showSnackBar(SnackBar(content: Text(e.toString())));
                  }
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Mi espacio',
        style: TextStyle(fontWeight: FontWeight.w800),
      ),
      actions: [
        IconButton(
          onPressed: account,
          tooltip: 'Mi cuenta',
          icon: const Icon(Icons.account_circle_outlined),
        ),
      ],
    ),
    floatingActionButton: widget.api.canCreate
        ? FloatingActionButton.extended(
            onPressed: create,
            icon: const Icon(Icons.add),
            label: const Text('Nuevo proyecto'),
          )
        : null,
    body: SafeArea(
      child: RefreshIndicator(
        onRefresh: () => load(target: page),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
          children: [
            Text(
              'Diseña desde cualquier lugar',
              style: Theme.of(
                context,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              'Tus proyectos, tu equipo y tu agente UML.',
              style: TextStyle(color: Colors.black54),
            ),
            const SizedBox(height: 24),
            TextField(
              controller: search,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => load(),
              decoration: InputDecoration(
                hintText: 'Buscar proyectos',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(
                  tooltip: 'Buscar',
                  onPressed: () => load(),
                  icon: const Icon(Icons.arrow_forward),
                ),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                const Text(
                  'PROYECTOS',
                  style: TextStyle(
                    letterSpacing: 1.5,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const Spacer(),
                Text(
                  '$total disponibles',
                  style: const TextStyle(color: Colors.black54),
                ),
              ],
            ),
            if (loading)
              const Padding(
                padding: EdgeInsets.all(28),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (error != null) ...[
              ErrorBox(error!),
              TextButton(
                onPressed: () => load(target: page),
                child: const Text('Volver a intentar'),
              ),
            ],
            if (!loading && error == null && projects.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 56),
                child: Column(
                  children: [
                    Icon(
                      Icons.folder_open_outlined,
                      size: 60,
                      color: Colors.black38,
                    ),
                    SizedBox(height: 16),
                    Text('No hay proyectos para mostrar.'),
                  ],
                ),
              ),
            if (!loading)
              ...projects.map(
                (p) => Card(
                  margin: const EdgeInsets.only(top: 14),
                  color: Colors.white,
                  elevation: 0,
                  child: InkWell(
                    borderRadius: BorderRadius.circular(12),
                    onTap: p['puedeVerModelo'] == true ? () => open(p) : null,
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(
                                Icons.account_tree_outlined,
                                color: tramaGreen,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  '${p['nombre']}',
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 18,
                                  ),
                                ),
                              ),
                              const Icon(Icons.chevron_right),
                            ],
                          ),
                          if ((p['descripcion'] ?? '').toString().isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 12),
                              child: Text(
                                '${p['descripcion']}',
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(color: Colors.black54),
                              ),
                            ),
                          const SizedBox(height: 14),
                          Wrap(
                            spacing: 8,
                            runSpacing: 6,
                            children: [
                              Chip(
                                label: Text(
                                  '${p['rol'] ?? 'Miembro'}'.replaceAll(
                                    '_',
                                    ' ',
                                  ),
                                ),
                                visualDensity: VisualDensity.compact,
                              ),
                              Chip(
                                label: Text('${p['estado']}'),
                                visualDensity: VisualDensity.compact,
                              ),
                              Chip(
                                label: Text('Rev. ${p['revisionModelo'] ?? 0}'),
                                visualDensity: VisualDensity.compact,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            if (total > 20)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  TextButton(
                    onPressed: !loading && page > 0
                        ? () => load(target: page - 1)
                        : null,
                    child: const Text('Anterior'),
                  ),
                  Text('${page + 1} / ${(total / 20).ceil()}'),
                  TextButton(
                    onPressed: !loading && (page + 1) * 20 < total
                        ? () => load(target: page + 1)
                        : null,
                    child: const Text('Siguiente'),
                  ),
                ],
              ),
          ],
        ),
      ),
    ),
  );
}

class CreateProject extends StatefulWidget {
  const CreateProject({super.key, required this.api});
  final Api api;
  @override
  State<CreateProject> createState() => _CreateProjectState();
}

class _CreateProjectState extends State<CreateProject> {
  final form = GlobalKey<FormState>();
  final code = TextEditingController(),
      name = TextEditingController(),
      description = TextEditingController();
  bool busy = false;
  String? error;
  @override
  void dispose() {
    code.dispose();
    name.dispose();
    description.dispose();
    super.dispose();
  }

  Future<void> save() async {
    if (!form.currentState!.validate()) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final p =
          await widget.api.request(
                'POST',
                '/proyectos',
                body: {
                  'codigo': code.text.trim(),
                  'nombre': name.text.trim(),
                  'descripcion': description.text.trim(),
                },
              )
              as Json;
      if (mounted) Navigator.pop(context, p);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: EdgeInsets.fromLTRB(
      24,
      24,
      24,
      MediaQuery.viewInsetsOf(context).bottom + 24,
    ),
    child: Form(
      key: form,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Un nuevo proyecto',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text(
            'Después podrás describir el modelo a tu agente por texto, dictado o fotografía.',
          ),
          const SizedBox(height: 24),
          TextFormField(
            controller: name,
            maxLength: 180,
            decoration: const InputDecoration(labelText: 'Nombre'),
            validator: requiredField,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: code,
            maxLength: 80,
            decoration: const InputDecoration(
              labelText: 'Código',
              hintText: 'cafeteria-01',
            ),
            validator: (v) => RegExp(r'^[A-Za-z0-9_.-]+$').hasMatch(v ?? '')
                ? null
                : 'Usa letras, números, puntos, guiones o _',
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: description,
            maxLength: 4000,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Descripción (opcional)',
            ),
          ),
          if (error != null) ErrorBox(error!),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: busy ? null : save,
            child: Text(busy ? 'Creando…' : 'Crear y abrir agente'),
          ),
        ],
      ),
    ),
  );
}
