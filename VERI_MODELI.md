# Çekirdek Veri Modeli Taslağı

| Alan | Değer |
|---|---|
| Görev | P-008 — Çekirdek veri modeli taslağını yaz |
| Belge sürümü | 4.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Yetki kaynağı | `YETKI_MATRISI.md` |
| Veri hassasiyet kaynağı | `KISISEL_VERI_ENVANTERI.md` |
| Çapraz kontrol | `AKTORLER_VE_KULLANIM_SENARYOLARI.md`, `YONETICI_BILGI_MIMARISI.md`, `HOCA_MOBIL_BILGI_MIMARISI.md` |
| Son güncelleme | 15 Temmuz 2026 |

---

## PLAN-005 sağlayıcı revizyon notu

A-004R3 tamamlanana kadar bu belgedeki `issuer + subject`, platform `user_id`, üyelik/rol/izin,
opaque platform token aileleri, `session_generation`, kurum kapsamlı iptal, provider-command
idempotency ve fail-closed `PROVISIONING` değişmezdir. `Keycloak` adı geçen create/finalize,
event polling, `Location`, `auth_time` ve token değişim ayrıntıları ise A-004'ün fallback
tasarımını belgeler; Cognito Essentials seçilirse A-004R1–A-004R3/IAM-001 aynı güvenlik sonuçlarını
sağlayacak alan ve akışları kesinleştirir. Bu ayrıntılar A-004R3 öncesinde migration'a veya
provider SDK'sına dönüştürülemez.

## Revizyon notu (v1.0 → v2.0)

