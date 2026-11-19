# Mesin pemisah: audit sumber Demucs Android

Dokumen ini merangkum pemeriksaan kode sumber publik jPaauwe/demucs-android dan membedakan kemampuan upstream dari pekerjaan integrasi CSR yang masih harus dibuat.

## 1. Kecocokan dengan target

Repositori upstream menyediakan mesin C++ untuk model Demucs v4 htdemucs_6s dengan enam keluaran: drums, bass, other, vocals, guitar, dan piano.

Mesin ditujukan sebagai library yang ditautkan ke proyek Android, bukan APK yang harus dipakai sebagai aplikasi. Ini cocok dengan sasaran kita: kode mesin C++ dipakai di dalam ekstensi CSR melalui pustaka native yang dipanggil dari Java DEX.

Audit kode dilakukan pada commit upstream f4bc42b6c209beea2aa4a04d37c75cd06b0c90d2:
https://github.com/jPaauwe/demucs-android/tree/f4bc42b6c209beea2aa4a04d37c75cd06b0c90d2

## 2. API C++ yang benar-benar tersedia

Header src/engine.hpp mendeklarasikan:
- sdengine::load_model(path)
- sdengine::prepare_input(rawPath, sampleRate, channels, outPath, progress)
- sdengine::analyze_input(preparedPath)
- sdengine::separate(preparedPath, outDir, options, progress)
- sdengine::compute_info(options)
- sdengine::request_cancel()

Input decode aplikasi berupa file raw interleaved float32. Mesin menyiapkan audio menjadi stereo float32 44,1 kHz. Hasil pemisahan ditulis sebagai WAV stereo 16-bit ke direktori keluaran.

## 3. JNI upstream dan penyesuaian ke DEX

Upstream memiliki android/jni/sdengine_jni.cpp dan contoh binding Kotlin NativeEngine.kt. JNI memakai RegisterNatives, sehingga nama kelas dapat diganti melalui CMake: SDENGINE_JNI_CLASS.

Binding upstream menyediakan native method untuk memuat model, menyiapkan input, menganalisis input, memisahkan stem, memeriksa kemampuan komputasi, dan meminta pembatalan. Callback progres mengembalikan boolean; nilai false meminta mesin berhenti. Callback dipanggil dari thread pemanggil native, bukan dari worker inferensi, dan dibatasi sekitar sepuluh kali per detik.

Kita tidak perlu menjalankan Kotlin atau membuat APK. Kita dapat menyediakan kelas Java biasa bernama DJLEngine di dalam DJLEngine.dex, dengan deklarasi native dan interface progres yang cocok dengan signature JNI. CMake kemudian dibangun dengan SDENGINE_JNI=ON dan SDENGINE_JNI_CLASS=DJLEngine. Kelas Java memuat pustaka menggunakan System.load(absolutePath), mengikuti cara pemuatan pustaka dari referensi PPK.

Ini adalah rancangan integrasi yang didukung oleh mekanisme JNI upstream dan pola pemuatan DEX referensi; kelas Java CSR-nya tetap harus ditulis dan diuji.

## 4. Pola memori dan pemrosesan yang ditemukan

Kode engine.cpp menggunakan memory mapping untuk file input float32, membagi lagu menjadi segmen Demucs sekitar 7,8 detik dengan overlap 25%, dan menggabungkan hasil segmen secara bertahap ke file WAV. File hasil sementara memakai akhiran .part dan baru diubah menjadi nama WAV final setelah seluruh pemrosesan berhasil. Jika proses gagal atau dibatalkan, file parsial dibuang.

Persiapan input juga dilakukan per blok buffer, bukan memuat seluruh audio ke array Java. Ini mendukung rancangan CSR yang menulis hasil decode MediaCodec ke file float32, lalu menyerahkan path file ke JNI.

Satu segmen inferensi dilaporkan membutuhkan sekitar 0,6 GB. Karena perangkat sasaran memiliki RAM 6 GB, pengaturan awal yang konservatif adalah satu segmen berjalan bersamaan. Ini angka awal dari dokumentasi upstream, bukan jaminan konsumsi total RAM pada Redmi 13C.

Perhitungan di dalam segmen menggunakan thread pool. Jumlah thread komputasi dan jumlah segmen paralel adalah dua pengaturan berbeda. Untuk menjaga perangkat tetap responsif, jumlah thread komputasi perlu diuji dan tidak langsung diasumsikan harus memakai semua core.

