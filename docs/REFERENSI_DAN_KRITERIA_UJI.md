# Referensi teknis dan kriteria pengembangan Vocal Remover

Dokumen ini mengikat referensi yang telah dibahas untuk proyek Vocal Remover offline Android. Referensi tidak boleh diabaikan ketika implementasi DSP, pemilihan metode, dan pengujian dibuat.

## 1. Keputusan produk yang berlaku

- Produk akhir adalah ekstensi Jieshuo/CSR untuk Android, bukan APK mandiri.
- Mesin utama yang dipilih: Kim Vocal 2 MDX-Net dalam format ONNX asli.
- Hasil akhir hanya dua berkas: `vocals.wav` dan `instrumental.wav`.
- Instrumental dibentuk sebagai residu campuran asli dikurangi estimasi vokal yang sudah disejajarkan dan dikompensasi; jangan menjumlahkan enam stem atau mengaktifkan HTDemucs.
- Pemrosesan offline setelah model dan runtime tersedia.
- Antarmuka tetap responsif, dapat dibatalkan, dan dapat dioperasikan dengan pembaca layar.
- Referensi model, buku, dan dataset dipakai sesuai perannya; tidak otomatis berarti semua bobot atau dataset boleh didistribusikan secara komersial.

## 2. Referensi arsitektur dan implementasi

### Demucs
Dipakai sebagai referensi umum untuk pemisahan sumber audio berbasis deep learning, pemrosesan audio, serta pembanding konsep. Implementasi HTDemucs lama dalam repositori ini bukan mesin produk dan tidak boleh dipanggil oleh Kim Vocal 2.

Sumber: https://github.com/facebookresearch/demucs

### Ultimate Vocal Remover (UVR)
Dipakai sebagai referensi utama alur kerja MDX-Net: pengolahan STFT, pemetaan tensor, inferensi model, inverse STFT, penanganan segmen, dan blending. Detail harus dicocokkan dengan konfigurasi Kim Vocal 2, bukan menyalin parameter model lain secara membabi buta.

Sumber: https://github.com/Anjok07/ultimatevocalremovergui

### ONNX Runtime
Runtime inferensi yang dipilih untuk menjalankan model ONNX pada Android ARM64. Validasi bentuk tensor dan satu inferensi sintetis hanya membuktikan kompatibilitas dasar model-runtime; belum membuktikan kualitas pemisahan audio.

Sumber: https://github.com/microsoft/onnxruntime

### ncnn
Referensi pembanding untuk kemungkinan optimisasi inferensi mobile. Bukan runtime aktif proyek ini dan tidak boleh menggantikan ONNX atau mengubah format model tanpa keputusan baru yang disetujui pengguna.

Sumber: https://github.com/Tencent/ncnn

## 3. Referensi musik dan data pengujian

### Notation of Javanese Gamelan
Referensi musik Jawa yang telah disebut dalam pembahasan proyek. Gunakan untuk memahami konteks instrumen dan pola musikal saat merancang kasus uji. Referensi ini bukan spesifikasi algoritma pemisahan. Detail bibliografi/edisi harus dipastikan dari salinan atau identitas buku yang digunakan sebelum membuat kutipan akademik.

### MUSDB18
Dataset acuan yang dikenal untuk evaluasi pemisahan musik. Berguna sebagai benchmark metodologis dan pembanding hasil; bukan pengganti pengujian musik tradisional Jawa.

Sumber: https://sigsep.github.io/datasets/musdb.html

### Slakh2100
Dataset musik sintetis multitrack yang dapat membantu pengujian teknis pemisahan dan penyelarasan stem. Karakter musik sintetisnya tidak mewakili seluruh karakter instrumen akustik gamelan.

Sumber: https://github.com/ethman/slakh

### Kasus uji musik Jawa
Pengujian khusus harus mencakup vokal dengan rebab, rebab solo, suling, gender, saron, bonang, gong, kendang, dan campuran gamelan. Kegagalan penting yang harus dicatat adalah instrumen melodis seperti rebab atau suling ikut terambil ke stem vokal. Jangan mengklaim model sudah cocok untuk gamelan sebelum pengujian dengar dan evaluasi berizin dilakukan.

