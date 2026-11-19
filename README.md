# Vocal Remover CSR

Proyek **ekstensi CSR/Jieshuo** untuk memisahkan audio musik secara offline di Android. Produk akhirnya adalah paket ekstensi PPK, **bukan APK**.

## Sasaran

- Antarmuka dan pengendali: Lua.
- Jembatan native: Java DEX yang dimuat melalui `DexClassLoader`.
- Mesin pemisahan: C++ JNI, model Demucs v4 HTDemucs 6-stem.
- Hasil: vocals, drums, bass, other, guitar, dan piano.
- Pemrosesan berjalan pada worker Java/C++, satu pekerjaan pada satu waktu.
- Progres, pembatalan, pemulihan pekerjaan, dan pembersihan file sementara.
- Model diunduh sekali melalui koneksi internet; pemisahan berikutnya berjalan offline.

## Perangkat sasaran

Redmi 13C, RAM 6 GB, penyimpanan 128 GB, ARM64, Android 16. Pengaturan awal dibatasi satu segmen dan maksimal tiga thread komputasi CPU. Mesin mencoba Vulkan bila tersedia dan menggunakan CPU jika GPU tidak dapat digunakan.

## Struktur implementasi

- `main.lua`: menu CSR, pemilih audio, progres, pembatalan, dan daftar enam hasil.
- `bridge/lua/DJLBridge.lua`: adapter Lua mengikuti pola `MD` pada PPK referensi.
- `bridge/java/DJLEngine.java`: kelas DEX, decoder MediaCodec, unduhan model, worker, dan pemanggilan JNI.
- `.github/workflows/build-engine.yml`: build otomatis C++ ARM64, DEX, pemeriksaan sintaks Lua, dan pengemasan PPK.

## Build otomatis

[Melihat build dan artefak ekstensi PPK di GitHub Actions](https://github.com/farelloefendi-pixel/vocal-remover-android/actions)

Jika build terbaru berhasil, artefak bernama `Vocal_Remover_CSR.ppk` tersedia pada bagian Artifacts di halaman run. Binary DEX dan SO juga disediakan terpisah untuk pemeriksaan.

## Model

Model tidak disimpan di Git dan tidak dimasukkan ke paket PPK. Saat pertama kali digunakan, ekstensi mengunduh `5c90dfd2.safetensors` dari sumber Hugging Face yang dipatok ke revisi tertentu, menyimpan secara streaming, lalu memeriksa SHA-256 sebelum digunakan. Ukuran unduhan sekitar 55 MB.

## Batas validasi

- Build CI membuktikan kode dapat dikompilasi dan dikemas; itu belum membuktikan pemisahan audio berhasil di Redmi 13C.
- Pemuatan DEX/SO, JNI, MediaCodec, Vulkan/CPU, pembatalan, dan pemulihan masih perlu uji perangkat nyata.
- Hasil saat ini disimpan pada direktori privat ekstensi dan dapat diputar dari menu hasil. Fitur ekspor ke folder musik bersama masih perlu diselesaikan dan diuji.
- HTDemucs 6S tidak memiliki stem rebab khusus. Rebab, suling, gender, saron, bonang, dan instrumen tradisional lain harus diuji; tidak ada jaminan semuanya akan selalu terpisah sempurna.

## Dokumen

- [Audit pola PPK referensi](docs/REFERENSI_INTEGRASI.md)
- [Audit mesin Demucs dan JNI](docs/MESIN_DEMUCS.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Panduan alur CSR](docs/PENGGUNAAN_CSR.md)
- [Roadmap dan pengujian](docs/ROADMAP.md)