1. Global kimlik (`users`/`people`), kurum üyeliği ayrımı (`organization_memberships`).
2. İzin ataması kurum üyeliğine bağlandı, sınıf atamasına değil.
3. Tenant bütünlüğü için bileşik `(id, organization_id)` FK tekniği.
4. `people.phone` `NOT NULL` yapıldı.
5. Öğrenci–sınıf ve hoca–sınıf ilişkileri tarihçeli/ayrık tablolara taşındı.
6. NULL-güvenli benzersizlik ilkesi (partial index'ler).
7. `plan_items`e `sequence_no` ile aynı gün çoklu kalem desteği.
8. Özel alan modelinde ayrılmış nullable FK + XOR CHECK.
9. Güncellenebilir kayıt / değişmez audit ayrımı netleştirildi.
10. `audit_logs.organization_id` nullable + `event_scope`.
11. İzin kataloğu kategorisi referans tabloya çevrildi; `RESTORE_ARCHIVED` ortak izin kararı.
12. Belge tutarlılığı — P-005/P-006 çapraz kontrolü.

(v1.0 → v2.0 ayrıntıları için önceki PR yorumlarına ve `git log`'a bakılabilir; bu bölüm v3.0
ile birlikte özetlenmiştir.)

## Revizyon notu (v2.0 → v3.0)

İkinci merkez incelemesinde bulunan sorunlar giderildi:

1. **`people` yeniden kurum kapsamlı oldu; `users` global kaldı.** v2.0'da her ikisi de global
   yapılmıştı — bu, aynı kurumun PII'sinin (ad, telefon, adres, not, fotoğraf) örtük olarak
   kurumlar arası paylaşılan bir havuzda tutulması riskini doğuruyordu. v3.0'da yalnızca
   kimlik doğrulama kimliği (`users`) globaldir; kişi profili (`people`) her zaman bir kuruma
   aittir. Köprü, `organization_memberships.person_id` ile kurulur (bkz. bölüm 4).
2. **Kurum üyeliği ile rol ayrıldı; çoklu rol desteklendi.** `organization_memberships.
   membership_role` tekil sütunu kaldırıldı; yerine `organization_membership_roles` tarihçeli
   çoklu-rol tablosu geldi. Aynı kullanıcı aynı kurumda hem `ORG_ADMIN` hem `TEACHER` olabilir.
3. **Literal içeren geçersiz FK sözdizimi düzeltildi.** `(...,'TEACHER') REFERENCES ...` gibi
   ifadeler gerçek SQL değildir. Artık her "pinlenmiş" bileşik FK, çocuk tabloda gerçek, sabit
   bir `CHECK` ile kilitlenmiş bir sütuna (`role_code`/`target_role_code`/`granted_role_code`)
   dayanır — bkz. bölüm 15.6.
4. **Kurum kapsamlı oturum iptali.** `refresh_tokens` artık hangi kurum üyeliği bağlamında
   verildiğini taşır; bir kurum yöneticisinin cihaz oturumu iptali yalnızca kendi kurumundaki
   oturumları etkiler (bkz. bölüm 4.11, `YETKI_MATRISI.md`'ye eklenen not).
5. **Ebeveyn-zinciri bütünlüğü.** `programs.current_program_version_id`, `plan_items.
   source_template_day_id` gibi ilişkiler artık bileşik FK ile doğru ebeveyne bağlanmayı
   zorunlu kılıyor; `plan_items.class_id` fazlalık/riskli ikinci kaynak olduğu için kaldırıldı
   (sınıf, `program_version → program` üzerinden türetilir). Statik olarak ifade edilemeyen
   zamana bağlı kurallar (öğrencinin oturum/plan tarihinde ilgili sınıfa kayıtlı olması vb.)
   `P-009`/`P-010` kabul senaryosu adayı olarak açıkça listelendi (bölüm 15.5).
   `student_class_enrollments`'ta tarih aralığı çakışması `EXCLUDE` kısıtıyla DB seviyesinde
   engellendi.
6. **Özel alan tip güvenliği güçlendirildi.** `field_type`/`entity_type` ilgili tablolara
   denormalize edilip bileşik FK + `CHECK` ile bağlandı; `SINGLE_CHOICE` için en fazla bir
   seçenek artık **DB seviyesinde** (partial unique index) zorlanıyor — önceki sürümde
   "uygulama katmanı sorumluluğu" olarak bırakılmıştı.
7. **Audit scope bütünlüğü.** `audit_logs.event_scope` gerçek bir sütun oldu;
   `audit_action_catalog` ile bileşik FK ve `organization_id`/`event_scope` tutarlılığını
   zorlayan `CHECK` eklendi.
8. **Menü sırası.** `organization_modules`'a `sort_order` eklendi (§9.2).
9. **Belge tutarlılığı.** Modül-varlık haritası, ilişki özeti, tenant istisna listesi,
   varsayımlar, sınırlamalar ve ana plan uyum kontrolü yeni modele göre güncellendi;
   `TERIMLER_SOZLUGU.md`'deki eski rol atama açıklaması ve `YETKI_MATRISI.md`'deki cihaz oturumu
   iptali satırı yeni modele göre netleştirildi (bu görevin PR'ında ayrıca not edilmiştir).

## Revizyon notu (v3.0 → v4.0)

Üçüncü merkez incelemesinde bulunan son teknik bütünlük/güvenlik sorunları giderildi:

1. **Platform yöneticisi profil fotoğrafı izolasyonu.** `platform_administrator_profiles.
   photo_asset_id`'nin kurum kapsamlı `file_assets`'e düz FK vermesi, global bir profilin
   herhangi bir kurumun dosyasına bağlanabilmesi riskini taşıyordu. Bu alan **kaldırılmıştır**
   (bkz. 4.4) — V1 için en sade çözüm; platform yöneticisi profilinin fotoğrafı ilk sürümde
   desteklenmez.
2. **Kurum kişisi ↔ kullanıcı hesabı tekilliği.** `organization_memberships`e `UNIQUE
   (person_id)` eklendi — bir `people` satırı en fazla bir global kullanıcı hesabına bağlanır
   (bkz. 4.5).
3. **Pinlenmiş rol sütunlarının tip uyumu.** `target_role_code`, `granted_role_code`,
   `class_teacher_assignments.role_code` önceki sürümde `TEXT` idi; `organization_membership_
   roles.role` ise `ENUM`. PostgreSQL'de bileşik bir FK'nin sütunları **aynı tipte** olmalıdır —
   `TEXT` bir sütun bir `ENUM` sütununa böyle bir FK ile bağlanamaz. Bütün pinlenmiş sütunlar
   artık `organization_membership_roles.role` ile **aynı** `membership_role_enum` tipini
   kullanır (bkz. 4.6, 4.9, 7.2).
4. **Cihaz/oturum sahiplik zinciri.** `trusted_devices`e `UNIQUE (id, user_id)`;
   `refresh_token_families`e bileşik FK `(trusted_device_id, user_id) REFERENCES trusted_devices
   (id, user_id)`, `refresh_tokens.token_hash` tekilliği ve aktif cihaz tekrarını önleyen partial
   `UNIQUE` eklendi
   (bkz. 4.10–4.11).
5. **Context-selection token güvenlik sınırı.** `organization_memberships.session_generation`
   sayacı, `refresh_tokens.issued_at_session_generation` anlık görüntüsü ve tek kullanımlı
   `context_selection_tokens` eklendi; tüketilmiş/iptal edilmiş/eski `auth_time` taşıyan tokenın
   iptal edilmiş bir kurum üyeliği için sessizce yeni kurum ailesi üretmesini engelleyen bağlayıcı
   kural yazıldı (bkz. 4.10a, 4.11, 15.5).
6. **Rol iptali yaşam döngüsü.** `TEACHER`/`ORG_ADMIN` rolü geri alındığında bağlı sınıf
   atamalarına, izin atamalarına ve geçmiş grantor kayıtlarına ne olacağı bağlayıcı biçimde
   yazıldı (yeni bölüm 4.12).
7. **Audit geri alma tenant bütünlüğü.** `audit_logs.undo_of_audit_log_id` artık scope-pinned
   iki bileşik FK ile korunuyor; kurumlar arası veya global/kurum karışık `undo` ilişkisi DB
   seviyesinde imkânsız (bkz. 13.2).
8. **`organizations.logo_asset_id` FK'si açık yazıldı** (bkz. 5.1).

---

## 1. Amaç ve kapsam

Bu belge, `URUN_VE_UYGULAMA_PLANI.md` içinde onaylanmış ilk sürüm (V1) kapsamının bütününü
taşıyan çekirdek veri modelini varlık/tablo seviyesinde tanımlar. Önceki Dalga 0 belgelerinin
izin şeması, program/şablon açık yapısı, özel alan genişletme modeli ve metaveri/operasyonel
veri ayrımı için bıraktığı kararları bağlayıcı biçimde verir.

Bu belge:

- Bütün V1 modüllerinin (`AGENT_GOREV_PLANI.md` bölüm 5) çekirdek varlıklarını, alanlarını,
  ilişkilerini, anahtarlarını ve kısıtlarını tanımlar.
- Kurum izolasyonu, arşivleme, denetim ve eşzamanlılık (sürüm/idempotency) için gereken temel
  alan ve tabloları kurar.
- Kurumlar arası veri sızıntısını yalnızca uygulama kontrolüne bırakmaz; mümkün olan her yerde
  veritabanı seviyesinde de (bileşik FK/UNIQUE/CHECK/EXCLUDE) zorunlu kılar (bkz. bölüm 15).
  Uygulama katmanındaki yetki/izolasyon kontrolü buna **ek**tir, onun yerine geçmez.
- Belirli bir veritabanı ürününe (`PostgreSQL` önerilir, kesin karar `A-003`) veya ORM'e
  bağlanmaz; alan tipleri mantıksal düzeyde (`UUID`, `TEXT`, `INTEGER`, `BOOLEAN`, `DATE`,
  `TIMESTAMPTZ`, `ENUM`, `JSONB`) verilir. Bölüm 15'teki teknikler (bileşik FK, pinlenmiş
  `CHECK`, `EXCLUDE`) `PostgreSQL` ve benzer ilişkisel veritabanlarında doğrudan uygulanabilir;
  farklı bir veritabanı seçilirse eşdeğer bir kısıt mekanizması (ör. tetikleyici) kullanılmalıdır.

Bu belge **değildir**:

- API istek/cevap şeması (`P-009` kapsamı).
- Senkronizasyon/çakışma protokolünün tam tel (wire) sözleşmesi (`P-010` kapsamı). Bu belge
  yalnızca `P-010`'un üzerine inşa edeceği temel alanları (sürüm sayacı, idempotency anahtarı
  deposu) kurar.
- Geri alma komutlarının kesin listesi ve kuralları `DENETIM_VE_GERI_ALMA_ILKELERI.md`'de tanımlıdır.
- Excel rapor sorgu/filtre sözleşmesi (`P-012` kapsamı).
- Belirli bir backend dili/framework'ü, hosting sağlayıcısı veya kimlik doğrulama sağlayıcısı
  seçimi (`A-002`, `A-003`, `A-004` ADR kapsamı).
- Gerçek migration dosyaları veya kod (Dalga 1 uygulama görevleri kapsamı).

---

## 2. Ortak tasarım ilkeleri

### 2.1. Birincil anahtar ve kimlik türü

- Bütün tablolarda birincil anahtar `UUID` olacaktır (`id`).
- Gerekçe: (1) istemci tarafında (mobil) çevrimdışı oluşturulan kayıtların sunucu ile
  çakışmadan kimliklenebilmesi (`P-010`) için otomatik artan tamsayıdan daha uygundur; (2) kurum
  kimliğinin sıralı tamsayı olması durumunda oluşabilecek bilgi sızıntısı riskleri azalır. Bu,
  bağlayıcı bir `P-008` kararıdır; `A-002`/`A-003` bu kararla çelişmeyecek bir veritabanı/
  hosting seçimi yapmalıdır.

### 2.2. Kurum izolasyonu ve global kimlik ayrımı

- Bir kuruma bağlı her tabloda açık bir `organization_id UUID` sütunu bulunur
  (`URUN_VE_UYGULAMA_PLANI.md` §11.4). **`people` bu kurala tabidir** — kurum kapsamlıdır,
  global değildir (bkz. bölüm 4.2; bu, önceki `v2.0` sürümünden farklıdır).
- **Yalnızca `users`, `platform_administrators` ve `platform_administrator_profiles` global
  kimlik/rol tablolarıdır** (bkz. bölüm 4.1, 4.3, 4.4). Aynı giriş hesabının birden fazla
  kurumda farklı rollere sahip olabilmesi (`TERIMLER_SOZLUGU.md` §3.1) bu üç tabloyla değil,
  **`users` (global kimlik doğrulama) ile `organization_memberships`/`organization_membership_
  roles` (kurum kapsamlı üyelik ve rol) arasındaki köprüyle** sağlanır: her kurum, o kullanıcı
  için **kendi ayrı `people` satırını** (kendi PII kopyasını) tutar — bkz. bölüm 4.5.
- Global kimlik tablolarının var olması, kurum izolasyonunu **gevşetmez**: bir kullanıcının
  hangi kurumlarda üyeliği olduğu bilgisi dahi yalnızca yetkili bağlamda sorgulanmalıdır.
- `organization_id` taşımayan bütün tabloların tam listesi ve gerekçesi bölüm 15.4'tedir.
- Kurum kapsamlı varlıklar arasındaki ilişkilerin **aynı kurum içinde kalması** yalnızca
  uygulama kontrolüne bırakılmaz; bölüm 15'te tanımlanan tekniklerle veritabanı seviyesinde de
  zorunlu kılınır. Sunucu tarafı yetki/rol doğrulaması (kullanıcının bu kuruma/sınıfa erişim
  **hakkı** olup olmadığı) yine de zorunludur ve bu belgenin kapsamı dışındadır (`P-009`);
  veritabanı kısıtları yalnızca **veri bütünlüğünü** korur, **yetkilendirmenin** yerine geçmez.

### 2.3. Zaman, sürüm ve denetim alanları

Aşağıdaki alan seti, aksi belirtilmedikçe her mutlak (silinmeyen/arşivlenen) çekirdek tabloda
tutarlı biçimde tekrarlanır:

| Alan | Tip | Açıklama |
|---|---|---|
| `created_at` | `TIMESTAMPTZ` | Kayıt oluşturulma zamanı (UTC saklanır, kurum saat dilimiyle sunulur). |
| `updated_at` | `TIMESTAMPTZ` | Son güncelleme zamanı. |
| `row_version` | `INTEGER`, varsayılan `1` | Her güncellemede artan iyimser eşzamanlılık sayacı. İstemci, değiştirdiği `row_version`'ı sunucuya bildirir; sunucu eşleşmiyorsa isteği reddeder (`URUN_VE_UYGULAMA_PLANI.md` §12.1). Kesin çakışma davranışı varlık türüne göre `P-010`'da ayrıntılandırılır. |
| `created_by_user_id` | `UUID`, nullable | Kaydı oluşturan kullanıcı (sistem/otomatik süreçlerde `NULL` olabilir). |
| `updated_by_user_id` | `UUID`, nullable | Kaydı son değiştiren kullanıcı. |

Bu beş alan aşağıdaki varlık tablolarında ayrıca tek tek yazılmamış, "temel alanlar" notuyla
başvurulmuştur. Aktör alanları (`created_by_user_id`, `recorded_by_user_id`, `granted_by_*`
hariç — bkz. bölüm 15.3) kasıtlı olarak `users(id)`'ye **düz** (bileşik olmayan) FK'dir.

### 2.4. Durum (status) alanları ve değişmezlik ayrımı

- Ana planın bölüm 14 hükmü gereği **öğrenci, sınıf, program ve kurum** normal arayüzden
  fiziksel silinmez; bunlarda `status` alanı zorunludur ve en az `ACTIVE`/`ARCHIVED` (kurum için
  ayrıca `SUSPENDED`) değerlerini taşır.
- Kullanıcı (`users`) fiziksel silinmez; `status` alanı `PROVISIONING`/`ACTIVE`/`SUSPENDED`
  taşır. `PROVISIONING`, Keycloak create/finalize uzlaştırması bitmeden giriş ve kurum seçimine
  kapalıdır.
- **Güncellenebilir güncel-durum tabloları:** `attendance_records` ve `progress_records`
  **değişmez geçmiş kaydı değildir** — bunlar bir öğrencinin/oturumun **güncel** durumunu
  tutan, `row_version` ile korunan, yerinde güncellenebilir satırlardır (§12.1, §12.4). Her
  güncelleme, eski ve yeni değeri `audit_logs`'a ayrıca yazar (§8.5, §8.10).
- **Yalnızca `audit_logs` değişmezdir:** Normal uygulama akışında güncellenmeyen veya
  silinmeyen tek tablo `audit_logs`'tur; geçmiş "ne olduğu" bilgisinin tek doğru kaynağı budur.

### 2.5. Kişisel veri ve hassasiyet işaretlemesi

`KISISEL_VERI_ENVANTERI.md` içinde "yüksek riskli kişisel veri (ürün içi sınıf)" olarak
işaretlenen her alan (telefon, adres, doğum tarihi, profil fotoğrafı, serbest metin not, kimlik
doğrulama sırrı, güvenilir cihaz bilgisi, yoklama durumu, ilerleme notu) aşağıdaki tablolarda
**[Hassas]** etiketiyle ayrıca işaretlenmiştir; bu etiket bölüm 2 KVKK sınıflaması değildir,
yalnızca bu envanterin ürün içi risk sınıfına geri referanstır.

### 2.6. NULL-güvenli benzersizlik ilkesi

PostgreSQL ve standart SQL'de `NULL`, kendisi dahil hiçbir değere eşit sayılmaz; bu nedenle
**nullable bir sütun bir `UNIQUE` kısıtının anahtar tuple'ının parçasıysa**, o sütun `NULL`
olduğunda kısıt tekrarları **engellemez**. Bu belgede bu hataya düşmemek için üç kural izlenir:

1. Bir kavramın kapsamı (ör. "global" ile "kurum kapsamlı") nullable bir ayırt edici sütunla
   ifade edilmek yerine, **ayrı tablolara bölünür** (ör. `platform_administrators` ile
   `organization_memberships`).
2. "Yalnızca aktif/güncel kaydı benzersiz kıl" ihtiyacı, nullable sütunu (ör. `revoked_at`,
   `unassigned_at`, `ended_at`) **kısıt tuple'ının dışında, yalnızca partial index `WHERE`
   koşulunda** kullanarak karşılanır — asla uniqueness anahtarının bir parçası olarak değil.
3. Bir varlığın kendisi doğası gereği "ya A ya B" şeklinde nullable iki ayrı hedefe işaret
   ediyorsa, her hedef için **ayrı bir partial unique index** tanımlanır; tek bir birleşik
   `UNIQUE` asla kullanılmaz.

---

## 3. Modül → varlık haritası

| Modül kodu | Modül | Bu belgede tanımlanan varlıklar |
|---|---|---|
| CORE | Ortak çekirdek | Ortak alanlar (bölüm 2), kapalı katalog tabloları |
| IAM | Kimlik ve erişim | `users`, `user_identities`, `people`, `platform_administrators`, `platform_administrator_profiles`, `organization_memberships`, `organization_membership_roles`, `permission_categories`, `permission_catalog`, `organization_membership_permissions`, `trusted_devices`, `context_selection_tokens`, `refresh_token_families`, `refresh_tokens`, `iam_provider_commands`, `iam_secret_deliveries`, `iam_event_cursors`, `iam_event_deduplications` |
| ORG | Kurum | `organizations`, `organization_brand_colors`, `organization_modules` |
| TERM | Dönem ve takvim | `terms`, `term_calendar_days` |
| CLS | Sınıf | `classes`, `class_teacher_assignments` |
| PEOPLE | Kişiler | `students`, `student_class_enrollments`, `student_guardians`, `custom_field_definitions`, `custom_field_options`, `custom_field_values`, `custom_field_value_selected_options` |
| ATT | Yoklama | `attendance_sessions`, `attendance_records`, `organization_attendance_statuses` |
| CONTENT | İçerik | `file_assets`, `contents` |
| PROGRAM | Program | `starter_program_templates`, `programs`, `program_versions`, `program_templates`, `program_template_days`, `plan_items` |
| PROGRESS | İlerleme | `progress_records` |
| AUDIT | Denetim | `audit_action_catalog`, `audit_logs` |
| EXPORT | Dışa aktarma | Bu görevde ayrı tablo tanımlanmaz; `P-012` kapsamındadır. |
| SYNC | Senkronizasyon | `idempotency_keys`, `sync_changes`; tam sözleşme `P-010` kapsamındadır. |
| REALTIME | Gerçek zaman | `sync_changes` outbox/değişiklik akışından sınıf olayları ve istemci güncellemesini besler; taşıma A-006'da seçilir. |
| NOTIFY | Bildirim | Bu görevde tablo tanımlanmaz (Dalga 8). |

---

## 4. IAM — Kimlik ve erişim

### 4.1. `users` (GLOBAL — uygulama hesabı)

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `status` | ENUM(`PROVISIONING`,`ACTIVE`,`SUSPENDED`) | Hayır | `PROVISIONING`, Keycloak hesabı kalıcı provider-command ile tamamlanana dek girişe kapalıdır. Kullanıcı normal arayüzden fiziksel silinmez (§14). Hesabın **kendisinin** genel durumudur; belirli bir kurumdaki üyeliğin durumu `organization_memberships.status`'tadır (bkz. 4.5). |
| `reauthentication_required_after` | TIMESTAMPTZ, varsayılan `'epoch'` | Hayır | Global yeni cihaz ve platform yöneticisi ailesi üretim eşiği. |
| temel alanlar | — | — | bkz. §2.3 |

`users`, parola özeti, kullanıcı adı veya sağlayıcıya ait oturum sırrı taşımaz. Bu kaynaklar
Keycloak'a aittir. **`users` artık `person_id` taşımaz.** Bir global kullanıcının, üye olduğu her kurumda **ayrı
bir `people` satırı** (o kurumun kendi tuttuğu PII kopyası) vardır; tek bir global "kişi"
kaydına indirgenemez (bkz. 4.2, 4.5). Bu, önceki sürümdeki `v2.0`'da `people`'ın da global
yapılmasının yol açtığı "kurumlar arası PII paylaşımı" riskini ortadan kaldırır.

Global parola değişimi/reset, kullanıcı devre dışı bırakma, global logout, global cihaz kaldırma
ve hesap ele geçirme olayında tek IAM transaction'ı bu sütunu transaction zamanına yükseltir,
kullanıcının tüm `refresh_token_families`/`refresh_tokens` satırlarını iptal eder ve audit,
idempotency ile provider-command kaydını yazar. Yeni güvenilir cihaz, kurum ailesi ve platform
yöneticisi global ailesi üretimi bu eşiğin sonrasındaki doğrulanmış `auth_time` olmadan yapılamaz.

### 4.1a. `user_identities` (GLOBAL — sağlayıcı kimliği eşlemesi)

Keycloak'ta doğrulanmış özne ile uygulama hesabının tek kaynak eşlemesidir. `issuer` OIDC
discovery belgesindeki değişmez `iss` değerinin kanonik metnidir; `subject`, o issuer'ın
`sub` değeridir. Kullanıcı adı ve geçici parola bu tabloda bulunmaz.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`) | Hayır | Uygulama hesabı. |
| `issuer` | TEXT | Hayır | Doğrulanmış OIDC issuer; URL normalizasyonu istemci girdisinden yapılmaz. |
| `subject` | TEXT | Hayır | Keycloak `sub`; **[Hassas]** kalıcı çevrimiçi kimlik. |
| `created_at` | TIMESTAMPTZ | Hayır | |
| `disabled_at` | TIMESTAMPTZ | Evet | Global hesap devre dışı bırakılınca eşleme korunur ama girişte kullanılamaz. |

Kısıtlar: `UNIQUE (issuer, subject)`; `UNIQUE (user_id, issuer)`. Bir Keycloak öznesi yalnız
bir uygulama hesabına, bir uygulama hesabı da bu V1'de yalnız seçili issuer'a bağlanabilir.
İstemcinin taşıdığı e-posta, kullanıcı adı veya ad/soyadla mevcut hesaba otomatik bağlama
yasaktır. Bağlama yalnız backend'in Keycloak management API ile oluşturduğu hesapta veya
zaten doğrulanmış aynı `(issuer, subject)` eşlemesinde yapılır; çakışma güvenli biçimde
reddedilir ve denetlenir. `TEACHER_ACCOUNT_CREATE` ilk transaction'ında `user_identities`
**oluşturulmaz**: Keycloak'un `POST /users` cevabındaki `Location` başlığından gerçek subject
alınır; ikinci finalize transaction'ı bu subject ile eşlemeyi yazar ve hesabı etkinleştirir.
Başarısızlıkta satırlar `PROVISIONING` durumundan normal girişe geçmez.

### 4.2. `people` (KURUM KAPSAMLI)

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `first_name` | TEXT | Hayır | |
| `last_name` | TEXT | Hayır | |
| `phone` | TEXT | **Hayır** | **[Hassas]** — `URUN_VE_UYGULAMA_PLANI.md` §7.1 gereği ad/soyad ile birlikte zorunlu çekirdek alan. |
| `photo_asset_id` | UUID | Evet | **[Hassas]** — Bileşik FK: `(photo_asset_id, organization_id) REFERENCES file_assets (id, organization_id)`. |
| `birth_date` | DATE | Evet | **[Hassas]** |
| `address` | TEXT | Evet | **[Hassas]** |
| `school` | TEXT | Evet | |
| `note` | TEXT | Evet | **[Hassas]** — serbest metin |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (id, organization_id)` — bileşik FK zinciri için (bkz. bölüm 15). Bu tablo,
öğrenci, veli ve kurum içindeki hoca/yönetici profillerinin **ortak, kurum kapsamlı** çekirdek
kaydıdır (`TERIMLER_SOZLUGU.md`'deki "Kişi" tanımı); `students`, `student_guardians` ve
`organization_memberships` bu tabloya bileşik FK ile (aynı kurum) bağlanır (bkz. 4.5, 8.1, 8.3).

**Anne/baba bilgisinin isteğe bağlı olması nasıl sağlanır:** Ana planın §7.3 hükmü,
`people.phone`'u koşullu yaparak değil, **anne/baba `student_guardians` kaydının kendisinin hiç
oluşturulmamasıyla** sağlanır. Oluşturulan her `people` satırı çekirdek alan kuralına (ad,
soyad, telefon zorunlu) tabidir.

İndeks: `(organization_id, last_name, first_name)`.

### 4.3. `platform_administrators` (GLOBAL)

Platform yöneticisi rolü global kapsamlıdır (`URUN_VE_UYGULAMA_PLANI.md` §5.1) ve hiçbir kurum
üyeliği gerektirmez.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`, **UNIQUE**) | Hayır | |
| `granted_by_user_id` | UUID (FK → `users.id`) | Evet | İlk kurulumda `NULL` olabilir. |
| `granted_at` | TIMESTAMPTZ | Hayır | |
| `revoked_at` | TIMESTAMPTZ | Evet | Dolu ise artık platform yöneticisi değildir. |

### 4.4. `platform_administrator_profiles` (GLOBAL, isteğe bağlı)

Platform yöneticisinin, hiçbir kuruma ait olmayan, **ayrı ve açık** global profilidir. `people`
tablosuna kasıtlı olarak eklenmemiştir — `people` artık kurum kapsamlı olduğundan (bkz. 4.2),
kurum bağlamsız bir profili orada `organization_id` nullable yaparak tutmak, kurum kapsamı
ilkesini (bölüm 2.2) yeniden bulanıklaştırırdı. Bu satır **isteğe bağlıdır**: platform
yöneticisi teknik olarak yalnızca `users` + `platform_administrators` ile de çalışabilir; bu
tablo yalnızca arayüzde ad/iletişim gösterilmesi gerekirse kullanılır.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`, **UNIQUE**) | Hayır | |
| `first_name` | TEXT | Hayır | |
| `last_name` | TEXT | Hayır | |
| `phone` | TEXT | Evet | **[Hassas]** |
| `note` | TEXT | Evet | **[Hassas]** |
| temel alanlar | — | — | bkz. §2.3 |

**Profil fotoğrafı bilinçli olarak desteklenmez.** Önceki sürümde bu satırda kurum kapsamlı
`file_assets`'e **düz** (bileşik olmayan) bir FK veren bir `photo_asset_id` alanı vardı; bu,
"bilinçli istisna" gerekçesiyle bırakılan gerçek bir tenant izolasyonu açığıydı — global bir
profilin, herhangi bir kurumun dosya kaydına serbestçe bağlanabilmesi mümkündü. Bu görev, iki
seçenekten (alanı tamamen kaldırmak / kurum bağlamsız dosyalar için ayrı bir global dosya
tablosu tanımlamak) V1 için en sade olanı seçmiştir: **alan kaldırılmıştır.** Platform
yöneticisinin profil fotoğrafı ihtiyacı ilk sürümde yoktur; ileride gerekirse ayrı, açıkça
global bir `platform_file_assets` tablosu (kurum kapsamlı `file_assets`'ten tamamen bağımsız)
tanımlanarak eklenebilir — bu, ayrı bir görev/karar gerektirir. **[Kapsam dışı]**

### 4.5. `organization_memberships`

Global kimlik (`users`) ile kurum kapsamlı profili (`people`) ve kurum bağlamını birbirine
bağlayan köprü tablodur.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `user_id` | UUID (FK → `users.id`) | Hayır | |
| `person_id` | UUID | Hayır | Bu kullanıcının **bu kurumdaki kendi** profili. Bileşik FK: `(person_id, organization_id) REFERENCES people (id, organization_id)`. |
| `status` | ENUM(`PROVISIONING`,`ACTIVE`,`SUSPENDED`) | Hayır | `PROVISIONING` üyeliği giriş/kurum seçimine kapalıdır; Keycloak hesabı tamamlanınca allow-listli aktivasyonla `ACTIVE` olur. Kurum yöneticisinin "hoca hesabını askıya alma" işlemi (`KURUM-04`) burada uygulanır; kullanıcının **başka kurumdaki** üyeliğini etkilemez. |
| `session_generation` | INTEGER, varsayılan `1` | Hayır | Bu üyeliğin oturum "kuşak" sayacı. `status` `SUSPENDED`'a çekildiğinde veya bir rolü geri alındığında artırılır; kurum kapsamlı `refresh_tokens`'ın hâlâ geçerli olup olmadığını doğrulamak için kullanılır (bkz. 4.11, 15.5). |
| `reauthentication_required_after` | TIMESTAMPTZ, varsayılan `'epoch'` | Hayır | Bu üyelik için yeni cihaz/kurum oturumu üretilebilmesinin alt sınırı. IAM, doğrulanmış Keycloak `auth_time` değerinin bundan **sonra** olmasını zorunlu kılar. |
| `granted_by_user_id` | UUID (FK → `users.id`) | Evet | |
| `granted_at` | TIMESTAMPTZ | Hayır | |

Kısıtlar:

- `UNIQUE (organization_id, user_id)` — bir kullanıcının bir kurumda en fazla bir üyeliği
  (dolayısıyla en fazla bir `people` profili) olur. **Aynı kullanıcı farklı kurumlarda farklı
  satırlarla, farklı `people` profilleriyle üye olabilir** (`TERIMLER_SOZLUGU.md` §3.1) — her
  kurum kendi PII kopyasını tutar, hiçbir alan kurumlar arasında paylaşılmaz.
- `UNIQUE (person_id)` — bir kurum kapsamlı `people` satırı en fazla **bir** global kullanıcı
  hesabına bağlanabilir; iki ayrı giriş hesabı aynı kişi profilini paylaşamaz. Kullanıcı–kişi
  bağı tek ve belirsiz olmayan bir kaynaktır. (`person_id` zaten `people.id` UUID'sine işaret
  ettiğinden ve her `people` satırı tam olarak bir kuruma ait olduğundan, bu tekillik hem
  "aynı kurum içinde" hem "kurumlar arasında" anlamına gelir — aynı `people` satırı zaten
  yalnızca bir kurumda var olabilir.)
- `UNIQUE (id, organization_id)` — bileşik FK zinciri için.
- `UNIQUE (id, user_id)` — `refresh_tokens`'ın bu üyeliğe bağlanırken aynı kullanıcıya ait
  olduğunu doğrulaması için (bkz. 4.11).

Rol, bu tabloda **değil**, ayrı `organization_membership_roles`'ta tutulur (bkz. 4.6) — bu
ayrım, aynı üyeliğin birden fazla aktif role sahip olabilmesini sağlar.

### 4.6. `organization_membership_roles`

Bir kurum üyeliğine verilmiş, tarihçeli ve **çoklu** rol atamalarıdır. Önceki sürümdeki tekil
`organization_memberships.membership_role` sütunu, "aynı kullanıcı aynı kurumda hem `ORG_ADMIN`
hem `TEACHER` olamaz" gibi ana planda dayanağı olmayan bir kısıtlamaya yol açtığı için
kaldırılmıştır.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_membership_id` | UUID | Hayır | Bileşik FK: `(organization_membership_id, organization_id) REFERENCES organization_memberships (id, organization_id)`. |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | Denormalize; aşağıdaki pinlenmiş bileşik FK'ler için (bkz. 15.6). |
| `role` | `membership_role_enum` (`ORG_ADMIN`,`TEACHER`) | Hayır | Kapalı, ürün seviyesinde sabit rol kümesi (ana plan §5). **Adlandırılmış bir PostgreSQL `ENUM` tipi** olarak tanımlanır (`CREATE TYPE membership_role_enum AS ENUM ('ORG_ADMIN', 'TEACHER')`) — bölüm 4.9 ve 7.2'deki pinlenmiş sütunlar (`target_role_code`, `granted_role_code`, `role_code`) bileşik FK verebilmek için **aynı** bu tipte tanımlanmalıdır; bir `TEXT` sütun bir `ENUM` sütununa bileşik FK ile bağlanamaz (bkz. 15.6). |
| `granted_by_user_id` | UUID (FK → `users.id`) | Evet | |
| `granted_at` | TIMESTAMPTZ | Hayır | |
| `revoked_at` | TIMESTAMPTZ | Evet | |

Kısıtlar:

- `UNIQUE (organization_membership_id, role) WHERE revoked_at IS NULL` (partial) — aynı role
  aynı anda yalnızca bir aktif atama; ancak bir üyelik **hem `ORG_ADMIN` hem `TEACHER`**
  satırına sahip olabilir (iki ayrı satır) — çoklu rol buradan gelir.
- `UNIQUE (id, organization_id, role)` — bölüm 4.9 ve 7.2'deki pinlenmiş bileşik FK'lerin
  hedefidir; `id` zaten `PRIMARY KEY` olduğundan bu üçlü doğası gereği tekildir, ancak
  PostgreSQL'de bileşik bir FK'nin hedef sütun kümesi üzerinde **açıkça tanımlı** bir
  `UNIQUE`/`PRIMARY KEY` bulunmalıdır — bu nedenle ayrı bir indeks olarak tanımlanır.

### 4.7. `permission_categories`

`YETKI_MATRISI.md` §4.3'teki beş devredilebilir izin kategorisinin genişletilebilir kataloğu.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `code` | TEXT | Hayır | PK: `CLASS_STUDENT_GUARDIAN`, `PROGRAM`, `ATTENDANCE_REPORTING`, `ORG_SETTINGS`, `STAFF_MANAGEMENT`. |
| `label` | TEXT | Hayır | |

### 4.8. `permission_catalog`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `code` | TEXT | Hayır | PK. |
| `category_code` | TEXT (FK → `permission_categories.code`) | Hayır | |
| `label` | TEXT | Hayır | |
| `description` | TEXT | Evet | |
| `introduced_in_version` | TEXT | Hayır | |

**İzin kodu listesi:**

| Kategori | Kod(lar) |
|---|---|
| `CLASS_STUDENT_GUARDIAN` | `CLASS_MANAGE`, `STUDENT_MANAGE`, `GUARDIAN_MANAGE`, `GUARDIAN_CONTACT_VIEW`, `RESTORE_ARCHIVED`, `TERM_CALENDAR_MANAGE` |
| `PROGRAM` | `PROGRAM_MANAGE`, `EVALUATION_SCHEMA_MANAGE` |
| `ATTENDANCE_REPORTING` | `ATTENDANCE_BACKDATE_CORRECT`, `REPORT_EXPORT`, `AUDIT_LOG_VIEW`, `AUDIT_UNDO` |
| `ORG_SETTINGS` | `BRAND_MANAGE`, `MODULE_MANAGE`, `CUSTOM_ATTENDANCE_STATUS_MANAGE` |
| `STAFF_MANAGEMENT` | `TEACHER_ACCOUNT_MANAGE`, `TEACHER_CLASS_ASSIGN`, `TEACHER_PERMISSION_VIEW`, `DEVICE_SESSION_REVOKE` |

Kuruma özel öğrenci alanı tanımlarını yönetmek için V1 izin kataloğunda hoca delegasyonu
yoktur. `custom_field_definitions` ve `custom_field_options` yazmaları yalnız kurum yöneticisi
ve açık destek bağlamındaki platform yöneticisi tarafından yapılır (`YETKI_MATRISI.md` §2.2
madde 6b, §3.1). Öğrenci kaydındaki özel alan **değerine** erişim ise hedef öğrenciye yönelik
mevcut görüntüleme/yönetme yetkisinin kapsam ve alan filtrelerine tabidir; tanım yönetimiyle
aynı işlem değildir.

**Bağlayıcı karar — `RESTORE_ARCHIVED` tek ve ortak izindir:** Öğrenci ve sınıf arşiv geri
yükleme tek, ortak bir izin kodudur; varlık başına ayrı izin yoktur (`YONETICI_BILGI_MIMARISI.md`
ve `HOCA_MOBIL_BILGI_MIMARISI.md`'deki açık soruyu kapatan karar, bkz. bölüm 19).

**Tasarım kararı:** İzin ve kategori kümeleri `ENUM` değil, `TEXT` kodlu referans tablolardır —
yeni izinler/kategoriler migration gerektirmeden eklenir.

### 4.9. `organization_membership_permissions`

Kurum yöneticisinin bir hocaya (belirli bir `TEACHER` rol atamasına) verdiği bağımsız izin
atamalarıdır. Sınıf atamasına değil, doğrudan **kurum üyeliğinin `TEACHER` rolüne** bağlıdır —
hiç sınıfa atanmamış bir hocanın da kurum kapsamlı izinlerini kullanabilmesi için zorunludur.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `target_membership_role_id` | UUID | Hayır | İzni alan hocanın **rol** ataması. |
| `target_role_code` | `membership_role_enum`, varsayılan `'TEACHER'` | Hayır | `CHECK (target_role_code = 'TEACHER')` — gerçek, sabit bir sütun; bileşik FK'nin "pinlenmesi" bu sütun üzerinden yapılır (bkz. 15.6). **`organization_membership_roles.role` ile aynı `ENUM` tipi** (`membership_role_enum`) — bileşik FK'nin tip uyumu için zorunludur. Bileşik FK: `(target_membership_role_id, organization_id, target_role_code) REFERENCES organization_membership_roles (id, organization_id, role)`. |
| `permission_code` | TEXT (FK → `permission_catalog.code`) | Hayır | |
| `granted_by_membership_role_id` | UUID | Evet | Kurum yöneticisi tarafından verildiyse dolu. |
| `granted_role_code` | `membership_role_enum` | `granted_by_membership_role_id` doluyken zorunlu, aksi hâlde `NULL` | `CHECK (granted_role_code IS NULL OR granted_role_code = 'ORG_ADMIN')`. **`organization_membership_roles.role` ile aynı `ENUM` tipi.** Bileşik FK: `(granted_by_membership_role_id, organization_id, granted_role_code) REFERENCES organization_membership_roles (id, organization_id, role)` (bir sütun `NULL` olduğunda çok sütunlu FK hiç doğrulanmaz — bu yalnızca `granted_by_membership_role_id` dolu iken devreye girer). |
| `granted_by_platform_admin_user_id` | UUID (FK → `platform_administrators.user_id`) | Evet | Platform yöneticisi destek amaçlı erişimde verdiyse dolu. |
| `granted_at` | TIMESTAMPTZ | Hayır | |
| `revoked_at` | TIMESTAMPTZ | Evet | |

Kısıtlar:

- `CHECK (num_nonnulls(granted_by_membership_role_id, granted_by_platform_admin_user_id) = 1)`
  — grantor ya kurum yöneticisi ya platform yöneticisidir.
- `CHECK ((granted_by_membership_role_id IS NULL AND granted_role_code IS NULL) OR
  (granted_by_membership_role_id IS NOT NULL AND granted_role_code = 'ORG_ADMIN'))` —
  `granted_role_code`'un yalnızca `granted_by_membership_role_id` doluyken ve yalnızca
  `'ORG_ADMIN'` değeriyle dolu olmasını zorunlu kılar. Bu ikinci `CHECK` olmadan,
  `granted_by_membership_role_id` dolu bırakılıp `granted_role_code` yanlışlıkla `NULL`
  bırakılabilirdi — bileşik FK'de bir sütun `NULL` olduğunda çok sütunlu FK hiç
  doğrulanmadığından, bu durumda `granted_by_membership_role_id` **hiçbir doğrulama olmadan**
  herhangi bir role (bir `TEACHER` rolü dahil) işaret edebilirdi ve "hoca yapısal olarak
  grantor olamaz" iddiası delinirdi. Bu iki `CHECK` birlikte, `granted_role_code`'un her zaman
  doğru ve tutarlı doldurulmasını garanti eder; ancak nihayetinde "bir hoca grantor olamaz"
  garantisi asıl olarak bileşik FK'nin kendisinden (bkz. 15.6) gelir — bu `CHECK`'ler FK'nin
  atlanabilir hâle gelmesini engeller.
- `UNIQUE (target_membership_role_id, permission_code) WHERE revoked_at IS NULL` (partial).

**Önceki sürümdeki hata ve düzeltmesi:** `v2.0`'da bu bileşik FK'ler `(...,'TEACHER')
REFERENCES ...` biçiminde, FK ifadesinin içine doğrudan bir **literal** gömülerek yazılmıştı —
bu geçerli bir SQL sözdizimi değildir (FK yalnızca gerçek sütunlara referans verebilir). `v3.0`
bunu, çocuk tabloda gerçek ve `CHECK` ile sabit bir değere kilitlenmiş bir sütun
(`target_role_code`, `granted_role_code`) ekleyerek düzeltir; bileşik FK artık yalnızca gerçek
sütunlara işaret eder (bkz. 15.6).

### 4.10. `trusted_devices`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`) | Hayır | |
| `device_identifier` | UUID | Hayır | **[Hassas]** — uygulamanın ilk güvenilir cihaz kaydında kriptografik rastgele ürettiği kimliktir; IMEI, reklam kimliği, OS vendor ID veya donanım parmak izi değildir. **Satır oluşturulduktan sonra immutable'dır** (bağlayıcı, IAM-002): bu, RLS `WITH CHECK`i değil — RLS ifadelerinde trigger `NEW`/`OLD` kaydı yoktur, bu yüzden "eski değerinden değişemez" RLS ile ifade edilemez — `iam_runtime`e yalnız `GRANT UPDATE (revoked_at) ON trusted_devices` verilmiş, bu kolona (ve `user_id`/`platform`/`device_name`/`trusted_at`a) hiç `GRANT UPDATE` verilmemiş olmasıyla (**column-level privilege**) sağlanır (bkz. `ADR-004` `trusted_devices` `UPDATE` satırı ve "değişmez kolonların korunması" bölümü). Bu, cihaz-bazlı reauth bariyerinin mantıksal kilit anahtarının (`user_id`, `device_identifier`) bir satırın ömrü boyunca sabit kalmasını garanti eder. |
| `device_name` | TEXT | Evet | |
| `platform` | ENUM(`IOS`,`ANDROID`) | Hayır | |
| `trusted_at` | TIMESTAMPTZ | Hayır | |
| `last_seen_at` | TIMESTAMPTZ | Evet | |
| `revoked_at` | TIMESTAMPTZ | Evet | Kullanıcının **kendi** cihazını tamamen kaldırması veya platform yöneticisinin global iptali burada uygulanır — bu, kurum kapsamlı oturum iptalinden (bkz. 4.11) farklı, cihazın **bütün** kurum bağlamlarını etkileyen bir işlemdir. |

Kısıtlar:

- `UNIQUE (id, user_id)` — `refresh_token_families`'in aşağıdaki bileşik FK'sinin hedefidir.
- `UNIQUE (user_id, device_identifier) WHERE revoked_at IS NULL` (partial) — aynı uygulama
  kurulum kimliği için **birden fazla aktif** güvenilir cihaz kaydı oluşamaz; bu
  önlenmezse aynı fiziksel cihaz için yinelenen, birbirinden habersiz kayıtlar (ve dolayısıyla
  tutarsız iptal davranışı) oluşabilirdi.

**Cihaz-bazlı yeniden kimlik doğrulama bariyeri (bağlayıcı, IAM-002):** Yukarıdaki partial
`UNIQUE` kısıtı yalnız "aynı anda birden fazla aktif satır" durumunu engeller; `revoked_at` dolu
bir satırla **aynı** `(user_id, device_identifier)` çifti için **yeni** bir aktif satır açılmasını
tek başına engellemez. Bu nedenle `PROVIDER_TOKEN_EXCHANGE` transaction'ı, yeni satırı yazmadan
önce **önce** aynı `(user_id, device_identifier)` mantıksal anahtarında bir transaction-scoped
advisory lock alır (satır hiç yokken bile serileştirir), sonra kilit altında aynı çift için
önceden var olan satırların **en son** (`MAX`) `revoked_at` değerini — yalnız kendi `user_id`si ve
tam eşleşen `device_identifier`i kapsayan **dar** bir `FORCE RLS` `SELECT` policy'siyle (başka
kullanıcı veya başka cihazın geçmişi hiçbir koşulda görünmez) — okur ve yeni satırı yalnız
doğrulanmış Cognito `auth_time` bu değerden **büyükse** açar; önceki satır yoksa bariyer devreye
girmez. Bu kontrol, `users.reauthentication_required_after` (§4.1) ve
`organization_memberships.reauthentication_required_after` (§4.5) eşiklerinden **bağımsız ve ek**
bir cihaz-düzeyi bariyerdir — `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE`
(`IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md`) bu iki eşiği bilinçli olarak değiştirmediğinden, bu
bariyer olmadan iptal edilmiş bir cihaz hâlâ geçerli/eski bir sağlayıcı oturumuyla anında yeniden
güven kazanabilirdi. Aynı mantıksal kilit `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`
transaction'larında da alınır; böylece `PROVIDER_TOKEN_EXCHANGE` ile eşzamanlı bir iptal, hangisi
önce commit olursa olsun tutarlı ve seri bir sonuç üretir. Bu iki işlem yalnız `deviceId` (`id`)
alır, `device_identifier`i **bilmez**; bu yüzden kilitten önce dar, kararsız ve salt okunur bir
keşif sorgusuyla (yalnız `id`/`user_id`/`device_identifier` okur, hiçbir güvenlik kararı vermez)
`device_identifier`i öğrenir, kilidi alır, sonra aynı satırı `SELECT ... FOR UPDATE` ile yeniden
okuyup `user_id`/`device_identifier` eşleşmesini doğrular (uyuşmazsa fail-closed `404`) ve ancak
bundan sonra karar/mutasyon yapar. Tam faz sırası `ADR-004` "IAM-002 — ... RLS eklentisi"
bölümündedir. Bu mekanizma ayrı `iam_runtime` rolü
kararını (bkz. §14, `ADR-003` §5.3) veya dar `FORCE RLS` modelini gevşetmez; genel `SELECT` yetkisi,
`BYPASSRLS` veya RLS'i atlayan bir `SECURITY DEFINER` fonksiyonu kullanılmaz. Tam `WITH CHECK`/RLS/
advisory lock ifadesi `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` "IAM-002 — Cihaz ve oturum
iptali `iam_runtime`/RLS eklentisi" bölümündedir.

