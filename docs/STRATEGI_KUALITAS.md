# Strategi kualitas — Kim Vocal 2 dua keluaran

Status dokumen ini mengikuti keputusan produk terbaru. Sasaran bukan lagi enam stem. Mesin utama adalah Kim Vocal 2 MDX-Net ONNX dan hasil akhir hanya vocals serta instrumental.

## Sasaran kualitas

- Mengambil vokal tanpa menghilangkan rebab, suling, atau instrumen melodis lain secara berlebihan.
- Mempertahankan transient, timbre, fase, dan durasi audio.
- Menghasilkan instrumental yang konsisten sebagai residu campuran asli dikurangi estimasi vokal.
- Bekerja offline pada Android ARM64.
- Tidak membekukan UI CSR/Jieshuo dan tetap dapat dioperasikan pembaca layar.

## Model dan DSP

Model yang dipatok:
- `Kim_Vocal_2.onnx`
- SHA-256 `ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b`
- input/output `[1,4,3072,256]`, node `input` dan `output`
- 44.100 Hz stereo, FFT 7680, hop 1024.

Pipeline yang harus diimplementasikan dan diuji:
1. Decode sumber ke PCM.
2. Resample dengan anti-aliasing ke 44.100 Hz stereo.
3. STFT dengan periodic Hann, center=True dan reflect padding sesuai konvensi referensi MDX.
4. Bentuk tensor empat bidang real/imaginary untuk kanal kiri dan kanan.
5. Terapkan perlakuan bin frekuensi awal sesuai referensi UVR/MDX.
6. Jalankan inferensi ONNX.
7. Rekonstruksi spektrum dan iSTFT dengan normalisasi envelope yang benar.
8. Gabungkan segmen menggunakan trim dan overlap-add tanpa kehilangan sampel.
9. Terapkan compensation sesuai konfigurasi Kim Vocal 2.
10. Tulis vocals dan bentuk instrumental sebagai residual dari mix yang telah disejajarkan.

Kontrak segmen yang telah dicatat:
- panjang segmen input 261.120 sampel;
- trim 3.840 sampel di setiap tepi;
- bagian tengah unik 253.440 sampel.
Overlap dan normalisasi akhir wajib diverifikasi terhadap kode referensi yang cocok dengan Kim Vocal 2; jangan mengasumsikan parameter model lain.

FFT 7680 bukan pangkat dua. Implementasi awal memakai Bluestein FFT berbasis radix-2 di Java agar ukuran transformasi 7680 dapat diproses tanpa DFT kuadratik. Akurasi dan kinerja Android belum tervalidasi.

## Pengujian audio

### Dataset dan benchmark
MUSDB18 dapat menjadi benchmark pemisahan musik umum. Slakh2100 dapat membantu pengujian teknis pada stem multitrack sintetis. Keduanya tidak cukup untuk menyimpulkan kualitas pada musik Jawa.

### Kasus musik Jawa
Siapkan audio berizin yang mencakup:
- vokal dengan rebab;
- rebab solo;
- vokal dengan suling;
- gender, saron, bonang, gong, dan kendang;
- campuran gamelan dengan vokal dan tanpa vokal.

Catat kebocoran vokal ke instrumental serta instrumen yang salah terambil ke vocals. Rebab dan suling merupakan kasus kritis. Referensi Notation of Javanese Gamelan dipakai untuk memahami konteks musikal, bukan untuk menggantikan pengujian audio nyata.

### Integritas
- Pastikan kedua keluaran memiliki jumlah frame dan titik awal yang sama.
- Periksa header WAV, sample rate, kanal, nilai finite, peak, clipping, dan durasi.
- Uji batas awal/akhir serta transisi antarsegmen.
- Jangan melakukan normalisasi independen yang mengubah perbandingan level vokal/instrumental tanpa keputusan eksplisit.
- Hasil sementara harus dihapus jika batal atau gagal; hasil final baru dipublikasikan setelah validasi.

## Kinerja Android

Uji pada Redmi 13C ARM64 dengan CSR/Jieshuo dan pembaca layar aktif:
- durasi audio 10 detik, 1 menit, 3 menit, dan 5 menit;
- peak RAM, waktu proses, suhu, baterai, dan stabilitas;
- pemrosesan satu segmen per giliran;
- pembatalan saat decode, inferensi, dan penulisan;
- UI tetap responsif dan pengumuman progres tidak berlebihan.

Ukuran file model tidak sama dengan kebutuhan RAM saat inferensi.

## Status kode saat ini

Identitas model, checksum, smoke test bentuk tensor, dan pipeline DSP Java awal telah diperbarui. Pemanggilan pemisahan lama diblokir. Uji direct-DFT dan round-trip DSP lulus; galat maksimum sekitar 3,87e-7. Model ONNX asli lolos pemeriksaan hash dan inferensi CPU. Kompilasi Java/D8 dan sintaks Lua lulus CI. Belum ada PPK yang tervalidasi pada perangkat. Model tidak boleh diaktifkan sampai implementasi dan pengujian lengkap.
