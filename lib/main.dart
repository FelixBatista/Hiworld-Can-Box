import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const TeyesCanListenerApp());
}

class TeyesCanListenerApp extends StatelessWidget {
  const TeyesCanListenerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TEYES CAN Listener',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const CanHomePage(),
    );
  }
}

class CanHomePage extends StatefulWidget {
  const CanHomePage({super.key});

  @override
  State<CanHomePage> createState() => _CanHomePageState();
}

class _CanHomePageState extends State<CanHomePage> {
  static const _channel = EventChannel('teyes_can_stream');
  static const _control = MethodChannel('teyes_can_control');

  StreamSubscription<dynamic>? _subscription;

  num? _rpm;
  num? _speed;
  final List<Map<String, dynamic>> _messages = <Map<String, dynamic>>[];
  static const int _maxMessages = 200;
  bool _testMode = false;

  @override
  void initState() {
    super.initState();
    _subscription = _channel.receiveBroadcastStream().listen(
      _onEvent,
      onError: (e) {},
      cancelOnError: false,
    );
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  void _onEvent(dynamic event) {
    if (event is Map) {
      final Map<String, dynamic> map = event.cast<String, dynamic>();
      final num? rpm = _extractNumeric(map, const [
        'rpm',
        'RPM',
        'engineRpm',
        'engine_rpm',
        'tacho',
      ]);
      final num? speed = _extractNumeric(map, const [
        'speed',
        'Speed',
        'vehicleSpeed',
        'vehicle_speed',
        'veh_speed',
      ]);

      setState(() {
        _rpm = rpm ?? _rpm;
        _speed = speed ?? _speed;

        _messages.insert(0, map);
        if (_messages.length > _maxMessages) {
          _messages.removeRange(_maxMessages, _messages.length);
        }
      });
    }
  }

  num? _extractNumeric(Map<String, dynamic> map, List<String> candidates) {
    for (final key in map.keys) {
      if (candidates.any((c) => key.toLowerCase().contains(c.toLowerCase()))) {
        final value = map[key];
        final parsed = _toNum(value);
        if (parsed != null) return parsed;
      }
    }
    return null;
  }

  num? _toNum(dynamic value) {
    if (value is num) return value;
    if (value is String) {
      final d = num.tryParse(value);
      if (d != null) return d;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('TEYES Phase B - CAN Listener'),
        actions: [
          IconButton(
            tooltip: _testMode ? 'Stop Test' : 'Start Test',
            icon: Icon(_testMode ? Icons.stop_circle_outlined : Icons.play_circle_outline),
            onPressed: _toggleTest,
          ),
        ],
      ),
      body: Column(
        children: [
          _Header(rpm: _rpm, speed: _speed),
          const Divider(height: 1),
          Expanded(child: _MessageList(messages: _messages)),
        ],
      ),
    );
  }

  Future<void> _toggleTest() async {
    try {
      if (_testMode) {
        await _control.invokeMethod('stopTest');
      } else {
        await _control.invokeMethod('startTest');
      }
      if (mounted) {
        setState(() {
          _testMode = !_testMode;
        });
      }
    } catch (_) {}
  }
}

class _Header extends StatelessWidget {
  final num? rpm;
  final num? speed;
  const _Header({required this.rpm, required this.speed});

  @override
  Widget build(BuildContext context) {
    final textStyle = Theme.of(context).textTheme.titleLarge;
    String rpmStr = rpm == null ? '—' : rpm!.toString();
    String speedStr = speed == null ? '—' : speed!.toString();
    return Container(
      width: double.infinity,
      color: Theme.of(context).colorScheme.surfaceVariant,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          _kv('RPM', rpmStr, textStyle),
          _kv('Speed', speedStr, textStyle),
        ],
      ),
    );
  }

  Widget _kv(String k, String v, TextStyle? style) {
    return Row(children: [
      Text('$k: ', style: style?.copyWith(fontWeight: FontWeight.w600)),
      Text(v, style: style),
    ]);
  }
}

class _MessageList extends StatelessWidget {
  final List<Map<String, dynamic>> messages;
  const _MessageList({required this.messages});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      reverse: false,
      itemCount: messages.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final msg = messages[index];
        final ts = msg['timestamp'];
        final action = msg['action'] ?? '';
        // Render a compact single-line summary: time, action, and keys
        final keys = msg.keys.where((k) => k != 'timestamp' && k != 'action').toList()..sort();
        final kvs = keys.map((k) => '$k=${msg[k]}').join('  ');
        return ListTile(
          dense: true,
          title: Text('$action'),
          subtitle: Text('t=$ts  $kvs'),
        );
      },
    );
  }
}
