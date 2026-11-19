# Pemeriksaan ekstensi referensi

Sumber utama yang diperiksa langsung: `Tools detector tempo dan chord_20261118073537.ppk` (147.725 byte). Arsip berhasil dibuka dan berisi:

| Berkas | Ukuran |
|---|---:|
| `main.lua` | 147.431 byte |
| `metronom.lua` | 353.045 byte |
| `DJLEngine.dex` | 12.172 byte |
| `libdjl32.so` | 49.176 byte |
| `libdjl64.so` | 52.224 byte |
| `djl.cpp` | 69.015 byte |

## 1. Alur yang benar-benar dipakai

### Pencarian dan pemuatan mesin

Di `main.lua`:

1. `MD.cariSumber()` mencari `DJLEngine.dex` dan pustaka native yang sesuai ABI.
2. ABI dipilih dengan `android.os.Process.is64Bit()`: `libdjl64.so` untuk 64-bit dan `libdjl32.so` untuk 32-bit.
3. Lokasi pencarian mencakup folder plugin, folder `Peralatan` pada akar instalasi, dan penyimpanan bersama.
4. `MD.siapkan()` menyalin DEX dan SO ke direktori privat melalui `ctx.getCacheDir()` atau `ctx.getFilesDir()`.
5. Nama cache diberi stempel berdasarkan ukuran dan sampel byte berkas.
6. DEX dimuat dengan `dalvik.system.DexClassLoader`, menggunakan `ctx.getClassLoader()` sebagai parent.
7. Kelas yang dimuat bernama `DJLEngine`.
8. Lua memanggil metode Java statis `muat(String soPath)`. Hasil yang diawali `OK` dianggap berhasil.
9. Kelas yang telah dimuat disimpan pada Java System Properties dengan kunci `djl.kelas`.

### Kontrak metode Lua–Java

Fungsi `MD.panggil()` mencari metode Java dengan refleksi. Dalam referensi, seluruh argumen yang dikirim melalui fungsi ini bertipe String.

| Metode Java | Parameter | Makna |
|---|---|---|
| `muat` | 1 String | Memuat pustaka native |
| `mulai` | 3 String | Memulai pekerjaan asinkron |
| `batal` | 2 String | Meminta pembatalan pekerjaan |
| `hidup` | 1 String | Memeriksa apakah worker masih hidup |

### Pengelolaan pekerjaan

- `MD.mulai(item)` membuat ID pekerjaan, memanggil `DJLEngine.mulai(path, workDir, id)`, lalu mencatat pekerjaan aktif.
- `MD.pantau()` membaca berkas status setiap 200 ms.
- `MD.hidup(id)` digunakan untuk membedakan pekerjaan aktif dari proses yang sudah berhenti.
- `MD.batal(id)` meneruskan permintaan pembatalan ke kelas Java.
- Saat layar ditinggalkan, pekerjaan dapat tetap berjalan; ketika ekstensi dibuka lagi, `MD.periksaAktif()` memeriksa status pekerjaan terdahulu.

## 2. Struktur DEX yang diperiksa

`DJLEngine.dex` adalah DEX Dalvik versi 035. Pemeriksaan tabel DEX menunjukkan empat kelas:

- `DJLEngine`
- `DJLEngine$1`
- `DJLEngine$Buf`
- `DJLEngine$Hasil`

Kelas utama memiliki metode berikut (nama dan signature diambil dari tabel metode DEX):

- `muat(String): String`
- `mulai(String, String, String): String`
- `batal(String, String): String`
- `hidup(String): String`
- `analisis(short[], int, int, short[], int, double, double, String, String): String`
- `versi(): String`
- `ujiMandiri(): String`

Tiga metode terakhir merupakan native method pada DEX referensi. Pustaka C++ mengekspornya sebagai `Java_DJLEngine_analisis`, `Java_DJLEngine_versi`, dan `Java_DJLEngine_ujiMandiri`. DEX juga memiliki kelas worker internal dan kelas buffer/hasil.

## 3. Status dan hasil

Lua membaca status dari `status_<id>.txt` dengan format lima kolom yang dipisahkan tanda `|`:

`state|percent|stage|message|timestamp`

Status yang dikenali Lua:
- `jalan`
- `selesai`
- `galat`
- `batal`

Hasil analisis disimpan pada `hasil_<id>.json`. Penulisan status C++ dilakukan melalui berkas sementara dengan akhiran `.tmp`, lalu dipindahkan ke nama status final.

## 4. Thread dan lifecycle

- Lua membuat `HandlerThread("DJL-kerja")` dan `HandlerThread("DJL-jaringan")`.
- Jika pembuatan thread gagal, handler kerja jatuh kembali ke handler UI. Untuk pekerjaan berat Vocal Remover, fallback seperti ini tidak boleh digunakan untuk inferensi.
- DEX memiliki kelas worker `DJLEngine$1`, metode `run()`, peta worker, serta pemeriksaan `Thread.isAlive()`. Ini menunjukkan bahwa pekerjaan native referensi dijalankan secara asinkron oleh thread Java, bukan menunggu di callback tombol.
- Pekerjaan aktif dicatat ke `aktif.txt` agar statusnya dapat diperiksa kembali setelah layar ekstensi dibuka ulang.

## 5. Pola decode audio yang tampak

DEX menggunakan:
- `MediaExtractor`
- `MediaCodec`
- `MediaCodec.BufferInfo`
- `MediaFormat`
- `ByteBuffer`, `ShortBuffer`, dan `FloatBuffer`
- `sample-rate`, `channel-count`, dan `pcm-encoding`

Tabel string DEX menunjukkan penanganan PCM float dan short. C++ referensi menerima array `short[]` melalui JNI. Karena itu, implementasi referensi menampung bagian audio terpilih dalam buffer Java sebelum memanggil analisis.

## 6. Hal yang harus dipertahankan dan diubah untuk Vocal Remover

**Pertahankan:**
- Pencarian DEX/SO berdasarkan ABI.
- Penyalinan ke direktori privat dan pemuatan melalui DexClassLoader.
- Kelas Java `DJLEngine` sebagai pintu masuk.
- Kontrak String untuk `muat`, `mulai`, `batal`, dan `hidup`.
- Worker Java asinkron, ID pekerjaan, file status, file hasil, dan pemulihan pekerjaan.
- Polling status dari Lua, tanpa menjalankan inferensi pada UI thread.

**Ubah:**
- JNI tidak lagi menerima seluruh lagu sebagai array `short[]`.
- MediaCodec harus menulis PCM float32 interleaved ke file sementara secara bertahap.
- JNI Demucs menerima path file dan opsi pemisahan, lalu menulis enam WAV.
- Pembatalan harus diperiksa selama decode, prepare, dan separation.
- Status hasil harus menyimpan enam lokasi WAV, bukan JSON hasil tempo/nada.
- Hanya satu pekerjaan native boleh aktif karena fungsi pembatalan upstream Demucs bersifat global.
- Jangan gunakan fallback ke UI thread untuk proses decode/inferensi berat.

## 7. Batas pemeriksaan

Paket PPK berisi DEX dan SO native yang dapat diperiksa sebagai berkas biner. Signature metode, tabel kelas, string, dan pemanggilan JNI di C++ sudah diverifikasi. Source Java asli tidak disertakan sebagai .java; karena itu, detail baris-per-baris implementasi internal DEX tidak boleh dianggap telah didekompilasi.