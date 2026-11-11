import 'dart:convert';
import 'dart:typed_data';
import 'dart:async';
import 'package:uuid/uuid.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:image_picker/image_picker.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'api.dart';
import 'app.dart';

class AgentView extends StatefulWidget {
  const AgentView({
    super.key,
    required this.api,
    required this.projectId,
    required this.modelId,
    required this.revision,
    required this.canEdit,
    required this.onApplied,
    this.selectedClass,
  });
  final Api api;
  final String projectId, modelId;
  final int revision;
  final bool canEdit;
  final String? selectedClass;
  final Future<void> Function() onApplied;
  @override
  State<AgentView> createState() => _AgentViewState();
}

class _AgentViewState extends State<AgentView> {
  final input = TextEditingController(), scroll = ScrollController();
  final speech = SpeechToText();
  final storage = const FlutterSecureStorage();
  final picker = ImagePicker();
  List<Json> messages = [];
  Json? proposal;
  bool busy = false, listening = false, loaded = false;
  bool autoSendVoice = true, fromVoice = false;
  String liveTranscript = '';
  String imagePreset = 'AUTO', imageMode = 'FUSION';
  String? error, attachmentName;
  Timer? refreshTimer, silenceTimer;
  bool syncing = false;
  int cursor = 0;
  final Map<String, Json> serverMessages = {};
  List<Json> jobs = [], agreements = [];
  String intention = 'AUTO';
  String? retryKey, retryText;
  Uint8List? attachment;
  String get scope =>
      '${widget.api.baseUrl}:${widget.api.userId}:${widget.projectId}:${widget.modelId}';
  @override
  void initState() {
    super.initState();
    restore();
  }

  Future<void> restore() async {
    final restoringScope = scope;
    try {
      final history = await storage.read(key: 'chat:$scope');
      final draft = await storage.read(key: 'draft:$scope');
      if (!mounted || restoringScope != scope) return;
      setState(() {
        if (history != null) messages = rows(jsonDecode(history));
        input.text = draft ?? '';
        loaded = true;
      });
      input.addListener(saveDraft);
      await synchronize();
      refreshTimer = Timer.periodic(
        const Duration(seconds: 3),
        (_) => synchronize(),
      );
      if (widget.canEdit) await pending();
      final lost = await picker.retrieveLostData();
      if (lost.files?.isNotEmpty == true) await attach(lost.files!.first);
    } catch (e) {
      if (mounted) {
        setState(() {
          loaded = true;
          error = 'No se pudo recuperar el borrador: $e';
        });
      }
    }
  }

  void saveDraft() {
    storage.write(key: 'draft:$scope', value: input.text);
  }

  Future<void> persist() =>
      storage.write(key: 'chat:$scope', value: jsonEncode(messages));

  Future<void> synchronize() async {
    if (syncing || !mounted) return;
    syncing = true;
    final modelId = widget.modelId;
    try {
      Json page;
      do {
        page =
            await widget.api.request(
                  'GET',
                  '/modelos/$modelId/ia/conversacion?cursor=$cursor',
                )
                as Json;
        if (!mounted || modelId != widget.modelId) return;
        for (final m in rows(page['mensajes'])) {
          serverMessages[m['id'].toString()] = m;
        }
        cursor = (page['cursor'] as num?)?.toInt() ?? cursor;
      } while (page['hayMas'] == true);
      if (!mounted) return;
      setState(() {
        messages = serverMessages.values
            .map(
              (m) => <String, dynamic>{
                'id': m['id'],
                'role': m['emisor'] == 'USUARIO' ? 'user' : 'assistant',
                'text': m['texto'],
                'extraction': m['extraccion'],
                'author': m['nombreAutor'],
                'authorId': m['autor'],
                'replyTo': m['respuestaA'],
                'at': m['creadoEn'],
                'proposal': m['idPropuesta'],
                'image': m['modalidad'] == 'IMAGEN',
                'voice': m['modalidad'] == 'VOZ',
                'sources': m['fuentes'],
                'assumptions': m['supuestos'],
              },
            )
            .toList();
        jobs = rows(page['ejecuciones']);
        agreements = rows(page['acuerdos']);
      });
      await persist();
      if (widget.canEdit) await pending();
    } catch (e) {
      if (mounted) {
        setState(
          () => error = 'No se pudo sincronizar el historial del servidor: $e',
        );
      }
    } finally {
      syncing = false;
    }
  }

