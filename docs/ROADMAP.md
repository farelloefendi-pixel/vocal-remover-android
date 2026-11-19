# Roadmap Vocal Remover — Kim Vocal 2

## Keputusan yang sudah dikunci

- Produk: ekstensi Jieshuo/CSR offline untuk Android ARM64.
- Model utama: Kim Vocal 2 MDX-Net ONNX asli.
- Keluaran: hanya `vocals.wav` dan `instrumental.wav`.
- Tidak memakai HTDemucs, tidak mengaktifkan enam stem, dan tidak mengganti model ke GGUF atau TFLite/LiteRT.
- Referensi teknis dan musik wajib mengikuti `REFERENSI_DAN_KRITERIA_UJI.md`.
- Izin tertulis komersial dari pembuat Kim Vocal 2 yang dinyatakan pengguna bertanggal 2 Oktober 2026 harus diarsipkan untuk rilis.

## Tahap 1 — kontrak model dan pengamanan

- [x] Catat nama model, checksum, sample rate, tensor, dan parameter MDX.
- [x] Arahkan nama file Java ke `Kim_Vocal_2.onnx`.
- [x] Ganti checksum verifikasi Java ke SHA-256 Kim Vocal 2.
- [x] Ganti smoke test ke node `input` dan `output`, bentuk `[1,4,3072,256]`, serta pemeriksaan nilai finite.
- [x] Blokir pemanggilan mesin pemisahan lama agar tidak dijalankan dengan model Kim Vocal 2.
- [x] Selaraskan dokumentasi arsitektur, kualitas, referensi, dan lisensi.
- [x] Pensiunkan workflow build lama yang membangun mesin Demucs dan menghasilkan paket yang tidak cocok.
- [x] Tambahkan workflow validasi kompilasi Java/DEX dan sintaks Lua.
- [x] Workflow validasi berhasil: checksum model, inferensi ONNX CPU, kompilasi Java/D8, direct-DFT, round-trip DSP, dan sintaks Lua lulus.

## Tahap 2 — DSP MDX-Net

- [x] Implementasikan Bluestein FFT 7680 berbasis radix-2 di Java; validasi numerik masih berjalan.
- [x] Implementasikan STFT periodic Hann, center=True, reflect padding, dan normalisasi sesuai referensi UVR/MDX.
- [x] Implementasikan pemetaan tensor empat bidang real/imaginary.
- [ ] Terapkan penanganan bin awal sesuai referensi model.
- [x] Integrasikan inferensi ONNX satu segmen dengan validasi node dan bentuk tensor.
- [x] Implementasikan iSTFT dan normalisasi envelope.
- [x] Implementasikan center trim, padding batas, dan penyambungan segmen tanpa kehilangan atau duplikasi sampel.
- [x] Luluskan direct-DFT dan round-trip STFT/iSTFT pada CI (galat maksimum round-trip 3,87e-7).

## Tahap 3 — audio dan dua keluaran

- [ ] Pertahankan decode MediaCodec bertahap dan resampling stereo 44,1 kHz dengan anti-aliasing.
- [ ] Tulis `vocals.wav` dengan frame count yang benar.
- [ ] Bentuk `instrumental.wav` sebagai residual mix dikurangi vokal yang sudah disejajarkan dan dikompensasi.
- [ ] Validasi header, durasi, sample rate, kanal, peak, clipping, dan nilai finite.
- [ ] Pastikan file parsial dibuang saat gagal atau dibatalkan.
- [ ] Hanya publikasikan hasil final setelah dua WAV lolos validasi.

## Tahap 4 — UI dan perangkat

- [ ] Pastikan worker tidak menjalankan inferensi atau I/O berat pada thread UI.
- [ ] Uji progres, pembatalan, pemulihan pekerjaan, dan pembersihan file sementara.
- [ ] Uji fokus dan navigasi pembaca layar Jieshuo/CSR.
- [ ] Uji audio 10 detik, 1 menit, 3 menit, dan 5 menit.
- [ ] Ukur RAM, suhu, baterai, waktu proses, dan stabilitas pada Redmi 13C.
- [ ] Uji musik Jawa: vokal+rebab, rebab solo, suling, gender, saron, bonang, gong, kendang, dan campuran gamelan.
- [ ] Catat apakah rebab atau suling ikut terambil ke stem vocals.

## Tahap 5 — rilis

- [ ] Pastikan izin tertulis Kim Vocal 2, atribusi, dan bukti hak distribusi diarsipkan.
- [x] Tambahkan workflow build PPK Kim Vocal 2; helper JNI lama masih dipakai untuk persiapan PCM dan perlu dipisahkan dari mesin Demucs.
- [ ] Selesaikan build DEX, native ARM64, dan paket PPK baru.
- [ ] Uji instalasi, pemuatan, pemisahan nyata, pembatalan, dan aksesibilitas.
- [ ] Rilis hanya setelah seluruh kriteria penerimaan lulus.

## Status saat ini

Kontrak identitas Kim Vocal 2, checksum, dan smoke test tensor sudah disiapkan. Mesin lama diblokir. Pipeline DSP awal, direct-DFT, round-trip, model ONNX CPU smoke inference, kompilasi Java/D8, dan sintaks Lua telah lulus CI. Build PPK ARM64 masih berlangsung. Belum ada hasil uji audio nyata atau uji perangkat.
