# Vocal Remover CSR

Proyek **ekstensi CSR/Jieshuo** untuk memisahkan audio musik secara offline di Android. Produk akhirnya adalah paket ekstensi PPK, **bukan APK**.

## Sasaran

- Antarmuka dan pengendali: Lua.
- Jembatan native: Java DEX yang dimuat melalui `DexClassLoader`.
- Mesin pemisahan: C++ JNI, model Demucs v4 HTDemucs 6-stem.
- Hasil: vocals, drums, bass, other, guitar, dan piano.
- Pemrosesan berjalan pada worker Java/C++, satu pekerjaan pada satu waktu.
- Progres, pembatalan, pemulihan pekerjaan, dan pembersihan file sementara.
- Model belum diaktifkan. Bobot HTDemucs 6-stem memiliki batas lisensi yang belum jelas untuk penggunaan umum.

## Perangkat sasaran

Redmi 13C, RAM 6 GB, penyimpanan 128 GB, ARM64, Android 16. Pengaturan awal dibatasi satu segmen dan maksimal tiga thread komputasi CPU. Mesin mencoba Vulkan bila tersedia dan menggunakan CPU jika GPU tidak dapat digunakan.

## Struktur implementasi

- `main.lua`: menu CSR, pemilih audio, progres, pembatalan, dan daftar enam hasil.
- `bridge/lua/DJLBridge.lua`: adapter Lua mengikuti pola `MD` pada PPK referensi.
- `bridge/java/DJLEngine.java`: kelas DEX, decoder MediaCodec, unduhan model, worker, dan pemanggilan JNI.
- `.github/workflows/build-engine.yml`: build otomatis C++ ARM64, DEX, pemeriksaan sintaks Lua, dan pengemasan PPK.

## Build otomatis

[Melihat build dan artefak ekstensi PPK di GitHub Actions](https://github.com/farelloefendi-pixel/vocal-remover-android/actions)

Jika build terbaru berhasil, artefak development `Vocal_Remover_CSR_DEV.ppk` tersedia pada bagian Artifacts di halaman run. Paket ini hanya untuk menguji struktur dan antarmuka, belum dapat memisahkan audio karena model ditahan. Binary DEX dan SO juga disediakan terpisah untuk pemeriksaan.

## Status model

Mesin HTDemucs 6-stem dipertahankan sebagai prototipe kode, tetapi penggunaan bobotnya ditahan karena pemelihara Demucs menyatakan bobot tidak tercakup MIT dan hanya disediakan untuk tujuan ilmiah. Pengunduhan otomatis dinonaktifkan. Lihat [pemeriksaan lisensi](docs/LISENSI_MODEL.md) dan alternatif yang sedang dinilai.

## Batas validasi

- Build CI membuktikan kode dapat dikompilasi dan dikemas; itu belum membuktikan pemisahan audio berhasil di Redmi 13C.
- Pemuatan DEX/SO, JNI, MediaCodec, Vulkan/CPU, pembatalan, dan pemulihan masih perlu uji perangkat nyata. PPK saat ini adalah paket development, bukan rilis yang berfungsi penuh.
- Setelah model pengganti diaktifkan, hasil dirancang disimpan pada direktori privat dan diputar dari menu hasil. Build development saat ini belum menghasilkan stem. Ekspor ke folder Musik bersama masih perlu diselesaikan.
- HTDemucs 6S tidak memiliki stem rebab khusus. Rebab, suling, gender, saron, bonang, dan instrumen tradisional lain harus diuji; tidak ada jaminan semuanya akan selalu terpisah sempurna.

## Dokumen

- [Audit pola PPK referensi](docs/REFERENSI_INTEGRASI.md)
- [Audit mesin Demucs dan JNI](docs/MESIN_DEMUCS.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Panduan alur CSR](docs/PENGGUNAAN_CSR.md)
- [Roadmap dan pengujian](docs/ROADMAP.md)
- [Kandidat alternatif Spleeter 5-stem ONNX](docs/ALTERNATIF_SPLEETER_ONNX.md)
