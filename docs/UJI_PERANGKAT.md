# Uji perangkat Vocal Remover CSR

Dokumen ini membedakan pemeriksaan yang sudah lulus di CI dan pemeriksaan yang hanya dapat dilakukan di ponsel.

## Status paket saat ini

Build development berhasil dan menghasilkan PPK ARM64. Paket saat ini berguna untuk memeriksa apakah CSR memuat ekstensi, dialog terbaca, fokus dapat dinavigasi, dan pemilih audio dapat dibuka. Mesin pemisahan belum diaktifkan karena lisensi bobot model enam stem belum jelas. Jangan menganggap pesan status atau enam nama keluaran sebagai bukti bahwa pemisahan audio sudah berjalan.

## Pemeriksaan yang dapat dilakukan pada ponsel

1. Pasang paket `Vocal_Remover_CSR_DEV.ppk` melalui pengelola ekstensi CSR/Jieshuo.
2. Buka ekstensi dari menu CSR.
3. Navigasikan setiap item dengan pembaca layar dan periksa apakah nama menu, judul dialog, pesan, serta tombol dibacakan dengan urutan yang masuk akal.
4. Buka menu pemilih audio. Pastikan folder dan berkas audio dibacakan, folder dapat dibuka, dan tombol Kembali kembali ke menu sebelumnya.
5. Pilih satu berkas audio. Paket akan menampilkan pemberitahuan bahwa model belum diaktifkan; pada build ini pemisahan memang tidak dimulai.
6. Buka menu status mesin dan model. Catat pesan galat jika pemuatan DEX atau library native gagal.
7. Tutup ekstensi dan buka kembali. Periksa apakah CSR tetap responsif dan tidak kehilangan fokus.

## Informasi yang berguna jika ada masalah

Jika terjadi masalah, catat:
- apakah ekstensi muncul di menu CSR;
- teks yang dibacakan pembaca layar ketika ekstensi dibuka;
- item menu yang menyebabkan fokus berpindah atau dialog tertutup;
- apakah pemilih audio dapat menampilkan folder internal;
- pesan galat persis dari menu status mesin dan model;
- versi Android dan versi CSR/Jieshuo yang digunakan.

## Pemeriksaan yang belum dapat dilakukan

- Pemisahan menjadi vocals, drums, bass, other, guitar, dan piano.
- Akurasi pemisahan untuk rebab, suling, gamelan, dangdut, dan musik pop.
- Progres pemisahan, pembatalan proses native, pemulihan proses setelah ekstensi ditutup, serta pembersihan hasil parsial.
- Pemutaran dan penghapusan enam stem hasil pemisahan pada sesi nyata.

Pemeriksaan tersebut baru bisa dijalankan setelah model enam stem yang hak penggunaannya jelas dipilih dan diintegrasikan, lalu paket dibangun ulang.
