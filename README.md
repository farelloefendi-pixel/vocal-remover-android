# Vocal Remover CSR

Proyek **ekstensi CSR/Jieshuo** untuk memisahkan audio musik secara offline di Android. Produk akhirnya adalah paket ekstensi PPK, **bukan APK**.

## Sasaran

- Antarmuka dan pengendali: Lua.
- Jembatan native: Java DEX yang dimuat melalui `DexClassLoader`.
- ONNX Runtime Android 1.32.0 dan kedua library native ARM64-nya kini dipaketkan. Bridge menginisialisasi runtime dan menyediakan smoke test satu segmen. Jalur inferensi streaming, enam writer WAV, dan instrumental kini sudah ditulis; hasil build dan uji perangkat belum diverifikasi. Mesin C++ Demucs lama masih berupa prototipe.
- Hasil: vocals, drums, bass, other, guitar, dan piano.
- Pemrosesan berjalan pada worker Java/C++, satu pekerjaan pada satu waktu.
- Progres, pembatalan, pemulihan pekerjaan, dan pembersihan file sementara.
- Model belum diaktifkan. Bobot HTDemucs 6-stem memiliki konflik lisensi yang belum terselesaikan; penggunaan komersial tidak boleh diasumsikan diizinkan.
- Karena produk direncanakan untuk dijual, seluruh bobot, model hasil konversi, dan dependensi harus melewati pemeriksaan hak komersial sebelum rilis.

## Perangkat sasaran

Redmi 13C, RAM 6 GB, penyimpanan 128 GB, ARM64, Android 16. Pengaturan awal memakai satu segmen inferensi, dua thread intra-op, satu thread inter-op, dan CPU. NNAPI belum diaktifkan sebelum uji perangkat membuktikan stabilitas dan akurasi.

## Struktur implementasi

- `main.lua`: menu CSR, pemilih audio, progres, pembatalan, dan daftar enam hasil.
- `bridge/lua/DJLBridge.lua`: adapter Lua mengikuti pola `MD` pada PPK referensi.
- `bridge/java/DJLEngine.java`: kelas DEX, decoder MediaCodec, unduhan model, worker, dan pemanggilan JNI.
- `.github/workflows/build-engine.yml`: build C++ ARM64, unduh dependensi ONNX Runtime Android 1.32.0, paketkan kedua library native ORT, gabungkan API Java ke DEX, periksa kelas dan ABI, cek signature JNI, sintaks Lua, alignment ELF 16 KB, lalu kemas PPK.

## Build otomatis

<https://github.com/farelloefendi-pixel/vocal-remover-android/actions/runs/44531097879>

[Unduh artefak PPK development](https://github.com/farelloefendi-pixel/vocal-remover-android/actions/runs/44531097879/artifacts/14534306372)

Artefak yang tertaut di atas adalah build development sebelumnya. Perubahan ONNX Runtime terbaru belum memiliki artefak build yang berhasil diverifikasi. Jangan gunakan tautan artefak lama untuk menganggap integrasi dua library atau smoke test terbaru sudah teruji.

## Status model

Mesin C++ HTDemucs yang ada belum menjadi jalur pemisahan aktif. ONNX Runtime Android 1.32.0 serta dua library native ARM64 sudah dimasukkan dalam workflow build. Bridge mengatur path keduanya sebelum membuat OrtEnvironment. Metode smoke test satu segmen memeriksa nama node, bentuk tensor, dan nilai output; belum ada hasil eksekusi pada perangkat. Kandidat HTDemucs 6S ONNX (136 MB, enam stem) belum diaktifkan karena hak penggunaan bobot hasil konversi belum dapat dipastikan. Pipeline inferensi streaming dan writer enam WAV stem plus instrumental sudah ditulis, tetapi belum dikompilasi ulang dan diuji pada perangkat. Lihat [pemeriksaan lisensi](docs/LISENSI_MODEL.md) dan [perbandingan kandidat](docs/KEPUTUSAN_MODEL.md).

## Standar mutu dan rilis komersial

Target mutu mencakup penghapusan vokal sepanjang lagu dan enam stem yang sinkron. Pengujian harus mencakup pop, dangdut, rebab, suling, dan gamelan dengan audio yang hak pengujiannya jelas. Model 6-stem tidak otomatis menghasilkan penghapusan vokal terbaik; benchmark penerbit menunjukkan perbedaan mutu vokal antara model 6S dan model 4-stem. Rilis komersial ditahan sampai izin bobot dan hasil uji mutu/perangkat terverifikasi. Lihat [rencana mutu dan gerbang rilis](docs/KEPUTUSAN_MODEL.md) serta [strategi inferensi streaming dan mode kualitas](docs/STRATEGI_KUALITAS.md).

## Batas validasi

- Build CI membuktikan kode dapat dikompilasi dan dikemas; itu belum membuktikan pemisahan audio berhasil di Redmi 13C.
- Pemuatan DEX/SO, JNI, MediaCodec, inferensi ONNX CPU, pembatalan, pemulihan, penggunaan RAM, suhu, dan aksesibilitas masih perlu uji perangkat nyata.
- Build development saat ini belum menghasilkan stem. Ekspor ke folder Musik bersama masih perlu diselesaikan.
- HTDemucs 6S tidak memiliki stem rebab khusus. Rebab, suling, gender, saron, bonang, dan instrumen tradisional lain harus diuji; tidak ada jaminan semuanya akan selalu terpisah sempurna.

## Dokumen

- [Audit pola PPK referensi](docs/REFERENSI_INTEGRASI.md)
- [Audit mesin Demucs dan JNI](docs/MESIN_DEMUCS.md)
- [Arsitektur teknis](docs/ARSITEKTUR.md)
- [Panduan alur CSR](docs/PENGGUNAAN_CSR.md)
- [Roadmap dan pengujian](docs/ROADMAP.md)
- [Kandidat alternatif Spleeter 5-stem ONNX](docs/ALTERNATIF_SPLEETER_ONNX.md)
- [Perbandingan dan keputusan kandidat model](docs/KEPUTUSAN_MODEL.md)
- [Panduan uji perangkat](docs/UJI_PERANGKAT.md)
