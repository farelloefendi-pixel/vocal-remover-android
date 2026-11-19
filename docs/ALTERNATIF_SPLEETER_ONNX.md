# Kandidat mesin alternatif: Spleeter 5-stem ONNX

Status: kandidat teknis untuk dipertimbangkan; belum dipilih dan belum diuji pada perangkat.

## Hasil dan batas stem

Model resmi Spleeter 5-stem menghasilkan:
1. vocals
2. drums
3. bass
4. piano
5. other

Model ini tidak menghasilkan stem gitar terpisah. Gitar masuk ke `other`. Karena itu kandidat ini tidak memenuhi persis target awal enam stem Demucs.

## Dasar lisensi

Paper JOSS yang ditulis oleh tim Spleeter secara eksplisit menyebut source code dan pretrained models didistribusikan di bawah MIT. README repositori Spleeter yang sekarang hanya menyebut kode MIT, dan isu klarifikasi #959 masih terbuka tanpa jawaban maintainer pada saat pemeriksaan. Repositori konversi ONNX juga menyertakan NOTICE yang mengutip pernyataan paper dan teks MIT untuk bobot.

Ini merupakan dasar yang lebih menjanjikan daripada HTDemucs, tetapi perbedaan antara paper dan README serta isu yang belum dijawab tetap perlu ditinjau. Jangan menganggapnya sebagai nasihat hukum atau menghapus kewajiban memeriksa izin atas audio input.