  Future<void> confirmAgreement(Json message) async {
    final controller = TextEditingController(text: '${message['text']}');
    final content = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Confirmar acuerdo del equipo'),
        content: TextField(
          controller: controller,
          maxLength: 2000,
          maxLines: 4,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (content == null || content.isEmpty) return;
    try {
      await widget.api.request(
        'POST',
        '/modelos/${widget.modelId}/ia/conversacion/acuerdos',
        body: {'idMensaje': message['id'], 'contenido': content},
      );
      await synchronize();
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    }
  }

  Future<void> pending() async {
    try {
      final data = await widget.api.request(
        'GET',
        '/modelos/${widget.modelId}/ia/propuestas/vigente',
      );
      if (mounted) setState(() => proposal = data as Json?);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    }
  }

  @override
  void didUpdateWidget(covariant AgentView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.modelId != widget.modelId ||
        oldWidget.projectId != widget.projectId) {
      refreshTimer?.cancel();
      silenceTimer?.cancel();
      input.removeListener(saveDraft);
      cursor = 0;
      serverMessages.clear();
      messages = [];
      jobs = [];
      agreements = [];
      proposal = null;
      retryKey = null;
      retryText = null;
      error = null;
      input.clear();
      attachment = null;
      attachmentName = null;
      fromVoice = false;
      liveTranscript = '';
      loaded = false;
      restore();
      return;
    }
    if (!oldWidget.canEdit && widget.canEdit && loaded) pending();
  }

  @override
  void dispose() {
    refreshTimer?.cancel();
    silenceTimer?.cancel();
    speech.cancel();
    input.removeListener(saveDraft);
    input.dispose();
    scroll.dispose();
    super.dispose();
  }

