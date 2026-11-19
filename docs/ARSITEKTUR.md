# Arsitektur teknis Vocal Remover CSR

## 1. Bentuk produk

Produk akhir adalah ekstensi CSR/Jieshuo. Lua mengendalikan UI dan alur penggunaan. Java DEX menjadi jembatan pemuatan dan pemanggilan kode native. Mesin pemisah audio berjalan dalam pustaka C++ `.so`.

Tidak ada APK mandiri dalam sasaran proyek ini.

## 2. Lapisan sistem

```text
CSR / Jieshuo
  main.lua
    - menu dan pemilih audio
    - pengumuman progres yang ramah screen reader
    - mulai / tinggalkan layar / batal / hasil
    - pemulihan status tugas
        |
        v
  DJLEngine.dex (Java)
    - pemuatan pustaka native
    - decode/akses audio melalui API Android bila diperlukan
    - pengelolaan worker dan lifecycle native
    - kontrak JNI
        |
        v
  libvocalengine.so (C++ / ARM64)
    - preprocessing audio
    - inferensi model pemisahan
    - pemrosesan segmen
    - penulisan enam stem
    - status dan pembatalan
        |
        v
  Direktori hasil
    vocals.wav
    drums.wav
    bass.wav
    other.wav
    guitar.wav
    piano.wav
```

Nama kelas, nama metode, dan nama file di atas merupakan rancangan, bukan nama yang sudah tersedia pada binary.

## 3. Kontrak Lua–Java–C++

Pola referensi menggunakan `DexClassLoader`, pemuatan SO, pemanggilan reflektif metode Java, status berbasis file, dan file pembatalan. Prinsip ini dipertahankan.

API logis yang dibutuhkan:
- `prepare(enginePath, modelPath, workDir)` — validasi dan inisialisasi mesin.
- `start(inputPath, outputDir, jobId, options)` — mulai tugas tanpa menahan UI.
- `getStatus(jobId)` — membaca tahap, persentase, dan pesan.
- `cancel(jobId)` — meminta pembatalan kooperatif.
- `isAlive(jobId)` — memeriksa apakah worker masih aktif.
- `release()` — melepas model dan sumber daya native setelah tugas benar-benar berhenti.

Nama dan tipe parameter final akan disesuaikan dengan pola kelas Java referensi setelah bytecode Java diperiksa lebih lanjut.

## 4. Audio dan memori

Jangan menampung seluruh audio, semua tensor, dan keenam hasil di RAM sekaligus.

Rancangan awal:
- Baca/decode audio secara bertahap.
- Resample dan ubah format dalam buffer terbatas.
- Proses satu segmen pada satu waktu pada perangkat RAM 6 GB.
- Tulis keluaran setiap segmen secara bertahap ke enam file sementara.
- Finalisasi WAV hanya setelah semua segmen berhasil.
- Jika dibatalkan/gagal, tutup file dan hapus hasil parsial sesuai kebijakan pemulihan.

Ukuran segmen dan overlap belum ditetapkan; harus mengikuti kebutuhan model dan hasil pengujian pada Redmi 13C. Jangan menganggap angka dari perangkat lain berlaku langsung.

## 5. Status tugas

Status minimal: `queued`, `decoding`, `separating`, `writing`, `completed`, `cancelled`, `failed`.

Informasi yang disimpan:
- ID tugas.
- Tahap dan persentase.
- Pesan kesalahan singkat.
- Waktu pembaruan.
- Lokasi input dan output.
- Versi mesin/model.

Status harus ditulis secara atomik (file sementara lalu rename) sebagaimana pola yang tampak pada referensi. UI hanya membaca status dan mengumumkan perubahan bermakna, bukan membacakan setiap pembaruan kecil.

## 6. Aksesibilitas

- Gunakan kontrol native yang labelnya terbaca.
- Setiap tombol memiliki nama aksi yang jelas.
- Urutan fokus konsisten.
- Jangan memindahkan fokus otomatis pada setiap pembaruan progres.
- Progres diumumkan saat tahap berubah atau persentase melewati interval yang wajar.
- Tombol batal dapat dijangkau tanpa harus menelusuri teks panjang.
- Setelah dialog progres ditutup atau layar ditinggalkan, fokus saat kembali harus dipulihkan secara terukur.
- Uji dengan Jieshuo/CSR pada perangkat sasaran; aksesibilitas tidak boleh dinyatakan berhasil hanya dari inspeksi kode.

## 7. Model dan keluaran

Target enam stem:
1. vocals
2. drums
3. bass
4. other
5. guitar
6. piano

Model enam stem bukan pemisah instrumen tradisional khusus. Rebab, suling, gender, saron, bonang, dan instrumen lain dapat masuk ke kategori stem yang berbeda atau mengalami kebocoran. Evaluasi khusus musik Jawa harus menjadi bagian pengujian, bukan janji kualitas.

## 8. Keamanan dan distribusi

- Jangan menyimpan API key karena target pemrosesan offline.
- Jangan mengunduh atau memuat binary/model dari sumber yang belum diverifikasi.
- Catat lisensi kode, model, dan dependensi secara terpisah.
- Catat checksum model dan binary sebelum distribusi.
- Model tidak dimasukkan ke Git biasa jika ukuran atau ketentuan lisensinya tidak sesuai.


## 9. Kontrak DEX yang mengikuti PPK referensi

Kelas Java di dalam DEX harus tetap bernama `DJLEngine` dan menyediakan metode statis yang dipanggil Lua melalui refleksi:
- `muat(String soPath): String`
- `mulai(String audioPath, String workDir, String jobId): String`
- `batal(String workDir, String jobId): String`
- `hidup(String jobId): String`

Metode `muat` memuat SO menggunakan path absolut. Untuk binding JNI Demucs, kelas yang sama juga mendeklarasikan native method upstream dan interface `DJLEngine$ProgressListener`. Build CMake harus mengatur `SDENGINE_JNI_CLASS=DJLEngine`.

Metode `mulai` memulai satu worker Java asinkron. Worker mendekode audio, menjalankan prepare dan separation native, lalu memperbarui `status_<jobId>.txt`. Lua tetap menjadi pemantau dan pengendali UI.

## 10. Lokasi model

Bridge mencari bobot `5c90dfd2.safetensors` di folder privat yang sama dengan pustaka SO. Loader Lua perlu menyalin bobot ke folder tersebut menggunakan salinan streaming berbasis buffer, bukan membaca seluruh file ke satu string. Checksum wajib diverifikasi sebelum model digunakan.

## 11. Format hasil untuk ekstensi

Worker menghasilkan:
- `stem_<jobId>/drums.wav`
- `stem_<jobId>/bass.wav`
- `stem_<jobId>/other.wav`
- `stem_<jobId>/vocals.wav`
- `stem_<jobId>/guitar.wav`
- `stem_<jobId>/piano.wav`
- `hasil_<jobId>.json` berisi lokasi keluaran.

Parser Lua hasil tempo/chord pada referensi tidak digunakan untuk hasil Vocal Remover; format JSON hasil harus ditangani secara khusus.
