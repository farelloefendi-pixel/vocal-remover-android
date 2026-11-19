# Panduan alur ekstensi CSR

## Persiapan paket

Paket PPK harus berisi:
- `main.lua`
- `DJLEngine.dex`
- `libdjl64.so` untuk Android ARM64
- `bridge/lua/DJLBridge.lua`

Model `5c90dfd2.safetensors` tidak dimasukkan ke paket. Penggunaan dan pengunduhan bobot HTDemucs dinonaktifkan sementara karena lisensi bobot belum jelas untuk penggunaan umum.

## Alur pengguna

1. Buka ekstensi Vocal Remover CSR.
2. Pilih **Pilih audio untuk dipisahkan**.
3. Jelajahi penyimpanan dan pilih satu berkas audio.
4. Pada build development saat ini, layar akan menjelaskan bahwa model belum diaktifkan karena status lisensinya.
5. Pemisahan audio belum dapat dijalankan sampai model alternatif dipilih dan diintegrasikan.
6. Setelah mesin pengganti tersedia, alur progres, pembatalan, dan enam hasil akan diaktifkan kembali.

## Format yang diterima

Pemilih audio menampilkan berkas dengan ekstensi:
- MP3
- M4A
- AAC
- WAV
- FLAC
- OGG
- OPUS

Decoder Android MediaCodec menentukan format PCM hasil decode. Bridge menangani float32, PCM 16-bit, 8-bit, packed 24-bit, dan 32-bit.

## Pengelolaan proses

- Hanya satu pemisahan aktif pada satu waktu.
- Decoder menulis raw float32 ke file, bukan menyimpan seluruh lagu dalam RAM.
- Mesin menyiapkan audio menjadi stereo 44,1 kHz.
- Satu segmen pemisahan dijalankan bersamaan; komputasi CPU dibatasi maksimal tiga thread.
- Mesin mencoba Vulkan. Jika tidak tersedia atau self-test gagal, mesin memakai CPU.
- Jika ekstensi dibuka kembali saat worker masih hidup, layar progres dilanjutkan.
- Jika proses Android mati di tengah pekerjaan, pekerjaan parsial yang terdeteksi saat ekstensi dibuka kembali dibersihkan.

## Lokasi file dan ekspor

Jika model pengganti diaktifkan, file sementara, status, dan hasil dirancang disimpan pada direktori privat CSR/Jieshuo. Menu pemutaran stem dan penyalinan path sudah disiapkan dalam kode, tetapi build development saat ini belum menghasilkan WAV. Ekspor ke folder Musik bersama juga belum tersedia.

## Batas hasil musik tradisional

Model HTDemucs 6S memisahkan enam kategori umum, bukan instrumen Jawa satu per satu. Rebab dapat masuk ke stem vocals, other, atau stem lain bergantung karakter rekaman. Pengujian rebab dominan, rebab bersama vokal, suling, dan gamelan diperlukan sebelum menilai kualitasnya.

## Status pengujian

Paket hasil CI hanya membuktikan build berhasil. Sebelum pemakaian rutin, uji:
- pemuatan DEX dan SO pada CSR di Android 16;
- izin baca berkas audio;
- pemilihan model alternatif dan verifikasi checksum;
- decode beberapa format audio;
- keenam WAV dapat dihasilkan dan diputar setelah model aktif;
- pembatalan saat decode dan inferensi;
- penggunaan RAM, suhu, waktu, dan respons screen reader;
- pemulihan setelah ekstensi ditutup atau sistem menghentikan proses.