### 4.10a. `context_selection_tokens` — tek kullanımlı kurum seçimi

İlk doğrulanmış Keycloak access-token değişiminden sonra verilen bu token, platformun kurum
bağlamsız kalıcı oturumu değildir; yalnız üyelik listesini göstermeye ve **bir** kurum seçimine
yarar. Ham değer en az 256-bit kriptografik rastgele opaque değerdir; yalnız cihazın güvenli
saklamasında bulunur ve DB/log/audit/telemetry'ye yazılmaz.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`) | Hayır | |
| `trusted_device_id` | UUID | Hayır | Bileşik FK: `(trusted_device_id, user_id) REFERENCES trusted_devices (id, user_id)`. |
| `token_hash` | TEXT, **UNIQUE** | Hayır | **[Hassas]** — `HMAC-SHA-256(pepper, token)`; ham değer saklanmaz. |
| `authenticated_at` | TIMESTAMPTZ | Hayır | Doğrulanmış Keycloak `auth_time`. |
| `issued_at` | TIMESTAMPTZ | Hayır | |
| `expires_at` | TIMESTAMPTZ | Hayır | Kesin ömür: **5 dakika**. |
| `consumed_at` | TIMESTAMPTZ | Evet | Başarılı kurum seçimiyle bir kez dolar. |
| `revoked_at` | TIMESTAMPTZ | Evet | |

Kısıtlar: `CHECK (issued_at < expires_at)`; yalnız `consumed_at IS NULL`, `revoked_at IS NULL`
ve `expires_at > transaction_time` tokenı kullanılabilir. Üyelik listeleme tokenı tüketmez;
başarılı kurum seçimi, `consumed_at` yazılması ve `refresh_token_families`/`refresh_tokens`
oluşturulmasıyla **aynı DB transaction'ında** olur. İkinci seçim atomik koşullu güncellemede
başarısız olur. Aile üretimi ayrıca `authenticated_at > users.reauthentication_required_after`,
hedef üyeliğin `reauthentication_required_after` eşiği, `ACTIVE` üyelik/rol ve güncel
`session_generation` koşullarını doğrular; böylece `DEVICE_SESSION_REVOKE`, global iptal veya
eski `auth_time` bu tokenla yeni aile üretimini engeller. Token refresh edilemez.
`iam_runtime` `IAM_AUTH` RLS politikası, bu satırı yalnız
`user_id = app.iam_actor_user_id AND EXISTS (SELECT 1 FROM trusted_devices td WHERE td.id =
trusted_device_id AND td.user_id = app.iam_actor_user_id AND td.revoked_at IS NULL)` predicate'i
ile açar. `CONTEXT_ACTIVATE` sırasında ayrıca yalnız server-set `app.iam_target_membership_id`
ve `app.iam_target_organization_id` eşleşmesiyle tüketilebilir; sahte üyelik/kurum zinciri
fail-closed reddedilir.

### 4.11. `refresh_token_families` ve `refresh_tokens` — kurum kapsamlı oturum iptali

`refresh_token_families`, aynı cihaz ve kurum bağlamında dönen platform tokenlarını bir ailede
toplar. Aile, `context_selection_tokens` değildir: bu tek kullanımlı token yalnız bağlam
listesi/seçimi için kısa ömürlüdür; refresh ailesi veya kalıcı genel refresh yolu oluşturmaz.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `user_id` | UUID (FK → `users.id`) | Hayır | |
| `trusted_device_id` | UUID | Hayır | Bileşik FK: `(trusted_device_id, user_id) REFERENCES trusted_devices (id, user_id)`. |
| `organization_membership_id` | UUID | Evet | Kurum ailesinde zorunlu; platform yöneticisi global ailesinde `NULL` olabilir. |
| `authenticated_at` | TIMESTAMPTZ | Hayır | IAM'in Keycloak access tokenından güvenilir biçimde doğruladığı `auth_time`. |
| `issued_at_session_generation` | INTEGER | Üyelik doluyken zorunlu | Üyeliğin token üretimindeki kuşağı. |
| `revoked_at` | TIMESTAMPTZ | Evet | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıtlar: üyelik doluyken `(organization_membership_id, user_id)` bileşik FK ile sahiplik
zorunludur; `authenticated_at > users.reauthentication_required_after` ve üyelik varsa
`authenticated_at > organization_memberships.reauthentication_required_after` kuralı uygulama
transaction'ında doğrulanır. Aile yalnız kendi kullanıcı/cihaz/üyelik kapsamını
taşır; aile iptali başka kullanıcı, cihaz veya kurum ailesine genişleyemez.

### 4.11a. `refresh_tokens`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `family_id` | UUID (FK → `refresh_token_families.id`) | Hayır | Aile; kullanıcı/cihaz/üyelik bu kaynaktan türetilir. |
| `previous_refresh_token_id` | UUID (self FK → `refresh_tokens.id`) | Evet | Rotasyonda tüketilen önceki token; ilk token için `NULL`. |
| `token_hash` | TEXT, **UNIQUE** | Hayır | **[Hassas]** — platform opaque refresh tokenının `HMAC-SHA-256(pepper, token)` özeti; ham değer saklanmaz. |
| `access_token_hash` | TEXT, **UNIQUE** | Hayır | **[Hassas]** — mevcut platform opaque access tokenının aynı pepper'lı HMAC özeti. |
| `access_expires_at` | TIMESTAMPTZ | Hayır | Access tokenın kısa ömrü; ADR-004'te 10 dakika. |
| `issued_at` | TIMESTAMPTZ | Hayır | |
| `used_at` | TIMESTAMPTZ | Evet | Refresh token başarıyla bir kez tüketildiğinde dolar. |
| `expires_at` | TIMESTAMPTZ | Hayır | |
| `revoked_at` | TIMESTAMPTZ | Evet | |

Kısıtlar: `UNIQUE (previous_refresh_token_id) WHERE previous_refresh_token_id IS NOT NULL` —
bir önceki tokendan en fazla bir ardıl doğar. Self FK, `previous_refresh_token_id`nin aynı
`family_id` içindeki tokenı göstermesini `CHECK` ile tek başına ifade edemez; IAM-003
migration'ı bunu family kimliğini pinleyen bileşik `UNIQUE (id, family_id)` + bileşik FK ile
zorunlu kılar. `issued_at ≤ used_at ≤ revoked_at` yalnız dolu değerler arasında geçerlidir;
`issued_at < expires_at`; `used_at` dolu token yeniden kullanılamaz.

**Kurum kapsamlı iptalin çalışma şekli:** `DEVICE_SESSION_REVOKE` iznine sahip bir kurum
yöneticisi/hoca "kullanıcının cihaz oturumlarını iptal etme" işlemini yaptığında, bu işlem
**tek JDBC/DB transaction'ında** şunları yapar: (1) hedef üyeliğin ailelerini ve tokenlarını
`revoked_at` ile iptal eder; (2) `session_generation`ı artırır; (3)
`reauthentication_required_after`ı transaction zamanına ayarlar; (4) audit, idempotency sonucu
ve gerekiyorsa outbox kaydını yazar. Hedef kullanıcının **başka bir kurumdaki** bağlamda
açılmış oturumları bu işlemden **etkilenmez** — bu, `YETKI_MATRISI.md` §2.2 madde 2 "kurum
kapsamlı roller kurum dışına çıkamaz" mutlak sınırının doğal bir sonucudur.

**Context-selection tokenı:** §4.10a'daki token kısa ömürlü, refresh edilemez ve yalnız üyelik
listesi/tek bağlam seçimi içindir. Seçilen üyelik için aile üretimi; `ACTIVE` üyelik, aktif rol,
güncel `session_generation` ve doğrulanmış Keycloak `auth_time >
reauthentication_required_after` koşullarının tümünü ister. Başka kurumun oturumu veya bu
token eski `auth_time` ile hedef üyelik için yeni aile oluşturamaz.

**`session_generation` kontrolü — halihazırda verilmiş token'lar için:** Her kurum kapsamlı API
isteği, sunulan token'ın `issued_at_session_generation`'ının, ilgili `organization_memberships.
session_generation`'ının **güncel** değerine eşit olduğunu doğrulamalıdır. Bir üyelik askıya
alındığında veya bir rolü geri alındığında sayaç arttığı için, o ana kadar üretilmiş **bütün**
eski token'lar (henüz `revoked_at` ile tek tek işaretlenmemiş olsalar bile) bu kontrolde
otomatik olarak geçersiz sayılır — bu, toplu iptal (`revoked_at`) işleminde bir satırın gözden
kaçması durumuna karşı ikinci bir savunma katmanıdır. Bu kontrol, iki farklı mutlak satırın
(token + üyelik) canlı karşılaştırılmasını gerektirdiğinden statik bir FK/CHECK ile ifade
edilemez; **zorunlu bir uygulama katmanı kuralı** olarak bölüm 15.5'te de kaydedilmiştir.
`YETKI_MATRISI.md` §3.3/§8.1 "kullanıcının bütün cihaz oturumlarını iptal etme" satırındaki
"bütün" ifadesinin kapsamına dair açıklayıcı not, bu görevin PR'ında `YETKI_MATRISI.md`'ye
eklenmiştir (bağlayıcı yeni bir yetki kararı değildir; mevcut kurum izolasyonu mutlak sınırının
teknik sonucudur). Kesin token/oturum sağlayıcı teknolojisi `A-004` ADR'sine bırakılmıştır; bu
belge yalnızca dolanmayı engelleyen değişmezi (invariant) tanımlar.

Keycloak refresh tokenı yalnız ilk etkileşimli değişime kadar mobil güvenli saklamadadır; başarılı
değişimden sonra silinir ve platform DB'sine hiç yazılmaz. Bu tablo
yalnız platform opaque refresh tokenını temsil eder. Platform access tokenı JWT değildir:
en az 256-bit kriptografik rastgele opaque bearer değerdir; `access_token_hash` üzerinden
sunucuda aranır ve tek istek transaction'ında üyelik, cihaz, `revoked_at`, süre ve
`session_generation` doğrulanır. Böylece access tokenın ayrı claim/anahtar seti veya JWT
iptal listesi yoktur; key rotation yalnız HMAC pepper'ı için çift-doğrulama geçişiyle yapılır.

**Refresh tekrar kullanımı:** Yenileme transaction'ı, aktif token satırını kilitler; `used_at`
boşsa onu doldurur, aynı `family_id` ile `previous_refresh_token_id` üzerinden bağlı yeni satırı
ve yeni access token özetini üretir. Eşzamanlı iki yenilemeden yalnız ilk transaction başarılı
olur. `used_at` dolu bir refresh token tekrar sunulursa çalınma varsayılır: aynı ailedeki tüm
refresh/access tokenları atomik `revoked_at` ile iptal edilir, güvenlik audit'i yazılır ve
`401 SESSION_REVOKED` döner. Bu davranış başka kurum veya aile tokenlarını etkilemez.

### 4.11b. `iam_provider_commands`, `iam_secret_deliveries`, `iam_event_cursors`, `iam_event_deduplications`

`iam_provider_commands`: `id` UUID PK, `idempotency_key` TEXT **UNIQUE**, `provider` TEXT,
`command_type` ENUM(`TEACHER_ACCOUNT_CREATE`,`USER_DISABLE`,`USER_LOGOUT`,`PASSWORD_RESET`),
`target_user_id` UUID nullable (FK → `users.id`), `target_identity_id` UUID nullable
(FK → `user_identities.id`), `organization_id` UUID nullable (FK → `organizations.id`),
`username_lookup_hash` TEXT nullable, `payload_fingerprint` TEXT,
`encrypted_command_payload` BYTEA nullable, `payload_key_id` TEXT nullable,
`status` ENUM(`PENDING`,`CLAIMED`,`COMPLETED`,`FAILED`), `attempt_count` INTEGER,
`next_attempt_at` TIMESTAMPTZ, `lease_expires_at` TIMESTAMPTZ, `fencing_token` BIGINT,
`created_at` TIMESTAMPTZ, `completed_at` TIMESTAMPTZ nullable, `last_safe_error_code` TEXT
nullable. `encrypted_command_payload`, A-013'ün yönettiği anahtarla şifrelenmiş kullanıcı adı,
`UPDATE_PASSWORD` required action'ı, create için gerekli güvenli komut alanları ve yalnız create
anında yazılan değişmez `platform_user_id` attribute değerini taşır. Yönetim akışları bu
attribute'u değiştiremez; `username_lookup_hash` yalnız kesin kullanıcı adıyla güvenli uzlaştırma
araması içindir. `payload_fingerprint` tek başına iş yürütmek için yeterli değildir.

`CHECK`: `command_type='TEACHER_ACCOUNT_CREATE'` ise `target_user_id`, `organization_id`,
`username_lookup_hash`, şifreli payload ve anahtar kimliği zorunlu, `target_identity_id NULL`
olmalıdır; diğer command type'larda `target_identity_id` zorunlu, `target_user_id NULL`
olmalıdır. Bu command-type XOR, create işleminde henüz provider identity bulunmadığını, mevcut
provider kimliğiyle çalışan komutların ise yalnız identity hedeflediğini zorunlu kılar.
`GLOBAL` provider komutunda `app.iam_target_identity_id`, istemcinin ayarlayabildiği bir alan
değildir: sunucu bunu doğrulanmış route hedefinden `SET LOCAL` ile kurar ve aynı transaction'da
`id = app.iam_target_identity_id` ile `user_id = app.iam_target_user_id` koşullarını birlikte
RLS'e uygular. Eksik, geçersiz, başka kullanıcıya ait veya farklı identity fail-closed reddedilir;
transaction sonlandığında bağlam temizlenir ve identity listeleme varsayılan olarak kapalıdır.
`UNIQUE (provider, command_type, organization_id, username_lookup_hash) WHERE
command_type='TEACHER_ACCOUNT_CREATE'` aynı kullanıcı/kurum provisioning'ini tek komuta bağlar.
Ham token/parola log, audit, normal uygulama tablosu veya `idempotency_keys.result_payload`da
saklanmaz. Aynı IAM-first transaction'ında audit ve idempotency sonucu ile yazılır; işi alan
worker `fencing_token`ı artırır, lease sahibi olmayan veya eski fencing değerli worker terminal
sonuç yazamaz.

`iam_secret_deliveries`: `id` UUID PK, `provider_command_id` UUID **UNIQUE** FK,
`recipient_actor_user_id` UUID FK, `encrypted_secret` BYTEA, `payload_key_id` TEXT,
`status` ENUM(`ESCROWED`,`READY`,`CONSUMED`,`EXPIRED`), `created_at`, `ready_at`,
`expires_at`, `consumed_at`. Worker geçici parolayı üretir; sağlayıcı çağrısından önce yalnız bu
ayrı şifreli escrow satırına yazar, Keycloak create/uzlaştırması doğrulanmadan `READY` yapmaz.
`expires_at`, oluşturma anından sonra ve en geç `created_at + INTERVAL '10 minutes'` değerindedir.
İkinci finalize transaction'ı `app.iam_operation_scope='IAM_PROVISIONING'` ve
`app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'` ile `user_identities` eşlemesini, `ACTIVE`
durumlarını ve yalnız escrow süresi geçmemişse `READY` teslim sonucunu birlikte yazar. Süre
finalize öncesinde dolmuşsa teslim `EXPIRED` kalır; hesap eşlemesi sahiplenilebilir fakat parola
gösterilmez ve yeni reset komutu gerekir.

Durum/zaman kısıtları DB seviyesinde en az şunları zorunlu kılar:

- `CHECK (created_at < expires_at AND expires_at <= created_at + INTERVAL '10 minutes')`.
- `ESCROWED`: `ready_at IS NULL AND consumed_at IS NULL`.
- `READY`: `ready_at IS NOT NULL AND ready_at < expires_at AND consumed_at IS NULL`.
- `CONSUMED`: `ready_at IS NOT NULL AND ready_at <= consumed_at AND consumed_at <= expires_at`.
- `EXPIRED`: `consumed_at IS NULL`; uygulama koşullu güncellemeyi `expires_at <= transaction_time`
  ön koşuluyla yapar, çünkü hareketli saat karşılaştırması değişmez bir PostgreSQL `CHECK`
  ifadesiyle güvenli biçimde modellenmez.

