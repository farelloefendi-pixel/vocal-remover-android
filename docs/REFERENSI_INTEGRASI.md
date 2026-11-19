# Pemeriksaan ekstensi referensi

Dokumen ini mencatat pola yang terlihat langsung pada berkas `Tools detector tempo dan chord` yang diberikan sebagai referensi. Catatan dibatasi pada isi berkas yang diperiksa; bagian yang belum dapat dipastikan ditandai.

## Isi paket

Paket PPK berisi:
- `main.lua` — antarmuka, pemuatan mesin, pengaturan tugas, dan pemantauan.
- `metronom.lua` — kode pendukung metronom.
- `DJLEngine.dex` — kelas Java penghubung.
- `libdjl32.so` dan `libdjl64.so` — pustaka native untuk dua ABI.
- `djl.cpp` — implementasi C++ dan fungsi JNI.

## Alur yang tampak pada Lua

1. Lua mengambil konteks dari `activity or service or this`.
2. Handler utama dibuat menggunakan `Handler(Looper.getMainLooper())`.
3. Kode membuat `HandlerThread` terpisah bernama `DJL-kerja` dan `DJL-jaringan`. Jika pembuatan gagal, pekerja utama jatuh kembali ke handler UI.
4. `MD.cariSumber()` mencari DEX dan pustaka native. Pemilihan nama pustaka menggunakan `android.os.Process.is64Bit()`.
5. `MD.siapkan()` menyalin DEX dan SO ke direktori privat, membuat direktori optimasi, lalu memuat kelas `DJLEngine` menggunakan `dalvik.system.DexClassLoader`.
6. Lua memanggil metode Java reflektif dengan nama metode dan parameter bertipe String.
7. `MD.mulai(item)` membuat ID pekerjaan dan memanggil metode Java `mulai` dengan tiga String: path audio, direktori kerja, dan ID.
8. `MD.status(id)` membaca berkas status. `MD.batal(id)` meminta pembatalan melalui metode Java.
9. `MD.pantau()` memeriksa status berkala, memperbarui pesan progres, menangani selesai/gagal/batal, dan memberi pilihan meninggalkan layar sambil proses tetap berjalan.

## Pola status dan pembatalan pada C++

Pada `djl.cpp`:
- Konteks native menerima `statusPath` dan `batalPath`.
- Pembatalan diperiksa dengan keberadaan berkas pada `batalPath`.
- Progres ditulis sebagai teks dengan pemisah `|`, ke berkas sementara berakhiran `.tmp`, lalu dipindahkan ke berkas status menggunakan `rename()`.
- JNI menyediakan metode `versi`, `ujiMandiri`, dan `analisis`.
- Fungsi JNI `analisis` menerima dua `jshortArray` PCM, jumlah sampel, laju sampel, offset, dan dua path status/pembatalan.
- Hasil analisis dikembalikan sebagai String dengan awalan `SUKSES|`, `BATAL|`, atau `GALAT|`; data hasil sukses berupa JSON.

## Temuan penting untuk Vocal Remover

Pola pemuatan DEX/SO, pemilihan ABI, penggunaan direktori privat, status berbasis berkas, dan permintaan pembatalan layak dijadikan acuan.

Namun, **antarmuka JNI analisis pada referensi tidak boleh disalin mentah-mentah**. Referensi mengirim PCM sebagai array Java `short[]`. Mengirim seluruh lagu panjang dan seluruh tensor model Demucs dengan pola satu array besar berisiko menambah tekanan RAM. Vocal Remover perlu menggunakan alur berbasis path/segmen atau buffer terbatas, lalu menulis stem secara bertahap.

Referensi juga memuat proses decode audio menggunakan kelas Android `MediaExtractor` dan `MediaCodec` di DEX. Detail internal lengkap kelas Java belum dapat dipastikan hanya dari nama-nama simbol DEX; implementasi Java perlu didekompilasi/diperiksa lebih lanjut sebelum kontrak metodenya ditetapkan.

## Prinsip yang dipertahankan

- Ekstensi tetap dimulai dan dikendalikan dari CSR/Jieshuo.
- Lua mengelola UI; operasi berat tidak dijalankan di main thread.
- Pemuatan DEX dan SO dilakukan dari penyimpanan privat yang sesuai.
- ABI harus cocok dengan perangkat.
- Status progres, pembatalan, dan pemulihan pekerjaan harus eksplisit.
- Error pemuatan DEX/SO/JNI harus disampaikan sebagai pesan yang dapat dibaca screen reader.

## Bagian yang harus didesain ulang

- Kontrak JNI untuk menerima input audio secara bertahap, bukan satu array PCM raksasa.
- Penulisan enam file stem secara streaming/bertahap.
- Manajemen memori model, buffer, dan segmen.
- Penghentian native yang aman dan pembersihan file sementara.
- Status progres yang membedakan tahap decode, inferensi, dan ekspor.
