# Arsitektur teknis Vocal Remover CSR

## 1. Bentuk produk

Produk akhir adalah ekstensi CSR/Jieshuo dalam paket PPK. Tidak ada APK mandiri. Lua mengelola menu dan navigasi, Java DEX menghubungkan API Android dengan JNI, dan C++ menjalankan pemisahan audio.

## 2. Struktur paket PPK

Paket yang dibuat CI berisi:
- `main.lua`
- `DJLEngine.dex`
- `libdjl64.so` untuk ABI ARM64 (mesin Demucs prototipe)
- `libonnxruntime.so` dan `libonnxruntime4j_jni.so` untuk ABI ARM64 (ONNX Runtime Android 1.32.0)
- `bridge/lua/DJLBridge.lua`

Model HTDemucs tidak dibundel dan saat ini tidak diunduh atau digunakan. Integrasi model ditahan sampai hak penggunaan bobot jelas.

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
  DJLEngine.dex (Java + API ONNX Runtime)
    - System.load pustaka native C++ lama
    - memuat kedua pustaka ONNX Runtime melalui properti jalur native eksplisit
    - inisialisasi OrtEnvironment dan sesi model ONNX
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
4. Kelas dimuat dengan `DexClassLoader(dexPath, optimizedDir, ortFolder, ctx.getClassLoader())`; `ortFolder` memuat `libonnxruntime.so` agar pemuatan native melalui `System.loadLibrary` dapat ditemukan.
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
- Downloader streaming dan pemeriksa SHA-256 masih tersedia sebagai komponen generik, tetapi adapter Lua mematikan pemakaian HTDemucs.
- Bobot HTDemucs 6-stem dinyatakan oleh pemelihara Demucs hanya untuk tujuan ilmiah; lihat `docs/LISENSI_MODEL.md`.
- Alternatif berlisensi jelas sedang dinilai.
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

Antarmuka memakai kontrol native `LuaDialog`, `ListView`, dan `Button`, mengikuti gaya referensi. Daftar hasil dan pemutar stem sudah disiapkan, tetapi pemisahan dinonaktifkan sementara. Hasil saat ini berada pada direktori privat CSR/Jieshuo; ekspor ke folder Musik bersama belum disediakan.

Label dan urutan kontrol sudah disusun untuk pembaca layar, tetapi fokus Jieshuo/CSR harus diuji langsung di perangkat. Kode tidak dapat membuktikan bahwa fokus tidak meloncat.

## 9. Batas model musik

HTDemucs 6S bukan model pemisah instrumen tradisional. Rebab, suling, gender, saron, bonang, dan instrumen lain bisa masuk ke stem vocals, other, atau kategori lain. Pengujian sampel musik Jawa diperlukan sebelum membuat klaim kualitas.

## 10. Status validasi

Build CI mengompilasi C++ JNI, DEX, memeriksa signature JNI, memeriksa sintaks Lua, memverifikasi alignment ELF 16 KB, dan mengemas PPK development. Build berhasil bukan bukti runtime atau izin penggunaan model. Pemuatan di CSR, decode audio nyata, pemisahan enam stem, pembatalan, Vulkan/CPU, penggunaan memori, ekspor, dan aksesibilitas masih menunggu pengujian pada Redmi 13C Android 16.



### 11.1 Kontrak inferensi ONNX 6-stem dan pemrosesan streaming

Model HTDemucs 6S ONNX yang sedang dievaluasi mempunyai kontrak tetap:
- Input bernama mix, bentuk [1, 2, 343980], float32, stereo planar, 44.100 Hz.
- Output bernama stems, bentuk [1, 6, 2, 343980], float32.
- Model yang dipatok untuk uji: `htdemucs_6s_fp16weights.onnx`, 136 MB, SHA-256 `7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869`.
- Urutan baris output: drums, bass, other, vocals, guitar, piano.
- Panjang segmen 7,8 detik; overlap 25 persen (85.995 sampel); stride 257.985 sampel. Jendela referensi memakai fade linear 0→1 pada awal dan 1→0 pada akhir segmen, bukan jendela Hann.

Sumber rujukan: model card dan skrip infer.py pada repositori StemSplitio. Skrip tersebut melakukan zero-padding untuk segmen terakhir, menerapkan jendela transisi pada tepi segmen, lalu menormalisasi jumlah bobot overlap. Detail ini harus direplikasi, bukan diganti dengan pemotongan segmen tanpa blending.

Implementasi Android sekarang memakai overlap-add streaming dan tidak menampung seluruh output lagu dalam array [6, 2, totalSamples]:
1. Baca satu segmen input dari file PCM siap-pakai, lalu susun tensor channel-first.
2. Jalankan satu inferensi dan validasi nama serta dimensi output.
3. Campurkan kontribusi segmen dengan jendela transisi ke buffer keluaran bergulir untuk enam stem.
4. Tulis bagian buffer yang sudah tidak mungkin disentuh segmen berikutnya ke WAV sementara, kemudian pertahankan hanya ekor overlap untuk iterasi selanjutnya.
5. Pada segmen terakhir, normalisasi bobot, tulis sisa buffer, tutup keenam WAV, lalu validasi header, ukuran, dan durasi.
6. Batalkan secara kooperatif di antara segmen dan saat menulis; hapus keenam keluaran parsial jika batal atau gagal.