## 4. Kontrak Kim Vocal 2 yang dipatok

- File: `Kim_Vocal_2.onnx`
- SHA-256: `ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b`
- Sample rate: 44.100 Hz, stereo
- Input node: `input`
- Output node: `output`
- Input/output tensor: `[1, 4, 3072, 256]`, float32
- `n_fft=7680`, `hop_length=1024`, `dim_f=3072`, `dim_t=256`
- Empat bidang tensor: real kiri, imajiner kiri, real kanan, imajiner kanan.

Alur DSP wajib: stereo PCM → STFT sesuai konvensi UVR/MDX → penyusunan tensor → inferensi ONNX → rekonstruksi spektrum dan iSTFT → penggabungan segmen tanpa celah/duplikasi → kompensasi → vocal WAV → instrumental residual.

Untuk segmen:
- Panjang input: `1024 × (256 - 1) = 261120` sampel.
- Trim tepi: `3840` sampel per sisi.
- Bagian tengah unik sebelum blending: `253440` sampel.
- Overlap-add memakai overlap 25% (stride 195.840 sampel, border 65.280 sampel) sebagai konfigurasi Kim Vocal 2 yang tercatat pada pengujian model komunitas. Implementasi menggunakan Hann crossfade dan envelope normalization; uji cakupan tanpa celah wajib lulus.

FFT 7680 bukan pangkat dua. Implementasi tidak boleh memakai DFT kuadratik sederhana. Pilih implementasi FFT mixed-radix/Bluestein yang efisien dan lisensinya jelas, lalu uji numerik terhadap referensi.

## 5. Aturan penggunaan referensi

1. Pertahankan Kim Vocal 2 ONNX; jangan mengganti ke GGUF, TFLite/LiteRT, atau model lain tanpa persetujuan pengguna.
2. Jangan mengaktifkan kembali enam stem atau HTDemucs.
3. Jangan menyalin konfigurasi STFT/overlap dari model yang berbeda tanpa validasi.
4. Catat sumber, versi, lisensi, dan atribusi untuk setiap kode, pustaka, model, buku, atau dataset.
5. Benchmark publik tidak menggantikan pengujian di Redmi 13C dengan CSR/Jieshuo dan pembaca layar aktif.
6. Hasil yang belum diuji harus diberi status belum tervalidasi; jangan disebut siap rilis.

## 6. Status implementasi saat ini

- Identitas model Java diarahkan ke `Kim_Vocal_2.onnx`.
- Pemeriksaan SHA-256 dipatok ke hash Kim Vocal 2.
- Smoke test Java diperbarui untuk memeriksa node `input`/`output`, bentuk tensor Kim Vocal 2, dan nilai output finite.
- Pemanggilan pemisahan lama diblokir agar mesin HTDemucs tidak pernah dijalankan dengan file Kim Vocal 2.
- Implementasi DSP Java mencakup Bluestein FFT 7680, periodic Hann STFT, tensor real/imaginary, penghilangan bin 0–2, iSTFT, weighted overlap-add 25%, kompensasi, dan dua WAV.
- Uji direct-DFT dan round-trip STFT/iSTFT lulus di CI; galat maksimum round-trip sekitar 3,87e-7.
- Model ONNX asli berhasil diunduh di CI, checksum cocok, metadata tensor dengan batch dinamis diterima, dan inferensi CPU menghasilkan tensor finite.
- Workflow build PPK Kim Vocal 2 telah dibuat. Helper JNI lama masih dipakai hanya untuk persiapan PCM/resampling; pemanggilan pemisahan Demucs diblokir dan tidak dipakai.
- Paket PPK kandidat integrasi ARM64 telah berhasil dibangun dan diverifikasi secara struktural. Model diunduh satu kali saat pemakaian pertama, bukan dibundel dalam PPK.
- Belum ada pengujian pemisahan audio nyata maupun pengujian perangkat Android; paket belum dapat disebut rilis final.
