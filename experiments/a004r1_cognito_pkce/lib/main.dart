import 'package:flutter/material.dart';
import 'package:flutter_appauth/flutter_appauth.dart';

void main() {
  runApp(A004R1App(config: ExperimentConfig.fromEnvironment()));
}

class ExperimentConfig {
  const ExperimentConfig({
    required this.domain,
    required this.clientId,
    this.redirectUri = 'kursplatforma004r1://oauth2redirect',
  });

  factory ExperimentConfig.fromEnvironment() => const ExperimentConfig(
    domain: String.fromEnvironment('COGNITO_DOMAIN'),
    clientId: String.fromEnvironment('COGNITO_CLIENT_ID'),
  );

  final String domain;
  final String clientId;
  final String redirectUri;

  bool get isValid {
    final uri = Uri.tryParse('https://$domain');
    return domain.isNotEmpty &&
        clientId.isNotEmpty &&
        uri != null &&
        uri.host == domain &&
        uri.scheme == 'https' &&
        uri.host.endsWith('.auth.eu-central-1.amazoncognito.com') &&
        redirectUri == 'kursplatforma004r1://oauth2redirect';
  }

  AuthorizationServiceConfiguration get serviceConfiguration {
    if (!isValid) {
      throw StateError('Cognito experiment configuration is missing.');
    }
    return AuthorizationServiceConfiguration(
      authorizationEndpoint: 'https://$domain/oauth2/authorize',
      tokenEndpoint: 'https://$domain/oauth2/token',
      endSessionEndpoint: 'https://$domain/logout',
    );
  }
}

class PkceEvidence {
  const PkceEvidence({
    required this.authorizationCodeReceived,
    required this.codeVerifierGenerated,
    required this.nonceGenerated,
    required this.accessTokenReceived,
    required this.idTokenReceived,
    required this.refreshTokenReceived,
    required this.accessTokenExpiresAt,
  });

  final bool authorizationCodeReceived;
  final bool codeVerifierGenerated;
  final bool nonceGenerated;
  final bool accessTokenReceived;
  final bool idTokenReceived;
  final bool refreshTokenReceived;
  final DateTime? accessTokenExpiresAt;

  bool get passed =>
      authorizationCodeReceived &&
      codeVerifierGenerated &&
      nonceGenerated &&
      accessTokenReceived &&
      idTokenReceived &&
      refreshTokenReceived;
}

abstract class AuthorizationClient {
  Future<PkceEvidence> signIn(ExperimentConfig config);
}

class AppAuthAuthorizationClient implements AuthorizationClient {
  AppAuthAuthorizationClient({FlutterAppAuth? appAuth})
    : _appAuth = appAuth ?? const FlutterAppAuth();

  final FlutterAppAuth _appAuth;

  @override
  Future<PkceEvidence> signIn(ExperimentConfig config) async {
    final authorization = await _appAuth.authorize(
      AuthorizationRequest(
        config.clientId,
        config.redirectUri,
        serviceConfiguration: config.serviceConfiguration,
        scopes: const ['openid', 'profile'],
        promptValues: const ['login'],
        externalUserAgent:
            ExternalUserAgent.ephemeralAsWebAuthenticationSession,
      ),
    );
    if (authorization.authorizationCode == null ||
        authorization.codeVerifier == null ||
        authorization.nonce == null) {
      throw StateError('Authorization response is incomplete.');
    }

    final token = await _appAuth.token(
      TokenRequest(
        config.clientId,
        config.redirectUri,
        authorizationCode: authorization.authorizationCode,
        codeVerifier: authorization.codeVerifier,
        nonce: authorization.nonce,
        serviceConfiguration: config.serviceConfiguration,
        scopes: const ['openid', 'profile'],
      ),
    );

    // Token values are deliberately reduced to booleans and never persisted,
    // printed, or rendered. A-004R1 only needs proof of the PKCE exchange.
    return PkceEvidence(
      authorizationCodeReceived: authorization.authorizationCode!.isNotEmpty,
      codeVerifierGenerated: authorization.codeVerifier!.isNotEmpty,
      nonceGenerated: authorization.nonce!.isNotEmpty,
      accessTokenReceived: token.accessToken?.isNotEmpty ?? false,
      idTokenReceived: token.idToken?.isNotEmpty ?? false,
      refreshTokenReceived: token.refreshToken?.isNotEmpty ?? false,
      accessTokenExpiresAt: token.accessTokenExpirationDateTime,
    );
  }
}

