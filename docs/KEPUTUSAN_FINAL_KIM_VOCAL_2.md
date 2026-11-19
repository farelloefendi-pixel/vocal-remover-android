# Keputusan implementasi final: Kim Vocal 2 MDX-Net

Tanggal keputusan: 19 November 2026.

## Keputusan produk

Target enam stem dibatalkan. Rilis proyek ini hanya menghasilkan dua berkas audio:

- `vocals.wav`: estimasi stem vokal.
- `instrumental.wav`: campuran asli dikurangi estimasi vokal, dengan kompensasi sesuai konfigurasi model.

Tidak boleh membuat keluaran drums, bass, other, guitar, atau piano, dan tidak boleh memberi label palsu pada dua stem tersebut.

## Mesin yang dikunci

- Model: Kim Vocal 2 MDX-Net.
- Format bobot: ONNX, tidak diubah.
- Nama berkas: `Kim_Vocal_2.onnx`.
- Ukuran unduhan terverifikasi di repositori model: 66.8 MB.
- SHA-256: `ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b`.
- Sample rate: 44,100 Hz, stereo.
- Parameter MDX: n_fft 7680, hop_length 1024, dim_f 3072, dim_t 256, compensation 1.009.
- Primary stem: vocals.
- Secondary stem: instrumental dihitung dari campuran dikurangi vokal yang telah dikompensasi.

Sumber bobot yang dipatok:
https://huggingface.co/aaatmy/uvr-mdx-net-onnx/resolve/main/Kim_Vocal_2.onnx

Sumber konfigurasi dan provenance:
https://huggingface.co/aaatmy/uvr-mdx-net-onnx

Model card mirror menyebut Kim_Vocal_2 dilatih oleh KimberleyJensen dengan izin penulis/pembuat model. Proyek ini juga memiliki catatan izin tertulis dari pembuat model bertanggal 2 Oktober 2026. Simpan bukti izin asli bersama arsip lisensi rilis; metadata mirror bukan pengganti bukti izin tersebut.

## Kontrak inferensi yang wajib dipenuhi

Model bukan pemisah audio waveform langsung. Host harus melakukan seluruh rangkaian berikut:

1. Decode audio masukan dan konversi ke stereo 44.1 kHz.
2. Terapkan MDX STFT sesuai parameter model: n_fft 7680, hop 1024, Hann window, serta dimensi frekuensi/waktu yang cocok.
3. Bentuk tensor float32 ONNX sesuai nama input dan dimensi aktual yang dibaca dari model, bukan menebak nama node atau ukuran.
4. Jalankan inferensi dengan ONNX Runtime Android.
5. Lakukan inverse STFT dan overlap-add dengan padding/cropping yang konsisten agar tidak kehilangan sampel awal/akhir.
6. Terapkan kompensasi vokal 1.009 sesuai konfigurasi UVR.
7. Hitung instrumental dari mix minus vocals; pertahankan sinkronisasi, durasi, kanal, dan sample rate.
8. Tulis dua WAV PCM dan validasi header, jumlah frame, nilai finite, dan batas amplitudo.

Kontrak input/output ONNX harus diperiksa otomatis saat model dimuat. Build wajib gagal jika nama tensor atau bentuk aktual tidak sesuai. Jangan menganggap tensor HTDemucs `mix/stems` masih berlaku.

## Persyaratan Android

- ABI: arm64-v8a.
- Pemrosesan offline setelah model tersedia secara lokal.
- Model harus disertakan di paket uji atau diunduh sekali melalui koneksi internet dengan checksum SHA-256 sebelum dapat dipakai. Preferensi rilis: bundel model dalam paket PPK agar pengguna tidak perlu memasangnya secara terpisah.
- Inferensi berjalan pada worker, dengan pembatalan, progres yang dapat dibaca screen reader, dan pembersihan berkas sementara.
- Batasi memori puncak dengan pemrosesan per segmen; jangan menampung seluruh spektrum atau seluruh audio hasil dalam RAM.
- Pertahankan UI CSR/Jieshuo dan navigasi yang aksesibel.

## Gerbang sebelum paket uji dibagikan

Paket belum boleh disebut siap atau diberikan sebagai hasil final sebelum:

- implementasi MDX-Net cocok dengan kontrak Kim Vocal 2;
- build ARM64 dan DEX berhasil;
- model ONNX lolos checksum dan pemeriksaan node/tensor;
- inferensi satu segmen berhasil dan menghasilkan dua stem finite;
- uji audio penuh memastikan durasi, sinkronisasi, WAV, padding, dan overlap-add benar;
- paket PPK berisi model dan semua dependensi runtime yang diperlukan;
- semua teks UI dan daftar hasil hanya menyebut dua stem;
- lisensi kode, ONNX Runtime, model, serta izin tertulis tetap disertakan/didokumentasikan.

Build Demucs/HTDemucs enam stem yang ada di riwayat CI adalah artefak pengembangan lama dan bukan paket yang dapat digunakan untuk keputusan ini. Jangan membagikannya sebagai build Kim Vocal 2.


## Metadata tensor Kim Vocal 2 yang sudah dipastikan

Kontrak tensor model Kim Vocal 2 (berdasarkan metadata MDX/UVR dan dokumentasi implementasi yang diuji terhadap ONNX asli):

- Nama input: `input`.
- Bentuk input: `[1, 4, 3072, 256]`, float32.
- Urutan kanal: `[real_L, imag_L, real_R, imag_R]`.
- Nama output: `output`.
- Bentuk output: `[1, 4, 3072, 256]`, float32.
- Satu segmen: `1024 * (256 - 1) = 261120` sampel per kanal (sekitar 5,92 detik).
- Jendela: Hann periodik; STFT berpusat dengan padding refleksi setengah ukuran FFT.
- Bin frekuensi 0 sampai 2 dinolkan sebelum inferensi; hasil spektrum dipulihkan ke ukuran penuh sebelum inverse STFT.
- Proses panjang harus memakai overlap-add dengan bobot jendela dan trimming yang konsisten. Implementasi tidak boleh memakai chunking waveform HTDemucs lama.

Nama node dan bentuk di atas tetap harus dibaca dari sesi ONNX saat runtime, lalu dibandingkan dengan kontrak ini. Pemeriksaan metadata bukan pengganti uji inferensi nyata.

## Temuan audit kode lama (19 November 2026)

Audit sumber menemukan implementasi Java yang masih:
- mencari node `mix` dan `stems`;
- mengharapkan tensor waveform `[1,2,343980]` dan keluaran enam stem `[1,6,2,343980]`;
- membuat tujuh WAV dan menjumlahkan lima stem untuk instrumental;
- memverifikasi SHA-256 bobot HTDemucs lama;
- mengatur nama model ke `htdemucs_6s_fp16weights.onnx`.

Bridge Lua juga masih menonaktifkan model dengan alasan lisensi HTDemucs, sedangkan workflow CI masih mengambil dan membangun mesin Demucs. Seluruh bagian tersebut wajib diganti secara terkoordinasi. Mengganti file model atau checksum saja tidak menghasilkan implementasi Kim Vocal 2 yang benar.

Karena itu, artefak CI lama tidak boleh dipakai untuk pengujian pengguna. Implementasi baru perlu memasukkan STFT/iSTFT MDX-Net dan inferensi ONNX dua-stem, kemudian menjalani uji build serta audio nyata sebelum paket PPK diterbitkan.
