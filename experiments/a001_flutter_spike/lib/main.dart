import 'package:flutter/material.dart';

void main() => runApp(const SpikeApp());

class SpikeApp extends StatelessWidget {
  const SpikeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'A-001 Spike',
      theme: ThemeData(colorSchemeSeed: Colors.teal),
      home: const LoginPage(),
    );
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Sentetik Giriş')),
      body: Center(
        child: FilledButton(
          key: const Key('login'),
          onPressed: () => Navigator.of(
            context,
          ).push(MaterialPageRoute<void>(builder: (_) => const StudentPage())),
          child: const Text('Hoca olarak giriş yap'),
        ),
      ),
    );
  }
}

class StudentPage extends StatelessWidget {
  const StudentPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ORG-A — Gül Sınıfı')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Öğrenci listesi'),
            const ListTile(title: Text('Ayşe Demir')),
            const ListTile(title: Text('Mehmet Kaya')),
            FilledButton(
              key: const Key('attendance'),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const AttendancePage()),
              ),
              child: const Text('Bugünkü yoklamayı aç'),
            ),
          ],
        ),
      ),
    );
  }
}

class AttendancePage extends StatefulWidget {
  const AttendancePage({super.key});

  @override
  State<AttendancePage> createState() => _AttendancePageState();
}

class _AttendancePageState extends State<AttendancePage> {
  var present = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Bugünkü Yoklama')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Ayşe Demir: ${present ? 'Geldi' : 'İşaretlenmedi'}'),
            const SizedBox(height: 12),
            FilledButton(
              key: const Key('mark-present'),
              onPressed: () => setState(() => present = true),
              child: const Text('Geldi olarak işaretle'),
            ),
          ],
        ),
      ),
    );
  }
}
