# Vocal Remover CSR

Proyek ekstensi CSR/Jieshuo untuk memisahkan vokal dan instrumental secara offline di Android. Produk akhir adalah paket ekstensi PPK, bukan APK.

## Keputusan mesin

Mesin yang dipilih adalah **Kim_Vocal_2 (MDX-Net)**. Model ini menghasilkan vokal; instrumental dihitung dari campuran asli dikurangi vokal. Izin penggunaan komersial diberikan langsung oleh pembuat bobot, KimberleyJensen, pada 2 Oktober 2026. Bukti dan atribusi dicatat di [docs/KEPUTUSAN_MDX_KIM.md](docs/KEPUTUSAN_MDX_KIM.md).

Jalur runtime yang sedang disiapkan:
- C++: `stems.cpp` + `ggml`, port MDX-Net/TFC-TDF.
- Model runtime: GGUF hasil konversi dari Kim_Vocal_2 ONNX.
- Integrasi Android: JNI + Java DEX.
- Antarmuka: Lua.
- Target: ARM64, Android 16, Redmi 13C RAM 6 GB.
- Operasi: offline setelah model diunduh otomatis pertama kali.
- Keluaran: `vocals.wav` dan `instrumental.wav`.

## Status implementasi

Repositori saat ini masih memiliki jalur lama HTDemucs 6-stem dan ONNX Runtime. Jalur lama itu **belum kompatibel** dengan Kim_Vocal_2: model MDX-Net menerima spektrogram kompleks dengan input `[B,4,3072,256]`, bukan waveform `[1,2,343980]`. Karena itu, model tidak boleh sekadar diganti nama atau URL pada kode lama.

Tahap berikutnya adalah mengganti mesin JNI lama dengan C ABI `stems.cpp`, membangun dependensi untuk Android NDK `arm64-v8a`, menyiapkan unduhan GGUF terverifikasi, lalu mengubah UI Lua menjadi dua keluaran. Sampai tahapan tersebut selesai, build yang ada tetap development dan belum berfungsi sebagai pemisah audio Kim_Vocal_2.

## Batas validasi

- Parity C++ terhadap referensi ONNX/UVR yang dilaporkan stems.cpp berasal dari pengujian desktop, bukan Redmi 13C.
- Build Android ARM64, pemuatan JNI, inferensi di perangkat, RAM, suhu, pembatalan, dan aksesibilitas screen reader belum diverifikasi.
- Musik Jawa—khususnya rebab, suling, gender, saron, bonang, dan gamelan—harus masuk pengujian kualitas. Tidak ada jaminan semua instrumen tradisional akan selalu terpisah sempurna.
- Model tidak dibundel ke PPK; ekstensi akan mengunduh otomatis saat pertama kali diperlukan sehingga pengguna tidak perlu mengunduh manual.

## Dokumentasi

- [Keputusan mesin Kim Vocal 2 dan dasar izin](docs/KEPUTUSAN_MDX_KIM.md)
- [Arsitektur lama dan catatan migrasi](docs/ARSITEKTUR.md)
- [Pemeriksaan lisensi model sebelumnya](docs/LISENSI_MODEL.md)
- [Roadmap dan pengujian](docs/ROADMAP.md)
- [Panduan uji perangkat](docs/UJI_PERANGKAT.md)
