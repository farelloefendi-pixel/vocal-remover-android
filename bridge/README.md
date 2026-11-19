# Java DEX dan adapter Lua CSR

Folder ini berisi sumber jembatan untuk ekstensi CSR/Jieshuo. Ini bukan APK dan bukan aplikasi Android mandiri.

## Referensi integrasi

Adapter `lua/DJLBridge.lua` mengikuti pola fungsi `MD` pada PPK referensi:
- mencari `DJLEngine.dex` dan pustaka SO sesuai ABI di folder plugin;
- menyalin DEX dan SO ke cache privat;
- memuat DEX menggunakan `DexClassLoader`;
- memuat SO menggunakan `System.load(pathAbsolut)`;
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
| `muat(String soPath)` | Memuat pustaka native dan menyiapkan path model |
| `lokasiModel()` | Mengembalikan lokasi privat model |
| `modelSiap()` | Memeriksa keberadaan dan checksum model |
| `unduhModel(String url, String statusPath)` | Mengunduh model secara streaming pada worker |
| `batalUnduhModel()` | Meminta pembatalan unduhan |
| `pasangModel(String sourcePath)` | Memasang model lokal secara streaming |
| `mulai(String audioPath, String workDir, String jobId)` | Memulai satu worker pemisahan |
| `batal(String workDir, String jobId)` | Membuat marker pembatalan dan meminta cancel native |
| `hidup(String jobId)` | Memeriksa worker |
| `infoMesin()` | Informasi konfigurasi komputasi |

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
- `nativeSeparate(String, String, boolean[], int, int, boolean, ProgressListener)`
- `nativeComputeInfo(int, boolean)`
- `nativeCancel()`

## Alur audio

1. Java memuat model dan mendekode audio dengan MediaExtractor/MediaCodec.
2. Decoder menulis PCM float32 interleaved ke file sementara secara bertahap.
3. Native prepare mengubah audio menjadi stereo float32 44,1 kHz.
4. Native separation memproses satu segmen pada satu waktu.
5. Vulkan dicoba; jika tidak tersedia atau self-test gagal, CPU digunakan.
6. Enam WAV ditulis pada direktori hasil.
7. JSON hasil berisi path enam WAV.
8. File PCM sementara dibersihkan pada akhir proses.

## Model dan lisensi

Bridge pernah disiapkan untuk model `5c90dfd2.safetensors`, tetapi adapter Lua kini mengunci `MODEL_ENABLED=false`. Pengunduhan, pemasangan, dan pemakaian HTDemucs tidak diaktifkan sampai dasar lisensi bobot sesuai tujuan penggunaan dipastikan. Rincian bukti dan alternatif ada di `docs/LISENSI_MODEL.md`.

## Status pekerjaan

File status memakai format:
`state|percent|stage|message|timestamp`

State: `jalan`, `selesai`, `galat`, `batal`.

Lua memantau status melalui Handler pada thread UI. Native callback progres tidak memperbarui tampilan langsung; callback hanya menulis status secara teratur. UI mengumumkan perubahan tahap dan progres yang berubah.

## Batas validasi

- Build CI dapat mengompilasi pustaka JNI, DEX, dan sintaks Lua, tetapi belum menguji runtime CSR.
- Pemanggilan JNI, decoder MediaCodec, Vulkan, pembatalan, pemulihan, serta navigasi screen reader harus diuji pada perangkat sasaran.
- Hasil saat ini disimpan di direktori privat. Ekspor ke folder Musik bersama belum tersedia.