`ESCROWED` ve `READY` satırlarında `encrypted_secret` ile `payload_key_id` zorunludur.
`CONSUMED` ve `EXPIRED` satırlarında ikisi de zorunlu olarak `NULL`dur: state trigger'ı terminal
geçişte bu temizliği aynı atomik update'te yapar ve `CHECK` terminal satırda gizli materyali
reddeder. Böylece durum görünürlüğü RLS new-row kontrolü için gerekse bile, ilk tüketimden sonra
kayıp yanıt veya terminal satırın sonraki okunması parolayı ya da anahtar referansını geri vermez.

Secret okuması satırı kilitleyen tek transaction'da `READY` → `CONSUMED` koşullu geçişiyle
birlikte yapılır. Yanıt istemciye ulaşmadan kaybolsa dahi ikinci gösterim yapılmaz; yeni parola
sıfırlama gerekir. Okuma anında `recipient_actor_user_id` çağıran kullanıcıyla eşleşmeli ve
çağıranın provider command'ın kurumunda etkin üyeliği ile geri alınmamış `ORG_ADMIN` rolü veya
etkin `TEACHER_ACCOUNT_MANAGE` izni canlı olarak yeniden doğrulanmalıdır. Provisioning'i daha
önce başlatmış olmak ya da yalnız recipient kimliğinin eşleşmesi teslim yetkisi değildir. Bu
tablo normal uygulama/idempotency sonucu değildir; ham değer yalnız worker belleğinde ve şifreli
gizli teslim yüzeyinde bulunur.

`iam_event_cursors`: `id` UUID PK, `source` ENUM(`ADMIN_EVENTS`,`USER_EVENTS`) **UNIQUE**,
`realm` TEXT, `last_event_time` TIMESTAMPTZ, `last_event_id` TEXT, `last_successful_poll_at`
TIMESTAMPTZ, `status` TEXT, `updated_at` TIMESTAMPTZ. `last_event_time`/`last_event_id` yalnız
yerel checkpoint'tir; Keycloak `eventId` start-after cursoru veya sıralama garantisi değildir.
`iam_event_deduplications`: `source`,
`event_id`, `event_time` TIMESTAMPTZ, `processed_at` TIMESTAMPTZ ve **UNIQUE (`source`,
`event_id`)**. Retention sonrası temizlenmesi cursor güvenlik penceresinden önce olamaz. Bu dört
tablo IAM-003 migration'ının fiziksel sahipliğidir; provider-command akışı IAM-004, olay kaybı,
tekrar teslim ve iptal gecikmesi testleri IAM-009 tarafından uygulanır. Aile yenileme/tekrar
kullanım etkisi IAM-005'in, cihaz ve global/kurum iptalindeki eşik etkisi IAM-006'nın sahibidir.
`iam_provider_commands` için `iam_runtime` `FORCE RLS` politikası `GLOBAL` bağlamda yalnız
`target_identity_id → user_id = app.iam_target_user_id` eşleşmesinde; ilk
`TEACHER_ACCOUNT_CREATE` provisioning'inde yalnız aynı operation code/`target_user_id` ve
kurumda satırı açar. Aynı `IAM_PROVISIONING` scope'undaki
`app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'`, yalnız aynı command/targetın gerçek provider
subject'iyle identity yazabilir; ayrı bir `IAM_PROVISIONING_FINALIZE` scope'u yoktur. Kurum
bağlamı, başka kullanıcı/hedef veya boş hedef varsayılan olarak reddedilir.

### 4.12. Rol iptali yaşam döngüsü (bağlayıcı kural)

Bölüm 15.6'da belirtildiği gibi, pinlenmiş bileşik FK tekniği bir rolün `revoked_at IS NULL`
(aktiflik) koşulunu **doğrulayamaz** — yalnızca rolün türünü (`ORG_ADMIN`/`TEACHER`) doğrular.
Bu nedenle, bir rolün geri alınmasının bağlı kayıtlara ne yapacağı burada **bağlayıcı bir
davranış kuralı** olarak yazılır (uygulama tekniği, `API_GENEL_KURALLARI.md` ve
`DENETIM_VE_GERI_ALMA_ILKELERI.md` ile uyumlu ilgili uygulama görevinde seçilir; sonuç burada
sabittir):

1. **Bir `TEACHER` rolü geri alındığında (`organization_membership_roles.revoked_at`
   doldurulduğunda), bu işlemle **aynı işlemde**:**
   - O role bağlı **aktif** `class_teacher_assignments` satırları `unassigned_at` ile
     kapatılır (bir hoca artık var olmayan bir role dayanarak bir sınıfa "atanmış" görünemez).
   - O role bağlı **aktif** `organization_membership_permissions` satırları (bu rol `target_
     membership_role_id` olarak) `revoked_at` ile kapatılır.
   - Bu iki adım atlanırsa, rolü geri alınmış bir hoca hâlâ aktif görünen sınıf ataması/izin
     kayıtlarına sahip kalır; bu, bölüm 15.6'nın FK seviyesinde doğrulayamadığı boşluğun
     **uygulama seviyesinde kapatılması gereken tam noktasıdır**.
2. **Bir `ORG_ADMIN` rolü geri alındığında,** bu rolün **geçmişte grantor olarak verdiği**
   `organization_membership_permissions` satırları **kendiliğinden iptal edilmez veya
   silinmez** — grantor geçmişi (denetim bütünlüğü için) korunur; hocaya verilmiş izinlerin
   kendisi, onu veren yöneticinin rolünün sonraki kaderinden bağımsız olarak geçerliliğini
   sürdürür (yalnızca kurum yöneticisinin kendisi veya başka bir `ORG_ADMIN` bu izinleri ayrıca
   geri alırsa sona erer).
3. **Yeni bir yetki değerlendirmesi (bir işlemin yapılıp yapılamayacağı kontrolü) her zaman
   üç koşulun aynı anda sağlanmasını gerektirir:** (a) `organization_memberships.status =
   ACTIVE`, (b) ilgili `organization_membership_roles.revoked_at IS NULL`, (c) ilgili
   `organization_membership_permissions.revoked_at IS NULL` (devredilmiş izin gerekiyorsa).
   Üçünden biri eksikse işlem reddedilir. Değerlendirme algoritmasının kesin uygulaması
   `PERM-002` kapsamındadır; bağlayıcı olan bu üç koşulun **birlikte ve her zaman** kontrol
   edilmesi gerektiğidir.

---

## 5. ORG — Kurum

### 5.1. `organizations`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `name` | TEXT | Hayır | |
| `short_name` | TEXT | Evet | |
| `logo_asset_id` | UUID | Evet | Bileşik FK: `FOREIGN KEY (logo_asset_id, id) REFERENCES file_assets (id, organization_id)` — burada `organizations.id`, `file_assets.organization_id` ile eşleşen tenant değeri olarak kullanılır (yani logo dosyasının `organization_id`'si **bu kurumun kendi `id`'sine** eşit olmalıdır). |
| `primary_color` | TEXT (hex) | Evet | |
| `status` | ENUM(`ACTIVE`,`SUSPENDED`,`ARCHIVED`) | Hayır | Yalnızca platform yöneticisi değiştirir. |
| `default_timezone` | TEXT | Hayır, varsayılan `Europe/Istanbul` | §6.1 |
| temel alanlar | — | — | bkz. §2.3 |

`organizations` bileşik FK zincirinin köküdür; kendi `id`'si (PK) yeterlidir. `logo_asset_id`
istisnadır — `organizations`'ın kendisi bir `organization_id` sütunu taşımadığından (kendisi
kurumun ta kendisidir), bileşik FK'de ikinci sütun olarak kendi `id`'si kullanılır; bu, bölüm
15.1'deki genel `(child.parent_id, child.organization_id) → parent (id, organization_id)`
deseninin `organizations` özelinde tek istisnasıdır ve yukarıda açıkça yazılmıştır.

### 5.2. `organization_brand_colors`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | PK'nin parçası |
| `color_hex` | TEXT | Hayır | PK'nin parçası |
| `sort_order` | INTEGER | Hayır | |

### 5.3. `organization_modules`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | PK'nin parçası |
| `module_code` | TEXT | Hayır | PK'nin parçası; `AGENT_GOREV_PLANI.md` §5'teki sabit modül kod listesi. |
| `is_enabled` | BOOLEAN, varsayılan `true` | Hayır | `KURUM-*` etkin modül yönetimi (§8.2). |
| `sort_order` | INTEGER, varsayılan `0` | Hayır | Kurum yöneticisinin menü sırasını kontrollü biçimde değiştirebilmesi için (`URUN_VE_UYGULAMA_PLANI.md` §9.2 "menü sırası kontrollü bir yapı içinde değiştirilebilir olmalıdır"). Bu alan yalnızca **görünüm sırasını** etkiler; bir modülün menüde görünüp görünmeyeceği veya erişilebilirliği `is_enabled` ve kullanıcının yetkisiyle belirlenir — `sort_order` hiçbir şekilde yetkisiz erişim sağlamaz. |
| `updated_at` | TIMESTAMPTZ | Hayır | |
| `updated_by_user_id` | UUID (FK → `users.id`) | Evet | |

---

## 6. TERM — Dönem ve takvim

### 6.1. `terms`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `name` | TEXT | Hayır | |
| `start_date` | DATE | Hayır | |
| `end_date` | DATE | Hayır | |
| `status` | ENUM(`ACTIVE`,`ARCHIVED`) | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (id, organization_id)`.

### 6.2. `term_calendar_days`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `term_id` | UUID | Hayır | Bileşik FK: `(term_id, organization_id) REFERENCES terms (id, organization_id)`. |
| `calendar_date` | DATE | Hayır | |
| `day_type` | ENUM(`WORKING`,`HOLIDAY`,`NON_INSTRUCTIONAL`) | Hayır | |

Kısıt: `UNIQUE (term_id, calendar_date)`.

---

## 7. CLS — Sınıf

### 7.1. `classes`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `term_id` | UUID | Hayır | Bileşik FK: `(term_id, organization_id) REFERENCES terms (id, organization_id)`. |
| `name` | TEXT | Hayır | |
| `status` | ENUM(`ACTIVE`,`ARCHIVED`) | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (id, organization_id)`. `primary_teacher_user_id` gibi bir sütun **yoktur** —
ana hoca bilgisi yalnızca `class_teacher_assignments.is_primary`'den okunur (tek kaynak).

### 7.2. `class_teacher_assignments`

Hoca–sınıf atamasının tek kaynağıdır; kurum üyeliğinin `TEACHER` rolüne bağlıdır (bir `TEACHER`
rolü hiçbir sınıfa atanmamış olabilir, bkz. 4.9).

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `class_id` | UUID | Hayır | Bileşik FK: `(class_id, organization_id) REFERENCES classes (id, organization_id)`. |
| `organization_membership_role_id` | UUID | Hayır | Atanan `TEACHER` rolü. |
| `role_code` | `membership_role_enum`, varsayılan `'TEACHER'` | Hayır | `CHECK (role_code = 'TEACHER')`. **`organization_membership_roles.role` ile aynı `ENUM` tipi.** Bileşik FK: `(organization_membership_role_id, organization_id, role_code) REFERENCES organization_membership_roles (id, organization_id, role)` (bkz. 15.6 — gerçek, pinlenmiş sütun tekniği). |
| `is_primary` | BOOLEAN, varsayılan `false` | Hayır | |
| `assigned_at` | TIMESTAMPTZ | Hayır | |
| `assigned_by_user_id` | UUID (FK → `users.id`) | Evet | |
| `unassigned_at` | TIMESTAMPTZ | Evet | |

Kısıtlar:

- `UNIQUE (class_id, organization_membership_role_id) WHERE unassigned_at IS NULL`
- `UNIQUE (class_id) WHERE is_primary AND unassigned_at IS NULL`

---

## 8. PEOPLE — Kişiler

### 8.1. `students`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `person_id` | UUID | Hayır | Bir kişi en fazla bir öğrenci kaydına sahip olabilir (`UNIQUE`). Bileşik FK: `(person_id, organization_id) REFERENCES people (id, organization_id)`. |
| `term_id` | UUID | Hayır | Bileşik FK: `(term_id, organization_id) REFERENCES terms (id, organization_id)`. |
| `enrollment_date` | DATE | Hayır | |
| `status` | ENUM(`ACTIVE`,`INACTIVE`,`ARCHIVED`) | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıtlar: `UNIQUE (id, organization_id)`; `UNIQUE (person_id)`.

**`class_id` yoktur** — öğrenci–sınıf ilişkisi tarihçeli `student_class_enrollments`'ta tutulur
(bkz. 8.2); güncel sınıf, oradaki aktif (`ended_at IS NULL`) satırdan okunur.

İndeks: `(organization_id, status)`.

### 8.2. `student_class_enrollments`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `student_id` | UUID | Hayır | Bileşik FK: `(student_id, organization_id) REFERENCES students (id, organization_id)`. |
| `class_id` | UUID | Hayır | Bileşik FK: `(class_id, organization_id) REFERENCES classes (id, organization_id)`. |
| `started_at` | TIMESTAMPTZ | Hayır | |
| `ended_at` | TIMESTAMPTZ | Evet | `NULL` ise hâlâ aktif üyeliktir. |
| `ended_reason` | ENUM(`TRANSFERRED`,`CLASS_ARCHIVED`,`STUDENT_ARCHIVED`) | Yalnızca `ended_at` dolu iken | |
| `created_by_user_id` | UUID (FK → `users.id`) | Evet | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıtlar:

- `UNIQUE (student_id) WHERE ended_at IS NULL` (partial) — en fazla bir **aktif** üyelik.
- `EXCLUDE USING gist (student_id WITH =, tstzrange(started_at, COALESCE(ended_at,
  'infinity'::timestamptz)) WITH &&)` — bu `EXCLUDE` kısıtı (`btree_gist` eklentisi gerektirir),
  **tarih aralığı çakışmasını** DB seviyesinde engeller: yukarıdaki partial index yalnızca
  "aynı anda birden fazla açık (`ended_at IS NULL`) üyelik" durumunu engellerken, bu kısıt
  ayrıca **geçmişe dönük** (backdated) bir üyeliğin daha önce kapatılmış başka bir üyelikle
  tarih aralığı olarak çakışmasını da engeller. Bu, önceki sürümde yalnızca uygulama katmanına
  bırakılan bir tutarlılık kuralının veritabanı seviyesinde ifade edilebilen gerçek bir
  örneğidir.

### 8.3. `student_guardians`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `student_id` | UUID | Hayır | Bileşik FK: `(student_id, organization_id) REFERENCES students (id, organization_id)`. |
| `person_id` | UUID | Hayır | Velinin kendi kişi kaydı. Bileşik FK: `(person_id, organization_id) REFERENCES people (id, organization_id)`. |
| `relation_type` | ENUM(`MOTHER`,`FATHER`) | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (student_id, relation_type)`.

### 8.4. Kuruma özel alanlar

Ana planın "her şeyi tek bir JSON alanında tutma veya tamamen dinamik EAV kullanma" yasağına
(§3.3) uyacak biçimde kurulmuş, **kontrollü, tip güvenli genişletme modelidir**. Bu, klasik
serbest EAV değildir: her değer, tanım tablosundan denormalize edilmiş `field_type`/
`entity_type` ile eşleştirilmiş, `CHECK`'lerle sınırlanmış özel sütunlarda tutulur.

**`custom_field_definitions`**

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `entity_type` | ENUM(`PERSON`,`STUDENT`) | Hayır | |
| `field_key` | TEXT | Hayır | |
| `label` | TEXT | Hayır | |
| `field_type` | ENUM(`SHORT_TEXT`,`LONG_TEXT`,`NUMBER`,`DATE`,`BOOLEAN`,`SINGLE_CHOICE`,`MULTI_CHOICE`) | Hayır | |
| `is_required` | BOOLEAN | Hayır | |
| `is_visible` | BOOLEAN | Hayır | |
| `sort_order` | INTEGER | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıtlar: `UNIQUE (organization_id, entity_type, field_key)`; `UNIQUE (id, organization_id)`;
`UNIQUE (id, entity_type)`; `UNIQUE (id, field_type)` — hepsi aşağıdaki bileşik FK'ler için
(her biri gerçekten kullanılır, bkz. altta).

**`custom_field_options`**

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `field_definition_id` | UUID | Hayır | Bileşik FK: `(field_definition_id, organization_id) REFERENCES custom_field_definitions (id, organization_id)`. |
| `field_type` | ENUM(`SHORT_TEXT`,`LONG_TEXT`,`NUMBER`,`DATE`,`BOOLEAN`,`SINGLE_CHOICE`,`MULTI_CHOICE`) | Hayır | Denormalize. Bileşik FK: `(field_definition_id, field_type) REFERENCES custom_field_definitions (id, field_type)`. |
| `option_value` | TEXT | Hayır | |
| `sort_order` | INTEGER | Hayır | |

Kısıtlar:

- `CHECK (field_type IN ('SINGLE_CHOICE', 'MULTI_CHOICE'))` — bir seçenek satırı, ancak
  tanımının türü seçim tipiyse var olabilir; bu, bileşik FK ile denormalize edilen
  `field_type`'ın kendisi üzerinden **DB seviyesinde** zorlanır (tanım `NUMBER` iken bu satıra
  `field_type='NUMBER'` yazılamaz çünkü bileşik FK tanımla eşleşmesini zaten zorunlu kılar; bu
  `CHECK` ayrıca `SHORT_TEXT` vb. türlerin hiç seçenek satırı almamasını garanti eder).
- `UNIQUE (field_definition_id, option_value)`.
- `UNIQUE (id, field_definition_id)` — aşağıdaki bileşik FK'ler için.

**`custom_field_values`**

Polimorfik tek `entity_id` yerine hedefe göre ayrılmış nullable FK; `field_type`/`entity_type`
denormalize edilerek değer sütunlarının ve hedefin türle tutarlılığı DB seviyesinde
sağlanmıştır.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `field_definition_id` | UUID | Hayır | Bileşik FK: `(field_definition_id, organization_id) REFERENCES custom_field_definitions (id, organization_id)`. |
| `entity_type` | ENUM(`PERSON`,`STUDENT`) | Hayır | Denormalize. Bileşik FK: `(field_definition_id, entity_type) REFERENCES custom_field_definitions (id, entity_type)`. |
| `field_type` | ENUM(...) | Hayır | Denormalize. Bileşik FK: `(field_definition_id, field_type) REFERENCES custom_field_definitions (id, field_type)`. |
| `target_person_id` | UUID | `entity_type = PERSON` iken dolu | Bileşik FK: `(target_person_id, organization_id) REFERENCES people (id, organization_id)`. |
| `target_student_id` | UUID | `entity_type = STUDENT` iken dolu | Bileşik FK: `(target_student_id, organization_id) REFERENCES students (id, organization_id)`. |
| `value_text` | TEXT | Evet | |
| `value_number` | NUMERIC | Evet | |
| `value_date` | DATE | Evet | |
| `value_boolean` | BOOLEAN | Evet | |

Kısıtlar:

- `CHECK ((entity_type = 'PERSON' AND target_person_id IS NOT NULL AND target_student_id IS
  NULL) OR (entity_type = 'STUDENT' AND target_student_id IS NOT NULL AND target_person_id IS
  NULL))` — hedef, tanımın `entity_type`'ıyla DB seviyesinde tutarlıdır (yalnızca soyut bir XOR
  değil, hangi tarafın doğru olduğu da zorlanır).
- `CHECK` — değer sütunu `field_type`'a göre sınırlıdır (tek `CHECK`, alt sütun karşılaştırmaları
  ile, alt sorgu gerektirmez, gerçek Postgres `CHECK`'i):
  ```
  (field_type IN ('SHORT_TEXT','LONG_TEXT') AND value_text IS NOT NULL
     AND value_number IS NULL AND value_date IS NULL AND value_boolean IS NULL)
  OR (field_type = 'NUMBER' AND value_number IS NOT NULL
     AND value_text IS NULL AND value_date IS NULL AND value_boolean IS NULL)
  OR (field_type = 'DATE' AND value_date IS NOT NULL
     AND value_text IS NULL AND value_number IS NULL AND value_boolean IS NULL)
  OR (field_type = 'BOOLEAN' AND value_boolean IS NOT NULL
     AND value_text IS NULL AND value_number IS NULL AND value_date IS NULL)
  OR (field_type IN ('SINGLE_CHOICE','MULTI_CHOICE')
     AND value_text IS NULL AND value_number IS NULL
     AND value_date IS NULL AND value_boolean IS NULL)
  ```
- `UNIQUE (field_definition_id, target_person_id) WHERE target_person_id IS NOT NULL` (partial)
- `UNIQUE (field_definition_id, target_student_id) WHERE target_student_id IS NOT NULL` (partial)
- `UNIQUE (id, field_definition_id)`; `UNIQUE (id, field_type)` — aşağıdaki
  `custom_field_value_selected_options` bileşik FK'leri için.

**`custom_field_value_selected_options`**

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `custom_field_value_id` | UUID | Hayır | PK'nin parçası. |
| `custom_field_option_id` | UUID | Hayır | PK'nin parçası. |
| `field_definition_id` | UUID | Hayır | Denormalize. |
| `field_type` | ENUM(...) | Hayır | Denormalize. |

Kısıtlar:

- `PRIMARY KEY (custom_field_value_id, custom_field_option_id)`
- Bileşik FK: `(custom_field_value_id, field_definition_id) REFERENCES custom_field_values
  (id, field_definition_id)`
- Bileşik FK: `(custom_field_value_id, field_type) REFERENCES custom_field_values (id,
  field_type)`
- Bileşik FK: `(custom_field_option_id, field_definition_id) REFERENCES custom_field_options
  (id, field_definition_id)`
- `UNIQUE (custom_field_value_id) WHERE field_type = 'SINGLE_CHOICE'` (partial) — **`SINGLE_
  CHOICE` için en fazla bir seçenek seçilmesi artık veritabanı seviyesinde zorunlu kılınır**
  (önceki sürümde bu, `field_type` bilgisi bu tabloda bulunmadığı için yalnızca uygulama
  katmanı sorumluluğu olarak bırakılmıştı; `field_type`'ın denormalize edilmesiyle artık bir
  partial `UNIQUE` ile ifade edilebilir hâle gelmiştir).

Bu tablodaki üç bileşik FK birlikte, seçilen seçeneğin her zaman değerin ait olduğu tanımla
aynı `field_definition_id` ve `field_type`'a sahip olmasını DB seviyesinde garanti eder.

---

## 9. ATT — Yoklama

### 9.1. `organization_attendance_statuses`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `code` | TEXT | Hayır | |
| `label` | TEXT | Hayır | |
| `is_active` | BOOLEAN, varsayılan `true` | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (organization_id, code)`; `UNIQUE (id, organization_id)`. `Geldi`/`Gelmedi` bu
tabloda **değildir** — sabit ürün durumlarıdır, `attendance_records.status_type`'ta `ENUM`
olarak tutulur.

