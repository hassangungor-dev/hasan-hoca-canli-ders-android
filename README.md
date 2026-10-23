# Hasan Hoca Canlı Ders — 1.0.10 kaynak projesi

Gönderilen altı Zoom fotoğrafına göre ana ekran yeniden düzenlendi. Bu paket kaynak koddur; APK değildir. Android SDK ile derleme ve gerçek tablet testi burada yapılamadı. Kuruluma hazır veya tüm özellikleri doğrulanmış sürüm olarak değerlendirilmemelidir.

## Yeni menü
- Beyaz ana ekranda Yeni toplantı, Katıl, Planla, Ekran paylaş, Beyaz tahta.
- Yeni toplantı penceresinde isim, video anahtarı ve kişisel ders kodu. Kamera varsayılan kapalıdır.
- Ana ekranda ve toplantı başlığında 1.0.10 etiketi.
- Toplantı alt çubuğunda mikrofon, kamera, sohbet, katılımcılar, paylaşım, daha fazla ve ayrılma.
- Sohbet ve katılımcılar için yerel pencereler SDK olaylarına/eylemlerine bağlandı.
- Paylaş menüsü ekran, fotoğraf, PDF, yerel beyaz tahta ve URL açma akışlarını içerir. Fotoğraf/PDF/URL/tahta, önce Android ekran paylaşımı başlatılıp tüm ekran izni verilerek gösterilir; dosya yükleme veya ortak düzenleme yapılmaz.
- Planla, cihazın takvim düzenleyicisini açar; kendiliğinden davet göndermez.
- Beyaz tahta yereldir, renk değiştirme/geri alma/temizleme içerir. Ekran döndürmede çizimler korunur; uygulama kapatıldığında kalıcı kayıt yoktur.
- Kayıt düğmesinin durumu kayıt hizmetinin gerçek başlangıç/duruş bildiriminden güncellenir.

## Bilinen sınırlar
Görüşme altyapısı Jitsi SDK ve meet.jit.si olarak kalır. Sunucunun doğrulama ekranı hâlâ görünebilir. 1.0.11 sürümünde derin bağlantı kapatılmıştır; “Toplantı sahibiyim” doğrulaması artık kullanıcıyı Google Play'deki bağımsız Jitsi uygulamasına göndermemelidir. Zoom hesabı, Zoom PMI ve fotoğraflardaki parolalar kullanılmaz; uygulama kendi ders kodunu üretir. Öğretmen olarak başlatmak sunucuda otomatik yönetici yetkisi sağlamaz. Ayrıl düğmesi diğer katılımcıların toplantısını sonlandırmaz.

Eşzamanlı yerel kayıt/ekran paylaşımı çözülmedi; iki işlem birlikte engellenir. Yerel kayıt mikrofon sesi kullanır; uzaktaki konuşmacıların sesi garanti değildir. Android 10 altı yerel kayıt engellenir. Uygulama dışına taşan tam ekran paylaşım çerçevesi uygulanmadı. İzin reddi/harici paylaşım duruşu ve mikrofonun görüşme/kayıt arasında paylaşılması gerçek cihazda doğrulanmalıdır.

Jitsi varsayılan Activity manifestten kaldırıldı; toplantı bağlantıları özel ScreenShareActivity üzerinde tutulur. Bunun kimlik doğrulamasından dönüş üzerindeki etkisi cihazda sınanmalıdır. Eski APK'nin görünmeye devam etmesinin nedeni bu ortamda kesinleştirilemedi.

## İnternet
Yeni bağımlılık eklenmedi; Android Studio daha önce indirilen bağımlılıkları kullanabilir. Eksik önbellek/SDK/Gradle dosyaları yine indirme gerektirebilir. Gradle --offline modu ağ indirmesini engeller ama eksik dosya varsa derleme başarısız olur. Derleme süresi veya gerekli indirme miktarı garanti edilmez. Yeni kaynak ZIP yaklaşık 70 KB mertebesindedir; üretilecek APK yine onlarca MB olacaktır. Telefon kotası için APK aktarımını Wi-Fi'da yapın.

## Doğrulama
JavaCompiler JavacTask.parse ile dört Java dosyasında sözdizimi kontrolü; XML ayrıştırma; manifest Activity sınıfları; sürüm numaraları ve paket bütünlüğü kontrol edildi. Android tür çözümleme, Gradle assembleDebug, UI çalıştırma ve iki cihazla görüşme testi YAPILMADI.

## Wi-Fi olduğunda
ZIP'i yeni klasöre çıkarın. Android Studio ile ZIP'in içindeki hasan-hoca-canli-ders-android klasörünü açın. Gerekirse Gradle JVM 21 seçin. Generate APKs ile derleyin. Kurulum sonrası ana ekranda Canlı Ders 1.0.10 · Yeni menü etiketi görülmelidir. Ardından iki cihazla mikrofon/kamera/sohbet/katılımcılar, ekran paylaşımında onay/iptal/durdurma ve kayıt dosyası ayrı ayrı doğrulanmalıdır.
