# Roadmap pengembangan

## Tahap 0 — pemeriksaan referensi

- [x] Buka paket PPK referensi.
- [x] Catat pola pemuatan DEX dan SO dari Lua.
- [x] Catat pemilihan pustaka 32/64-bit.
- [x] Catat pemantauan progres dan pembatalan berbasis file.
- [x] Identifikasi perbedaan kontrak PCM array pada JNI referensi.
- [ ] Periksa bytecode `DJLEngine.dex` secara penuh untuk memverifikasi metode dan lifecycle Java.

## Tahap 1 — validasi mesin

- [ ] Audit sumber mesin pemisah dan dependensinya.
- [ ] Pastikan model, format bobot, lisensi, dan checksum.
- [ ] Pastikan jalur inferensi benar-benar dapat dibangun untuk Android ARM64.
- [ ] Buat uji native minimal yang tidak bergantung pada UI.
- [ ] Ukur penggunaan RAM, waktu, suhu, dan stabilitas.

## Tahap 2 — kontrak Java DEX dan JNI

- [ ] Rancang API Java untuk prepare/start/status/cancel/release.
- [ ] Implementasikan worker native yang tidak memblokir thread UI.
- [ ] Gunakan input berbasis path dan buffer/segmen terbatas.
- [ ] Implementasikan penulisan enam stem bertahap.
- [ ] Uji pembatalan saat decode, inferensi, dan ekspor.
- [ ] Uji pemulihan setelah ekstensi ditutup atau proses dihentikan sistem.

## Tahap 3 — integrasi Lua CSR

- [ ] Pertahankan pola pemuatan DEX/SO dari referensi.
- [ ] Buat menu pilih audio dan lokasi hasil.
- [ ] Buat layar progres yang dapat dinavigasi pembaca layar.
- [ ] Tambahkan batal, hasil, dan pembersihan.
- [ ] Pastikan fokus tidak meloncat ketika progres berubah.

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
- [ ] Bangun binary final untuk ABI sasaran.
- [ ] Buat paket ekstensi CSR.
- [ ] Uji instalasi, upgrade, pembatalan, dan penghapusan.
- [ ] Rilis hanya setelah uji perangkat nyata lulus.

## Kriteria tidak boleh dilewati

Jangan menyatakan ekstensi siap pakai sebelum mesin native, model, enam keluaran, pembatalan, dan navigasi screen reader benar-benar diuji pada perangkat sasaran.
