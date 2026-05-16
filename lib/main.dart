import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Blackout Zone',

      theme: ThemeData(
        fontFamily: 'Inter',
        useMaterial3: true,
      ),

      debugShowCheckedModeBanner: false,

      home: const TriageScreen(),
    );
  }
}

class TriageScreen extends StatefulWidget {
  const TriageScreen({super.key});

  @override
  State<TriageScreen> createState() => _TriageScreenState();
}

class _TriageScreenState extends State<TriageScreen> {

  static const _channel = MethodChannel('com.blackoutzone/triage');

  final _symptomsController = TextEditingController();

  bool _loading = false;

  String? _result;
  String? _error;
  String? _modelSchema;

  bool _showModelSchema = false;

  String? _offlineModeNote;

  @override
  void initState() {
    super.initState();
    _loadOfflineMode();
  }

  Future<void> _loadOfflineMode() async {

    try {

      final mode =
          await _channel.invokeMethod<Map<Object?, Object?>>(
        'getOfflineMode',
      );

      final enabled = mode?['gemmaEnabled'] == true;

      final message = mode?['message']?.toString();

      if (!mounted) return;

      setState(() {

        _offlineModeNote = enabled
            ? 'On-device Gemma is available on this phone.'
            : (message ??
                'Using local medical protocols only on this device.');
      });

    } catch (_) {
      // Non-fatal
    }
  }

  @override
  void dispose() {
    _symptomsController.dispose();
    super.dispose();
  }

  Color _getPriorityColor(String result) {

    if (result.contains('TRIAGE: RED')) {
      return Colors.red.shade100;
    }

    if (result.contains('TRIAGE: YELLOW')) {
      return Colors.orange.shade100;
    }

    if (result.contains('TRIAGE: GREEN')) {
      return Colors.green.shade100;
    }

    return Colors.grey.shade100;
  }

  Color _getPriorityTextColor(String result) {

    if (result.contains('TRIAGE: RED')) {
      return Colors.red.shade900;
    }

    if (result.contains('TRIAGE: YELLOW')) {
      return Colors.orange.shade900;
    }

    if (result.contains('TRIAGE: GREEN')) {
      return Colors.green.shade900;
    }

    return Colors.grey.shade900;
  }