### 9.2. `attendance_sessions`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `class_id` | UUID | Hayır | Bileşik FK: `(class_id, organization_id) REFERENCES classes (id, organization_id)`. |
| `session_date` | DATE | Hayır | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıt: `UNIQUE (class_id, session_date)`; `UNIQUE (id, organization_id)`.

### 9.3. `attendance_records`

**Bu tablo güncel-durum tablosudur, değişmez geçmiş kaydı değildir** (bkz. §2.4).

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `attendance_session_id` | UUID | Hayır | Bileşik FK: `(attendance_session_id, organization_id) REFERENCES attendance_sessions (id, organization_id)`. |
| `student_id` | UUID | Hayır | Bileşik FK: `(student_id, organization_id) REFERENCES students (id, organization_id)`. |
| `status_type` | ENUM(`UNMARKED`,`PRESENT`,`ABSENT`,`CUSTOM`) | Hayır | `UNMARKED`, yeni oturumda henüz kullanıcı tarafından işaretlenmemiş öğrenci için teknik başlangıç durumudur; arayüzde seçilebilir yoklama durumu değildir. |
| `custom_status_id` | UUID | Yalnızca `status_type=CUSTOM` iken dolu | Bileşik FK: `(custom_status_id, organization_id) REFERENCES organization_attendance_statuses (id, organization_id)`. |
| `recorded_by_user_id` | UUID (FK → `users.id`) | Evet | `UNMARKED` iken `NULL`; kullanıcı tarafından işaretlenmiş durumda işlemi yapan kullanıcı. |
| `recorded_at` | TIMESTAMPTZ | Evet | `UNMARKED` iken `NULL`; kullanıcı tarafından işaretlenmiş durumda kayıt zamanı. |
| `row_version` | INTEGER, varsayılan `1` | Hayır | |

Kısıtlar: `UNIQUE (attendance_session_id, student_id)`; `CHECK ((status_type = 'CUSTOM' AND
custom_status_id IS NOT NULL) OR (status_type IN ('UNMARKED','PRESENT','ABSENT') AND custom_status_id IS
NULL))`; `CHECK ((status_type = 'UNMARKED' AND recorded_by_user_id IS NULL AND recorded_at IS
NULL) OR (status_type IN ('PRESENT','ABSENT','CUSTOM') AND recorded_by_user_id IS NOT NULL AND
recorded_at IS NOT NULL))` — durum ile özel durum/işleyen/zaman ilişkisi salt prose olarak
kalmaz, aynı satırdaki sütunlara bakan bu `CHECK`lerle DB seviyesinde zorunlu kılınır.

Oturum oluşturulurken o tarihte sınıfa kayıtlı her öğrenci için fiziksel olarak silinmeyen bir
`attendance_records` satırı `UNMARKED` ile hazırlanır. Bu teknik durum
`organization_attendance_statuses` tablosunda değildir; kurum yöneticisi tarafından
tanımlanamaz, kullanıcı tarafından seçilemez ve raporda “Geldi/Gelmedi” gibi bir yoklama sonucu
sayılmaz. İlk “Hepsi Geldi” geri alındığında ilgili satırlar silinmeden tekrar `UNMARKED` olur;
`recorded_by_user_id` ve `recorded_at` da `NULL`a döner; audit geçmişi korunur.

**[Hassas]** `status_type=CUSTOM` ve ilgili durum "Hasta" gibi bir değer taşıdığında,
`KISISEL_VERI_ENVANTERI.md` satır 12'deki dolaylı sağlık verisi çıkarımı riskini taşır.

**Statik olarak ifade edilemeyen kural — öğrencinin oturum tarihinde ilgili sınıfa kayıtlı
olması:** Bir `attendance_records` satırının `student_id`'si, `attendance_session_id`'nin
işaret ettiği `attendance_sessions.class_id`'ye, o oturumun `session_date`'inde geçerli bir
`student_class_enrollments` aralığıyla kayıtlı olmalıdır. Bu, iki farklı tablo arasında,
**zamana bağlı bir aralık koşuluyla** ifade edilen bir kuraldır; statik FK/CHECK bunu ifade
edemez (bkz. bölüm 15.5 — uygulama katmanı zorunluluğu ve `P-009`/`P-010` kabul senaryosu
adayı).

---

## 10. CONTENT — İçerik

### 10.1. `file_assets`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `storage_key` | TEXT | Hayır | |
| `original_filename` | TEXT | Hayır | |
| `mime_type` | TEXT | Hayır | |
| `size_bytes` | BIGINT | Hayır | |
| `uploaded_by_user_id` | UUID (FK → `users.id`) | Hayır | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıt: `UNIQUE (id, organization_id)`. `people.photo_asset_id` ve içerik PDF ekleri (`contents.
pdf_asset_id`) bu tabloya bileşik FK ile (aynı kurum) bağlanır; `organizations.logo_asset_id`
kendi `id`'sini tenant değeri olarak kullanan tek istisnadır (bkz. bölüm 5.1). Bu tabloya düz
(bileşik olmayan) FK veren **hiçbir global tablo/alan yoktur** — `platform_administrator_
profiles`'ın (bölüm 4.4) bu tabloyla hiçbir ilişkisi yoktur; profil fotoğrafı v1 kapsamında
desteklenmez (bkz. bölüm 15.4).

### 10.2. `contents`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `content_type` | ENUM(`TEXT`,`TEXT_WITH_PDF`) | Hayır | |
| `body_text` | TEXT | Hayır | |
| `pdf_asset_id` | UUID | Yalnızca `content_type=TEXT_WITH_PDF` iken dolu | Bileşik FK: `(pdf_asset_id, organization_id) REFERENCES file_assets (id, organization_id)`. |
| temel alanlar | — | — | bkz. §2.3 |

Kısıtlar: `UNIQUE (id, organization_id)`; `CHECK ((content_type = 'TEXT_WITH_PDF' AND
pdf_asset_id IS NOT NULL) OR (content_type = 'TEXT' AND pdf_asset_id IS NULL))`.

---

## 11. PROGRAM — Program, şablon ve plan

### 11.1. `starter_program_templates`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `code` | TEXT | Hayır | PK, ör. `MEMORIZATION_DAILY`, `SURAH_DUA_LIST`, `CARD_SECTION`, `QURAN_PAGE_TRACKING`, `ELIF_BA`, `PRAYER_TRACKING`, `FREE_TASK`. |
| `label` | TEXT | Hayır | |
| `description` | TEXT | Evet | |

Yalnızca sistemin önerdiği bir başlangıç kısayoludur; bir `program` bu kataloğa referans
vermek zorunda değildir (bkz. 11.7).

