import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'api.dart';
import 'app.dart';
import 'diagram.dart';
import 'agent.dart';

class Workspace extends StatefulWidget {
  const Workspace({
    super.key,
    required this.api,
    required this.project,
    this.startWithAgent = false,
  });
  final Api api;
  final Json project;
  final bool startWithAgent;
  @override
  State<Workspace> createState() => _WorkspaceState();
}

class _WorkspaceState extends State<Workspace> with WidgetsBindingObserver {
  Json? model, editing;
  WebSocket? socket;
  Timer? heartbeat, reconnect;
  int tab = 0, connectionGeneration = 0;
  bool busy = true, online = false, active = true;
  String? error, selected;
  String get modelId => widget.project['idModelo'].toString();
  @override
  void initState() {
    super.initState();
    tab = widget.startWithAgent ? 1 : 0;
    WidgetsBinding.instance.addObserver(this);
    widget.api.addListener(authChanged);
    connect();
  }

  void authChanged() {
    if (widget.api.session == null && mounted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    active = state == AppLifecycleState.resumed;
    if (active) {
      connect();
    } else {
      disconnect();
    }
  }

  void disconnect() {
    connectionGeneration++;
    heartbeat?.cancel();
    reconnect?.cancel();
    socket?.close();
    socket = null;
    if (mounted) setState(() => online = false);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.api.removeListener(authChanged);
    connectionGeneration++;
    heartbeat?.cancel();
    reconnect?.cancel();
    socket?.close();
    // No DELETE de membresía: escritorio y móvil pueden compartir el mismo participante.
    super.dispose();
  }

  Future<void> refresh() async {
    final data = await widget.api.request('GET', '/modelos/$modelId') as Json;
    if (mounted &&
        (model == null ||
            (data['revision'] as num) >= (model!['revision'] as num))) {
      setState(() => model = data);
    }
  }

  Future<void> connect() async {
    disconnect();
    final ticket = connectionGeneration;
    if (mounted) {
      setState(() {
        busy = model == null;
        error = null;
      });
    }
    try {
      await refresh();
      final session =
          await widget.api.request(
                'POST',
                '/modelos/$modelId/sesiones',
                body: {'idSolicitudCliente': const Uuid().v4()},
              )
              as Json;
      if (!mounted || !active || ticket != connectionGeneration) return;
      editing = session;
      final channel = await widget.api.connect(
        modelId,
        session['id'].toString(),
      );
      if (!mounted || !active || ticket != connectionGeneration) {
        channel.close();
        return;
      }
      socket = channel;
      setState(() => online = true);
      heartbeat = Timer.periodic(
        const Duration(seconds: 20),
        (_) => channel.add(jsonEncode({'tipo': 'PING'})),
      );
      channel.listen(
        (raw) {
          if (!mounted || ticket != connectionGeneration) return;
          final event = jsonDecode(raw as String) as Json;
          final data = event['datos'] as Json? ?? {};
          if (event['tipo'] == 'PRESENCIA' && data['sesion'] is Map) {
            setState(
              () => editing = Map<String, dynamic>.from(data['sesion'] as Map),
            );
          }
          if (event['tipo'] == 'MODELO_CONFIRMADO') {
            refresh().catchError((Object e) {
              if (mounted) setState(() => error = e.toString());
            });
          }
          if (event['tipo'] == 'ERROR') {
            setState(() => error = data['mensaje']?.toString());
          }
        },
        onError: (Object e) => lost(ticket),
        onDone: () => lost(ticket),
        cancelOnError: true,
      );
    } catch (e) {
      if (mounted && ticket == connectionGeneration) {
        setState(() => error = e.toString());
        if (e is! ApiException || ![401, 403, 404].contains(e.status)) {
          scheduleReconnect(ticket);
        }
      }
    } finally {
      if (mounted && ticket == connectionGeneration) {
        setState(() => busy = false);
      }
    }
  }

  void lost(int ticket) {
    if (!mounted || ticket != connectionGeneration) return;
    heartbeat?.cancel();
    setState(() => online = false);
    scheduleReconnect(ticket);
  }

  void scheduleReconnect(int ticket) {
    reconnect?.cancel();
    if (active && widget.api.session != null) {
      reconnect = Timer(const Duration(seconds: 5), () {
        if (mounted && ticket == connectionGeneration) connect();
      });
    }
  }

  void selectClass(Json c) {
    selected = c['nombre'].toString();
    socket?.add(
      jsonEncode({
        'tipo': 'FOCO',
        'tipoEntidad': 'CLASE',
        'idEntidad': c['id'],
      }),
    );
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => ClassDetails(
        model: model!,
        item: c,
        ask: () {
          Navigator.pop(sheetContext);
          setState(() => tab = 1);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${widget.project['nombre']}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          Text(
            '${online ? '● En vivo' : '○ Sin sincronizar'} · revisión ${model?['revision'] ?? '—'}',
            style: TextStyle(
              fontSize: 12,
              color: online ? Colors.teal : Colors.deepOrange,
            ),
          ),
        ],
      ),
      actions: [
        IconButton(
          onPressed: connect,
          tooltip: 'Sincronizar',
          icon: const Icon(Icons.sync),
        ),
      ],
    ),
    body: busy
        ? const Center(child: CircularProgressIndicator())
        : model == null
        ? Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ErrorBox(error ?? 'No se pudo cargar el modelo.'),
                FilledButton(
                  onPressed: connect,
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          )
        : Column(
            children: [
              if (error != null)
                MaterialBanner(
                  content: Text(error!, maxLines: 3),
                  actions: [
                    TextButton(
                      onPressed: () => setState(() => error = null),
                      child: const Text('Cerrar'),
                    ),
                  ],
                ),
              Expanded(
                child: IndexedStack(
                  index: tab,
                  children: [
                    DiagramView(model: model!, onClass: selectClass),
                    AgentView(
                      api: widget.api,
                      projectId: widget.project['id'].toString(),
                      modelId: modelId,
                      revision: (model!['revision'] as num).toInt(),
                      canEdit:
                          (model!['puedeSolicitarIa'] == true ||
                              model!['puedeEditar'] == true) &&
                          online,
                      selectedClass: selected,
                      onApplied: refresh,
                    ),
                    ProjectInfo(
                      api: widget.api,
                      project: widget.project,
                      editing: editing,
                    ),
                  ],
                ),
              ),
            ],
          ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (v) => setState(() => tab = v),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.account_tree_outlined),
          selectedIcon: Icon(Icons.account_tree),
          label: 'Diagrama',
        ),
        NavigationDestination(
          icon: Icon(Icons.auto_awesome_outlined),
          selectedIcon: Icon(Icons.auto_awesome),
          label: 'Agente',
        ),
        NavigationDestination(
          icon: Icon(Icons.groups_outlined),
          selectedIcon: Icon(Icons.groups),
          label: 'Equipo',
        ),
      ],
    ),
  );
}