Dengan cara ini, memori kerja audio dapat dibatasi pada satu input segmen, satu output segmen, buffer overlap enam stem, dan memori internal ONNX Runtime. Ini belum menjamin perangkat cukup kuat: alokasi internal model, thread pool, dan cache tetap harus diukur pada perangkat sasaran. Model FP16-weights tetap menghasilkan tensor float32 saat inferensi menurut model card; ukuran file 136 MB tidak boleh dianggap sebagai RAM yang dibutuhkan.

### 11.2 Kriteria penerimaan ONNX Runtime

- ORT Android ARM64 dan DEX dimuat melalui jalur classloader yang sama tanpa merusak aksesibilitas UI.
- Sesi model dibuka dengan satu thread inferensi awal, optimisasi graph, dan CPU execution provider sebagai fallback wajib. NNAPI hanya opsi setelah uji perangkat membuktikan stabil dan akurat.
- Uji tensor sintetis memverifikasi bentuk input/output dan nilai finite sebelum file audio digunakan.
- Audio input mono diduplikasi ke stereo; multichannel dilipat ke stereo dengan aturan yang diuji. Resampling ke 44,1 kHz harus memiliki anti-aliasing dan mempertahankan durasi.
- Uji audio nyata menghasilkan tepat enam WAV PCM stereo 44,1 kHz dengan panjang sama dan tanpa NaN/Inf.
- Uji durasi 10 detik, 1 menit, dan 3 menit memeriksa lonjakan RAM, waktu, suhu, progres, pembatalan, serta file sementara.
- Uji aksesibilitas memastikan progres tidak membanjiri pembaca layar, fokus tidak pindah ke area lain, dan tombol batal tetap dapat dijangkau.

Status: runtime ONNX, pembukaan sesi, inferensi per segmen, overlap-add streaming, writer enam WAV, dan writer instrumental sudah ditulis. Metode smoke test juga memeriksa bentuk tensor serta nilai finite. Semua ini masih menunggu kompilasi CI dan pengujian runtime nyata. Model tetap dinonaktifkan karena dasar lisensi bobot ONNX hasil konversi belum dapat dipastikan.

## 12. Jalur integrasi ONNX Runtime yang sedang dinilai

Kandidat enam stem ONNX tidak kompatibel langsung dengan JNI Demucs yang sekarang. Runtime Android dan kelas Java ONNX sudah dipaketkan serta inisialisasinya disiapkan melalui bridge; pemuatan model, tensor input stereo 44,1 kHz, inferensi segmen sekitar 7,8 detik dengan overlap-add, dan penulisan enam keluaran WAV belum diimplementasikan.

ONNX Runtime Android 1.32.0 kini ditambahkan sebagai dependensi AAR resmi Maven Central. Workflow mengambil versi yang dipatok, mengekstrak kelas Java dan pustaka ARM64, menggabungkan kelas API ONNX ke DEX, serta memasukkan libonnxruntime.so ke paket. DexClassLoader diberi jalur pencarian pustaka native privat sehingga API Java ONNX Runtime dapat menemukan libonnxruntime.so. DJLEngine menetapkan jalur kedua pustaka native secara eksplisit sebelum memanggil OrtEnvironment.getEnvironment(). Metode ujiModelEnamStem membuka sesi ONNX, memastikan node mix/stems, menjalankan tensor nol berukuran [1,2,343980], lalu memeriksa 4.127.760 nilai output float agar finite. Ini adalah smoke test satu segmen, bukan pemisahan lagu nyata.

Tahap kode berikutnya kini mencakup pembukaan sesi model, validasi nama dan bentuk tensor, inferensi satu segmen, pemrosesan streaming seluruh durasi, penulisan WAV enam stem, dan instrumental. Mesin C++ lama hanya dipakai untuk menyiapkan/resampling PCM; inferensi tidak lagi memakai model safetensors atau nativeSeparate. Model ONNX FP16-weights sekitar 136 MB diverifikasi dengan SHA-256 yang dipatok, tetapi hak distribusi dan penggunaan komersialnya tetap harus dipastikan. Kebutuhan RAM serta kecepatan di Apple M4 Pro yang dilaporkan penerbit tidak dapat digeneralisasi ke Redmi 13C.

CI memeriksa keberadaan kelas OrtEnvironment di DEX, memastikan kedua pustaka ONNX Runtime berarsitektur AArch64, dan memeriksa alignment halaman ELF 16 KB pada keduanya. Pemeriksaan tersebut hanya validasi paket/kompilasi; belum membuktikan runtime dapat dimuat di CSR pada perangkat. Sebelum jalur ONNX dinyatakan siap, build dan uji perangkat harus membuktikan pembukaan model, satu inferensi uji, pemrosesan file audio panjang secara bertahap, enam WAV yang valid, pembatalan tanpa file rusak, dan pembersihan file sementara. Pengujian perangkat nyata tetap wajib.
