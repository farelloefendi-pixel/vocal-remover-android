# Java DEX dan adapter Lua CSR

Folder ini berisi sumber jembatan untuk ekstensi CSR/Jieshuo. Ini bukan APK dan bukan aplikasi Android mandiri.

## Referensi integrasi

Adapter `lua/DJLBridge.lua` mengikuti pola fungsi `MD` pada PPK referensi:
- mencari `DJLEngine.dex`, library mesin, serta dua library ONNX Runtime ARM64 di folder plugin;
- menyalin DEX dan seluruh library native ke cache privat;
- memuat DEX menggunakan `DexClassLoader`;
- menetapkan path eksplisit `libonnxruntime.so` dan `libonnxruntime4j_jni.so` sebelum inisialisasi ORT;
- memanggil metode statis melalui `getDeclaredMethod` dan `invoke`;
- membaca status pekerjaan dari file teks;
- menyimpan ID tugas aktif agar dapat dipulihkan saat ekstensi dibuka kembali.

Di `main.lua`, adapter dimuat dari folder paket:
```lua
local base = -- direktori tempat main.lua berada
local Bridge = assert(loadfile(base .. "bridge/lua/DJLBridge.lua"))()
Bridge.mulai(ctx, base)
local kelas, pesan = Bridge.siapkan()
```

## Metode publik Java yang dipanggil Lua

| Metode | Fungsi |
|---|---|
| `muat(String soPath, String ortDirectory)` | Memuat mesin native lama dan menginisialisasi ONNX Runtime melalui dua library native |
| `lokasiModel()` | Mengembalikan lokasi privat model |
| `modelSiap()` | Memeriksa keberadaan dan checksum model |
| `unduhModel(String url, String statusPath)` | Mengunduh model secara streaming pada worker |
| `batalUnduhModel()` | Meminta pembatalan unduhan |
| `pasangModel(String sourcePath)` | Memasang model lokal secara streaming |
| `mulai(String audioPath, String workDir, String jobId)` | Memulai satu worker pemisahan |
| `batal(String workDir, String jobId)` | Membuat marker pembatalan dan meminta cancel native |
| `hidup(String jobId)` | Memeriksa worker |
| `infoMesin()` | Informasi konfigurasi runtime |
| `ujiModelEnamStem(String path)` | Membuka model ONNX, memeriksa nama dan bentuk tensor, lalu menjalankan inferensi uji satu segmen |

Empat metode inti `muat/mulai/batal/hidup` mempertahankan kontrak PPK referensi. Metode tambahan mengelola model dan informasi mesin.

## Kontrak JNI

CMake membangun dengan:
```cmake
-D SDENGINE_JNI=ON
-D SDENGINE_JNI_CLASS=DJLEngine
```

Kelas Java memakai default package `DJLEngine`. Callback progres:
`DJLEngine$ProgressListener.onProgress(float fraction, String stage): boolean`

Native methods mengikuti tabel registrasi upstream:
- `nativeLoadModel(String)`
- `nativePrepareInput(String, int, int, String, ProgressListener)`
- `nativeAnalyze(String)`
- `nativeSeparate(String, String, boolean[], int, int, boolean, ProgressListener)` (jalur Demucs lama, tidak dipakai oleh inferensi ONNX)
- `nativeComputeInfo(int, boolean)`
- `nativeCancel()`

## Alur audio

1. Java mendekode audio dengan MediaExtractor/MediaCodec.
2. Decoder menulis PCM float32 interleaved ke file sementara secara bertahap.
3. Jalur Demucs C++ lama masih prototipe dan tidak digunakan untuk kandidat ONNX.
4. Jalur ONNX kini memiliki smoke test satu segmen serta kode inferensi streaming dan writer tujuh WAV; kompilasi dan uji runtime belum diverifikasi.
5. Model masih dinonaktifkan sampai hak bobot jelas.

## Model dan lisensi

Bridge kini diarahkan ke kandidat `htdemucs_6s_fp16weights.onnx` dan memverifikasi SHA-256 berkas tersebut. Adapter Lua tetap mengunci `MODEL_ENABLED=false`; pengunduhan dan pemisahan belum dapat diaktifkan sampai dasar hak penggunaan bobot ONNX hasil konversi sesuai tujuan komersial dipastikan. Rincian bukti dan alternatif ada di `docs/LISENSI_MODEL.md`.

## Status pekerjaan

File status memakai format:
`state|percent|stage|message|timestamp`

State: `jalan`, `selesai`, `galat`, `batal`.

Lua memantau status melalui Handler pada thread UI. Native callback progres tidak memperbarui tampilan langsung; callback hanya menulis status secara teratur. UI mengumumkan perubahan tahap dan progres yang berubah.

## Batas validasi

- Build CI dapat mengompilasi pustaka JNI, DEX, dan sintaks Lua, tetapi belum menguji runtime CSR.
- Pemanggilan JNI untuk prepare/resampling, decoder MediaCodec, inferensi ONNX CPU, pembatalan, pemulihan, serta navigasi screen reader harus diuji pada perangkat sasaran.
- Hasil saat ini disimpan di direktori privat. Ekspor ke folder Musik bersama belum tersedia.