class ProjectInfo extends StatefulWidget {
  const ProjectInfo({
    super.key,
    required this.api,
    required this.project,
    this.editing,
  });
  final Api api;
  final Json project;
  final Json? editing;
  @override
  State<ProjectInfo> createState() => _ProjectInfoState();
}

class _ProjectInfoState extends State<ProjectInfo> {
  late Future<List<dynamic>> data;
  @override
  void initState() {
    super.initState();
    data = load();
  }

  Future<List<dynamic>> load() => Future.wait([
    widget.api.request('GET', '/proyectos/${widget.project['id']}/equipo'),
    if (widget.project['puedeVerActividad'] == true)
      widget.api.request(
        'GET',
        '/proyectos/${widget.project['id']}/actividad?pagina=0&tamano=20',
      )
    else
      Future<dynamic>.value(null),
  ]);
  @override
  Widget build(BuildContext context) => FutureBuilder<List<dynamic>>(
    future: data,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ErrorBox(snapshot.error.toString()),
              TextButton(
                onPressed: () => setState(() => data = load()),
                child: const Text('Reintentar'),
              ),
            ],
          ),
        );
      }
      if (!snapshot.hasData) {
        return const Center(child: CircularProgressIndicator());
      }
      final activity = snapshot.data![1];
      final entries = activity is Map
          ? rows(activity['eventos'])
          : rows(activity);
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            'En este proyecto',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          ...rows(snapshot.data![0]).map(
            (m) => ListTile(
              leading: const CircleAvatar(child: Icon(Icons.person_outline)),
              title: Text('${m['nombre'] ?? 'Miembro'}'),
              subtitle: Text('${m['roles'] ?? ''} · ${m['estado'] ?? ''}'),
            ),
          ),
          const Divider(),
          Text(
            'Participantes de la sesión',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          ...rows(widget.editing?['participantes']).map(
            (p) => ListTile(
              leading: Icon(
                Icons.circle,
                size: 12,
                color: p['estado'] == 'EN_LINEA' ? Colors.teal : Colors.grey,
              ),
              title: Text('${p['nombre']}'),
              subtitle: Text('${p['modo']} · ${p['estado']}'),
            ),
          ),
          const Divider(),
          Text(
            'Actividad reciente',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (entries.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('No hay actividad disponible.'),
            ),
          ...entries.map(
            (e) => ListTile(
              leading: const Icon(Icons.history),
              title: Text('${e['accion'] ?? 'Cambio del proyecto'}'),
              subtitle: Text(
                '${e['responsable'] ?? ''} · ${e['ocurridoEn'] ?? ''}',
              ),
            ),
          ),
        ],
      );
    },
  );
}
