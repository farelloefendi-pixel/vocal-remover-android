# Roadmap pengembangan

## Tahap 0 — pemeriksaan referensi

- [x] Buka arsip PPK terbaru dan periksa seluruh daftar berkas.
- [x] Catat pola pemuatan DEX dan SO dari Lua.
- [x] Catat pemilihan pustaka 32/64-bit.
- [x] Catat pemantauan progres dan pembatalan berbasis file.
- [x] Identifikasi perbedaan kontrak PCM array pada JNI referensi.
- [x] Periksa header DEX, kelas, field, method signature, serta native method melalui tabel DEX.\n- [ ] Dekompilasi instruksi metode DEX lebih lanjut jika dibutuhkan untuk menyamakan detail internal.

## Tahap 1 — audit dan validasi mesin

- [x] Audit API C++ dan binding JNI dari repositori Demucs Android.
- [x] Identifikasi model enam sumber, format bobot, kebutuhan build, dan checksum yang dinyatakan upstream.
- [ ] Periksa teks lisensi dan atribusi seluruh dependensi sebelum distribusi.
- [ ] Pastikan jalur inferensi dapat dibangun untuk Android ARM64 dalam lingkungan build yang tersedia.
- [ ] Buat uji native minimal yang tidak bergantung pada UI.
- [ ] Ukur penggunaan RAM, waktu, suhu, dan stabilitas pada perangkat sasaran.

## Tahap 2 — kontrak Java DEX dan JNI

- [x] Tentukan arah adaptasi: kelas Java DEX CSR menggantikan contoh binding Kotlin upstream.
- [x] Tentukan input mesin berupa file raw float32, bukan satu array PCM besar.
- [x] Susun kelas Java DEX awal dengan kontrak Lua `muat/mulai/batal/hidup` dan native methods upstream.
- [x] Implementasikan rancangan worker Java asinkron; validasi runtime masih menunggu build dan uji perangkat.
- [x] Tulis decoder MediaCodec bertahap ke file raw float32; perlu verifikasi kompilasi dan pengujian format nyata.
- [x] Hubungkan pemanggilan separation native dan format hasil enam file; perlu uji output nyata.
- [ ] Uji pembatalan saat decode, inferensi, dan ekspor.
- [ ] Uji pemulihan setelah ekstensi ditutup atau proses dihentikan sistem.

## Tahap 3 — integrasi Lua CSR

- [x] Pertahankan pola pemuatan DEX/SO dari referensi pada adapter Lua.
- [x] Buat menu pilih audio berbasis penelusuran folder dan daftar enam hasil.
- [x] Buat layar progres dan daftar hasil dengan kontrol native yang terbaca pembaca layar; uji navigasi nyata masih tertunda.
- [x] Tambahkan batal, pembacaan hasil, pemutaran stem, dan pembersihan pekerjaan terputus.
- [ ] Uji fokus screen reader agar tidak meloncat ketika progres berubah.

## Tahap 4 — uji kualitas musik

Sampel uji:
- Lagu Jawa dengan rebab dominan.
- Lagu Jawa dengan rebab dan vokal bersamaan.
- Lagu dengan suling dan gamelan.
- Lagu dengan gender, saron, dan bonang.
- Campuran vokal/instrumen yang padat.
- Pop atau dangdut sebagai pembanding.

Catat:
- Apakah vokal terpisah dengan baik.
- Apakah rebab/suling ikut terhapus atau bocor ke stem vocals.
- Apakah enam file hasil dapat diputar.
- Durasi pemrosesan, RAM, suhu, dan apakah CSR tetap responsif.

## Tahap 5 — rilis

- [ ] Audit lisensi dan atribusi.
- [x] Siapkan build binary ARM64 dan DEX otomatis; uji perangkat masih diperlukan.
- [x] Tambahkan pengemasan PPK otomatis pada workflow CI; uji instalasi masih diperlukan.
- [ ] Uji instalasi PPK, upgrade, pembatalan, ekspor hasil, dan penghapusan.
- [ ] Rilis hanya setelah uji perangkat nyata lulus.

## Kriteria tidak boleh dilewati

Jangan menyatakan ekstensi siap pakai sebelum mesin native, model, enam keluaran, pembatalan, dan navigasi screen reader benar-benar diuji pada perangkat sasaran.

## Build otomatis

- [x] Tambahkan workflow GitHub Actions untuk membangun `DJLEngine.dex` dan `libdjl64.so`.
- [x] Pastikan workflow build ARM64 lulus untuk C++ JNI dan Java DEX.
- [x] Verifikasi output ELF AArch64 dan berkas Dalvik DEX; artefak CI berhasil diunggah.\n- [ ] Uji pemuatan DEX/SO dan pemanggilan JNI di perangkat Android nyata sebelum membuat paket PPK.

## Implementasi UI dan model

- [x] Buat adapter Lua berdasarkan alur pemuatan dan refleksi pada PPK referensi.
- [x] Buat antarmuka CSR awal untuk pemilih audio, unduhan model, progres, pembatalan, dan enam hasil.
- [x] Siapkan mekanisme unduh streaming dan verifikasi SHA-256; pemakaian HTDemucs dinonaktifkan setelah audit lisensi.
- [x] Atur pemisahan satu segmen dan maksimal tiga thread komputasi CPU.
- [x] Tambahkan deteksi Vulkan dengan fallback CPU.
- [ ] Uji seluruh alur di perangkat Redmi 13C / Android 16.
- [ ] Tambahkan ekspor enam stem ke folder Musik bersama dengan izin Android yang sesuai.

## Penghalang lisensi model

- [x] Temukan pernyataan pemelihara Demucs bahwa bobot HTDemucs tidak tercakup MIT dan hanya untuk tujuan ilmiah.
- [x] Nonaktifkan unduhan/pemakaian bobot HTDemucs dari adapter Lua.
- [x] Ubah paket hasil CI menjadi PPK development, bukan rilis fungsional.
- [ ] Pilih dan validasi model pengganti dengan lisensi bobot yang jelas.