Mesin memakai status pembatalan global dan mengosongkannya ketika operasi prepare/separate baru dimulai. Karena itu, implementasi CSR harus membatasi satu pekerjaan aktif pada satu waktu dan tidak menjalankan dua analisis secara bersamaan.

## 5. Decode audio untuk CSR

Upstream meminta aplikasi Android mendekode audio dengan MediaCodec menjadi file raw interleaved float32. File tersebut lalu diberikan ke prepare_input.

Referensi PPK sudah memiliki jalur decode menggunakan MediaExtractor dan MediaCodec, tetapi kontrak JNI-nya menggunakan array PCM short[]. Untuk Vocal Remover, bagian decode perlu diadaptasi agar data PCM dikonversi dan ditulis bertahap ke file float32, bukan disimpan sebagai satu array besar.

Detail format output codec, channel count, sample rate, dan penanganan akhir stream harus diverifikasi pada implementasi Java sebelum ditetapkan.

## 6. Vulkan dan CPU

Upstream menyediakan backend Vulkan yang dimuat saat runtime dan fallback CPU. README upstream menyatakan shader GPU belum dituning untuk setiap GPU ponsel. Karena GPU Redmi 13C belum diverifikasi pada runtime ini, tahap uji pertama harus menggunakan CPU atau melakukan deteksi kemampuan, lalu membandingkan GPU setelah jalur CPU stabil.

Jangan mengaktifkan GPU sebagai asumsi wajib. Kegagalan Vulkan tidak boleh membuat ekstensi gagal total jika CPU fallback dapat berjalan.

## 7. Build upstream

Petunjuk upstream menyebut:
- Android NDK r27.
- CMake minimal 3.22.1.
- minSdk 29 atau lebih tinggi.
- ABI arm64-v8a.
- Eigen sebagai git submodule.
- Opsi SDENGINE_JNI=ON.
- Opsi alignment halaman 16 KB pada target JNI untuk perangkat Android 15+.

Konfigurasi dan binary belum dibangun atau diuji untuk proyek CSR ini. Perlu validasi build sebelum menambahkan klaim dukungan perangkat.

## 8. Model, checksum, dan lisensi

Model upstream yang dipakai dalam prototipe:
- Model: Demucs v4.1.0 HTDemucs 6-stem.
- Bobot: `5c90dfd2.safetensors`.
- Ukuran yang dilaporkan: sekitar 55 MB.
- SHA-256 yang dicantumkan upstream:
  `d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411`.

Sumber bobot:
https://huggingface.co/adefossez/HTDemucs-6s/resolve/3c5ee475be622df764938de97e4281a7b07ffa58/5c90dfd2.safetensors

Penting untuk memisahkan lisensi kode dari hak atas bobot. README repositori Android turunan menyebut bobot MIT, tetapi pemelihara Demucs asli menyatakan bahwa bobot pretrained tidak tercakup MIT dan hanya disediakan untuk tujuan ilmiah. Pernyataan pemelihara model asli menjadi dasar kehati-hatian proyek ini.

Akibatnya, unduhan dan pemakaian bobot HTDemucs dinonaktifkan dalam adapter CSR. Kode JNI dan jalur inferensi tetap disimpan sebagai prototipe teknis, tetapi paket PPK saat ini hanya development dan tidak menghasilkan stem.

Untuk rincian bukti, alternatif, dan status audit, lihat `docs/LISENSI_MODEL.md`.

## 9. Batas kualitas model

Model 6-stem tidak memiliki kategori khusus rebab atau instrumen gamelan. README resmi Demucs memperingatkan bahwa stem piano pada model enam sumber masih memiliki kualitas yang kurang baik dan artefak/bocoran. Karena itu, enam keluaran adalah format model, bukan jaminan bahwa setiap instrumen akan terpisah bersih.

Pengujian musik Jawa tetap wajib, terutama untuk mengukur apakah rebab atau suling ikut masuk ke stem vocals.

## Catatan lisensi bobot

Walaupun kode mesin Android dan port demucs.cpp berlisensi MIT, pemelihara Demucs menyatakan bobot pretrained tidak tercakup MIT dan hanya disediakan untuk tujuan ilmiah: https://github.com/facebookresearch/demucs/issues/327#issuecomment-1134828611. Oleh karena itu, bobot `5c90dfd2.safetensors` tidak boleh dianggap bebas untuk penggunaan umum. Pengunduhan otomatis dan pemakaiannya dinonaktifkan sampai hak penggunaan yang sesuai dipastikan. Lihat `docs/LISENSI_MODEL.md`.
