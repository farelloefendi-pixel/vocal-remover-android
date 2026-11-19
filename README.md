# Vocal Remover CSR

Proyek **ekstensi CSR/Jieshuo** untuk memisahkan audio musik secara offline di Android. Produk akhirnya adalah paket ekstensi PPK, **bukan APK**.

## Sasaran

- Antarmuka dan pengendali: Lua.
- Jembatan native: Java DEX yang dimuat melalui `DexClassLoader`.
- Mesin C++ Demucs lama masih berupa prototipe; kandidat berikutnya adalah integrasi ONNX Runtime Android untuk HTDemucs 6-stem.
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
- `.github/workflows/build-engine.yml`: build otomatis C++ ARM64, DEX, pemeriksaan signature JNI, sintaks Lua, alignment ELF 16 KB, dan pengemasan PPK.

## Build otomatis

<https://github.com/farelloefendi-pixel/vocal-remover-android/actions/runs/44531097879>

[Unduh artefak PPK development](https://github.com/farelloefendi-pixel/vocal-remover-android/actions/runs/44531097879/artifacts/14534306372)

Build terbaru **berhasil** pada 19 November 2026. Artefak `vocal-remover-csr-dev-arm64` berisi `Vocal_Remover_CSR_DEV.ppk`, DEX, library ARM64, dan informasi build. Paket ini dapat digunakan untuk menguji pemuatan ekstensi, menu, navigasi pembaca layar, serta pemilih audio. Paket ini **belum dapat memisahkan audio** karena model belum diaktifkan.

## Status model

Mesin C++ HTDemucs yang ada belum menjadi jalur pemisahan aktif. Kandidat HTDemucs 6S ONNX (136 MB, enam stem) sedang dinilai, tetapi belum diintegrasikan atau diunduh karena pernyataan MIT pada ekspor belum cukup untuk memastikan hak bobot dasar. Integrasi ONNX memerlukan runtime dan pipeline inferensi yang berbeda. Lihat [pemeriksaan lisensi](docs/LISENSI_MODEL.md) dan [perbandingan kandidat](docs/KEPUTUSAN_MODEL.md).

## Batas validasi

- Build CI membuktikan kode dapat dikompilasi dan dikemas; itu belum membuktikan pemisahan audio berhasil di Redmi 13C.
- Pemuatan DEX/SO, JNI, MediaCodec, Vulkan/CPU, pembatalan, dan pemulihan masih perlu uji perangkat nyata.
- Build development saat ini belum menghasilkan stem. Ekspor ke folder Musik bersama masih perlu diselesaikan.
- HTDemucs 6S tidak memiliki stem rebab khusus. Rebab, suling, gender, saron, bonang, dan instrumen tradisional lain harus diuji; tidak ada jaminan semuanya akan selalu terpisah sempurna.

## Dokumen

- [Audit pola PPK referensi](docs/REFERENSI_INTEGRASI.md)
- [Audit mesin Demucs dan JNI](docs/MESIN_DEMUCS.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Panduan alur CSR](docs/PENGGUNAAN_CSR.md)
- [Roadmap dan pengujian](docs/ROADMAP.md)
- [Kandidat alternatif Spleeter 5-stem ONNX](docs/ALTERNATIF_SPLEETER_ONNX.md)
- [Perbandingan dan keputusan kandidat model](docs/KEPUTUSAN_MODEL.md)
- [Panduan uji perangkat](docs/UJI_PERANGKAT.md)