Sumber:
- [Paper Spleeter](https://github.com/deezer/spleeter/blob/master/paper.md)
- [README Spleeter](https://github.com/deezer/spleeter)
- [Isu lisensi model #959](https://github.com/deezer/spleeter/issues/959)
- [Repositori konversi Spleeter ONNX](https://github.com/matthewbae/spleeter-onnx-models)
- [NOTICE konversi dan bobot](https://github.com/matthewbae/spleeter-onnx-models/blob/main/NOTICE)

## Paket ONNX yang tersedia

Repositori `matthewbae/spleeter-onnx-models` menyediakan release `spleeter-5stems-onnx-v1` dengan lima model FP16:
- `spleeter-5stem-vocals-logits-fp16.onnx`
- `spleeter-5stem-drums-logits-fp16.onnx`
- `spleeter-5stem-bass-logits-fp16.onnx`
- `spleeter-5stem-piano-logits-fp16.onnx`
- `spleeter-5stem-other-logits-fp16.onnx`

Ukuran gabungan yang dinyatakan penerbit: 98,311,836 byte. File checksum tersedia di `5STEMS_CHECKSUMS.txt`. Model dibuat dari checkpoint resmi Spleeter v1.4.0, dan masing-masing graph menerima input float32 dengan kontrak `[1, 2, 512, 1024]`, menghasilkan pre-softmax logits.

[Release ONNX 5-stem](https://github.com/matthewbae/spleeter-onnx-models/releases/tag/spleeter-5stems-onnx-v1)

## Rancangan inferensi C++

1. Decode audio ke PCM float32 stereo 44,1 kHz.
2. Lakukan STFT periodic Hann, `n_fft=4096`, `hop=1024`.
3. Ambil 1024 bin pertama dan bentuk blok 512 frame.
4. Jalankan lima ONNX model secara berurutan agar tidak perlu menahan lima sesi inferensi sekaligus.
5. Simpan lima logits, lalu lakukan joint softmax sesuai Spleeter.
6. Bentuk power-2 ratio mask, dengan epsilon.
7. Perluas mask ke 2049 bin; mode `average` pada konversi ini mengisi bagian frekuensi atas dengan rata-rata per frame, bukan memotong frekuensi di atas 11 kHz.
8. Kalikan mask dengan STFT kompleks input dan jalankan iSTFT weighted overlap-add.
9. Tulis lima WAV.

Penting: logits dari lima graph harus dinormalisasi bersama. Memproses setiap stem dengan softmax sendiri-sendiri akan salah.

## Runtime Android

Dokumentasi resmi ONNX Runtime menyatakan AAR Android berisi `libonnxruntime.so` dan header C/C++ yang dapat dipakai langsung dari proyek NDK. Ini cocok dengan pola produk: JNI C++ dipanggil oleh kelas Java DEX, dan Lua tetap mengelola UI CSR.

- [ONNX Runtime — instalasi Android C/C++](https://onnxruntime.ai/docs/install/)
- [ONNX Runtime — build Android](https://onnxruntime.ai/docs/build/android.html)

## Risiko dan pekerjaan sebelum memilih

- Belum ada benchmark pada Redmi 13C / Android 16.
- Belum dipastikan semua operator FP16 berjalan pada CPU ARM64 yang tersedia; mungkin perlu graph FP32 atau konversi yang teruji.
- Perlu membangun/menyertakan ONNX Runtime ARM64 dan mengaudit lisensinya.
- Perlu menguji konsumsi RAM, panas, durasi, dan respons pembaca layar.
- Kualitas Spleeter tidak boleh diasumsikan setara Demucs.
- Tidak ada stem gitar khusus.
- Rebab, suling, gender, saron, bonang dan instrumen Jawa lain tetap perlu diuji; tidak ada jaminan rebab akan masuk ke stem yang diinginkan.

## Keputusan

Spleeter 5-stem adalah kandidat yang layak untuk prototipe bila pengguna menerima lima stem dan batas kualitasnya. Jika enam stem termasuk gitar merupakan syarat mutlak, kandidat ini tidak menggantikan Demucs 6S; kita perlu mencari model enam stem dengan hak bobot yang jelas atau mendapatkan izin yang sesuai.


## Pemeriksaan kandidat enam-stem: BS-RoFormer SW

Kandidat lain yang ditemukan adalah BS-RoFormer SW enam-stem. Urutan keluarannya sama dengan target awal: bass, drums, other, vocals, guitar, piano. Ada ekspor ONNX FP16 sekitar 336 MB dan FP32 sekitar 669 MB. Kontrak model memakai STFT eksternal dan potongan audio tetap sekitar empat detik; dimensi waktu tidak benar-benar dinamis.

Namun **model ini tidak lolos pemeriksaan lisensi bobot**:
- Model card ONNX menampilkan metadata MIT, tetapi teks README-nya sendiri menjelaskan bahwa bobot pretrained berasal dari rehost `jarredou/BS-ROFO-SW-Fixed` tanpa lisensi yang dinyatakan.
- Pemilik rehost ONNX menyatakan tidak terlibat dalam pelatihan dan tidak memiliki informasi provenance bobot.
- Diskusi teknis tentang provenance menyatakan tidak ada lisensi atau asal pelatih yang dapat diverifikasi; lisensi MIT arsitektur tidak memberikan hak otomatis atas bobot tersebut.

Karena itu, label MIT pada metadata repositori ekspor tidak cukup untuk menyimpulkan bahwa bobotnya boleh digunakan. Kandidat ini tidak diaktifkan.

Sumber yang diperiksa:
- [Model card BS-RoFormer SW 6-stem ONNX](https://huggingface.co/elicwhite/bs-roformer-sw-6stem-onnx)
- [Catatan provenance dan lisensi BS-RoFormer SW](https://github.com/openmirlab/bs-roformer-infer/issues/3)
- [Panduan kebutuhan model BS-RoFormer pada INTERSECT](https://tucktuckg00se.github.io/INTERSECT/stem-separation/)

### Kecocokan perangkat

Dokumentasi implementasi desktop yang diperiksa menyebut kebutuhan sekitar 6 GB RAM bebas untuk BS-RoFormer SW dan sekitar 11 menit CPU untuk lagu tiga menit pada PC desktop 12-core. Ini bukan benchmark Android, tetapi sudah cukup untuk menandai risiko besar pada ponsel dengan RAM fisik total 6 GB. Ukuran bobot 336 MB juga jauh lebih besar daripada kandidat Spleeter 5-stem sekitar 98 MB.

Kesimpulan: BS-RoFormer cocok secara jumlah stem, tetapi saat ini terhalang hak bobot yang tidak jelas dan risiko memori/performa. Jangan jadikan backend perangkat sasaran sebelum izin dan benchmark nyata tersedia.
