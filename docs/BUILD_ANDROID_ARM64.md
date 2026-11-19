# Build native Android ARM64 — Kim Vocal 2

Dokumen ini mencatat jalur build yang sudah diperiksa dari CMake stems.cpp dan ggml. Ini adalah resep build yang perlu dijalankan di CI; belum merupakan bukti bahwa hasil Android sudah berhasil dibangun atau diuji di Redmi 13C.

## Versi sumber yang dipatok

Gunakan `betweentwomidnights/stems.cpp` tag `v0.1.6` sebagai baseline stabil. Tag `v0.1.7` pada saat pencatatan masih berstatus pre-release. Kim_Vocal_2 sudah tersedia pada v0.1.4 dan ABI `libstems_v1.h` tidak berubah pada v0.1.5/v0.1.6.

Sumber:
- https://github.com/betweentwomidnights/stems.cpp/releases
- https://github.com/betweentwomidnights/stems.cpp/blob/v0.1.6/docs/MDX.md
- https://github.com/betweentwomidnights/stems.cpp/blob/v0.1.6/src/libstems_v1.h

## Dukungan Android

Submodul ggml yang dipakai stems.cpp memiliki pilihan backend CPU khusus Android ARM, termasuk varian Android ARMv8.0 dan ARMv8.2. CMake ggml juga menautkan `dl` pada Android. Ini mendukung kelayakan cross-build CPU ARM64; bukan bukti bahwa seluruh integrasi aplikasi sudah selesai.

## Konfigurasi CMake yang dituju

- Android NDK toolchain: `$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake`
- ABI: `arm64-v8a`
- Platform minimum: Android API 24 (perlu diselaraskan lagi dengan minimum CSR/Jieshuo).
- Build type: Release.
- Backend: CPU saja untuk baseline.
- `STEMS_BUILD_TOOLS=OFF` agar CLI/server/test desktop tidak ikut dibangun.
- `STEMS_STATIC=OFF` agar target C ABI menghasilkan `libstems.so`; library ini masih membutuhkan JNI wrapper kecil sebelum dapat dipanggil langsung dari Java.
- `GGML_OPENMP=OFF` untuk menghindari ketergantungan OpenMP tambahan pada build awal.

Contoh konfigurasi:

```sh
cmake -S "$STEMS_CPP_DIR" -B build/android-arm64 \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release \
  -DSTEMS_BUILD_TOOLS=OFF \
  -DSTEMS_STATIC=OFF \
  -DSTEMS_CUDA=OFF \
  -DSTEMS_VULKAN=OFF \
  -DSTEMS_METAL=OFF \
  -DGGML_OPENMP=OFF

cmake --build build/android-arm64 --target stems_shared --parallel 2
```

## Integrasi aplikasi yang masih harus dibuat

1. Tambahkan JNI wrapper untuk memanggil `stems_get_api(STEMS_ABI_VERSION_1)`.
2. `context_create` memuat GGUF dari direktori privat aplikasi.
3. Baca audio dengan decoder Android; kirim PCM float interleaved ke `separate`.
4. Salin hasil vokal dan instrumental ke WAV melalui Java/native writer.
5. Kirim progres ke listener; callback cancel menangani pembatalan.
6. Jangan memanggil inferensi pada UI thread.
7. Pastikan context dan result dibebaskan pada semua jalur sukses, error, dan cancel.
8. Lua hanya mengendalikan tampilan dan status; tidak mengerjakan DSP berat.

## Catatan memori dan performa

Dokumentasi upstream melaporkan model Kim sekitar 63.6 MiB F32 dan waktu 17.7 detik untuk klip 20 detik pada CPU desktop Core Ultra 9. Angka tersebut bukan estimasi Android. Jangan menetapkan angka RAM atau kecepatan Redmi 13C sebelum pengukuran perangkat nyata.

Build awal wajib memakai satu pekerjaan inferensi pada satu waktu. Hasil harus diuji untuk lagu 3–5 menit, pembatalan, pemanasan, dan pemakaian memori puncak.

## Model dan unduhan

Model runtime adalah GGUF `mdx_net_kim_vocal_2-17M-v1.0-F32.gguf` sekitar 63.6 MiB. Model diunduh otomatis saat pengguna pertama kali memilih pemisahan, disimpan di direktori privat, lalu diverifikasi SHA-256 yang dipatok untuk file GGUF final. Jangan menggunakan checksum ONNX untuk memverifikasi file GGUF karena keduanya berbeda.

Sumber model GGUF: https://huggingface.co/thepatch/mdx-net-kim-vocal-2-GGUF

Izin penggunaan Kim_Vocal_2 berasal dari konfirmasi langsung pembuat bobot: https://github.com/KimberleyJensen/kmdx-net_music-source-separation/issues/3. Sertakan bukti izin dan atribusi dalam NOTICE. `stems.cpp` memiliki lisensi MIT; semua lisensi dan notice submodul ggml juga harus ikut dalam paket.

## Status

- Model dan API C sudah tersedia upstream.
- Jalur CPU ARM Android tersedia di konfigurasi ggml.
- Build NDK untuk repositori ini: belum dijalankan.
- JNI wrapper CSR/Jieshuo: belum dibuat.
- Pengujian Redmi 13C: belum dilakukan.