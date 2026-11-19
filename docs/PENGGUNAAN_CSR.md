# Panduan alur ekstensi CSR

## Persiapan paket

Paket PPK harus berisi:
- `main.lua`
- `DJLEngine.dex`
- `libdjl64.so` untuk Android ARM64
- `bridge/lua/DJLBridge.lua`

Model `5c90dfd2.safetensors` tidak dimasukkan ke paket. Ekstensi mengunduhnya saat pertama kali diminta.

## Alur pengguna

1. Buka ekstensi Vocal Remover CSR.
2. Pilih **Pilih audio untuk dipisahkan**.
3. Jelajahi penyimpanan dan pilih satu berkas audio.
4. Jika model belum tersedia, pilih **Unduh model**. Unduhan sekitar 55 MB dan memerlukan internet satu kali.
5. Tunggu verifikasi checksum model selesai.
6. Mesin memulai pemisahan dan menampilkan progres serta tahap yang sedang dikerjakan.
7. Tombol **Batalkan** meminta worker menghentikan proses secara kooperatif.
8. Setelah selesai, pilih salah satu dari enam stem untuk memutarnya atau menyalin lokasi berkas.

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

File sementara, status, dan hasil saat ini disimpan pada direktori privat CSR/Jieshuo. Menu hasil dapat memutar setiap WAV dan menyalin path lengkap. Menyalin file ke folder Musik bersama belum termasuk pada build awal; perlu jalur ekspor Android yang kompatibel dengan izin penyimpanan dan perlu diuji terpisah.

## Batas hasil musik tradisional

Model HTDemucs 6S memisahkan enam kategori umum, bukan instrumen Jawa satu per satu. Rebab dapat masuk ke stem vocals, other, atau stem lain bergantung karakter rekaman. Pengujian rebab dominan, rebab bersama vokal, suling, dan gamelan diperlukan sebelum menilai kualitasnya.

## Status pengujian

Paket hasil CI hanya membuktikan build berhasil. Sebelum pemakaian rutin, uji:
- pemuatan DEX dan SO pada CSR di Android 16;
- izin baca berkas audio;
- unduhan model dan checksum;
- decode beberapa format audio;
- keenam WAV dapat diputar;
- pembatalan saat decode dan inferensi;
- penggunaan RAM, suhu, waktu, dan respons screen reader;
- pemulihan setelah ekstensi ditutup atau sistem menghentikan proses.
