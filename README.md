# Vocal Remover CSR — Kim Vocal 2

Ekstensi Jieshuo/CSR untuk memisahkan vokal dari musik pada Android ARM64. Produk akhir berupa paket ekstensi PPK, bukan APK.

## Keputusan produk yang dikunci
- Model: **Kim Vocal 2 MDX-Net, ONNX asli** (Kim_Vocal_2.onnx).
- Keluaran hanya dua berkas: vocals.wav dan instrumental.wav.
- Instrumental dihitung sebagai campuran asli dikurangi estimasi vokal yang telah disejajarkan dan dikompensasi.
- HTDemucs dan pemisahan enam stem tidak digunakan.
- Model dan runtime diunduh satu kali; setelah tersedia lokal, pemisahan berjalan offline.
- Jangan mengganti model atau format ONNX tanpa persetujuan pengguna.

## Perangkat sasaran
Redmi 13C, ARM64, RAM 6 GB, penyimpanan 128 GB, Android minimum API 29. Kinerja, penggunaan RAM, suhu, dan kualitas audio pada perangkat sasaran belum diverifikasi.

## Implementasi saat ini
- main.lua: antarmuka CSR, pemilihan audio, status pekerjaan, pembatalan, dan akses hasil.
- bridge/lua/DJLBridge.lua: pemuatan DEX/native libraries, pengunduhan dan verifikasi model, serta komunikasi status.
- bridge/java/DJLEngine.java: decoder MediaCodec, worker pemisahan, ONNX Runtime, penulisan dua WAV, dan pembersihan pekerjaan.
- bridge/java/MDXNetDSP.java: Bluestein FFT 7680, STFT/iSTFT MDX, penghilangan tiga bin frekuensi awal, serta dukungan overlap-add.
- .github/workflows/validate-kim-contract.yml: validasi checksum model, inferensi ONNX CPU sintetis, kompilasi Java, uji DSP, dan sintaks Lua.
- .github/workflows/build-engine.yml: build ARM64, DEX, dan paket PPK.

Helper JNI dari proyek audio lama masih digunakan hanya untuk menyiapkan PCM/resampling. Jalur pemisahan Demucs tidak digunakan dan bobot Demucs tidak disertakan.

## Kontrak model
- SHA-256: ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b
- Sample rate: 44.100 Hz stereo.
- Node ONNX: input dan output.
- Tensor: [1, 4, 3072, 256], float32.
- n_fft=7680, hop_length=1024, dim_f=3072, dim_t=256.
- Kompensasi vokal: 1.009.
- Tiga bin frekuensi paling rendah dinolkan sebelum inferensi.

Model tidak dibundel ke dalam PPK. Pada pemakaian pertama, ekstensi mengunduh model sekitar 67 MB dan memeriksa SHA-256. Setelah unduhan berhasil, model disimpan di penyimpanan internal aplikasi untuk pemakaian offline.

## Status validasi
Validasi CI sebelumnya berhasil untuk checksum model, inferensi ONNX CPU dengan sinyal sintetis, kompilasi Java/D8, pemeriksaan numerik FFT, round-trip STFT/iSTFT, dan cakupan overlap-add. Perubahan penghilangan tiga bin frekuensi awal sedang divalidasi ulang melalui GitHub Actions.

**Belum ada pengujian pada perangkat Android nyata.** Belum terbukti bahwa pemisahan lagu penuh, pemuatan DEX/SO, aksesibilitas screen reader, pembatalan, dan kinerja berjalan baik pada Redmi 13C. Hasil CI dengan sinyal sintetis bukan bukti kualitas musik nyata.

## Kualitas musik dan rilis
Kasus uji wajib mencakup musik pop, dangdut, serta musik Jawa/gamelan: vokal dengan rebab, rebab solo, suling, gender, saron, bonang, gong, kendang, dan campuran gamelan. Perlu dicatat jika rebab atau suling ikut terambil ke stem vokal. Tidak ada klaim bahwa model sudah sempurna untuk musik tradisional.

Pengguna menyatakan memiliki izin tertulis komersial dari pembuat model tertanggal 2 Oktober 2026. Bukti asli harus disimpan bersama arsip rilis dan dipastikan mencakup checkpoint ONNX yang digunakan serta distribusinya. Metadata lisensi pada mirror bukan pengganti bukti izin tersebut.

Paket PPK yang sudah dibangun adalah **kandidat integrasi untuk pengujian**, bukan rilis final. Rilis ditahan sampai uji audio nyata, perangkat, aksesibilitas, dan arsip hak penggunaan selesai.

## Dokumen
- [Roadmap dan tahapan pengujian](docs/ROADMAP.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Keputusan final Kim Vocal 2](docs/KEPUTUSAN_FINAL_KIM_VOCAL_2.md)
- [Referensi dan kriteria uji](docs/REFERENSI_DAN_KRITERIA_UJI.md)
- [Pemeriksaan lisensi model](docs/LISENSI_MODEL.md)
- [Panduan uji perangkat](docs/UJI_PERANGKAT.md)