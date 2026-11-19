# Mesin pemisah: audit sumber Demucs Android

Dokumen ini merangkum pemeriksaan kode sumber publik `jPaauwe/demucs-android` dan membedakan kemampuan upstream dari pekerjaan integrasi CSR yang masih harus dibuat.

## 1. Kecocokan dengan target

Repositori upstream menyediakan mesin C++ untuk model Demucs v4 `htdemucs_6s` dengan enam keluaran:
`drums`, `bass`, `other`, `vocals`, `guitar`, dan `piano`.

Mesin ditujukan sebagai library yang ditautkan ke proyek Android, bukan APK yang harus dipakai sebagai aplikasi. Ini cocok dengan sasaran kita: kode mesin C++ dipakai di dalam ekstensi CSR melalui pustaka native yang dipanggil dari Java DEX.

Sumber: https://github.com/jPaauwe/demucs-android

## 2. API C++ yang benar-benar tersedia

Header `src/engine.hpp` mendeklarasikan:
- `sdengine::load_model(path)`
- `sdengine::prepare_input(rawPath, sampleRate, channels, outPath, progress)`
- `sdengine::analyze_input(preparedPath)`
- `sdengine::separate(preparedPath, outDir, options, progress)`
- `sdengine::compute_info(options)`
- `sdengine::request_cancel()`

Input decode aplikasi berupa file raw interleaved float32. Mesin menyiapkan audio menjadi stereo float32 44,1 kHz. Hasil pemisahan ditulis sebagai WAV stereo 16-bit ke direktori keluaran.

## 3. JNI upstream dan penyesuaian ke DEX

Upstream memiliki `android/jni/sdengine_jni.cpp` dan contoh binding Kotlin `NativeEngine.kt`. JNI memakai `RegisterNatives`, sehingga nama kelas dapat diganti melalui CMake:
`SDENGINE_JNI_CLASS`.

Kita tidak perlu menjalankan Kotlin atau membuat APK. Kita dapat menyediakan kelas Java biasa bernama `DJLEngine` di dalam `DJLEngine.dex`, dengan deklarasi native dan interface progres yang cocok dengan signature JNI. CMake kemudian dibangun dengan `SDENGINE_JNI=ON` dan `SDENGINE_JNI_CLASS=DJLEngine`. Kelas Java memuat pustaka menggunakan `System.load(absolutePath)`, mengikuti cara pemuatan pustaka dari referensi PPK.

Ini adalah rancangan integrasi yang didukung oleh mekanisme JNI upstream dan pola pemuatan DEX referensi; kelas Java CSR-nya tetap harus ditulis dan diuji.

## 4. Pola memori dan pemrosesan yang ditemukan

Kode `engine.cpp` menggunakan memory mapping untuk file input float32, membagi lagu menjadi segmen Demucs sekitar 7,8 detik dengan overlap 25%, dan menggabungkan hasil segmen secara bertahap ke file WAV. Dengan demikian, panjang lagu tidak mengharuskan seluruh enam hasil ditampung sekaligus di RAM.

Satu segmen inferensi dilaporkan membutuhkan sekitar 0,6 GB. Karena perangkat sasaran memiliki RAM 6 GB, pengaturan awal yang konservatif adalah satu segmen berjalan bersamaan. Ini angka awal dari dokumentasi upstream, bukan jaminan konsumsi total RAM pada Redmi 13C.

Perhitungan di dalam segmen menggunakan thread pool. Jumlah thread komputasi dan jumlah segmen paralel adalah dua pengaturan berbeda.

## 5. Decode audio untuk CSR

Upstream meminta aplikasi Android mendekode audio dengan `MediaCodec` menjadi file raw interleaved float32. File tersebut lalu diberikan ke `prepare_input`.

Referensi PPK sudah memiliki jalur decode menggunakan `MediaExtractor` dan `MediaCodec`, tetapi kontrak JNI-nya menggunakan array PCM `short[]`. Untuk Vocal Remover, bagian decode perlu diadaptasi agar data PCM dikonversi dan ditulis bertahap ke file float32, bukan disimpan sebagai satu array besar.

Detail format output codec, channel count, sample rate, dan penanganan akhir stream harus diverifikasi pada implementasi Java sebelum ditetapkan.

## 6. Vulkan dan CPU

Upstream menyediakan backend Vulkan yang dimuat saat runtime dan fallback CPU. README upstream menyatakan shader GPU belum dituning untuk setiap GPU ponsel. Karena GPU Redmi 13C belum diverifikasi pada runtime ini, tahap uji pertama harus menggunakan CPU atau melakukan deteksi kemampuan, lalu membandingkan GPU setelah jalur CPU stabil.

Jangan mengaktifkan GPU sebagai asumsi wajib. Kegagalan Vulkan tidak boleh membuat ekstensi gagal total jika CPU fallback dapat berjalan.

## 7. Build upstream

Petunjuk upstream menyebut:
- Android NDK r27.
- CMake minimal 3.22.1.
- minSdk 29 atau lebih tinggi.
- ABI `arm64-v8a`.
- Eigen sebagai git submodule.
- Opsi `SDENGINE_JNI=ON`.
- Opsi alignment halaman 16 KB pada target JNI untuk perangkat Android 15+.

Konfigurasi dan binary belum dibangun atau diuji untuk proyek CSR ini. Perlu validasi build sebelum menambahkan klaim dukungan perangkat.

## 8. Model dan lisensi

README upstream menyebut bobot `htdemucs_6s` v4.1.0 berukuran sekitar 55 MB dan berlisensi MIT, dengan checksum SHA-256:

`d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411`

Bobot tidak akan disimpan langsung di Git. Saat distribusi, sumber unduhan dan checksum harus diverifikasi.

Upstream menyebut lisensi mesin repository MIT, demucs.cpp MIT, bobot Demucs MIT, dan Eigen MPL 2.0. Salinan teks lisensi dan atribusi harus diperiksa dan disertakan saat paket distribusi dibuat.

## 9. Batas kualitas model

Model 6-stem tidak memiliki kategori khusus rebab atau instrumen gamelan. Selain itu, README resmi Demucs memperingatkan bahwa stem piano pada model enam sumber masih memiliki kualitas yang kurang baik dan artefak/bocoran. Karena itu, enam keluaran adalah format model, bukan jaminan bahwa setiap instrumen akan terpisah bersih.

Pengujian musik Jawa tetap wajib, terutama untuk mengukur apakah rebab atau suling ikut masuk ke stem vocals.