  // Únicamente una acción explícita del usuario mueve el historial.
  void bottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && scroll.hasClients) {
        scroll.animateTo(
          scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> pauseVoiceToEdit() async {
    silenceTimer?.cancel();
    await speech.stop();
    if (mounted) {
      setState(() {
        listening = false;
        fromVoice = true;
      });
    }
  }

  Future<void> cancelVoice() async {
    silenceTimer?.cancel();
    await speech.stop();
    if (mounted) {
      setState(() {
        listening = false;
        fromVoice = false;
        liveTranscript = '';
        input.clear();
      });
    }
  }

  Future<void> dictate() async {
    if (listening) {
      silenceTimer?.cancel();
      await speech.stop();
      if (mounted) setState(() => listening = false);
      if (autoSendVoice && input.text.trim().isNotEmpty && !busy) {
        await send(fromVoiceCommand: true);
      }
      return;
    }
    try {
      final available = await speech.initialize(
        onStatus: (status) {
          if (mounted && (status == 'done' || status == 'notListening')) {
            final wasListening = listening;
            silenceTimer?.cancel();
            setState(() => listening = false);
            if (wasListening &&
                autoSendVoice &&
                !busy &&
                input.text.trim().isNotEmpty) {
              unawaited(send(fromVoiceCommand: true));
            }
          }
        },
        onError: (e) {
          silenceTimer?.cancel();
          if (mounted) {
            if (autoSendVoice && !busy && input.text.trim().isNotEmpty) {
              setState(() => listening = false);
              unawaited(send(fromVoiceCommand: true));
            } else {
              setState(() {
                listening = false;
                error =
                    'No se pudo completar el dictado. Revisa el permiso de micrófono o escribe el mensaje.';
              });
            }
          }
        },
      );
      if (!available) {
        if (mounted) {
          setState(
            () => error =
                'Dictado no disponible. Habilita el reconocimiento de voz y el permiso de micrófono en Android.',
          );
        }
        return;
      }
      final locales = await speech.locales();
      final spanish = locales
          .where((l) => l.localeId.startsWith('es'))
          .firstOrNull;
      final previous = input.text.trim();
      if (!mounted) return;
      setState(() {
        listening = true;
        fromVoice = true;
        liveTranscript = '';
        error = null;
      });
      await speech.listen(
        onResult: (result) {
          final recognized = result.recognizedWords.trim();
          final combined = [
            previous,
            recognized,
          ].where((s) => s.isNotEmpty).join(' ');
          if (mounted) {
            setState(() {
              liveTranscript = recognized;
              fromVoice = true;
              input.text = combined;
              input.selection = TextSelection.collapsed(
                offset: input.text.length,
              );
            });
          }
          silenceTimer?.cancel();
          if (result.finalResult &&
              autoSendVoice &&
              combined.trim().isNotEmpty) {
            silenceTimer = Timer(const Duration(milliseconds: 350), () {
              if (mounted && !busy && input.text.trim().isNotEmpty) {
                speech.stop();
                setState(() => listening = false);
                unawaited(send(fromVoiceCommand: true));
              }
            });
          } else if (autoSendVoice && combined.trim().isNotEmpty) {
            silenceTimer = Timer(const Duration(milliseconds: 1750), () {
              if (mounted &&
                  listening &&
                  !busy &&
                  input.text.trim().isNotEmpty) {
                speech.stop();
                setState(() => listening = false);
                unawaited(send(fromVoiceCommand: true));
              }
            });
          }
        },
        listenOptions: SpeechListenOptions(
          localeId: spanish?.localeId,
          listenFor: const Duration(seconds: 120),
          pauseFor: const Duration(seconds: 4),
          partialResults: true,
          cancelOnError: true,
          listenMode: ListenMode.dictation,
        ),
      );
    } catch (e) {
      if (mounted) {
        setState(() {
          listening = false;
          error = 'No se pudo iniciar el dictado: $e';
        });
      }
    }
  }

  Future<void> chooseImage() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      useSafeArea: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'Adjuntar diagrama o boceto',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt_outlined),
              title: const Text('Tomar fotografía'),
              subtitle: const Text('Pizarra, cuaderno o boceto en papel'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Elegir de la galería'),
              subtitle: const Text('Foto guardada o captura de pantalla'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    try {
      final file = await picker.pickImage(
        source: source,
        imageQuality: 88,
        maxWidth: 2400,
        maxHeight: 2400,
      );
      if (file != null) await attach(file);
    } catch (e) {
      if (mounted) {
        setState(
          () => error =
              'No se pudo abrir la imagen. Revisa los permisos de cámara o galería.',
        );
      }
    }
  }

  Future<void> attach(XFile file) async {
    final photo = await preparePhoto(
      await file.readAsBytes(),
      file.name.isEmpty ? 'diagrama.jpg' : file.name,
    );
    if (mounted) {
      setState(() {
        attachment = Uint8List.fromList(photo.bytes);
        attachmentName = photo.name;
        error = null;
      });
    }
  }

  Future<void> send({bool fromVoiceCommand = false}) async {
    final text = input.text.trim();
    if (busy || !widget.canEdit || (text.isEmpty && attachment == null)) return;
    silenceTimer?.cancel();
    await speech.stop();
    final bytes = attachment, filename = attachmentName;
    final isVoice = (fromVoiceCommand || fromVoice) && bytes == null;
    setState(() {
      busy = true;
      listening = false;
      fromVoice = false;
      liveTranscript = '';
      error = null;
      messages.add({
        'role': 'user',
        'text': text.isEmpty ? 'Analiza este diagrama.' : text,
        'author':
            widget.api.session?['usuario']?['nombreCompleto']?.toString() ??
            'Tú',
        'authorId': widget.api.userId,
        'image': bytes != null,
        'voice': isVoice,
        'at': DateTime.now().toIso8601String(),
      });
    });
    try {
      await persist();
      final needsTarget =
          widget.selectedClass != null &&
          RegExp(
            r'atributo|campo|propiedad|operaci[oó]n|m[eé]todo',
            caseSensitive: false,
          ).hasMatch(text) &&
          !RegExp(r'\bclase\b', caseSensitive: false).hasMatch(text);
      final contextual = needsTarget
          ? '$text en la clase ${widget.selectedClass}'
          : text;
      if (bytes == null) {
        if (retryText != contextual) {
          retryText = contextual;
          retryKey = const Uuid().v4();
        }
        if (isVoice) {
          await widget.api.voice(
            widget.modelId,
            contextual,
            messageKey: retryKey,
            intention: intention,
          );
        } else {
          await widget.api.request(
            'POST',
            '/modelos/${widget.modelId}/ia/conversacion/mensajes',
            body: {
              'texto': contextual,
              'clave': retryKey,
              'intencion': intention,
            },
          );
        }
        retryKey = null;
        retryText = null;
        if (!mounted) return;
        input.clear();
        await synchronize();
        return;
      }
      retryKey ??= const Uuid().v4();
      final effectiveIntention =
          (imageMode == 'CONSULTAR' || intention == 'CONSULTAR')
          ? 'CONSULTAR'
          : 'MODIFICAR';
      final enrichedInstruction =
          '[ORIGEN: $imagePreset] [MODO: ${effectiveIntention == 'CONSULTAR' ? 'CONSULTAR' : imageMode}] '
          '${contextual.isEmpty ? (effectiveIntention == 'CONSULTAR' ? 'Analiza esta imagen de diagrama UML y dame tu diagnóstico.' : 'Digitaliza este diagrama UML.') : contextual}';
      await widget.api.image(
        widget.modelId,
        bytes,
        filename ?? 'diagrama.jpg',
        enrichedInstruction,
        messageKey: retryKey,
        intention: effectiveIntention,
      );
      if (!mounted) return;
      setState(() {
        input.clear();
        attachment = null;
        attachmentName = null;
        retryKey = null;
        retryText = null;
      });
      await synchronize();
    } catch (e) {
      if (mounted) {
        setState(
          () => error =
              '$e\nTu entrada se conserva. Comprueba las propuestas antes de reenviar.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> review() async {
    if (proposal == null) return;
    final result = await showModalBottomSheet<Json>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => ProposalSheet(
        api: widget.api,
        modelId: widget.modelId,
        proposalId: proposal!['id'].toString(),
        currentRevision: widget.revision,
        canEdit: widget.canEdit,
      ),
    );
    if (!mounted || result == null) return;
    setState(() {
      proposal = result['estado'] == 'LISTO' ? result : null;
      messages.add({
        'role': 'assistant',
        'text':
            result['mensaje'] ??
            'Propuesta ${result['estado'].toString().toLowerCase()}.',
        'at': DateTime.now().toIso8601String(),
      });
    });
    await persist();
    if (result['aplicada'] == true) {
      try {
        await widget.onApplied();
      } catch (e) {
        if (mounted) {
          setState(
            () => error =
                'El cambio fue aplicado, pero falta actualizar el visor: $e',
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Container(
        width: double.infinity,
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFFE3F2EC),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.auto_awesome, color: Color(0xFF176B52)),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                widget.selectedClass == null
                    ? 'Agente del modelo · revisión ${widget.revision}\nDescribe tu sistema o envía una fotografía.'
                    : 'Contexto: ${widget.selectedClass}\nRevisión ${widget.revision}',
              ),
            ),
            IconButton(
              onPressed: busy ? null : pending,
              tooltip: 'Recuperar propuesta pendiente',
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
      ),
      Expanded(
        child: ListView(
          controller: scroll,
          padding: const EdgeInsets.all(16),
          children: [
            if (messages.isEmpty) ...[
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: Text(
                  '¿Qué construimos hoy?',
                  style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800),
                ),
              ),
              ...[
                'Genera un diagrama de clases para una cafetería que soporte inventarios y pagos',
                'Crea clase Producto con atributos nombre: String, precio: Double, stock: Integer',
              ].map(
                (example) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: ActionChip(
                    label: Text(example, maxLines: 3),
                    onPressed: () => input.text = example,
                  ),
                ),
              ),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Chat compartido guardado en el servidor. Los cambios requieren revisar y aplicar una propuesta; los acuerdos se confirman explícitamente.',
                  style: TextStyle(color: Colors.black54, fontSize: 12),
                ),
              ),
            ],
            ...messages.map((m) {
              final human = m['role'] == 'user';
              final mine =
                  human &&
                  (m['authorId']?.toString() == widget.api.userId ||
                      m['authorId'] == null);
              final author = m['author']?.toString().trim().isNotEmpty == true
                  ? m['author'].toString()
                  : mine
                  ? 'Tú'
                  : 'Integrante';
              return Align(
                key: ValueKey(m['id'] ?? '${m['at']}:${m['role']}'),
                alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
                child: Container(
                  constraints: const BoxConstraints(maxWidth: 540),
                  margin: EdgeInsets.only(
                    top: 10,
                    left: mine ? 32 : 0,
                    right: mine ? 0 : 24,
                  ),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: mine
                        ? const Color(0xFF176B52)
                        : human
                        ? const Color(0xFFE3F2EC)
                        : Colors.white,
                    borderRadius: BorderRadius.circular(18),
                    border: mine
                        ? null
                        : Border.all(color: const Color(0xFFD2E3DB)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 6,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Text(
                            human
                                ? 'CONSULTA DE $author${mine ? ' · TÚ' : ''}'
                                : 'AGENTE UML',
                            style: TextStyle(
                              fontSize: 10,
                              letterSpacing: 1.5,
                              color: mine
                                  ? Colors.white70
                                  : const Color(0xFF176B52),
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          if (m['voice'] == true)
                            Text(
                              '🎙️ Voz',
                              style: TextStyle(
                                fontSize: 10,
                                color: mine
                                    ? Colors.white70
                                    : const Color(0xFF176B52),
                              ),
                            ),
                          if (m['image'] == true)
                            Text(
                              '🖼️ Imagen',
                              style: TextStyle(
                                fontSize: 10,
                                color: mine
                                    ? Colors.white70
                                    : const Color(0xFF176B52),
                              ),
                            ),
                          if (!human &&
                              m['replyTo'] != null &&
                              m['replyTo'].toString().isNotEmpty)
                            Text(
                              '↪ para ${m['replyTo']}',
                              style: const TextStyle(
                                fontSize: 10,
                                color: Color(0xFF176B52),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      if (m['image'] == true)
                        Padding(
                          padding: EdgeInsets.only(bottom: 8),
                          child: Icon(
                            Icons.image_outlined,
                            color: mine ? Colors.white : tramaGreen,
                          ),
                        ),
                      SelectableText(
                        '${m['text']}',
                        style: TextStyle(
                          color: mine ? Colors.white : const Color(0xFF24362F),
                          height: 1.45,
                        ),
                      ),
                      if (m['extraction'] != null)
                        ExpansionTile(
                          title: const Text(
                            'Transcripción o lectura utilizada',
                          ),
                          children: [SelectableText('${m['extraction']}')],
                        ),
                      if (human && m['id'] != null && widget.canEdit)
                        TextButton(
                          onPressed: () => confirmAgreement(m),
                          child: Text(
                            'Confirmar acuerdo',
                            style: TextStyle(
                              color: mine ? Colors.white : tramaGreen,
                            ),
                          ),
                        ),
                      if (m['assumptions'] is List &&
                          (m['assumptions'] as List).isNotEmpty)
                        Text(
                          'Supuestos: ${(m['assumptions'] as List).join('; ')}',
                        ),
                      if (m['sources'] is List &&
                          (m['sources'] as List).isNotEmpty)
                        Text(
                          'Fuentes: ${(m['sources'] as List).join(', ')}',
                          style: const TextStyle(fontSize: 11),
                        ),
                    ],
                  ),
                ),
              );
            }),
            if (busy)
              const Padding(
                padding: EdgeInsets.all(20),
                child: Row(
                  children: [
                    SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    SizedBox(width: 12),
                    Text('Analizando el modelo…'),
                  ],
                ),
              ),
            if (error != null) ErrorBox(error!),
            ...jobs
                .where(
                  (j) =>
                      j['estado'] == 'PENDIENTE' || j['estado'] == 'PROCESANDO',
                )
                .map(
                  (j) => ListTile(
                    title: Text(
                      '${j['estado'] == 'PENDIENTE' ? 'Solicitud en cola' : 'Preparando respuesta…'}'
                      '${j['nombreAutor'] != null ? ' · ${j['nombreAutor']}' : ''}',
                    ),
                    subtitle: j['textoMensaje'] != null
                        ? Text(
                            '${j['modalidad'] == 'VOZ'
                                ? '🎙️ '
                                : j['modalidad'] == 'IMAGEN'
                                ? '🖼️ '
                                : ''}${j['textoMensaje']}',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          )
                        : null,
                    trailing: TextButton(
                      onPressed: !widget.canEdit
                          ? null
                          : () async {
                              try {
                                await widget.api.request(
                                  'POST',
                                  '/modelos/${widget.modelId}/ia/conversacion/ejecuciones/${j['id']}/cancelar',
                                );
                                await synchronize();
                              } catch (e) {
                                if (mounted) {
                                  setState(() => error = e.toString());
                                }
                              }
                            },
                      child: const Text('Cancelar'),
                    ),
                  ),
                ),
            ...jobs
                .where((j) => j['estado'] == 'FALLIDO')
                .map((j) => ErrorBox('${j['error']}')),
            if (agreements.isNotEmpty)
              ExpansionTile(
                title: const Text('Acuerdos del equipo'),
                children: agreements
                    .map(
                      (a) => ListTile(
                        title: Text('${a['contenido']}'),
                        subtitle: Text(
                          '${a['nombreAutor']}${a['vigente'] == true ? '' : ' (sustituido)'}',
                        ),
                      ),
                    )
                    .toList(),
              ),
            if (proposal != null)
              Card(
                color: const Color(0xFFE5F3EE),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text(
                        'Propuesta para revisar',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 17,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text('${proposal!['resumen']}'),
                      Text(
                        '${rows(proposal!['operaciones']).length} cambios · revisión base ${proposal!['revisionBase']}',
                      ),
                      if (proposal!['revisionBase'] != widget.revision)
                        const Text(
                          'El modelo cambió: la propuesta necesita actualizarse.',
                          style: TextStyle(color: Colors.deepOrange),
                        ),
                      const SizedBox(height: 10),
                      FilledButton.tonalIcon(
                        onPressed: busy ? null : review,
                        icon: const Icon(Icons.fact_check_outlined),
                        label: const Text('Revisar cambios'),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
      Align(
        alignment: Alignment.centerRight,
        child: TextButton.icon(
          onPressed: bottom,
          icon: const Icon(Icons.arrow_downward, size: 18),
          label: const Text('Ver últimos mensajes'),
        ),
      ),
      if (!widget.canEdit)
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          child: Text(
            'Se necesita conexión y una sesión con permiso de edición para solicitar o aplicar cambios.',
            style: TextStyle(fontSize: 12, color: Colors.deepOrange),
          ),
        ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: DropdownButtonFormField<String>(
          initialValue: intention,
          decoration: const InputDecoration(labelText: 'Intención del mensaje'),
          items: const [
            DropdownMenuItem(value: 'AUTO', child: Text('Automática')),
            DropdownMenuItem(
              value: 'CONSULTAR',
              child: Text('Consultar sin cambios'),
            ),
            DropdownMenuItem(
              value: 'MODIFICAR',
              child: Text('Proponer cambios'),
            ),
          ],
          onChanged: busy || !widget.canEdit
              ? null
              : (value) => setState(() => intention = value ?? 'AUTO'),
        ),
      ),
      if (attachment != null)
        Container(
          margin: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: const Color(0xFFE3F2EC),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Image.memory(
                      attachment!,
                      width: 56,
                      height: 56,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const SizedBox(
                        width: 56,
                        height: 56,
                        child: Icon(Icons.image_outlined),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Imagen lista para analizar',
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                        Text(
                          '${attachmentName ?? 'diagrama.jpg'} · ${(attachment!.length / 1024).toStringAsFixed(1)} KB',
                          style: const TextStyle(
                            fontSize: 11,
                            color: Colors.black54,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: busy
                        ? null
                        : () => setState(() {
                            attachment = null;
                            attachmentName = null;
                          }),
                    tooltip: 'Quitar imagen',
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  for (final entry in const [
                    ('AUTO', 'Automático'),
                    ('PAPEL', 'Cuaderno/Papel'),
                    ('PIZARRA', 'Pizarra'),
                    ('DIGITAL', 'Digital'),
                  ])
                    ChoiceChip(
                      label: Text(
                        entry.$2,
                        style: const TextStyle(fontSize: 11),
                      ),
                      selected: imagePreset == entry.$1,
                      visualDensity: VisualDensity.compact,
                      onSelected: busy
                          ? null
                          : (_) => setState(() => imagePreset = entry.$1),
                    ),
                ],
              ),
              const SizedBox(height: 4),
              Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  for (final mode in const [
                    ('FUSION', 'Fusionar'),
                    ('NUEVO', 'Reemplazar'),
                    ('CONSULTAR', 'Solo consultar'),
                  ])
                    ChoiceChip(
                      label: Text(
                        mode.$2,
                        style: const TextStyle(fontSize: 11),
                      ),
                      selected: imageMode == mode.$1,
                      visualDensity: VisualDensity.compact,
                      onSelected: busy
                          ? null
                          : (_) => setState(() => imageMode = mode.$1),
                    ),
                ],
              ),
            ],
          ),
        ),
      if (listening)
        Container(
          margin: const EdgeInsets.fromLTRB(16, 6, 16, 4),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: const Color(0xFFFFF3E0),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.deepOrange.shade200),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '● Escuchando comando de voz en vivo…',
                      style: TextStyle(
                        color: Colors.deepOrange,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Checkbox(
                        value: autoSendVoice,
                        visualDensity: VisualDensity.compact,
                        onChanged: (v) =>
                            setState(() => autoSendVoice = v ?? true),
                      ),
                      const Text('Auto-enviar', style: TextStyle(fontSize: 11)),
                    ],
                  ),
                ],
              ),
              Text(
                liveTranscript.isNotEmpty
                    ? '"$liveTranscript"'
                    : 'Habla ahora… se transcribirá en pantalla y se enviará al terminar de hablar.',
                style: const TextStyle(
                  fontSize: 12,
                  fontStyle: FontStyle.italic,
                  color: Colors.black87,
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: pauseVoiceToEdit,
                    child: const Text(
                      '⏸ Editar',
                      style: TextStyle(fontSize: 12),
                    ),
                  ),
                  TextButton(
                    onPressed: cancelVoice,
                    child: const Text(
                      '✕ Cancelar',
                      style: TextStyle(fontSize: 12, color: Colors.red),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              IconButton(
                onPressed: busy || !widget.canEdit ? null : chooseImage,
                tooltip: 'Cámara o galería',
                icon: const Icon(Icons.add_photo_alternate_outlined),
              ),
              Expanded(
                child: TextField(
                  controller: input,
                  enabled: !busy && loaded,
                  minLines: 1,
                  maxLines: 5,
                  maxLength: 11500,
                  decoration: const InputDecoration(
                    hintText: 'Escribe o dicta tu idea…',
                    counterText: '',
                    contentPadding: EdgeInsets.all(12),
                  ),
                ),
              ),
              IconButton(
                onPressed: busy || !widget.canEdit ? null : dictate,
                tooltip: listening
                    ? 'Terminar y enviar comando de voz'
                    : 'Comando de voz (habla y se envía al terminar)',
                icon: Icon(
                  listening ? Icons.stop_circle : Icons.mic_none,
                  color: listening ? Colors.red : null,
                ),
              ),
              IconButton.filled(
                onPressed: busy || !widget.canEdit || !loaded
                    ? null
                    : () => send(),
                tooltip: 'Enviar al agente',
                icon: const Icon(Icons.arrow_upward),
              ),
            ],
          ),
        ),
      ),
    ],
  );
}

