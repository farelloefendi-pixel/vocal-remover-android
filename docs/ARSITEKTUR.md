# Arsitektur teknis Vocal Remover CSR

## 1. Bentuk produk

Produk akhir adalah ekstensi CSR/Jieshuo dalam paket PPK. Tidak ada APK mandiri. Lua mengelola menu dan navigasi, Java DEX menghubungkan API Android dengan JNI, dan C++ menjalankan pemisahan audio.

## 2. Struktur paket PPK

Paket yang dibuat CI berisi:
- `main.lua`
- `DJLEngine.dex`
- `libdjl64.so` untuk ABI ARM64
- `bridge/lua/DJLBridge.lua`

Model tidak dibundel. Ekstensi mengunduh model pada pemakaian pertama dan menyimpan di cache privat.

## 3. Lapisan sistem

```text
CSR / Jieshuo
  main.lua
    - menu dan pemilih audio
    - dialog progres dan pembatalan
    - pemulihan tugas dan daftar hasil
        |
        v
  bridge/lua/DJLBridge.lua
    - mencari DEX dan SO
    - DexClassLoader
    - refleksi metode statis
    - status tugas berbasis file
        |
        v
  DJLEngine.dex (Java)
    - System.load pustaka native
    - unduh model streaming + checksum
    - MediaExtractor / MediaCodec
    - worker tunggal
    - progres dan pembatalan
        |
        v
  libdjl64.so (C++ / JNI)
    - prepare stereo float32 44,1 kHz
    - inferensi HTDemucs 6S
    - Vulkan bila tersedia, CPU fallback
    - enam WAV
```

## 4. Kesesuaian dengan PPK referensi

Pola Lua direplikasi dari `main.lua` PPK referensi:
1. `DJL.base` digunakan untuk mencari file mesin di folder ekstensi.
2. `DJLEngine.dex` dan pustaka SO dipilih berdasarkan ABI.
3. File disalin ke direktori cache privat.
4. Kelas dimuat dengan `DexClassLoader(dexPath, optimizedDir, null, ctx.getClassLoader())`.
5. Kelas disimpan pada `System.getProperties()` agar tidak dibuat ulang setiap kali fungsi dipanggil.
6. Metode Java statis dipanggil menggunakan `getDeclaredMethod` dan `invoke`.
7. Worker menulis status ke file; Lua membaca status melalui Handler.
8. ID pekerjaan disimpan agar ekstensi dapat melanjutkan pemantauan setelah dibuka kembali.

Kontrak inti yang sama:
- `muat(String soPath)`
- `mulai(String audioPath, String workDir, String jobId)`
- `batal(String workDir, String jobId)`
- `hidup(String jobId)`

Metode tambahan mengelola lokasi model, verifikasi, unduhan, dan informasi mesin.

## 5. Model dan input audio

- Model: Demucs v4.1.0 HTDemucs 6-stem.
- File: `5c90dfd2.safetensors`.
- SHA-256: `d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411`.
- Model diunduh secara streaming ke file sementara, diverifikasi, lalu dipindahkan ke lokasi final.
- Verifikasi checksum dicache berdasarkan path, ukuran, dan waktu modifikasi file.
- MediaCodec mendekode input ke PCM float32 interleaved secara bertahap.
- Mesin menyiapkan input menjadi stereo float32 44,1 kHz.

## 6. Batas memori dan komputasi

- Satu pemisahan aktif.
- Satu segmen inferensi dalam satu waktu.
- Maksimal tiga thread komputasi CPU untuk mengurangi perebutan sumber daya dengan UI dan pembaca layar.
- Mesin mencoba Vulkan dan beralih ke CPU jika Vulkan tidak tersedia atau self-test gagal.
- Audio mentah dan audio hasil prepare disimpan di file, bukan ditampung sebagai satu array besar.
- File raw dan prepared dihapus setelah selesai, gagal, atau dibatalkan.
- Hasil WAV disimpan terpisah agar dapat diputar kembali.

Angka ini adalah konfigurasi awal konservatif untuk perangkat RAM 6 GB; penggunaan RAM, waktu, dan suhu harus diukur langsung.

## 7. Status dan lifecycle

Status file memakai format:
`state|percent|stage|message|timestamp`

State:
- `jalan`
- `selesai`
- `galat`
- `batal`

Lua membaca status setiap 400–500 ms dan hanya memperbarui pesan ketika teks berubah. Jika ekstensi dibuka kembali saat worker masih hidup, pemantauan dilanjutkan. Jika proses sudah mati dan tugas belum selesai, file sementara dan hasil parsial dibersihkan.

Pembatalan dilakukan dengan marker file dan permintaan cancel native. Pembatalan bersifat kooperatif: worker berhenti ketika mencapai pemeriksaan atau callback progres berikutnya.

## 8. Hasil dan aksesibilitas

Enam keluaran:
1. `vocals.wav`
2. `drums.wav`
3. `bass.wav`
4. `other.wav`
5. `guitar.wav`
6. `piano.wav`

Antarmuka memakai kontrol native `LuaDialog`, `ListView`, dan `Button`, mengikuti gaya referensi. Pengguna dapat memutar stem atau menyalin path file. Hasil saat ini berada pada direktori privat CSR/Jieshuo; ekspor ke folder Musik bersama belum disediakan.

Label dan urutan kontrol sudah disusun untuk pembaca layar, tetapi fokus Jieshuo/CSR harus diuji langsung di perangkat. Kode tidak dapat membuktikan bahwa fokus tidak meloncat.

## 9. Batas model musik

HTDemucs 6S bukan model pemisah instrumen tradisional. Rebab, suling, gender, saron, bonang, dan instrumen lain bisa masuk ke stem vocals, other, atau kategori lain. Pengujian sampel musik Jawa diperlukan sebelum membuat klaim kualitas.

## 10. Status validasi

Build CI mengompilasi C++ JNI, DEX, memeriksa signature JNI, memeriksa sintaks Lua, dan mengemas PPK. Build berhasil bukan bukti runtime. Pemuatan di CSR, decode audio nyata, pemisahan enam stem, pembatalan, Vulkan/CPU, penggunaan memori, ekspor, dan aksesibilitas masih menunggu pengujian pada Redmi 13C Android 16.
