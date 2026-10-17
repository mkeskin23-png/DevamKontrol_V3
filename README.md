# Devam Takip — Android

Türkçe arayüzlü, yerel SQLite veritabanı kullanan Android uygulaması. Veriler internet bağlantısı gerektirmeden cihazda saklanır.

## Özellikler

- Birden fazla ders oluşturma, düzenleme ve silme.
- Her ders için **Bolvadin MYO** veya **BUB Fakültesi** birimini seçme.
- Kaydedilen dersleri **Derslerim** ekranında listeleme; buradan ilgili derse yoklama alma veya ayarlarını düzenleme.
- Ders adı, 1. haftanın başlangıç tarihi, pazartesi–cuma günleri ve her gün için 1–8 ders saati ayarlama.
- 14 normal ders haftasına eklenen ve 1–15 arasından seçilebilen 1 vize haftası (toplam 15 takvim haftası).
- XLSX veya CSV öğrenci listesi aktarımı. Başlık satırı ilk 25 satır içinde aranır; `Öğrenci No` ve `Adı Soyadı` ya da `Öğrenci No`, `Ad`, `Soyad` ayrı başlıkları desteklenir. Aktarım sonrası numara ve ad-soyad önizlemesi gösterilir.
- Yoklama önce 15 haftayı dikey bir ekranda listeler; hafta seçildiğinde ayrı öğrenci/yoklama ekranı açılır. Yalnızca seçili dersin programında bulunan günler gösterilir.
- Her öğrenci kartında öğrenci numarasını başlık, ad-soyad bilgisini alt satırda gösterme.
- Her öğrenci için `Tüm saatleri seç` (o günün bütün saatlerini `Yok` işaretler) ve `Tüm saatleri kaldır` (o günün devamsızlık işaretlerini temizleyip bütün saatleri `Var` yapar) düğmeleri.
- Tanımlı her ders saati için düğme; dört düğme yan yana sığacak şekilde düzenlenir. `Var` / `Yok` durumları arasında dokunarak geçiş yapılır.
- Ders bazında bireysel ve toplu devam raporu.
- Bireysel ve toplu raporları PDF ve gerçek Excel `.xlsx` dosyası olarak dışa aktarma. Excel raporunda özet ve devamsızlık ayrıntıları ayrı çalışma sayfalarındadır.

## Öğrenci Excel dosyasının biçimi

Aşağıdaki biçimlerden biri kullanılabilir.

**Tek bir ad-soyad sütunu:**

| Öğrenci No | Adı Soyadı |
|---|---|
| 1001 | Ayşe Yılmaz |
| 1002 | Mehmet Demir |

**Ad ve soyad ayrı sütunlarda:**

| Öğrenci No | Ad | Soyad |
|---|---|---|
| 1001 | Ayşe | Yılmaz |
| 1002 | Mehmet | Demir |

İlk çalışma sayfası okunur. `.xlsx` ve `.csv` desteklenir; eski `.xls` dosyasını Excel'de `.xlsx` veya `.csv` olarak kaydedin. Aynı öğrenci numarası tekrar aktarılırsa mevcut kayıt güncellenir. Öğrenci listesi tüm derslerde ortak kullanılır, devamsızlık kayıtları derslere göre ayrı tutulur.

## Android Studio ile derleme

1. ZIP dosyasını bilgisayarınıza indirin ve çıkarın.
2. Android Studio'da **Open** ile içindeki `DevamTakipAndroid` klasörünü seçin. Bu klasörde `settings.gradle.kts`, `build.gradle.kts` ve `app/` dizini bulunmalıdır.
3. Gradle eşitlemesinin tamamlanmasını bekleyin. JDK 17 ve Android SDK Platform 35 kurulu olmalıdır.
4. Test için önce **Ders Ekle** bölümünde dersi ve programı kaydedin, ardından **Derslerim** ekranından dersi seçip öğrenci Excel dosyasını aktarın.

## GitHub Actions ile APK derleme

1. ZIP'i bilgisayarınızda çıkarın.
2. GitHub deposunun ana dizinine `DevamTakipAndroid` klasörünün **içindeki tüm dosyaları ve klasörleri** yükleyin. `settings.gradle.kts`, `build.gradle.kts`, `app/` ve `.github/` depoda görünmelidir. İsterseniz tüm projeyi bir alt klasörde tutabilirsiniz; iş akışı `settings.gradle.kts` dosyasını bularak proje kökünü belirlemeyi dener.
3. Dosyaları `main` veya `master` dalına gönderin; ardından **Actions → Android APK Build** iş akışını açın. İhtiyaç varsa **Run workflow** ile elle başlatın.
4. Başarılı çalıştırmanın altındaki **Artifacts** bölümünden `devam-takip-debug-apk` paketini indirin. ZIP içindeki `app-debug.apk` test amaçlı Android APK'sıdır.

İş akışı Android SDK kurulumunda eski `tools` paketinin kaldırılmış olması sorununu önlemek için `android-actions/setup-android@v4` ve açıkça belirtilen `platform-tools` paketini kullanır. Bu çalışma ortamında Android SDK/Gradle kurulu olmadığından APK yerel olarak derlenemedi. GitHub Actions üzerinde yeni çalıştırma başarılı olana kadar APK'nın derlendiği doğrulanmış sayılmaz.

## Veritabanı ve rapor notları

- Her dersin ayarları ve yoklama kayıtları ayrı saklanır. Uygulamanın eski tek dersli sürümünden gelen kayıtlar, kaydedilmiş eski ders ayarı varsa yeni ders listesine taşınır.
- Raporlarda planlanan ders saati, tanımlı haftalık programın 15 hafta boyunca değişmeden sürdüğü varsayımıyla hesaplanır. Resmî tatiller otomatik düşülmez.
- Veriler cihazda tutulur. Yedek amacıyla raporları düzenli dışa aktarmak önerilir.


## v1.3.0 güncellemesi

- Yoklama ekranındaki her öğrenci kartına toplu saat kontrolü eklendi. `Tüm saatleri seç` seçili gün için tüm saatleri devamsız (`Yok`) yapar; `Tüm saatleri kaldır` aynı gün ve ders için o öğrencinin devamsızlık işaretlerini kaldırır. Bu işlemler yalnızca seçili ders, tarih ve öğrenci için uygulanır.
- Başlıkta kullanılan uygulama sürümü `1.3.0` olarak güncellendi.