class ProposalSheet extends StatefulWidget {
  const ProposalSheet({
    super.key,
    required this.api,
    required this.modelId,
    required this.proposalId,
    required this.currentRevision,
    required this.canEdit,
  });
  final Api api;
  final String modelId, proposalId;
  final int currentRevision;
  final bool canEdit;
  @override
  State<ProposalSheet> createState() => _ProposalSheetState();
}

class _ProposalSheetState extends State<ProposalSheet> {
  Json? proposal;
  List<Json> operations = [];
  bool busy = true;
  String? error;
  String get path =>
      '/modelos/${widget.modelId}/ia/propuestas/${widget.proposalId}';
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final p = await widget.api.request('GET', path) as Json;
      if (mounted) {
        setState(() {
          proposal = p;
          operations = rows(p['operaciones']);
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> decide(bool apply) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      if (apply) {
        final revised =
            await widget.api.request(
                  'PUT',
                  path,
                  body: {
                    'revision': proposal!['revisionEdicion'],
                    'operaciones': operations
                        .map(
                          (o) => {
                            'id': o['id'],
                            'seleccionada': o['seleccionada'],
                            'valorPropuesto': o['valorPropuesto'],
                          },
                        )
                        .toList(),
                  },
                )
                as Json;
        if (!mounted) return;
        setState(() {
          proposal = revised;
          operations = rows(revised['operaciones']);
        });
        if (revised['estado'] != 'LISTO') {
          throw ApiException(
            409,
            'El modelo cambió. Genera una propuesta nueva sobre la revisión actual.',
          );
        }
        if (operations.any(
          (o) => o['seleccionada'] == true && o['estado'] == 'INVALIDO',
        )) {
          throw ApiException(
            422,
            'Hay cambios inválidos. Revisa las advertencias o descártalos.',
          );
        }
      }
      final result =
          await widget.api.request(
                'POST',
                '$path/${apply ? 'aplicar' : 'rechazar'}',
                body: apply ? null : {'motivo': 'Rechazada desde Android'},
              )
              as Json;
      if (mounted) Navigator.pop(context, result);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void toggle(Json operation, bool checked) {
    final byId = {for (final o in operations) o['id']: o};
    final visited = <dynamic>{};
    void select(Json o) {
      if (!visited.add(o['id'])) return;
      o['seleccionada'] = true;
      for (final id in (o['dependencias'] as List? ?? [])) {
        if (byId[id] != null) select(byId[id]!);
      }
    }

    void unselect(Json o) {
      if (!visited.add(o['id'])) return;
      o['seleccionada'] = false;
      for (final other in operations) {
        if ((other['dependencias'] as List? ?? []).contains(o['id'])) {
          unselect(other);
        }
      }
    }

    setState(() {
      checked ? select(operation) : unselect(operation);
    });
  }

  String describe(dynamic value) {
    if (value is! Map) return value?.toString() ?? '—';
    return value.entries
        .map(
          (e) =>
              '${e.key}: ${e.value is Map || e.value is List ? jsonEncode(e.value) : e.value}',
        )
        .join('\n');
  }

  @override
  Widget build(BuildContext context) {
    final stale =
        proposal != null &&
        (proposal!['revisionBase'] != widget.currentRevision ||
            proposal!['revisionBase'] != proposal!['revisionActual'] ||
            proposal!['estado'] != 'LISTO');
    return FractionallySizedBox(
      heightFactor: .93,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    'Revisar propuesta',
                    style: TextStyle(fontSize: 23, fontWeight: FontWeight.bold),
                  ),
                ),
                IconButton(
                  onPressed: busy ? null : () => Navigator.pop(context),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
          ),
          if (busy) const LinearProgressIndicator(),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                if (proposal != null) ...[
                  Text('${proposal!['resumen']}'),
                  const SizedBox(height: 8),
                  Text(
                    'Origen: ${proposal!['tipoEntrada']} · revisión ${proposal!['revisionBase']}',
                  ),
                  if (stale)
                    const ErrorBox(
                      'Esta propuesta ya no es aplicable. Actualiza el modelo y solicita una nueva.',
                    ),
                  const SizedBox(height: 16),
                ],
                if (error != null) ErrorBox(error!),
                ...operations.map(
                  (o) => Card(
                    child: Column(
                      children: [
                        CheckboxListTile(
                          value: o['seleccionada'] == true,
                          onChanged: busy || stale || !widget.canEdit
                              ? null
                              : (v) => toggle(o, v ?? false),
                          title: Text(
                            '${o['tipoOperacion']} ${o['tipoEntidad']}',
                          ),
                          subtitle: Text(
                            '${(o['valorPropuesto'] as Map?)?['nombre'] ?? (o['valorActual'] as Map?)?['nombre'] ?? o['justificacion'] ?? ''}',
                          ),
                        ),
                        ExpansionTile(
                          title: const Text('Ver cambios y validaciones'),
                          childrenPadding: const EdgeInsets.all(16),
                          expandedCrossAxisAlignment:
                              CrossAxisAlignment.stretch,
                          children: [
                            const Text(
                              'ACTUAL',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            SelectableText(describe(o['valorActual'])),
                            const SizedBox(height: 12),
                            const Text(
                              'PROPUESTO',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            SelectableText(describe(o['valorPropuesto'])),
                            if (o['mensajesValidacion'] != null)
                              Text(describe(o['mensajesValidacion'])),
                            if (o['estado'] == 'INVALIDO')
                              const Text(
                                'Cambio inválido',
                                style: TextStyle(color: Colors.red),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed:
                          busy ||
                              proposal == null ||
                              proposal!['estado'] != 'LISTO' ||
                              !widget.canEdit
                          ? null
                          : () => decide(false),
                      child: const Text('Rechazar'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      onPressed:
                          busy ||
                              stale ||
                              proposal == null ||
                              !widget.canEdit ||
                              !operations.any((o) => o['seleccionada'] == true)
                          ? null
                          : () => decide(true),
                      child: const Text('Aplicar cambios'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
