# Keputusan mesin Kim Vocal 2 MDX-Net

Tanggal keputusan: 19 November 2026.

## Keputusan

Mesin pemisahan vokal/instrumental yang dipilih adalah **Kim_Vocal_2 (MDX-Net)**. Target keluaran tahap ini adalah dua berkas: `vocals.wav` dan `instrumental.wav`. Model enam stem HTDemucs tidak lagi menjadi mesin aktif untuk jalur ini.

## Hak penggunaan bobot

Pembuat model, KimberleyJensen, memberi izin tertulis pada 2 Oktober 2026 di issue publik repositori pelatihannya:

> You are free to use the MDX-Net model Kim_Vocal_2 (Kim_Vocal_2.onnx) in any way you like, including for commercial use.

Tautan bukti: https://github.com/KimberleyJensen/kmdx-net_music-source-separation/issues/3

Izin ini berasal dari pembuat bobot, bukan sekadar metadata lisensi pada mirror. Simpan salinan issue/NOTICE dan atribusi bersama paket rilis. Jangan menyebut bobot ini sebagai MIT karena izin yang tersedia adalah pemberian izin langsung, bukan berkas lisensi MIT tersendiri.

## Berkas model dan identitas

- Model asli: `Kim_Vocal_2.onnx`, sekitar 66,8 MB.
- SHA-256 ONNX: `ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b`.
- Parameter UVR: 44.100 Hz, stereo, FFT 7.680, hop 1.024, `dim_f=3072`, `dim_t=256`, compensation 1.009.
- Mirror dokumentasi dan checksum: https://huggingface.co/aaatmy/uvr-mdx-net-onnx
- Sumber file ONNX: https://huggingface.co/aaatmy/uvr-mdx-net-onnx/resolve/main/Kim_Vocal_2.onnx

## Jalur runtime yang dipilih

Untuk menghindari pipeline ONNX enam-stem lama yang bentuk tensor dan preprocessing-nya tidak cocok, integrasi berikutnya akan menggunakan port C++ MDX-Net yang telah memiliki implementasi TFC-TDF dan uji parity terhadap ONNX/UVR:

- Runtime: `stems.cpp` + `ggml`, model `mdx_net_kim_vocal_2`.
- Kode stems.cpp: MIT; sertakan LICENSE dan seluruh notice dependensi.
- Model runtime: GGUF hasil konversi ONNX asli, sekitar 63,6 MiB. Konversi tidak mengubah hak bobot; izin langsung pembuat Kim_Vocal_2 tetap menjadi dasar penggunaan.
- Inferensi: C++ native ARM64, dipanggil melalui JNI dari Java DEX; Lua tetap mengelola UI CSR/Jieshuo.
- Hasil: vokal diprediksi model; instrumental dihitung sebagai campuran asli dikurangi vokal, mengikuti pipeline referensi UVR.

Sumber implementasi:
- https://github.com/betweentwomidnights/stems.cpp
- https://github.com/betweentwomidnights/stems.cpp/blob/main/docs/MDX.md
- https://github.com/betweentwomidnights/stems.cpp/blob/main/src/libstems_v1.h

## Catatan integrasi

1. Port C++ MDX-Net sudah memiliki jalur STFT/iSTFT dan parity desktop terhadap ONNX/UVR. Angka parity desktop bukan bukti performa Android.
2. Tahap berikutnya adalah membangun `stems.cpp` dan `ggml` untuk Android NDK `arm64-v8a`, lalu membuat JNI wrapper kecil untuk membaca PCM, memanggil C ABI, menulis dua WAV, progres, dan pembatalan.
3. Model diunduh otomatis oleh ekstensi pada pemakaian pertama, disimpan di direktori privat, lalu diverifikasi SHA-256. Pengguna tidak diminta mengunduh atau memasang model secara manual.
4. Model GGUF final harus dipatok dengan URL versi tetap dan SHA-256 berkas GGUF sebelum fitur unduhan diaktifkan.
5. ONNX Runtime dan kode HTDemucs enam stem yang lama tidak boleh dipakai oleh jalur Kim_Vocal_2. Keduanya harus dikeluarkan dari paket final setelah jalur native baru lulus build dan uji.

## Batas validasi

Belum ada klaim bahwa runtime ini sudah berhasil dibangun untuk Android, berjalan di Redmi 13C, atau menghasilkan pemisahan audio di perangkat. Uji wajib mencakup pemuatan model, lagu penuh, pembatalan, penggunaan RAM/suhu, kualitas vokal dan instrumental, serta aksesibilitas Jieshuo/CSR. Musik Jawa harus masuk sampel uji, terutama rebab, suling, dan gamelan.