class A004R1App extends StatelessWidget {
  const A004R1App({super.key, required this.config, this.authorizationClient});

  final ExperimentConfig config;
  final AuthorizationClient? authorizationClient;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: ExperimentPage(
        config: config,
        authorizationClient:
            authorizationClient ?? AppAuthAuthorizationClient(),
      ),
    );
  }
}

class ExperimentPage extends StatefulWidget {
  const ExperimentPage({
    super.key,
    required this.config,
    required this.authorizationClient,
  });

  final ExperimentConfig config;
  final AuthorizationClient authorizationClient;

  @override
  State<ExperimentPage> createState() => _ExperimentPageState();
}

class _ExperimentPageState extends State<ExperimentPage> {
  bool _busy = false;
  PkceEvidence? _evidence;
  String? _message;

  Future<void> _run() async {
    setState(() {
      _busy = true;
      _evidence = null;
      _message = null;
    });
    try {
      final evidence = await widget.authorizationClient.signIn(widget.config);
      if (!mounted) return;
      setState(() {
        _evidence = evidence;
        _message = evidence.passed
            ? 'Authorization Code + PKCE deneyi geçti.'
            : 'Yanıt geldi ancak zorunlu kanıtlardan biri eksik.';
      });
    } on FlutterAppAuthUserCancelledException {
      if (!mounted) return;
      setState(() => _message = 'Giriş kullanıcı tarafından iptal edildi.');
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _message = 'Giriş başarısız. Token veya parola kaydedilmedi.';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final evidence = _evidence;
    return Scaffold(
      appBar: AppBar(title: const Text('A-004R1 Cognito PKCE')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Bu deney sistem tarayıcısını ve secretsiz public mobile clientı '
            'kullanır. Parola ve token değerleri uygulamada gösterilmez veya '
            'saklanmaz.',
          ),
          const SizedBox(height: 20),
          if (!widget.config.isValid)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Yapılandırma eksik. COGNITO_DOMAIN ve COGNITO_CLIENT_ID '
                  '--dart-define ile verilmelidir.',
                ),
              ),
            ),
          FilledButton.icon(
            key: const Key('start-pkce'),
            onPressed: widget.config.isValid && !_busy ? _run : null,
            icon: _busy
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.login),
            label: const Text('Gerçek PKCE girişini başlat'),
          ),
          if (_message != null) ...[
            const SizedBox(height: 20),
            Semantics(
              liveRegion: true,
              child: Text(_message!, key: const Key('result-message')),
            ),
          ],
          if (evidence != null) ...[
            const SizedBox(height: 16),
            _EvidenceRow(
              'Authorization code alındı',
              evidence.authorizationCodeReceived,
            ),
            _EvidenceRow(
              'PKCE code_verifier üretildi',
              evidence.codeVerifierGenerated,
            ),
            _EvidenceRow('OIDC nonce üretildi', evidence.nonceGenerated),
            _EvidenceRow('Access token alındı', evidence.accessTokenReceived),
            _EvidenceRow('ID token alındı', evidence.idTokenReceived),
            _EvidenceRow('Refresh token alındı', evidence.refreshTokenReceived),
            if (evidence.accessTokenExpiresAt != null)
              const Text('Access token için son kullanma zamanı alındı.'),
          ],
        ],
      ),
    );
  }
}

class _EvidenceRow extends StatelessWidget {
  const _EvidenceRow(this.label, this.ok);

  final String label;
  final bool ok;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      leading: Icon(
        ok ? Icons.check_circle : Icons.error,
        color: ok ? Colors.green : Colors.red,
      ),
      title: Text(label),
    );
  }
}
