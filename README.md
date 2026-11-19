# Vocal Remover CSR

Proyek ekstensi **Commentary Screen Reader (CSR) / Jieshuo** untuk memisahkan audio musik secara offline di Android.

> **Status: tahap perancangan dan pemeriksaan integrasi.** Belum ada ekstensi siap pakai atau binary mesin pemisah yang dapat dipasang.

## Sasaran

- Bentuk produk akhir: ekstensi CSR/Jieshuo, bukan APK mandiri.
- Antarmuka dan alur penggunaan: Lua.
- Jembatan ke kode native: Java DEX + JNI, mengikuti pola referensi ekstensi yang diberikan.
- Mesin pemisahan: C++ dengan model pemisahan musik enam stem yang akan divalidasi.
- Keluaran yang dituju: vocals, drums, bass, other, guitar, dan piano.
- Operasi offline, progres yang dapat dibaca pembaca layar, pembatalan, serta pembersihan berkas sementara.

## Perangkat sasaran awal

Redmi 13C, RAM 6 GB, penyimpanan 128 GB, ARM64, Android 16. Pengaturan awal harus konservatif: satu pekerjaan pemisahan pada satu waktu, pembatasan memori, dan pemrosesan bertahap.

## Arsitektur yang dituju

`main.lua` mengelola antarmuka CSR dan memanggil kelas Java dari DEX melalui `DexClassLoader`. Kelas Java memuat pustaka native ARM64 (`.so`) dan menjadi penghubung JNI ke mesin C++. Mesin memproses audio dan menghasilkan berkas stem.

Pola pemuatan DEX, pemilihan pustaka berdasarkan arsitektur, pemisahan pekerjaan dari utas antarmuka, pemantauan status, dan permintaan pembatalan mengacu pada ekstensi referensi. Mesin Demucs yang dipilih menyediakan API C++ dan binding JNI yang dapat diarahkan ke kelas Java DEX khusus.

## Implementasi awal

- Sumber Java DEX: `bridge/java/DJLEngine.java`.
- Kontrak Lua–Java mengikuti `muat`, `mulai`, `batal`, dan `hidup` pada PPK referensi.
- Workflow GitHub Actions disiapkan untuk membangun DEX dan pustaka JNI ARM64: [Build CSR native engine](.github/workflows/build-engine.yml).
- Model terpisah dan tidak dimasukkan ke repositori: `5c90dfd2.safetensors` (checksum diverifikasi oleh bridge saat mulai bekerja).
- Build dan pengujian perangkat masih menunggu hasil CI; kode bridge belum boleh dianggap siap dipasang.

## Dokumen proyek

- [Pemeriksaan pola ekstensi referensi](docs/REFERENSI_INTEGRASI.md)
- [Audit mesin Demucs C++ dan kontrak JNI](docs/MESIN_DEMUCS.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Tahapan pengembangan dan pengujian](docs/ROADMAP.md)

## Batasan yang sudah diketahui

- Model enam stem tidak menyediakan stem rebab khusus. Hasil rebab, suling, gamelan, gender, saron, dan bonang harus diuji; tidak ada jaminan instrumen tradisional akan selalu dipertahankan sempurna.
- Model dan binary native belum disertakan dalam repositori.
- Mesin belum dikompilasi atau dijalankan pada perangkat sasaran.
- APK mandiri bukan target proyek.

## Prinsip pengembangan

1. Ikuti pola integrasi yang benar-benar ditemukan pada ekstensi referensi.
2. Adaptasi JNI untuk input audio berbasis berkas dan buffer bertahap, bukan satu array PCM besar.
3. Jaga UI CSR tetap responsif dan fokus pembaca layar tetap stabil.
4. Uji pada perangkat sasaran sebelum menyatakan fitur berhasil.
5. Catat lisensi, asal model, checksum, dan instruksi distribusi sebelum menyertakan aset model.