  Future<void> _getModelSchema() async {

    setState(() {
      _loading = true;
    });

    try {

      final schema =
          await _channel.invokeMethod<String>('getModelSchema');

      setState(() {

        _modelSchema = schema ?? 'No schema data.';
        _showModelSchema = true;
      });

    } on PlatformException catch (e) {

      setState(() {
        _error = '${e.code}: ${e.message ?? 'Unknown error'}';
      });

    } catch (e) {

      setState(() {
        _error = e.toString();
      });

    } finally {

      if (mounted) {

        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _analyze() async {

    final symptoms = _symptomsController.text.trim();

    if (symptoms.isEmpty) {

      setState(() {

        _error =
            'Please describe symptoms (short, plain language).';

        _result = null;
      });

      return;
    }

    setState(() {

      _loading = true;

      _error = null;

      _result = null;
    });

    try {

      final response =
          await _channel.invokeMethod<String>(
        'analyzeSymptoms',
        {'symptoms': symptoms},
      );

      setState(() {

        _result = response ?? 'No response.';
      });

    } on PlatformException catch (e) {

      setState(() {

        _error =
            '${e.code}: ${e.message ?? 'Unknown error'}';
      });

    } catch (e) {

      setState(() {
        _error = e.toString();
      });

    } finally {

      if (mounted) {

        setState(() {
          _loading = false;
        });
      }
    }
  }

  Widget _sampleChip(String text) {

    return ActionChip(

      label: Text(text),

      onPressed: () {

        _symptomsController.text = text;
      },
    );
  }

  @override
  Widget build(BuildContext context) {

    return Scaffold(

      appBar: AppBar(
        title: const Text(
          'Blackout Zone Triage (Offline)',
        ),
        backgroundColor:
            Theme.of(context).colorScheme.surface,
        elevation: 2,
      ),

      body: SafeArea(

        child: ListView(

          padding: const EdgeInsets.all(20),

          children: [

            Container(
              width: double.infinity,

              padding: const EdgeInsets.all(14),

              margin: const EdgeInsets.only(bottom: 16),

              decoration: BoxDecoration(
                color: Colors.green.shade50,

                borderRadius: BorderRadius.circular(14),

                border: Border.all(
                  color: Colors.green.shade300,
                ),
              ),

              child: const Row(
                children: [

                  Icon(
                    Icons.offline_bolt,
                    color: Colors.green,
                  ),

                  SizedBox(width: 10),

                  Expanded(
                    child: Text(
                      'Offline AI Active • No internet required',

                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            Text(
              'Describe symptoms and context. Keep it factual: age, major conditions, what happened, bleeding, breathing, etc.',

              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(
                    color: Colors.grey.shade700,
                  ),
            ),

            if (_offlineModeNote != null) ...[

              const SizedBox(height: 12),

              Container(

                padding: const EdgeInsets.all(12),

                decoration: BoxDecoration(
                  color: Colors.blue.shade50,

                  borderRadius:
                      BorderRadius.circular(8),

                  border: Border.all(
                    color: Colors.blue.shade200,
                  ),
                ),

                child: Text(
                  _offlineModeNote!,

                  style: TextStyle(
                    fontSize: 13,
                    color: Colors.blue.shade900,
                  ),
                ),
              ),
            ],

            const SizedBox(height: 16),

            TextField(

              controller: _symptomsController,

              minLines: 4,
              maxLines: 10,

              textInputAction:
                  TextInputAction.newline,

              decoration: const InputDecoration(
                labelText: 'Symptoms',

                alignLabelWithHint: true,

                hintText:
                    'Example: 35M, crushing chest pain 20 min, sweating...',

                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 12),

            Wrap(
              spacing: 8,
              runSpacing: 8,

              children: [

                _sampleChip(
                    'Chest pain + sweating'),

                _sampleChip(
                    'Heavy bleeding'),

                _sampleChip(
                    'Mild burn with blister'),

                _sampleChip(
                    'Broken arm after fall'),

                _sampleChip(
                    'Severe dehydration'),
              ],
            ),

            const SizedBox(height: 20),

            SizedBox(

              width: double.infinity,
              height: 52,

              child: FilledButton(

                onPressed:
                    _loading ? null : _analyze,

                child: _loading

                    ? const SizedBox(
                        width: 20,
                        height: 20,

                        child:
                            CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )

                    : const Text(
                        'Analyze (Offline)',

                        style: TextStyle(
                          fontSize: 16,
                          fontWeight:
                              FontWeight.w600,
                        ),
                      ),
              ),
            ),

            const SizedBox(height: 12),

            SizedBox(

              width: double.infinity,
              height: 52,

              child: OutlinedButton(

                onPressed:
                    _loading ? null : _getModelSchema,

                child: const Text(
                  'Offline AI Details',

                  style: TextStyle(
                    fontSize: 16,
                    fontWeight:
                        FontWeight.w600,
                  ),
                ),
              ),
            ),

            const SizedBox(height: 20),

            if (_loading)

              Container(

                padding:
                    const EdgeInsets.all(16),

                margin:
                    const EdgeInsets.only(
                  bottom: 16,
                ),

                decoration: BoxDecoration(
                  color: Colors.blue.shade50,

                  borderRadius:
                      BorderRadius.circular(14),
                ),

                child: Row(

                  children: [

                    const CircularProgressIndicator(),

                    const SizedBox(width: 16),

                    Expanded(

                      child: Text(
                        'Analyzing symptoms locally using offline AI...',

                        style: TextStyle(
                          fontSize: 16,
                          fontWeight:
                              FontWeight.w600,

                          color:
                              Colors.blue.shade900,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            if (_error != null)

              Container(

                padding:
                    const EdgeInsets.all(12),

                decoration: BoxDecoration(
                  color: Colors.red.shade50,

                  borderRadius:
                      BorderRadius.circular(8),
                ),

                child: Text(

                  _error!,

                  style: TextStyle(
                    color: Theme.of(context)
                        .colorScheme
                        .error,
                  ),
                ),
              ),

            if (_showModelSchema &&
                _modelSchema != null) ...[

              const Divider(),

              const SizedBox(height: 12),

              Text(
                'Offline AI Details',

                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(
                      fontWeight:
                          FontWeight.bold,
                    ),
              ),

              const SizedBox(height: 12),

              Container(

                width: double.infinity,

                padding:
                    const EdgeInsets.all(16),

                decoration: BoxDecoration(
                  color: Colors.blue.shade50,

                  borderRadius:
                      BorderRadius.circular(12),

                  border: Border.all(
                    color: Colors.blue.shade200,
                  ),
                ),

                child: SelectableText(

                  _modelSchema!,

                  style: TextStyle(
                    color:
                        Colors.blue.shade900,

                    fontSize: 12,

                    fontFamily: 'monospace',

                    height: 1.5,
                  ),
                ),
              ),

              const SizedBox(height: 20),
            ],

            if (_result != null) ...[

              const Divider(),

              const SizedBox(height: 12),

              Text(

                'Emergency Guidance',

                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(
                      fontWeight:
                          FontWeight.bold,
                    ),
              ),

              const SizedBox(height: 12),

              Container(

                width: double.infinity,

                padding:
                    const EdgeInsets.all(16),

                decoration: BoxDecoration(

                  color:
                      _getPriorityColor(
                    _result!,
                  ),

                  borderRadius:
                      BorderRadius.circular(12),

                  border: Border.all(
                    color:
                        _getPriorityTextColor(
                      _result!,
                    ).withOpacity(0.3),
                  ),
                ),

                child: SelectableText(

                  _result!,

                  style: TextStyle(
                    color:
                        _getPriorityTextColor(
                      _result!,
                    ),

                    fontSize: 16,

                    height: 1.5,
                  ),
                ),
              ),
            ],

            const SizedBox(height: 24),

            Container(

              padding:
                  const EdgeInsets.all(12),

              decoration: BoxDecoration(
                color: Colors.amber.shade50,

                borderRadius:
                    BorderRadius.circular(8),

                border: Border.all(
                  color: Colors.amber.shade200,
                ),
              ),

              child: Text(

                'Important: This is not a doctor. If there is severe bleeding, trouble breathing, chest pain, or stroke signs, prioritize emergency action immediately.',

                style: TextStyle(
                  fontSize: 12,
                  color: Colors.amber.shade900,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}