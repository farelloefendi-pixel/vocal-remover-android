# Java DEX bridge

Direktori ini berisi kelas Java DEX dan adapter Lua untuk ekstensi CSR/Jieshuo. Ini bukan APK dan tidak membuat aplikasi Android mandiri.

## Adapter Lua

File `lua/DJLBridge.lua` mengikuti pola `MD` pada `main.lua` PPK referensi: pencarian `DJLEngine.dex` dan SO berdasarkan folder plugin, penyalinan ke cache, pemuatan melalui `DexClassLoader`, pemanggilan metode statis lewat refleksi, serta status pekerjaan berbasis file. Adapter menambahkan pengunduhan model streaming dengan progres, status pekerjaan, pembatalan, pembacaan enam hasil, dan penghapusan hasil.

Inisialisasi dari `main.lua`:

```lua
local Bridge = require "bridge.lua.DJLBridge"
Bridge.mulai(ctx, DJL.base)
local kelas, pesan = Bridge.siapkan()
```

Jika model belum tersedia, mulai unduhan pada worker dan pantau `Bridge.statusModel()` dari handler UI:

```lua
Bridge.unduhModel()
-- polling berkala: Bridge.statusModel()
```

Setelah model siap, `Bridge.mulaiPemisahan(pathAudio)` mengembalikan ID pekerjaan. Pantau `Bridge.status(id)`, batalkan dengan `Bridge.batal(id)`, dan baca enam path hasil melalui `Bridge.ambilHasil(id)`.

## Kontrak Lua yang mengikuti referensi

Lua pada PPK referensi menggunakan refleksi untuk memanggil empat metode statis dengan argumen String:

- `muat(String soPath)`
- `mulai(String audioPath, String workDir, String jobId)`
- `batal(String workDir, String jobId)`
- `hidup(String jobId)`

Kelas `DJLEngine.java` mempertahankan nama dan signature tersebut agar pola pemanggilan `MD.panggil()` dan pemantauan status Lua dapat digunakan kembali.

## Kontrak JNI Demucs

Mesin upstream mendaftarkan native method pada kelas yang ditentukan oleh CMake. Kelas bridge ini memakai default package, sehingga build native harus memakai:

```cmake
set(SDENGINE_JNI ON)
set(SDENGINE_JNI_CLASS "DJLEngine")
```

JNI memerlukan interface bertingkat bernama `DJLEngine$ProgressListener` dengan metode:

`boolean onProgress(float fraction, String stage)`

Signature dan nama ini harus dipertahankan bersama dengan tabel registrasi native di upstream.

## Alur kerja

1. Lua mencari DEX dan SO berdasarkan ABI, lalu menyalinnya ke direktori privat.
2. Lua memuat kelas `DJLEngine` dengan `DexClassLoader`.
3. Lua memanggil `muat(soPath)`; metode tersebut memanggil `System.load(absolutePath)`. JNI_OnLoad mendaftarkan native method pada kelas DEX yang sama.
4. Jika model belum terpasang, adapter Lua menjalankan `unduhModel(url, statusPath)` pada worker Java. Data diunduh secara streaming ke folder privat, progres ditulis ke file status, dan checksum SHA-256 diverifikasi. `pasangModel(modelSourcePath)` juga tersedia untuk model lokal.
5. Lua memanggil `mulai(audioPath, workDir, jobId)`. Metode ini memulai satu Java worker thread.
6. Worker mendekode audio melalui MediaExtractor/MediaCodec menjadi file raw interleaved float32 secara bertahap.
7. Worker memanggil native prepare dan separation; progres ditulis ke file status.
8. Lua membaca status secara berkala dan tetap responsif.
9. Lua memanggil `batal(workDir, jobId)` untuk membuat marker pembatalan dan meminta pembatalan native.

Pekerjaan native dibatasi satu pada satu waktu karena pembatalan mesin upstream bersifat global.

## Model

Bridge mencari bobot di folder yang sama dengan pustaka SO:

`5c90dfd2.safetensors`

Berkas tersebut adalah bobot Demucs v4.1.0 htdemucs_6s yang dirujuk upstream. SHA-256 yang harus diverifikasi sebelum digunakan:

`d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411`

Lua perlu menyalin bobot secara streaming ke folder privat mesin; jangan memakai helper yang membaca seluruh 55 MB ke satu string Lua.

## Input audio

Decoder menulis PCM float32 interleaved ke file. Lagu tidak disimpan seluruhnya sebagai array Java. Mesin menyiapkan file itu menjadi stereo 44,1 kHz, kemudian memproses segmen dan menulis enam WAV.

Format PCM yang saat ini ditangani bridge: float32, signed 16-bit, unsigned 8-bit, packed 24-bit, dan signed 32-bit. Format output decoder yang tidak dikenali harus menghasilkan pesan galat yang jelas, bukan diteruskan sebagai data rusak.

## Status dan hasil

Status ditulis sebagai satu baris UTF-8:

`state|percent|stage|message|timestamp`

Nilai state mengikuti referensi Lua: `jalan`, `selesai`, `galat`, `batal`.

Hasil JSON menyimpan path enam file WAV. Lua perlu membaca format hasil Vocal Remover ini, bukan memakai parser JSON hasil tempo/nada/chord pada ekstensi referensi.

## Batas validasi

Kode bridge ini merupakan implementasi awal yang belum dikompilasi dengan Android SDK/NDK dan belum diuji pada Redmi 13C. DEX hasil build, signature JNI, decoder MediaCodec, pemulihan worker, serta aksesibilitas layar CSR harus diuji sebelum paket dinyatakan siap.