### 11.2. `programs`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `class_id` | UUID | Hayır | Bileşik FK: `(class_id, organization_id) REFERENCES classes (id, organization_id)`. |
| `name` | TEXT | Hayır | |
| `based_on_starter_template_id` | TEXT (FK → `starter_program_templates.code`) | Evet | |
| `status` | ENUM(`ACTIVE`,`PASSIVE`,`ARCHIVED`) | Hayır | |
| `current_program_version_id` | UUID | Evet | Bileşik FK: `(current_program_version_id, id) REFERENCES program_versions (id, program_id)` — bu, referans verilen sürümün **gerçekten bu programa ait** olmasını (başka bir programın sürümü olamayacağını) DB seviyesinde zorunlu kılar (kendi `id`'sini FK tuple'ının bir parçası olarak kullanan, PostgreSQL'de geçerli bir öz-referans tekniğidir; `program_versions` üzerinde `UNIQUE (id, program_id)` gerektirir, bkz. 11.3). |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (id, organization_id)`.

### 11.3. `program_versions`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `program_id` | UUID | Hayır | Bileşik FK: `(program_id, organization_id) REFERENCES programs (id, organization_id)`. |
| `version_no` | INTEGER | Hayır | |
| `evaluation_score_enabled` | BOOLEAN, varsayılan `false` | Hayır | |
| `evaluation_note_enabled` | BOOLEAN, varsayılan `false` | Hayır | |
| `evaluation_repeat_required_enabled` | BOOLEAN, varsayılan `false` | Hayır | |
| `content_planning_mode` | ENUM(`MANUAL`,`TEMPLATE`,`MIXED`) | Hayır | |
| `created_by_user_id` | UUID (FK → `users.id`) | Evet | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıtlar: `UNIQUE (program_id, version_no)`; `UNIQUE (id, organization_id)`; `UNIQUE (id,
program_id)` — bölüm 11.2'deki öz-referans bileşik FK'nin hedefi.

**Not — büyük/küçük değişiklik eşiği:** `PROGRAM-003`'te kesinleştirilecektir.
**[Öneri/bekleyen karar]**

### 11.4. `program_templates`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `name` | TEXT | Hayır | |
| `day_count` | INTEGER | Hayır | |
| temel alanlar | — | — | bkz. §2.3 |

Kısıt: `UNIQUE (id, organization_id)`.

### 11.5. `program_template_days`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `program_template_id` | UUID | Hayır | Bileşik FK: `(program_template_id, organization_id) REFERENCES program_templates (id, organization_id)`. |
| `day_offset` | INTEGER | Hayır | |
| `sequence_no` | INTEGER, varsayılan `1` | Hayır | |
| `content_id` | UUID | Evet | Bileşik FK: `(content_id, organization_id) REFERENCES contents (id, organization_id)`. |
| `title` | TEXT | Evet | |

Kısıtlar: `UNIQUE (program_template_id, day_offset, sequence_no)`; `UNIQUE (id,
organization_id)`; `UNIQUE (id, program_template_id)` — aşağıdaki `plan_items` bileşik FK'si
için.

### 11.6. `plan_items`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `program_version_id` | UUID | Hayır | Bileşik FK: `(program_version_id, organization_id) REFERENCES program_versions (id, organization_id)`. |
| `planned_date` | DATE | Hayır | |
| `sequence_no` | INTEGER, varsayılan `1` | Hayır | |
| `content_id` | UUID | Evet | Bileşik FK: `(content_id, organization_id) REFERENCES contents (id, organization_id)`. |
| `source` | ENUM(`MANUAL`,`TEMPLATE`) | Hayır | |
| `source_program_template_id` | UUID | Yalnızca `source=TEMPLATE` iken dolu | Bileşik FK: `(source_program_template_id, organization_id) REFERENCES program_templates (id, organization_id)`. |
| `source_template_day_id` | UUID | Yalnızca `source=TEMPLATE` iken dolu | Bileşik FK: `(source_template_day_id, source_program_template_id) REFERENCES program_template_days (id, program_template_id)` — referans verilen günün **gerçekten** `source_program_template_id` şablonuna ait olmasını zorunlu kılar (önceki sürümde bu iki alan arasında tutarlılığı zorlayan bir kısıt yoktu). |
| `created_by_user_id` | UUID (FK → `users.id`) | Hayır | |
| `row_version` | INTEGER, varsayılan `1` | Hayır | |
| `created_at` | TIMESTAMPTZ | Hayır | |

Kısıtlar: `UNIQUE (program_version_id, planned_date, sequence_no)`; `UNIQUE (id,
organization_id)`; `CHECK ((source = 'TEMPLATE' AND source_program_template_id IS NOT NULL AND
source_template_day_id IS NOT NULL) OR (source = 'MANUAL' AND source_program_template_id IS
NULL AND source_template_day_id IS NULL))`.

**`class_id` sütunu kaldırıldı.** Önceki sürümde `plan_items` doğrudan bir `class_id` taşıyordu;
bu, `program_version_id → program_id → programs.class_id` zincirinden **bağımsız, ikinci bir
kaynak** olduğundan, uygulama hatasıyla `plan_items.class_id`'nin ilgili programın gerçek
sınıfından **farklı** bir değere sahip olması mümkündü (hiçbir FK bu ikisinin eşleşmesini
zorlamıyordu). Sınıf bilgisi artık yalnızca `program_version_id → program_versions.program_id →
programs.class_id` zinciri üzerinden **türetilir**; ikinci bir kaynak yoktur. Sorgu
performansı için bu zincirin bir görünüm (`VIEW`) veya sorgu zamanı `JOIN` ile okunması
önerilir; kalıcı bir önbellek sütunu gerekirse bu, uygulama görevinde (`ATT-*`/`PROGRAM-*`)
ayrıca değerlendirilecek bir performans kararıdır, bir şema zorunluluğu değildir.

### 11.7. Takip türlerinin plan/program modeliyle örnek eşlemesi

| Takip türü | `programs.based_on_starter_template_id` | Günlük `plan_items` düzeni |
|---|---|---|
| Günlük ezber | `MEMORIZATION_DAILY` | Genelde günde 1 kalem. |
| Haftalık ezber | `MEMORIZATION_DAILY` | Haftanın belirli günlerinde 1'er kalem. |
| Sûre ve dua listeleri | `SURAH_DUA_LIST` | Aynı günde birden fazla kalem (`sequence_no=1,2`). |
| Kart/bölüm sistemi | `CARD_SECTION` | Her kart/bölüm ayrı bir kalem. |
| Kur'an sayfa takibi | `QURAN_PAGE_TRACKING` | Günde 1+ kalem. |
| Elif-Ba ilerlemesi | `ELIF_BA` | Genelde günde 1 kalem. |
| Namaz takibi | `PRAYER_TRACKING` | Aynı günde 5 kalem (her vakit için). |
| Serbest görev/ödev | `FREE_TASK` / `NULL` | Tamamen serbest. |

---

## 12. PROGRESS — İlerleme

### 12.1. `progress_records`

**Bu tablo güncel-durum tablosudur, değişmez geçmiş kaydı değildir** (bkz. §2.4).

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | Hayır | |
| `plan_item_id` | UUID | Hayır | Bileşik FK: `(plan_item_id, organization_id) REFERENCES plan_items (id, organization_id)`. |
| `student_id` | UUID | Hayır | Bileşik FK: `(student_id, organization_id) REFERENCES students (id, organization_id)`. |
| `completed` | BOOLEAN, varsayılan `false` | Hayır | |
| `score` | SMALLINT | Evet | `CHECK (score BETWEEN 0 AND 10)`. |
| `teacher_note` | TEXT | Evet | **[Hassas]** |
| `repeat_required` | BOOLEAN | Evet | |
| `recorded_by_user_id` | UUID (FK → `users.id`) | Hayır | |
| `recorded_at` | TIMESTAMPTZ | Hayır | |
| `row_version` | INTEGER, varsayılan `1` | Hayır | |
| `updated_at` | TIMESTAMPTZ | Hayır | |

Kısıt: `UNIQUE (plan_item_id, student_id)`.

**[Hassas]** `KISISEL_VERI_ENVANTERI.md` satır 13'teki risk sınıfı geçerlidir.

**Statik olarak ifade edilemeyen kural — öğrencinin plan tarihinde programın sınıfına kayıtlı
olması:** `plan_item_id`'nin işaret ettiği planın `planned_date`'inde, `student_id`'nin ilgili
programın sınıfına (`plan_items.program_version_id → programs.class_id` zinciri) geçerli bir
`student_class_enrollments` kaydıyla kayıtlı olması gerekir. Zamana bağlı ve çok adımlı bir
zincir olduğundan statik FK/CHECK ile ifade edilemez (bkz. bölüm 15.5).

---

## 13. AUDIT — Denetim ve geri alma temeli

### 13.0. Kademeli migration sahipliği

Audit sözleşmesinin davranışı değişmeden iki migration aşamasında uygulanır:

1. `AUDIT-001A`, `audit_action_catalog` ile `audit_logs` çekirdeğini erken oluşturur. Bu aşama
   yalnız sınıf kapsamı gerektirmeyen `ORG_CREATED`, `ORG_STATUS_CHANGED`,
   `ORG_SETTING_CHANGED` ve `PLATFORM_ADMIN_ORG_ACCESS` katalog satırlarını açar.
   `audit_logs.scope_class_id` sütunu
   şemada bulunur; ancak `audit_action_catalog.requires_class_scope = false` ve
   `audit_logs.requires_class_scope = false AND scope_class_id IS NULL` geçici DB
   kısıtlarıyla sınıf kapsamlı katalog veya olay yazımı fail-closed reddedilir.
2. `CLS-002` ile `classes (id, organization_id)` fiziksel olarak oluştuktan sonra `AUDIT-001`,
   bu geçici kısıtları kontrollü migration ile kaldırır; `(scope_class_id, organization_id)`
   bileşik FK'sini ve aşağıda tanımlı sınıf kapsamlı katalog satırlarını ekler.

Bu kademelendirme sınıf kapsamı doğrulanmayan audit kaydına izin vermez, `classes` tablosu
oluşmadan sahte veya zayıf bir FK üretmez ve Dalga 2 kurum yaşam döngüsünün audit kaydını aynı
transaction'da fail-closed yazabilmesini sağlar. `AUDIT-001A` yeni runtime rolü veya geniş
tablo yetkisi oluşturmaz; görev sahibi runtime rolüne ait dar `INSERT` policy/grant'i ilgili
uygulama migration'ında eklenir.

### 13.1. `audit_action_catalog`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `code` | TEXT | Hayır | Eylem kodu, ör. `STUDENT_CREATED`, `STUDENT_ARCHIVED`, `GUARDIAN_UPDATED`, `CLASS_ENROLLMENT_CHANGED`, `TEACHER_ASSIGNMENT_CHANGED`, `ATTENDANCE_RECORD_CHANGED`, `ATTENDANCE_BULK_PRESENT_RECORD_CHANGED`, `PROGRAM_CHANGED`, `CONTENT_CHANGED`, `PROGRESS_CHANGED`, `PERMISSION_CHANGED`, `ORG_CREATED`, `ORG_STATUS_CHANGED`, `ORG_SETTING_CHANGED`, `REPORT_EXPORTED`, `PLATFORM_ADMIN_ORG_ACCESS`, `LOGIN_SUCCEEDED`, `SESSION_REVOKED`. |
| `payload_schema_version` | SMALLINT | Hayır | Bu eylemin `old_value`, `new_value`, `event_metadata` ve izinli `reason_code` alanlarının şema sürümü. |
| `target_entity_type` | TEXT | Hayır | |
| `event_scope` | ENUM(`ORGANIZATION`,`GLOBAL`) | Hayır | Bu işlem türünün doğası gereği bir kuruma mı bağlı olduğu, yoksa gerçekten global mi olduğu. |
| `event_kind` | ENUM(`DATA_MUTATION`,`SECURITY`,`ACCESS`,`EXPORT`) | Hayır | `DATA_MUTATION` hedefin durumunu değiştiren olaydır; diğerleri hedef kimliği bilinmeden de kaydedilebilir. |
| `requires_target_entity` | BOOLEAN | Hayır | `DATA_MUTATION` katalog satırlarında `true` olmak zorundadır; hedefi bilinmeyen güvenlik/erişim olayları için `false` olabilir. |
| `requires_class_scope` | BOOLEAN | Hayır | Operasyonel sınıf olayında `true`; kurum/genel olayında `false`. |
| `requires_operation_group` | BOOLEAN | Hayır | Her hedef satırının bir toplu komuta ait olması zorunluysa `true`; tekil eylemde `false`. |
| `is_undoable` | BOOLEAN, varsayılan `false` | Hayır | |
| `payload_schema` | JSONB | Hayır | Eylem + sürüm için izinli eski/yeni değer alanları, meta veri anahtarları ve reason kodları. Serbest JSON sözleşmesi değildir; uygulama katmanı bu şemaya göre doğrular. |

Kısıtlar: `PRIMARY KEY (code, payload_schema_version)`; `UNIQUE (code,
payload_schema_version, event_scope, target_entity_type, event_kind, requires_target_entity,
requires_class_scope, requires_operation_group)`;
`CHECK ((event_kind = 'DATA_MUTATION' AND requires_target_entity = true) OR event_kind <>
'DATA_MUTATION')`; `CHECK (requires_operation_group = false OR requires_class_scope = true)`.
Böylece bir eylemin geçmiş payload sürümü değişmeden kalır; yeni alan veya anlam gerektiğinde
mevcut katalog satırı değiştirilmez, yeni `payload_schema_version` açılır.

Bağlayıcı katalog değerleri: `ATTENDANCE_RECORD_CHANGED` ve `PROGRESS_CHANGED` için
`requires_class_scope=true`, `requires_operation_group=false`; V1 “Hepsi Geldi” için ayrı
`ATTENDANCE_BULK_PRESENT_RECORD_CHANGED` satırı `requires_class_scope=true` ve
`requires_operation_group=true` taşır. Bu ayrım, toplu işlemin sıradan tekil yoklama olayı
olarak kaydedilmesini engeller.

Dalga 2 kurum katalogları aşağıdaki anlamı taşır:

| Kod | `event_scope` | `event_kind` | Hedef | `is_undoable` | Kapalı payload sınırı |
|---|---|---|---|---:|---|
| `ORG_CREATED` | `ORGANIZATION` | `DATA_MUTATION` | `ORGANIZATION`, zorunlu | `false` | `old_value=NULL`; `new_value` yalnız `status`, `rowVersion`; metadata yalnız `operationCode` |
| `ORG_STATUS_CHANGED` | `ORGANIZATION` | `DATA_MUTATION` | `ORGANIZATION`, zorunlu | `false` | Eski/yeni değer yalnız `status`, `rowVersion`; metadata yalnız iptal edilen üyelik/family/token sayıları ve `operationCode` |
| `ORG_SETTING_CHANGED` | `ORGANIZATION` | `DATA_MUTATION` | `ORGANIZATION`, zorunlu | `true` | Eski/yeni değer yalnız ad, kısa ad, saat dilimi, onaylı marka/modül/yoklama ayarı alanı ve `rowVersion`; metadata yalnız `operationCode` |
| `PLATFORM_ADMIN_ORG_ACCESS` | `ORGANIZATION` | `ACCESS` | `ORGANIZATION`, zorunlu | `false` | `old_value/new_value=NULL`; metadata yalnız `operationCode`, `outcome`; kontrollü red nedeni dışında serbest metin yok |

Bu dört katalog satırında `requires_class_scope=false` ve
`requires_operation_group=false`dır. `payload_schema` boş `{}` olamaz: en az eski/yeni değer,
metadata ve reason-code allow-list'lerini taşıyan kapalı bir nesne olmalı; bilinmeyen alanlar
uygulama doğrulamasında reddedilmelidir. Kurum oluşturma veya durum değişikliği
`ORG_SETTING_CHANGED` olarak yazılamaz; aksi halde action seviyesindeki geri alınabilirlik
yanlışlıkla oluşturma/arşivleme akışına taşınır.

### 13.2. `audit_logs`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `organization_id` | UUID (FK → `organizations.id`) | `event_scope=GLOBAL` iken `NULL` | `CHECK ((event_scope = 'GLOBAL' AND organization_id IS NULL) OR (event_scope = 'ORGANIZATION' AND organization_id IS NOT NULL))`. |
| `scope_class_id` | UUID | Evet | Olay anındaki operasyonel sınıf kapsamı; güncel öğrenci üyeliğinden türetilmez. Bileşik FK: `(scope_class_id, organization_id) REFERENCES classes (id, organization_id)`. `event_scope=GLOBAL` iken `NULL` olmak zorundadır. |
| `actor_user_id` | UUID (FK → `users.id`) | Evet | Sistem/otomatik süreç kaynaklı olaylarda `NULL` olabilir. |
| `request_id` | TEXT | Evet | API/güvenlik isteğinin `X-Request-Id` değeri; P-009'a göre 1–128 karakter, yalnızca ASCII harf/rakam ile `.`, `_`, `-`, `:` içerir. İstek dışı sistem işleri için `NULL` olabilir. |
| `action_type` | TEXT | Hayır | Aşağıdaki katalog bileşik FK'sinin parçası. |
| `payload_schema_version` | SMALLINT | Hayır | Eylem türü için kullanılan değişmez payload şema sürümü. |
| `event_scope` | ENUM(`ORGANIZATION`,`GLOBAL`) | Hayır | |
| `target_entity_type` | TEXT | Hayır | |
| `event_kind` | ENUM(`DATA_MUTATION`,`SECURITY`,`ACCESS`,`EXPORT`) | Hayır | Katalogdan denormalize edilir ve bileşik FK ile pinlenir. |
| `requires_target_entity` | BOOLEAN | Hayır | Katalogdan denormalize edilir ve hedef kimliği `CHECK`i için kullanılır. |
| `requires_class_scope` | BOOLEAN | Hayır | Katalogdan denormalize edilir; `true` ise `scope_class_id`, `false` ise `NULL` olmak zorundadır. |
| `requires_operation_group` | BOOLEAN | Hayır | Katalogdan denormalize edilir; `true` ise `operation_group_id`, `false` ise `NULL` olmak zorundadır. |
| `target_entity_id` | UUID | Evet | Bilinçli olarak polimorfik ve FK'sizdir. `requires_target_entity=true` veya `is_undo=true` ise zorunludur; hedefi henüz bilinmeyen güvenlik/erişim olayı için `NULL` olabilir. |
| `old_value` | JSONB | Evet | `action_type + payload_schema_version`ın `payload_schema`sına uyan sunucu içi ters işlem verisi. |
| `new_value` | JSONB | Evet | Aynı şemaya uyan yeni değer verisi. |
| `event_metadata` | JSONB | Evet | Yalnızca katalogdaki sürümlü şemanın izin verdiği anahtarları taşır; serbest hata/telemetri deposu değildir. |
| `reason_code` | TEXT | Evet | Yalnızca katalogdaki sürümlü şemanın izin verdiği kontrollü kodlardan biri; serbest açıklama alanı değildir. |
| `operation_group_id` | UUID | Evet | Tek kullanıcı komutunun hedef başına ürettiği audit satırlarını bağlar. V1 `HEPSI_GELDI` için zorunlu, tekil işlemler için `NULL` olabilir. |
| `occurred_at` | TIMESTAMPTZ | Hayır | |
| `ip_address` | TEXT | Evet | |
| `device_id` | UUID (FK → `trusted_devices.id`) | Evet | |
| `is_undo` | BOOLEAN, varsayılan `false` | Hayır | `CHECK ((is_undo = true AND undo_of_audit_log_id IS NOT NULL) OR (is_undo = false AND undo_of_audit_log_id IS NULL))`. |
| `undo_of_audit_log_id` | UUID | Evet | Hangi orijinal denetim kaydını geri aldığı. `CHECK (undo_of_audit_log_id <> id)`. |

Kısıtlar: `UNIQUE (id, organization_id)`; `UNIQUE (id, event_scope)`; bileşik katalog FK
`(action_type, payload_schema_version, event_scope, target_entity_type, event_kind,
requires_target_entity, requires_class_scope, requires_operation_group) REFERENCES
audit_action_catalog (code, payload_schema_version, event_scope, target_entity_type,
event_kind, requires_target_entity, requires_class_scope, requires_operation_group)`;
`CHECK ((scope_class_id IS NULL) OR event_scope = 'ORGANIZATION')`; `CHECK
((requires_target_entity = true OR is_undo = true) AND target_entity_id IS NOT NULL OR
(requires_target_entity = false AND is_undo = false))`; `CHECK ((requires_class_scope = true
AND scope_class_id IS NOT NULL) OR (requires_class_scope = false AND scope_class_id IS NULL))`;
`CHECK ((requires_operation_group = true AND operation_group_id IS NOT NULL) OR
(requires_operation_group = false AND operation_group_id IS NULL))`; `CHECK (request_id IS
NULL OR (char_length(request_id) BETWEEN 1 AND 128 AND request_id ~
'^[A-Za-z0-9._:-]+$'))`. Bu `CHECK`ler, veri değişikliği/geri alma olaylarında hedef kimliğini,
operasyonel olaylarda tarihsel sınıf kapsamını, toplu hedef olaylarında grup kimliğini ve API
istek kimliğinin P-009 biçimini DB seviyesinde zorunlu tutar.

`is_undoable` değerleri `DENETIM_VE_GERI_ALMA_ILKELERI.md` §6'nın kapalı listesidir.
Özellikle `PROGRAM_CHANGED` ve çok varlıklı `GUARDIAN_UPDATED` katalog satırları `false`
kalır; güvenli aggregate ters işlem sözleşmesi oluşmadan `program_versions` satırı yerinde
değiştirilemez veya çoklu veli ilişkisi kısmen geri alınamaz.

**`undo_of_audit_log_id` tenant/scope bütünlüğü — iki bileşik FK birlikte:** Düz bir FK
(yalnızca `id`'ye referans), A kurumundaki bir geri alma kaydının B kurumundaki (veya global bir
kaydın kurum kapsamlı bir kaydın) `id`'sini göstermesine izin verirdi — `id` tek başına tekil
olduğundan hiçbir çapraz kurum/scope kontrolü yapmaz. Bunun yerine iki bileşik FK **birlikte**
tanımlanmıştır:

1. `FOREIGN KEY (undo_of_audit_log_id, organization_id) REFERENCES audit_logs (id,
   organization_id)` — `event_scope=ORGANIZATION` olan satırlar için (ikisinin de
   `organization_id`'si `NOT NULL` olduğundan bu FK gerçekten doğrulanır), hedefin **aynı
   kurumda** olmasını zorunlu kılar. `event_scope=GLOBAL` satırlarda `organization_id` her iki
   tarafta da `NULL` olduğundan bu FK **doğrulanmaz** (çok sütunlu FK'de bir sütun `NULL` ise
   FK atlanır) — bu durumda tenant eşleşmesi zaten anlamsızdır (global kaydın kurumu yoktur),
   ikinci FK devreye girer.
2. `FOREIGN KEY (undo_of_audit_log_id, event_scope) REFERENCES audit_logs (id, event_scope)` —
   `event_scope` **hiçbir zaman `NULL` olmadığından** bu FK **her zaman** doğrulanır ve hedefin
   `event_scope`'unun geri alan kayıtla **aynı** olmasını zorunlu kılar.

Bu iki FK birlikte: bir `ORGANIZATION` kapsamlı geri alma yalnızca **aynı kurumdaki ve yine
`ORGANIZATION` kapsamlı** bir kaydı gösterebilir (FK 1 kurumu, FK 2 kapsamı doğrular); bir
`GLOBAL` geri alma yalnızca **başka bir `GLOBAL`** kaydı gösterebilir (FK 2 kapsamı doğrular,
FK 1 organization_id'nin ikisi de `NULL` olduğu için zaten anlamsızdır). Kurumlar arası veya
global/kurum karışık bir `undo` ilişkisi bu ikisiyle birlikte **DB seviyesinde imkânsızdır**.
**Tek kaynak geri alma kuralı:** `CREATE UNIQUE INDEX audit_logs_one_undo_per_source_idx ON
audit_logs (undo_of_audit_log_id) WHERE is_undo = true;` Aynı kaynak audit olayı için iki farklı
`clientMutationId` ile eşzamanlı geri alma isteği gelse bile bu partial unique index ikinci
geri alma satırını DB seviyesinde reddeder. Uygulama, bu ihlali `UNDO_ALREADY_APPLIED` olarak
çevirir; hedef değişikliği ve geri alma audit satırı aynı transaction olduğundan ikinci istek
hiçbir kısmi etki bırakmaz.

**Global olay örnekleri (`event_scope=GLOBAL`, `organization_id=NULL`):** Platform yöneticisinin
kendi girişi/çıkışı, sistem genelinde bir bakım işi. `PLATFORM_ADMIN_ORG_ACCESS` her zaman
`event_scope=ORGANIZATION` ile katalogda tanımlıdır ve hedef `organization_id` ile doldurulur.
`iam_runtime` için `audit_logs` da `FORCE RLS` altındadır: `app.iam_operation_scope='ORGANIZATION'`
yalnız `organization_id=app.organization_id` satırını; `GLOBAL` yalnız `event_scope='GLOBAL'`,
`target_entity_type='USER'` ve `target_entity_id=app.iam_target_user_id` olan güvenlik satırını;
`IAM_AUTH` ise yalnız allow-listli auth güvenlik olaylarını (`PROVIDER_TOKEN_EXCHANGE`,
`PLATFORM_ADMIN_ACTIVATE`, `CONTEXT_ACTIVATE`, `SESSION_REFRESH`, `SESSION_LOGOUT`,
`DEVICE_SELF_REVOKE`) ve aynı aktör/cihaz/üyelik/kurum/family zinciriyle uyumlu satırları
okur/yazar. `DEVICE_SELF_REVOKE`, kullanıcının kendi güvenilir cihazını iptal ettiği kurum
bağlamsız işlemdir (bkz. `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md`); satırı `event_scope=GLOBAL`,
`organization_id=NULL` ile yazar. Scope dışı, eksik veya iki bağlamı birlikte taşıyan transaction
varsayılan olarak reddedilir.

**Payload ve API gösterimi:** `old_value`/`new_value`, `action_type +
payload_schema_version` tarafından belirlenen şemalı veridir. API görünümü alan bazlı sunucuda
maskelenir; maskeleme yalnızca cevabı etkiler, denetim kaydındaki sunucu içi ters işlem verisini
değiştirmez. `event_metadata` ve `reason_code` da aynı sürümlü katalog şemasına göre sunucuda
doğrulanır.

Denetim kaydı hiçbir normal uygulama akışında güncellenmez veya silinmez.

İndeks: `(organization_id, occurred_at DESC)`, `(organization_id, scope_class_id,
occurred_at DESC)`, `(organization_id, target_entity_type, target_entity_id)`,
`(operation_group_id) WHERE operation_group_id IS NOT NULL`, yukarıdaki partial unique index.

---

## 14. SYNC — `idempotency_keys`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `scope_type` | ENUM(`GLOBAL`,`ORGANIZATION`,`IAM_AUTH`) | Hayır | İdempotency anahtarının kapsamı. |
| `organization_id` | UUID (FK → `organizations.id`) | Koşullu | `ORGANIZATION` kapsamda zorunlu; `GLOBAL` ve `IAM_AUTH` kapsamlarında `NULL`. |
| `user_id` | UUID (FK → `users.id`) | Hayır | İsteği yapan `actorUserId`; düz FK (bileşik değil) — bkz. bölüm 15.3. Hedef kullanıcı değildir. |
| `client_mutation_id` | TEXT | Hayır | |
| `operation_type` | TEXT | Hayır | |
| `request_fingerprint` | TEXT | Hayır | Kanonik yöntem/yol/hedef/gövde/sürüm özetinin güvenli parmak izi. `IAM_AUTH` için en az `actorUserId`, `operationType`, `deviceIdentifier` ve işlem tipine göre `requestTokenFingerprint` girdisini kapsar: `PROVIDER_TOKEN_EXCHANGE` için Cognito access token, `PLATFORM_ADMIN_ACTIVATE`/`CONTEXT_ACTIVATE` için `contextSelectionToken`, `SESSION_REFRESH`/`SESSION_LOGOUT` için platform refresh token, `DEVICE_SELF_REVOKE` için çağıranın platform access tokenı + çağıranın isteği doğrulayan kendi `trusted_devices.id`si (çağıran cihaz) + hedef `trusted_devices.id` (`deviceId`, iptal edilecek cihaz — çağıran cihazla aynı olabilir) (bkz. `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md` §5/§5.1). |
| `status` | ENUM(`PENDING`,`COMPLETED`,`FAILED`) | Hayır | |
| `result_entity_id` | UUID | Evet | |
| `terminal_http_status` | SMALLINT | Koşullu | Terminal sonuçta HTTP durumu. |
| `terminal_error_code` | TEXT | Koşullu | `FAILED` durumunda güvenli makine kodu. |
| `result_payload` | JSONB | Evet | Minimize edilmiş, yeniden oynatılabilir güvenli sonuç. Ham access/refresh/context token bu alana yazılamaz. |
| `result_reference` | TEXT | Evet | Sonuç gövdesi yerine güvenli kanonik sonuç başvurusu. Ham sır içeren auth cevapları için zorunlu yüzey budur. |
| `lease_owner` | TEXT | Evet | `PENDING` işlemi güvenle yürüten sahiplik belirteci. |
| `lease_generation` | BIGINT | Evet | Her yeniden sahiplenmede artan fencing sayacı. |
| `lease_expires_at` | TIMESTAMPTZ | Evet | Süresi geçen `PENDING` işlemin yeniden sahiplenilmesi için üst sınır. |
| `created_at` | TIMESTAMPTZ | Hayır | |
| `completed_at` | TIMESTAMPTZ | Evet | |
| `result_expires_at` | TIMESTAMPTZ | Evet | Sonuç gövdesi/referansının minimizasyon için temizlenebileceği en erken an. |
| `key_retention_expires_at` | TIMESTAMPTZ | Hayır | Anahtar tombstone'unun korunacağı en erken bitiş anı. |

Kısıtlar:

- `CHECK (((scope_type = 'GLOBAL' OR scope_type = 'IAM_AUTH') AND organization_id IS NULL) OR (scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL))`.
- `CHECK ((result_payload IS NULL) OR (result_reference IS NULL))` — güvenli gövde ve referans birlikte yazılamaz.
- `CHECK ((status = 'PENDING' AND completed_at IS NULL AND lease_owner IS NOT NULL AND lease_generation IS NOT NULL AND lease_expires_at IS NOT NULL AND terminal_http_status IS NULL AND terminal_error_code IS NULL AND result_entity_id IS NULL AND result_payload IS NULL AND result_reference IS NULL AND result_expires_at IS NULL AND key_retention_expires_at >= lease_expires_at) OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= created_at AND lease_owner IS NULL AND lease_generation IS NULL AND lease_expires_at IS NULL AND terminal_http_status BETWEEN 200 AND 299 AND terminal_error_code IS NULL AND key_retention_expires_at > completed_at) OR (status = 'FAILED' AND completed_at IS NOT NULL AND completed_at >= created_at AND lease_owner IS NULL AND lease_generation IS NULL AND lease_expires_at IS NULL AND terminal_http_status IN (400, 403, 404, 409, 422) AND terminal_error_code IS NOT NULL AND result_entity_id IS NULL AND result_payload IS NULL AND result_reference IS NULL AND result_expires_at IS NULL AND key_retention_expires_at > completed_at))`.
- `CHECK ((result_payload IS NULL AND result_reference IS NULL AND result_expires_at IS NULL) OR (completed_at IS NOT NULL AND result_expires_at >= completed_at AND result_expires_at <= key_retention_expires_at))`.
- `CHECK (key_retention_expires_at >= created_at)`.
- `CREATE UNIQUE INDEX idempotency_keys_global_scope_uq ON idempotency_keys (user_id, client_mutation_id) WHERE scope_type = 'GLOBAL';`
- `CREATE UNIQUE INDEX idempotency_keys_organization_scope_uq ON idempotency_keys (organization_id, user_id, client_mutation_id) WHERE scope_type = 'ORGANIZATION';`
- `CREATE UNIQUE INDEX idempotency_keys_iam_auth_scope_uq ON idempotency_keys (user_id, client_mutation_id) WHERE scope_type = 'IAM_AUTH';`

Bu partial unique index'ler, SQL'deki `NULL` değerlerin birbirine eşit sayılmaması nedeniyle
tek başına bir bileşik `UNIQUE` kısıtının sağlayamayacağı NULL-güvenli tekilliği zorunlu kılar.
`GLOBAL` kapsam yalnızca platform yöneticisinin, örneğin henüz `organization_id` oluşmamış
kurum oluşturma işlemleri için kullanılabilir; sunucu bu rolü her istekte doğrular. `IAM_AUTH`
kapsamı ise yalnız kurum öncesi kimlik doğrulama/token değişimi akışları içindir; `GLOBAL`
yerine kullanılır çünkü normal kullanıcı da bu yüzeye erişir. `IAM_AUTH` replay'i başka
kullanıcının veya başka token değişiminin sonucunu okuyamaz: tek başına
`user_id + client_mutation_id` yeterli değildir; `request_fingerprint` içinde `operation_type`,
`deviceIdentifier` ve işlem tipine göre güvenli token fingerprint'i de doğrulanır. Aynı kullanıcı
için aynı `client_mutation_id` farklı `operation_type` ile yeniden kullanılırsa fingerprint
çatışması oluşur ve `409 IDEMPOTENCY_KEY_REUSED` döner.
`PENDING` durumda yalnızca lease alanları dolu; tüm terminal alanlar ile sonuç kimliği,
gövdesi/referansı ve sonuç saklama anı boştur; anahtar tombstone'u lease'den önce bitemez.
`COMPLETED` veya `FAILED` durumunda lease alanları boştur; `created_at ≤ completed_at <
key_retention_expires_at` zorunludur. Sonuç gövdesi/referansı terminal kayıttan daha erken
temizlenebilir; varsa `result_expires_at`, tamamlanma ile anahtar tombstone'u arasındadır.
Başarıda hata kodu boş, başarısızlıkta hata kodu zorunludur; `FAILED` yalnızca kesin terminal
`400`/`403`/`404`/`409`/`422` sonuçlarını saklar. `ORGANIZATION` kapsam, mevcut
kurum-kapsamlı davranışı korur. Kesin durum makinesi, lease ve sonuç saklama ayrıntıları
`SENKRONIZASYON_VE_CAKISMA.md`'de tanımlıdır.

`iam_runtime` erişiminde `idempotency_keys` `FORCE RLS` altındadır: `ORGANIZATION` bağlamında
`scope_type='ORGANIZATION'`, `organization_id=app.organization_id` ve
`user_id=app.iam_actor_user_id`; `GLOBAL` güvenlik bağlamında `scope_type='GLOBAL'`,
`organization_id IS NULL` ve `user_id=app.iam_actor_user_id`; `IAM_AUTH` bağlamında ise
`scope_type='IAM_AUTH'`, `organization_id IS NULL`, `user_id=app.iam_actor_user_id`,
`operation_type=app.iam_operation_code` ve auth replay escrow erişiminde ayrıca
`request_fingerprint` içindeki cihaz/token bağlamı zorunludur. `user_id`, P-010'daki
`actorUserId`dir; `app.iam_target_user_id` yalnız güvenlik olayının hedefidir. Böylece başka
kurumun, aktörün veya başka Cognito access token değişiminin önceki sonucu okunamaz ya da
güncellenemez; `app.iam_operation_scope` iki bağlamın birlikte kurulmasını reddeder.
`PROVIDER_TOKEN_EXCHANGE`, önce salt okunur `AUTHENTICATION` transaction'ında yalnız
`issuer + subject` ile identity çözer, ardından ayrı `IAM_AUTH` transaction'ında bu çözülmüş
`actorUserId` ile mutation yapar; scope'lar aynı DB transaction'ında birikemez.

### 14.0a. `iam_auth_response_escrows`

Ham token dönen auth cevapları (`provider-token-exchange`, `platform-admin/activate`,
kurum `activate`, `sessions/refresh`) güvenli replay için `idempotency_keys.result_payload`
yerine ayrı escrow yüzeyi kullanır.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `id` | UUID | Hayır | PK |
| `idempotency_key_id` | UUID (FK → `idempotency_keys.id`) | Hayır | **UNIQUE** — her auth replay sonucu tek idempotency kaydına bağlıdır. |
| `actor_user_id` | UUID (FK → `users.id`) | Hayır | Replay yalnız aynı aktöre açılır. |
| `operation_type` | TEXT | Hayır | `PROVIDER_TOKEN_EXCHANGE`, `PLATFORM_ADMIN_ACTIVATE`, `CONTEXT_ACTIVATE`, `SESSION_REFRESH` allow-list değerlerinden biri. |
| `device_identifier` | UUID | Hayır | Aynı auth replay yüzeyini farklı cihaz okuyamaz. |
| `token_fingerprint` | TEXT | Hayır | İşlem bazlı güvenli `requestTokenFingerprint`; `PROVIDER_TOKEN_EXCHANGE` için Cognito access token, `PLATFORM_ADMIN_ACTIVATE`/`CONTEXT_ACTIVATE` için `contextSelectionToken`, `SESSION_REFRESH` için platform refresh token. Ham token değildir. |
| `result_context_token_id` | UUID (FK → `context_selection_tokens.id`) | Evet | `PROVIDER_TOKEN_EXCHANGE` sonucunda üretilen context token bağı. |
| `result_refresh_token_family_id` | UUID (FK → `refresh_token_families.id`) | Evet | Aktivasyon veya refresh sonucunda etkilenen aile. |
| `result_refresh_token_id` | UUID (FK → `refresh_tokens.id`) | Evet | Aktivasyon veya refresh sonucunda üretilen token. |
| `ciphertext` | BYTEA | Evet | `status='READY'` iken AEAD ile şifrelenmiş tam yanıt gövdesi; `EXPIRED`/`REVOKED` durumunda zorunlu olarak `NULL`'lanır. |
| `aead_key_reference` | TEXT | Evet | `status='READY'` iken kullanılan A-013 secret yöneticisi anahtar/versiyon kimliği; purge sonrası `NULL` olur. |
| `aead_nonce` | BYTEA | Evet | `status='READY'` iken aynı anahtar altında benzersiz AEAD nonce; purge sonrası `NULL` olur. |
| `aad_context` | TEXT | Evet | Actor/operation/device/idempotency bağlamının kanonik özeti; decrypt sırasında zorunlu ek doğrulama verisi. `EXPIRED`/`REVOKED` purge sonrası `NULL` olur. |
| `status` | ENUM(`READY`,`EXPIRED`,`REVOKED`) | Hayır | Replay cevabının erişim durumu. Aynı key için çoklu güvenli replay desteklendiğinden `CONSUMED` kullanılmaz. |
| `expires_at` | TIMESTAMPTZ | Hayır | Operation bazlı kesin TTL. |
| `created_at` | TIMESTAMPTZ | Hayır | |
| `deleted_at` | TIMESTAMPTZ | Evet | Süre dolumu veya güvenli temizlik anı. |

Kısıtlar:

- `CHECK (created_at < expires_at)`.
- `CHECK ((status = 'READY' AND deleted_at IS NULL AND ciphertext IS NOT NULL AND aead_key_reference IS NOT NULL AND aead_nonce IS NOT NULL AND aad_context IS NOT NULL) OR (status IN ('EXPIRED','REVOKED') AND deleted_at IS NOT NULL AND deleted_at >= created_at AND ciphertext IS NULL AND aead_key_reference IS NULL AND aead_nonce IS NULL AND aad_context IS NULL))`.
- `CHECK (num_nonnulls(result_context_token_id, result_refresh_token_family_id, result_refresh_token_id) >= 1)`.
- `CHECK ((operation_type = 'PROVIDER_TOKEN_EXCHANGE' AND result_context_token_id IS NOT NULL AND result_refresh_token_family_id IS NULL AND result_refresh_token_id IS NULL) OR (operation_type IN ('PLATFORM_ADMIN_ACTIVATE','CONTEXT_ACTIVATE') AND result_context_token_id IS NULL AND result_refresh_token_family_id IS NOT NULL AND result_refresh_token_id IS NOT NULL) OR (operation_type = 'SESSION_REFRESH' AND result_context_token_id IS NULL AND result_refresh_token_family_id IS NOT NULL AND result_refresh_token_id IS NOT NULL))`.
- Escrow, `actor_user_id`, `operation_type`, `device_identifier` ve `token_fingerprint` eşleşmeden okunamaz.
- `PROVIDER_TOKEN_EXCHANGE`, `PLATFORM_ADMIN_ACTIVATE` ve `CONTEXT_ACTIVATE` için `expires_at = created_at + INTERVAL '5 minutes'`; `SESSION_REFRESH` için `expires_at = created_at + INTERVAL '10 minutes'`.
- Ham token audit, log, `idempotency_keys.result_payload` veya başka açık JSON yüzeyine yazılamaz.
- `expires_at` sonrası replay isteği fail-closed davranır: işlem ikinci kez yürütülmez; bağlı
  context token `revoked_at` ile, bağlı family/token sonucu ise `revoked_at` ile idempotent
  kapatılır; güvenlik audit'i yazılır, `deleted_at` doldurulur ve `ciphertext`,
  `aead_key_reference`, `aead_nonce`, `aad_context` aynı state geçişinde `NULL`'lanır.
  İstemci güvenli yeniden auth/yeniden deneme akışına yönlendirilir.
- Cleanup geçişi RLS açısından old-row/new-row olarak bağlayıcıdır: `USING` yalnız
  `status='READY' AND (expires_at <= transaction_time OR current_setting('app.iam_security_revoke_required', true) = 'true')`
  satırlarını açar; `WITH CHECK` yalnız `status IN ('EXPIRED','REVOKED')`, `deleted_at IS NOT NULL`
  ve `ciphertext`/`aead_key_reference`/`aead_nonce`/`aad_context` alanlarının `NULL` olduğu
  new-row'u kabul eder. Aynı `UPDATE` içinde terminal durum + purge zorunludur; zaten terminal
  satırdaki idempotent tekrar güvenli no-op olabilir.

`iam_runtime` bu tabloda yalnız `IAM_AUTH` bağlamında `SELECT`/`INSERT`/`UPDATE` hakkı taşır;
RLS, `actor_user_id=app.iam_actor_user_id`, `operation_type=app.iam_operation_code` ve
escrow'un bağlı olduğu `idempotency_key_id`nin aynı aktör/IAM_AUTH kapsamında olmasını zorunlu
kılar. `CONTEXT_ACTIVATE`te ayrıca bağlanan üyelik/kurumun, `SESSION_REFRESH`te bağlanan
family/tokenın aynı aktör ve cihaz zincirinde olması zorunludur. Başka aktör, başka operation
code, farklı cihaz/token bağlamı veya çapraz kurum/family erişimi varsayılan reddir.

Cleanup/reconciliation sahipliği `IAM-005` ve `IAM-006`dadır; dar yetkili cleanup akışı old-row'da
yalnız `READY` ve süresi dolmuş veya server-set güvenlik revoke'u gereken escrow satırlarını açar,
new-row'da ise yalnız `EXPIRED`/`REVOKED`, `deleted_at` dolu ve dört gizli alanı `NULL` satırı
yazabilir. `IAM-009` escrow expiry, replay reconciliation ve RLS izolasyon otomasyonunu kanıtlar.

---

### 14.1. SYNC/REALTIME — `sync_changes` outbox ve değişiklik akışı

#### `sync_entity_catalog`

Bu kapalı katalog, her eşitlenen varlığın sınıf kapsamı zorunluluğunu şemada taşır.

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `entity_type` | TEXT | Hayır | PK; yalnızca katalogda tanımlı eşitleme varlığı kullanılabilir. |
| `requires_class_scope` | BOOLEAN | Hayır | `true` ise değişiklik sınıf kapsamı olmadan yazılamaz. |

Kısıt: `UNIQUE (entity_type, requires_class_scope)`. Katalog fiziksel tablo başına olay
üretmez; mobil yerel görünüm ve gerçek zamanlı yenilemenin kullandığı V1 mantıksal
aggregate'ları aşağıdaki kapalı eşlemeyle taşır:

| Aggregate türü | `requires_class_scope` | V1 eşleme ve sınır |
|---|---:|---|
| `CLASS` | `true` | Sınıfın ve sınıf görünümünü etkileyen değişikliklerin olayıdır. |
| `PROGRAM` | `true` | Program, sürümü ve programa bağlı içerik değişikliğinin aggregate olayıdır. `CONTENT` için ayrı V1 olay türü yoktur. |
| `PLAN_ITEM` | `true` | Günlük plan/görev görünümünü etkileyen değişikliktir. |
| `STUDENT` | `true` | Öğrenci ve öğrenciye bağlı `people` değişikliği bu aggregate üzerinden yayımlanır. |
| `GUARDIAN` | `true` | Öğrenci velisi ve veliye bağlı `people` değişikliği bu aggregate üzerinden yayımlanır. |
| `ATTENDANCE_SESSION` | `true` | Günlük oturum görünümünü etkileyen değişikliktir. |
| `ATTENDANCE_RECORD` | `true` | Öğrenci yoklama durumunun değişikliğidir. |
| `PROGRESS_RECORD` | `true` | Öğrenci ilerleme/değerlendirme görünümünün değişikliğidir. |
| `TERM_CALENDAR` | `false` | Dönem, takvim günü ve tatil değişikliklerinin kurum kapsamlı aggregate'ıdır. |
| `ORGANIZATION` | `false` | Kurum temel bilgisi/marka görünümünün aggregate'ıdır. |
| `ORGANIZATION_SETTINGS` | `false` | Etkin modül, kurum ayarı, özel yoklama durumu ve özel öğrenci alanı tanımı değişikliklerinin aggregate'ıdır; bunlar için ayrı V1 olay türü yoktur. |

Kurum personelinin, öğrenci/veli ilişkisi olmayan `people` değişikliği operasyonel sınıf
akışına girmez; ilgili yönetim kaynağının normal yenilemesiyle alınır. Yeni bir V1 çekirdek
değişikliği yalnız bu katalogta karşılığı olan bir aggregate üzerinden `sync_changes`a
yazılabilir; karşılık yoksa uygulama üreticisi reddeder ve migration önce katalog eşlemesini
ekler. Böylece bir V1 çekirdek değişikliği sessizce eşitleme dışı kalamaz.

#### `sync_changes`

| Alan | Tip | Null | Açıklama |
|---|---|---|---|
| `change_sequence` | BIGINT | Hayır | PK; sunucunun ürettiği monoton artan değişiklik sırası. |
| `event_id` | UUID | Hayır | `UNIQUE`; taşıma yinelense de değişmeyen mantıksal olay kimliği. |
| `scope_type` | ENUM(`GLOBAL`,`ORGANIZATION`) | Hayır | Değişiklik akışının kapsamı. |
| `organization_id` | UUID (FK → `organizations.id`) | Koşullu | `ORGANIZATION` kapsamda zorunlu, `GLOBAL` kapsamda `NULL`. |
| `scope_class_id` | UUID | Evet | Operasyonel sınıf kapsamı; kurum satırında `NULL` olabilir. |
| `entity_type` | TEXT | Hayır | Değişen kaynak türü. |
| `requires_class_scope` | BOOLEAN | Hayır | Katalogtan denormalize edilir; aşağıdaki bileşik FK ile pinlenir. |
| `entity_id` | UUID | Hayır | Değişen kaynak kimliği. |
| `change_type` | ENUM(`UPSERT`,`ARCHIVED`,`REMOVED_FROM_SCOPE`) | Hayır | İstemcinin yerel davranışını belirler. |
| `row_version` | INTEGER | Hayır | Kaynak değişikliğinin kanonik sürümü. |
| `occurred_at` | TIMESTAMPTZ | Hayır | İş transaction'ındaki oluşma anı. |
| `delivery_status` | ENUM(`PENDING`,`PUBLISHED`) | Hayır | Taşıma katmanının yayımlama durumu. |
| `publish_attempt_count` | INTEGER, varsayılan `0` | Hayır | Yayımlama denemesi sayısı. |
| `next_publish_at` | TIMESTAMPTZ | Evet | Geçici taşıma hatası sonrası en erken deneme anı. |
| `published_at` | TIMESTAMPTZ | Evet | En az bir taşıma denemesinin başarılı olduğu an. |
| `last_publish_error_code` | TEXT | Evet | Sır/kişisel veri içermeyen son taşıma hata kodu. |

Kısıtlar: `CHECK ((scope_type = 'GLOBAL' AND organization_id IS NULL AND scope_class_id IS
NULL) OR (scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL))`;
`FOREIGN KEY (scope_class_id, organization_id) REFERENCES classes (id, organization_id)`;
`FOREIGN KEY (entity_type, requires_class_scope) REFERENCES sync_entity_catalog (entity_type,
requires_class_scope)`; `CHECK (requires_class_scope = false OR (scope_type = 'ORGANIZATION'
AND scope_class_id IS NOT NULL))`; `CHECK (change_type <> 'REMOVED_FROM_SCOPE' OR
(scope_type = 'ORGANIZATION' AND scope_class_id IS NOT NULL))`;
`CHECK ((delivery_status = 'PENDING' AND published_at IS NULL) OR (delivery_status = 'PUBLISHED'
AND published_at IS NOT NULL))`; `CHECK (publish_attempt_count >= 0)`.

`sync_changes` teknoloji bağımsız transactional outbox'tır: iş değişikliği, zorunlu
`audit_logs` satırı, `idempotency_keys` terminal sonucu ve tam bir `sync_changes` satırı
aynı transaction içinde yazılır. Başarısız transaction bu satırların hiçbirini bırakmaz.
Taşıma/yayımlama, sabit `event_id` ile sonradan ve yinelenebilir yürütülür; taşıma teknolojisi
A-006'da seçilecektir.

`REMOVED_FROM_SCOPE` satırı yalnız eski sınıfın `scope_class_id`sini, türü, kimliği,
`row_version`ı ve gerekli tombstone bilgisini taşır; yeni sınıf kimliği veya başka yetkisiz
veri için alan içermez. Kurum ayarı gibi `requires_class_scope=false` metaveri olaylarında
`scope_class_id=NULL` kalabilir.

---

## 15. Tenant bütünlüğü ve ilişkisel bütünlük teknikleri

### 15.1. Bileşik `(id, organization_id)` FK tekniği

Her kurum-kapsamlı tablo `UNIQUE (id, organization_id)` taşır; her çocuk tablo ebeveynine
`(parent_id, organization_id) REFERENCES parent (id, organization_id)` ile bağlanır. Bir
uygulama hatası B kurumuna ait bir kaydı A kurumunun `organization_id`'siyle ilişkilendirmeye
çalışırsa, veritabanı bunu **FK ihlali olarak reddeder**.

### 15.2. Bileşik FK zincirinin kapsamı

`classes→terms`, `student_class_enrollments→students,classes`, `class_teacher_assignments→
classes,organization_membership_roles`, `students→people,terms`, `student_guardians→
students,people`, `attendance_sessions→classes`, `attendance_records→attendance_sessions,
students,organization_attendance_statuses`, `programs→classes`, `program_versions→programs`,
`programs.current_program_version_id→program_versions` (öz-referans), `program_template_days→
program_templates`, `plan_items→program_versions,contents,program_templates,
program_template_days` (şablon günü ayrıca kendi şablonuna bileşik FK ile bağlı), `progress_
records→plan_items,students`, `contents→file_assets`, `people→file_assets` (fotoğraf),
`organizations.logo_asset_id→file_assets`, `custom_field_options→custom_field_definitions`,
`custom_field_values→custom_field_definitions,people,students`, `custom_field_value_selected_
options→custom_field_values,custom_field_options`, `organization_memberships→people`,
`organization_membership_roles→organization_memberships`, `organization_membership_
permissions→organization_membership_roles` (hedef ve grantor için ayrı, pinlenmiş bileşik
FK'ler — bkz. 15.6), `refresh_tokens→organization_memberships` (kurum bağlamı; bkz. 4.11),
`refresh_token_families→trusted_devices` (cihaz sahipliği; bkz. 4.10–4.11), `term_calendar_days→terms`,
`audit_logs→audit_logs` (öz-referans `undo_of_audit_log_id`; iki ayrı scope-pinned bileşik FK
— bkz. 13.2), `audit_logs→classes` (`scope_class_id`, olay anındaki sınıf kapsamı). `organizations.logo_asset_id→file_assets` listede yukarıda geçer; tek istisnası
kendi `id`'sinin tenant değeri olarak kullanılmasıdır (bkz. 5.1).

### 15.3. Kapsam dışı bırakılan ilişki türü — aktör/işleyen kullanıcı alanları

`created_by_user_id`, `recorded_by_user_id`, `assigned_by_user_id`, `uploaded_by_user_id`,
`actor_user_id`, `idempotency_keys.user_id` gibi "bu işlemi kim yaptı" alanları **bilinçli
olarak** bileşik FK ile `organization_memberships`'e bağlanmamıştır (yalnızca düz `users.id`
FK'dir). Gerekçe: platform yöneticisi, kendi kurum üyeliği **olmadan**, destek amaçlı erişimle
bu alanların birçoğunu doldurabilir. `refresh_tokens.organization_membership_id` bu genel
kuralın **istisnasıdır** — o alan "kim yaptı" değil, "bu oturum hangi kurum bağlamında açıldı"
bilgisini taşır ve bilinçli olarak bileşik FK ile korunur (bkz. 4.11).

### 15.4. `organization_id` taşımayan tablolar ve gerekçesi

| Tablo | Neden `organization_id` yok | Tenant bağlamı nasıl korunur |
|---|---|---|
| `users`, `user_identities` | Global uygulama hesabı ve Keycloak issuer/subject eşlemesi — bkz. bölüm 4.1–4.1a. | Kendileri tenant verisi değildir; `organization_memberships` üzerinden kurum bağlamına girilir. |
| `platform_administrators`, `platform_administrator_profiles` | Rol/profil global kapsamlıdır (§5.1). | Kapsam dışı — tanımı gereği kurum bağlamsızdır. |
| `permission_categories`, `permission_catalog`, `audit_action_catalog`, `starter_program_templates` | Platform genelinde paylaşılan, kuruma özel olmayan kataloglardır. | İçerikleri kuruma özel değildir; kurum bağlamı bunlara referans veren tablolarda taşınır. |
| `trusted_devices` | Global kullanıcıya ait fiziksel/güvenilir cihaz kaydıdır; tek kuruma özgü değildir. | `user_id` ile global hesaba bağlıdır; kurum kapsamlı oturum yetkisi taşımaz. |
| `refresh_token_families`, `refresh_tokens` | Aile global kullanıcı/cihaz sahipliği taşır; kurum ailesi üyeliğe bağlıdır, platform yöneticisi ailesi `NULL` üyelik taşıyabilir. | `refresh_token_families.organization_membership_id` + `user_id`, `authenticated_at`, `session_generation` ve reauth eşiği aile üretimini sınırlar (§4.11). |
| `custom_field_value_selected_options` | Çoktan seçmeli değer ile seçenek arasındaki bağlantı tablosudur; tenant sütununu tekrar etmez. | `custom_field_values` ve `custom_field_options` bileşik FK zincirleri aynı alan tanımını ve dolaylı kurum kapsamını zorlar (§8.4). |
| `sync_entity_catalog` | Platform genelindeki kapalı mantıksal aggregate kataloğudur. | Kuruma özel veri taşımaz; gerçek değişiklik kapsamı `sync_changes.organization_id` ve katalog kapsam kuralıyla korunur (§14.1). |

**Not:** `people` artık bu listede **değildir** — `v2.0`'daki global `people` kararı bu sürümde
geri alınmış, kurum kapsamına döndürülmüştür (bkz. bölüm 4.2, 19 madde 1). **`v3.0`'daki tek
"bilinçli istisna" olan `platform_administrator_profiles.photo_asset_id`'nin kurum kapsamlı
`file_assets`'e düz FK vermesi de kaldırılmıştır** (bkz. 4.4) — bu, gerçek bir tenant izolasyonu
açığıydı ve "bilinçli istisna" olarak adlandırılması onu güvenli kılmıyordu. `v4.0` itibarıyla
bu tabloda **hiçbir kurum kapsamlı tabloya düz (bileşik olmayan) FK veren global tablo/alan
yoktur**; tek kalan istisna, `organizations.logo_asset_id`'nin kendi `id`'sini tenant değeri
olarak kullanmasıdır (bkz. 5.1) — bu bir izolasyon açığı değildir, çünkü `organizations`
zaten kurumun kendisidir.

### 15.5. DB'nin ifade edemediği kalan iş kuralları

Aşağıdakiler hâlâ **yalnızca uygulama katmanında** doğrulanabilir; bu, ana planın §15 "kurum
izolasyonu otomatik testlerle doğrulanmalıdır" ilkesi gereği zorunlu kalır ve DB kısıtlarının
**yerine geçmez**, onları tamamlar:

- Bir kullanıcının belirli bir işlemi yapmaya yetkisi olup olmadığı (`PERM-002` kapsamı).
- Kurum yöneticisinin yalnızca kendi yetki tavanındaki izinleri devredebilmesi
  (`YETKI_MATRISI.md` §4.1).
- **Öğrencinin, yoklama oturumu/plan kaleminin tarihinde ilgili sınıfa kayıtlı olması**
  (bkz. §9.3, §12.1 sonu) — zamana bağlı, çok tablolu bir koşuldur.
- **Öğrencinin `term_id`'sinin aktif sınıf üyeliğinin dönemiyle tutarlı olması** — aynı şekilde
  zamana bağlıdır.
- **Kurum kapsamlı `refresh_tokens`'ın `issued_at_session_generation`'ının, ilgili
  `organization_memberships.session_generation`'ının güncel değerine eşit olması** (bkz. 4.11)
  — iki mutlak satırın canlı karşılaştırılmasını gerektirdiğinden statik FK/CHECK ile ifade
  edilemez; her kurum kapsamlı API isteğinde zorunlu bir kontroldür.
- **Tek kullanımlı `context_selection_tokens` ile aile üretiminde hedef üyeliğin `status =
  ACTIVE`, ilgili rolün `revoked_at IS NULL`, tokenın tüketilmemiş/süresi geçmemiş olması ve
  `auth_time` eşiklerinin doğrulanması** (bkz. 4.10a–4.11) — bu, iptal edilmiş üyelik için
  eski veya kurum bağlamsız tokenla yeni erişim üretilmesini engelleyen zorunlu ön koşuldur.
- **Rol iptali yaşam döngüsü** (bkz. 4.12): bir `TEACHER` rolü geri alındığında bağlı
  `class_teacher_assignments`/`organization_membership_permissions` satırlarının aynı işlemde
  kapatılması gerekir; bu, pinlenmiş bileşik FK'nin doğrulayamadığı "rol hâlâ aktif mi" boşluğunu
  kapatan zorunlu bir uygulama kuralıdır.

Bu maddeler, `KRITIK_TEST_VE_KABUL_PLANI.md`deki KAP-01, KAP-03, KAP-16, KAP-23 ve KAP-31
ile **kabul senaryosu olarak izlenir** (ör.
"geçmişte farklı bir sınıfa kayıtlıyken alınmış bir yoklamanın öğrenci geçtikten sonra da
doğru sınıfa referans verdiğinin testi"); bu belge yalnızca kuralın var olduğunu ve DB
tarafından ifade edilemediğini kaydeder, test senaryosunun kendisini yazmaz.

**Artık bu listede olmayan, önceki sürümde burada sayılan bir madde:** `custom_field_values`'te
`SINGLE_CHOICE` alanı için en fazla bir seçenek seçilmiş olması — bu artık §8.4'teki partial
`UNIQUE (custom_field_value_id) WHERE field_type = 'SINGLE_CHOICE'` ile **DB seviyesinde**
zorunlu kılınmaktadır; uygulama katmanı listesinden çıkarılmıştır.

### 15.6. Gerçek (literal içermeyen) pinlenmiş bileşik FK tekniği

Bu belgede birçok yerde (bkz. 4.9, 7.2) "bir FK'nin hedef satırının belirli bir sabit değere
sahip olmasını DB seviyesinde zorunlu kılma" ihtiyacı doğar (ör. "bu izin yalnızca `TEACHER`
rolüne verilebilir", "bu grantor yalnızca `ORG_ADMIN` olabilir"). Bunu ifade etmenin **geçersiz**
yolu, FK tanımının içine doğrudan bir literal yazmaktır (`v2.0`'daki hata):

```text
FOREIGN KEY (target_membership_id, organization_id, 'TEACHER')
  REFERENCES organization_memberships (id, organization_id, membership_role)   -- GEÇERSİZ SQL
```

**Geçerli teknik:** Çocuk tabloya, `CHECK` ile tek bir değere sabitlenmiş **gerçek bir sütun**
eklenir (ör. `target_role_code membership_role_enum NOT NULL DEFAULT 'TEACHER' CHECK
(target_role_code = 'TEACHER')`), ve bileşik FK bu gerçek sütuna referans verir:

```text
FOREIGN KEY (target_membership_role_id, organization_id, target_role_code)
  REFERENCES organization_membership_roles (id, organization_id, role)         -- GEÇERLİ SQL
```

Bu, standart ve çalışan bir PostgreSQL tekniğidir: `target_role_code` her zaman `'TEACHER'`
olduğundan (CHECK bunu garanti eder), FK yalnızca hedef tablodaki `role = 'TEACHER'` olan
satırlarla eşleşebilir — çünkü hedef tablodaki `(id, organization_id, role)` üçlüsü tekildir
(bkz. ilgili tabloların `UNIQUE` kısıtları) ve FK, üçlünün tamamının (sabitlenmiş üçüncü değer
dahil) hedefte var olmasını ister. Bu belgede bu teknik `organization_membership_roles`'a
pinlenen üç yerde (4.9 hedef, 4.9 grantor, 7.2) kullanılmıştır.

**Kritik önkoşul — tip uyumu:** PostgreSQL'de bileşik bir FK'nin her sütunu, karşılık geldiği
hedef sütunla **aynı tipte** olmalıdır (örtük dönüşüm bileşik FK'ler için yeterli değildir).
`organization_membership_roles.role` bir `ENUM` (`membership_role_enum`) olduğundan,
`target_role_code`/`granted_role_code`/`role_code` sütunlarının **`TEXT` değil, aynı
`membership_role_enum` tipinde** tanımlanması zorunludur — önceki bir sürümde bu sütunlar
yanlışlıkla `TEXT` olarak tanımlanmıştı; bu, `CREATE TABLE` aşamasında tip uyuşmazlığı
hatasına yol açardı. Bu belgede artık dört sütunun tamamı (`role`, `target_role_code`,
`granted_role_code`, `role_code`) aynı adlandırılmış `ENUM` tipini kullanır (bkz. 4.6, 4.9,
7.2).

**Sınırlama:** Bu teknik yalnızca **rolün kendisini** (statik bir alan değerini) doğrular;
`revoked_at IS NULL` (aktiflik) gibi zamana bağlı bir koşulu FK ile ifade edemez — bir izin,
teknik olarak geri alınmış (`revoked_at` dolu) bir role de FK seviyesinde "bağlı" kalabilir. Bu
nedenle aktiflik kontrolü (bir rolün gerçekten hâlâ geçerli olup olmadığı) her zaman uygulama
katmanında da yapılmalıdır; bu, bölüm 15.5'teki genel ilkeyle tutarlıdır.

---

## 16. Metaveri / operasyonel veri ayrımının şema karşılığı

| Kategori | Bu şemadaki karşılığı |
|---|---|
| Metaveri (kurum kapsamlı izinle erişilebilir) | `classes.id`/`name`/`term_id`/`status`; `organization_memberships`/`organization_membership_roles` (yalnızca kurum içindeki hoca listesi ve rol bilgisi); `class_teacher_assignments` (atama var/yok bilgisi); `organization_attendance_statuses`; `organization_brand_colors`; `organization_modules`. |
| Operasyonel veri (yalnızca atanmış sınıfta + ilgili işlem izniyle) | `students.*`, `student_class_enrollments.*`, `student_guardians.*`, `people.*` (öğrenci/veli kişileri), `attendance_records.*`, `progress_records.*`, `plan_items.*`, `contents.*`. |

---

## 17. İlişki özeti (metinsel)

```text
organizations 1—n terms, classes, people, students, programs, contents,
  organization_memberships
terms 1—n classes, students, term_calendar_days
classes 1—n student_class_enrollments, class_teacher_assignments, programs,
  attendance_sessions
people 1—1 students (nullable, person_id üzerinden)
people 1—n student_guardians (veli olarak), organization_memberships (bir kurumdaki profil)
users 1—n user_identities, organization_memberships, trusted_devices, context_selection_tokens,
  refresh_token_families
users 0—1 platform_administrators, platform_administrator_profiles
trusted_devices 1—n context_selection_tokens, refresh_token_families (bileşik FK, aynı user_id — bkz. 4.10–4.11)
organization_memberships 1—n organization_membership_roles, refresh_token_families (kurum bağlamı)
refresh_token_families 1—n refresh_tokens
iam_provider_commands 0—1 iam_secret_deliveries (yalnız TEACHER_ACCOUNT_CREATE teslim escrow'u)
organization_membership_roles 1—n class_teacher_assignments (yalnızca TEACHER)
organization_membership_roles 1—n organization_membership_permissions (hedef; yalnızca TEACHER)
organization_membership_roles 0—n organization_membership_permissions (grantor; yalnızca ORG_ADMIN)
permission_catalog 1—n organization_membership_permissions
permission_categories 1—n permission_catalog
students 1—n student_class_enrollments, student_guardians, attendance_records,
  progress_records
custom_field_definitions 1—n custom_field_options, custom_field_values
custom_field_values 1—n custom_field_value_selected_options (custom_field_options ile birlikte)
attendance_sessions 1—n attendance_records
organization_attendance_statuses 1—n attendance_records (custom_status_id)
programs 1—n program_versions; programs 0—1 program_versions (current_program_version_id,
  öz-referans)
program_versions 1—n plan_items
program_templates 1—n program_template_days
program_template_days 0..1—n plan_items (source=TEMPLATE, bileşik FK ile şablonuyla eşleşen)
plan_items 1—n progress_records
contents 0..1—n plan_items, program_template_days
file_assets 0..1—n people (photo), organizations (logo, kendi id'si tenant değeri olarak),
  contents (pdf) — platform_administrator_profiles'ın artık bir dosya ilişkisi yoktur
  (photo_asset_id kaldırıldı, bkz. 4.4)
audit_action_catalog 1—n audit_logs
audit_logs 0..1 self-reference (undo_of_audit_log_id; iki ayrı scope-pinned bileşik FK ile)
classes 0..1—n audit_logs (scope_class_id; olay anındaki operasyonel kapsam)
classes 0..1—n sync_changes (scope_class_id; değişiklik anındaki yayın kapsamı)
```

---

## 18. Ana ürün planıyla ve önceki Dalga 0 belgeleriyle uyum kontrolü

- Kurum, sınıf, kişi, öğrenci, anne/baba, program, program şablonu, plan, günlük görev, içerik,
  değerlendirme, ilerleme, yoklama oturumu, yoklama kaydı, denetim kaydı ve geri alma
  varlıklarının tamamı `TERIMLER_SOZLUGU.md` bölüm 2 ile uyumludur. "Kullanıcı" teriminin "bir
  kullanıcı birden fazla rol atamasına sahip olabilir" tanımı artık hem kurumlar arası (`users`
  + `organization_memberships`, her kurumda ayrı `people` profili) hem kurum içi (`organization_
  membership_roles` ile aynı üyelikte birden fazla aktif rol) düzeyde tam karşılanmaktadır.
- Bölüm 4 (izin kataloğu) `YETKI_MATRISI.md` §4.3/§6.2 ile uyumludur; mutlak sınır "hoca
  izinlerini değiştirme" veri modelinde hocaya hiçbir yazma yolu açılmayarak yansıtılmıştır —
  `organization_membership_permissions.granted_by_membership_role_id` yalnızca gerçek, `CHECK`
  ile pinlenmiş bir sütun üzerinden `ORG_ADMIN` rolüne bağlı bir satıra referans verebilir (bkz.
  4.9, 15.6); bir `TEACHER` rolü bu sütuna hiçbir zaman yerleştirilemez.
- `YONETICI_BILGI_MIMARISI.md` ve `HOCA_MOBIL_BILGI_MIMARISI.md`'nin bıraktığı açık soru —
  "arşivlenmiş kayıt geri yükleme öğrenci/sınıf için ortak mı ayrı mı izin" — tek, ortak
  `RESTORE_ARCHIVED` kodu olarak bağlayıcı biçimde kapatılmıştır (bkz. bölüm 19).
- `KISISEL_VERI_ENVANTERI.md` tablosundaki 18 veri öğesinin tamamı bu belgede bir karşılık
  bulur; **[Hassas]** notuyla tekrar edilmiştir.
- Kurum, sınıf, program, öğrenci ve kullanıcının fiziksel silinmeyeceği (§14) `status` alanıyla
  karşılanmıştır.
- Eşzamanlılık, idempotency ve gerçek zamanlı yayılım (§12) `row_version`,
  `idempotency_keys` ve teknoloji bağımsız `sync_changes` outbox'ıyla desteklenir;
  `attendance_records`/`progress_records`in güncellenebilir, yalnızca `audit_logs`ın
  değişmez olduğu §2.4'te netleştirilmiştir.
- Kurum izolasyonu (§11.4, §15) hem bileşik FK zinciriyle (§15.1–15.4) hem de bunun ifade
  edemediği zamana bağlı kuralların açıkça kaydedilmesiyle (§15.5) ele alınmıştır; bu, uygulama
  testlerine **ek** bir güvence katar, onların yerine geçmez.
- Menü sırasının kontrollü değiştirilebilirliği (§9.2) `organization_modules.sort_order` ile
  karşılanmıştır; bu alanın yetkilendirme etkisi olmadığı açıkça belirtilmiştir.
- Bu belgede taranan bölümler için ana plana veya önceki Dalga 0 belgelerine aykırı bir tanım
  saptanmamıştır; bu, bu belgeyi hazırlayan agent tarafından yapılan bir çapraz kontrolün
  sonucudur.

---

## 19. Varsayımlar ve bu görevde alınan bağlayıcı kararlar

1. **`people` kurum kapsamlı, `users` global (bölüm 2.2, 4.1–4.2):** `v2.0`'daki "her ikisi de
   global" kararı geri alınmıştır; PII'nin kurumlar arası paylaşılmaması için `people` her
   zaman bir kuruma aittir. `organization_memberships.person_id` köprüsü kurulmuştur. `[Karar]`.
2. **Kurum üyeliği ile rol ayrımı, çoklu rol desteği (bölüm 4.5–4.6):** Bir kullanıcı aynı
   kurumda hem `ORG_ADMIN` hem `TEACHER` olabilir; önceki sürümün "tek rol" basitleştirmesi
   kaldırılmıştır. `[Karar]`.
3. **İzin ataması kurum üyeliğinin rolüne bağlı, sınıf atamasına değil (bölüm 4.9):** Hiç
   sınıfı olmayan bir hocanın kurum kapsamlı izinleri kullanabilmesi için zorunludur. `[Karar]`.
4. **Gerçek, pinlenmiş bileşik FK tekniği (bölüm 15.6):** Literal içeren geçersiz FK
   sözdiziminin yerine, `CHECK` ile sabitlenmiş gerçek sütunlar kullanılmıştır. `[Karar]`.
5. **Kurum kapsamlı oturum iptali (bölüm 4.11):** `refresh_tokens.organization_membership_id`
   ile bir kurumun cihaz oturumu iptali yalnızca o kurumun bağlamındaki oturumları etkiler.
   `[Karar]`.
6. **`programs.current_program_version_id` öz-referans bileşik FK'si (bölüm 11.2):** Yanlış
   programın sürümüne işaret edilmesini engeller. `[Karar]`.
7. **`plan_items.class_id` kaldırıldı, sınıf türetilir (bölüm 11.6):** İkinci, tutarsız
   olabilecek bir veri kaynağını ortadan kaldırır. `[Karar]`.
8. **`student_class_enrollments` tarih aralığı çakışması `EXCLUDE` ile engellenir (bölüm 8.2):**
   `btree_gist` gerektiren, gerçek bir DB kısıtıdır. `[Karar]`.
9. **Özel alan tip güvenliği — denormalize `field_type`/`entity_type` + `CHECK` + partial
   `UNIQUE` (bölüm 8.4):** `SINGLE_CHOICE` tekilliği artık DB seviyesinde zorunludur. `[Karar]`.
10. **Audit `event_scope` gerçek sütun + bileşik FK + `CHECK` (bölüm 13):** `[Karar]`.
11. **`organization_modules.sort_order` (bölüm 5.3):** Ana plan §9.2 menü sırası hükmünü
    karşılar; yetkilendirmeyi etkilemez. `[Karar]`.
12. **`RESTORE_ARCHIVED` öğrenci ve sınıf için tek, ortak izin kodudur (bölüm 4.8):** P-005/P-006
    açık sorusunu kapatan karar. `[Karar]`.
13. **Program açık yapı kararı, program sürümleme, aynı gün birden fazla plan kalemi, özel alan
    ayrılmış nullable FK modeli, değerlendirme şemasının program sürümüne gömülü olması, UUID
    birincil anahtar:** `v2.0`'dan devralınan kararlar, değişmeden korunmuştur.
14. **Platform yöneticisi profil fotoğrafı desteklenmez (bölüm 4.4):** Kurum kapsamlı
    `file_assets`'e düz FK veren "bilinçli istisna" kaldırılmıştır; V1 için en sade çözüm
    seçilmiştir. `[Karar]`.
15. **`organization_memberships.person_id` tekildir (bölüm 4.5):** Bir kişi profili en fazla
    bir global kullanıcı hesabına bağlanabilir. `[Karar]`.
16. **Bütün pinlenmiş rol sütunları aynı `membership_role_enum` tipindedir (bölüm 4.6, 4.9,
    7.2):** `TEXT`↔`ENUM` bileşik FK tip uyumsuzluğu giderilmiştir. `[Karar]`.
17. **`refresh_tokens` hem cihaz hem kurum üyeliği için bileşik FK ile sahiplik doğrular; kurum
    bağlamsız token'lar `session_generation` ile korunur (bölüm 4.11):** `[Karar]`.
18. **Rol iptali yaşam döngüsü bağlayıcı kural olarak yazıldı (bölüm 4.12):** Rol geri
    alındığında bağlı sınıf ataması/izin kayıtlarının aynı işlemde kapatılması zorunludur;
    grantor geçmişi (ORG_ADMIN rolü geri alınsa da) korunur. `[Karar]`.
19. **`audit_logs.undo_of_audit_log_id` iki ayrı scope-pinned bileşik FK ile korunur (bölüm
    13.2):** Kurumlar arası veya global/kurum karışık `undo` ilişkisi DB seviyesinde
    imkânsızdır. `[Karar]`.
20. **`organizations.logo_asset_id` FK'sinde kendi `id`'si tenant değeri olarak kullanılır
    (bölüm 5.1):** Genel bileşik FK deseninin tek, açıkça yazılmış istisnasıdır. `[Karar]`.

---

## 20. Bilinen sınırlamalar

- Fiziksel indeks/performans ayarları (`A-003` ve ilgili uygulama görevlerinde netleşir).
- Bölüm 15.6'daki teknik `PostgreSQL`/benzer veritabanlarında doğrudan uygulanabilir; farklı
  bir veritabanı seçilirse (`A-003`) eşdeğer bir mekanizma (tetikleyici) gerekir. Aynı şekilde
  bölüm 8.2'deki `EXCLUDE` kısıtı `btree_gist` eklentisini (veya eşdeğerini) gerektirir.
- Bölüm 15.5'te sayılan zamana bağlı kurallar (öğrenci-sınıf-tarih tutarlılığı, `session_
  generation` kontrolü, context-selection token tüketimi/yeniden doğrulama ön koşulu, rol iptali yaşam döngüsü)
  veritabanı kısıtlarıyla ifade edilemez; uygulama katmanında zorunludur ve
  `KRITIK_TEST_VE_KABUL_PLANI.md`deki ilgili KAP kartlarında izlenir.
- Rol iptalinin bağlı kayıtları kapatmasının **atomik bir işlem** (tek transaction) olarak mı
  yoksa yalnızca **değerlendirme-zamanı kontrolü** olarak mı uygulanacağı (bölüm 4.12),
  `API_GENEL_KURALLARI.md` ve `DENETIM_VE_GERI_ALMA_ILKELERI.md` ile uyumlu uygulama
  görevinde seçilir; bu belge yalnızca nihai davranışı (bağlı kayıtların aktif kalmaması
  gerektiğini) bağlayıcı olarak belirler.
- `session_generation`'ın artırılma tetikleyicisi (uygulama kodu mu, DB tetikleyicisi mi)
  belirtilmemiştir; bu bir uygulama görevi kararıdır.
- `role_assignments`, `role_assignment_permissions` (v1.0) ve `organization_memberships.
  membership_role` (v2.0) bu belgede artık **yoktur**; yerine `organization_memberships` +
  `organization_membership_roles` geçmiştir.
- `plan_items.class_id` kaldırıldığından, bu alana bağımlı olabilecek gelecekteki uygulama
  sorgularının `program_version_id → program_id → class_id` zincirini kullanması gerekir; bu,
  ilgili uygulama görevlerinde (`ATT-*`, `PROGRAM-*`) bir performans/indeksleme kararı
  gerektirebilir.
- `EXPORT`/`NOTIFY` modülleri için tablo tanımlanmamıştır (`P-012`, Dalga 8).
- Program "büyük değişiklik" eşiği (`PROGRAM-003`) ve `people` dedüplikasyonu
  `[Öneri/bekleyen karar]` olarak işaretlidir. İdempotency durum makinesi ise
  `SENKRONIZASYON_VE_CAKISMA.md` §3–§4'te kesinleşmiştir.
- Eski Excel/HTML/Apps Script dosyaları bu repoda bulunmadığından, bu şemanın eski sistemle
  karşılaştırması yapılmamıştır.

---

## 21. Kapsam dışı bırakılanlar

- API istek/cevap sözleşmesi ve sayfalama/filtreleme standardı `API_GENEL_KURALLARI.md`de
  tanımlıdır.
- Senkronizasyon kuyruğu, yeniden bağlanma ve çakışma çözümü protokolünün tam ayrıntısı
  `SENKRONIZASYON_VE_CAKISMA.md`de tanımlıdır.
- Geri alma komutlarının kesin listesi ve her komutun ters işlem tanımı `DENETIM_VE_GERI_ALMA_ILKELERI.md`'dedir.
- Excel rapor filtre/sorgu sözleşmesi ve dosya yaşam döngüsü (`P-012`).
- Gerçek veritabanı migration dosyaları (Dalga 1 uygulama görevleri).
- Belirli bir backend/veritabanı/kimlik doğrulama/dosya depolama/bildirim sağlayıcısı seçimi
  (`A-002`–`A-008` ADR'leri).
- Veli/öğrenci girişi için ayrıntılı veri modeli genişletmesi (sonraki faz, Dalga 8 `PORTAL-*`).
- Var olan bir `people` kaydının kurumlar arası aranıp yeniden bağlanması akışı — ilerideki bir
  görevin kapsamıdır.
- Bölüm 15.5'teki zamana bağlı kuralların uygulama testlerine dönüştürülmesi; izlenebilirlik
  `KRITIK_TEST_VE_KABUL_PLANI.md`deki ilgili KAP kartlarıyla sağlanır.
