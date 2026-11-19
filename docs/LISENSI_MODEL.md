# Pemeriksaan lisensi model dan dependensi

Tanggal pemeriksaan: 19 November 2026.

## Ringkasan keputusan

**Bobot Demucs HTDemucs 6-stem tidak boleh diasumsikan berlisensi MIT.** Kode mesin C++ berlisensi MIT, tetapi bobot model merupakan aset terpisah. Pemelihara Demucs menyatakan secara langsung bahwa bobot tidak tercakup lisensi MIT dan hanya disediakan untuk tujuan ilmiah.

Karena proyek ini ditujukan untuk pemisahan musik pribadi dan belum ada dasar yang memastikan penggunaan tersebut termasuk tujuan ilmiah, pengunduhan otomatis dan penggunaan model HTDemucs ditahan sampai hak penggunaan yang sesuai jelas. Paket PPK tidak boleh dipromosikan sebagai rilis siap pakai dengan model tersebut.

## Bukti primer

1. Isu resmi Demucs [#327 — License of pre-trained models](https://github.com/facebookresearch/demucs/issues/327) menanyakan apakah bobot model ikut MIT.
2. Pemelihara Demucs, `adefossez`, menjawab pada 23 Mei 2022: bobot model tidak tercakup MIT dan hanya disediakan untuk tujuan ilmiah. [Komentar asli](https://github.com/facebookresearch/demucs/issues/327#issuecomment-1134828611).
3. Pertanyaan lanjutan pada Juni 2024 tentang bobot yang dikonversi/didistribusikan ulang tidak memperoleh jawaban dari pemelihara.
4. Halaman Hugging Face HTDemucs-6s saat ini tidak memberikan dasar lisensi baru yang mengesampingkan pernyataan pemelihara. Riwayatnya mencatat penghapusan metadata lisensi.
5. README repositori Demucs Android menyebut bobot 55 MB sebagai MIT, tetapi pernyataan itu bertentangan dengan jawaban pemelihara model di repositori Demucs asli. Untuk kehati-hatian, klaim README turunan tidak diperlakukan sebagai pemberian hak dari pemilik bobot.

## Pisahkan lisensi kode dan bobot

| Komponen | Lisensi / status |
|---|---|
| Mesin `jPaauwe/demucs-android` | MIT untuk kode |
| Port `demucs.cpp` | MIT untuk kode |
| Eigen | MPL 2.0 |
| Bobot `5c90dfd2.safetensors` HTDemucs 6S | Tidak tercakup MIT menurut pemelihara Demucs; dinyatakan hanya untuk tujuan ilmiah |
| Hak atas rekaman lagu yang diproses | Terpisah; pengguna harus memiliki izin yang sesuai |

## Dampak pada implementasi

- Kode C++/JNI dan adapter Lua dapat tetap menjadi prototipe pengembangan.
- Pengunduhan otomatis bobot HTDemucs dinonaktifkan sementara.
- Build PPK yang dihasilkan CI harus diberi label development/prototype, bukan rilis siap pakai.
- Jangan menyertakan bobot HTDemucs dalam repositori, PPK, atau tautan unduhan.
- Jangan menyatakan bobot HTDemucs berlisensi MIT.
- Sebelum mengaktifkan kembali model ini, diperlukan dasar hak yang sesuai untuk tujuan penggunaan yang dimaksud.

## Alternatif yang sedang dinilai

### Spleeter 5-stem

Paper resmi Spleeter di JOSS menyatakan bahwa source code dan model pretrained didistribusikan di bawah MIT. Paper juga menyebut lima keluaran: vocals, drums, bass, piano, dan other. Namun README repositori saat ini hanya menyebut kode MIT, dan beberapa isu lisensi model masih meminta konfirmasi maintainer. Ini kandidat yang menjanjikan, tetapi perlu audit lebih lanjut. Model ini tidak menyediakan stem gitar.

- [Paper resmi Spleeter](https://github.com/deezer/spleeter/blob/master/paper.md)
- [README Spleeter](https://github.com/deezer/spleeter)
- [Isu lisensi model #898](https://github.com/deezer/spleeter/issues/898)
- [Isu klarifikasi #959](https://github.com/deezer/spleeter/issues/959)

### BS-RoFormer SW 6-stem

Model ini cocok dengan jumlah enam stem yang diminta, tetapi bobot pretrained berasal dari checkpoint yang direhost tanpa lisensi dan provenance yang dapat diverifikasi. Metadata MIT pada satu ekspor ONNX tidak mengubah status bobot: README ekspor menyatakan bobot asli tidak memiliki lisensi yang dinyatakan, dan pemilik rehost tidak mengetahui siapa pelatih atau asal dataset. Lisensi MIT arsitektur dan pipeline konversi tidak otomatis mencakup bobot.

Selain itu, ukuran ONNX FP16 yang ditemukan sekitar 336 MB; panduan desktop yang diperiksa menyebut sekitar 6 GB RAM bebas untuk pemrosesan dan waktu CPU sekitar 11 menit untuk lagu tiga menit pada PC 12-core. Ini berisiko terlalu berat untuk ponsel dengan RAM fisik 6 GB.

- [Model card ONNX 6-stem](https://huggingface.co/elicwhite/bs-roformer-sw-6stem-onnx)
- [Diskusi provenance bobot](https://github.com/openmirlab/bs-roformer-infer/issues/3)
- [Panduan kebutuhan komputasi](https://tucktuckg00se.github.io/INTERSECT/stem-separation/)

### Open-Unmix UMX-HQ 4-stem

Bobot UMX-HQ di Zenodo menyediakan empat target: vocals, drums, bass, other. Namun halaman record Zenodo yang diperiksa tidak menampilkan nilai lisensi yang tegas. Beberapa model card ONNX pihak ketiga menyebut MIT, tetapi itu belum sama dengan bukti pemberian lisensi primer dari pemilik bobot. Karena itu UMX-HQ masih kandidat yang perlu diverifikasi, bukan model yang sudah disetujui. Kualitas dan kelayakan port Android C++/ONNX juga harus diuji. Tidak ada stem piano atau gitar tersendiri.

- [Record bobot UMX-HQ di Zenodo](https://zenodo.org/records/3370489)
- [Open-Unmix model documentation](https://sigsep.github.io/open-unmix/)
- [Contoh model card ONNX pihak ketiga yang menyebut MIT](https://huggingface.co/edgetools/umx-hq) (bukan bukti primer dari pemilik bobot)

## Pemeriksaan tambahan atas Spleeter

Paper JOSS Spleeter menyatakan source code dan pretrained models didistribusikan di bawah MIT. Namun README repositori saat ini hanya menyebut kode MIT. Issue #959, dibuka pada 13 September 2026, meminta konfirmasi apakah bobot pretrained juga tercakup MIT, termasuk untuk penggunaan komersial; pada pemeriksaan ini belum ada jawaban maintainer yang menyelesaikan perbedaan tersebut.

Repositori konversi ONNX pihak ketiga menuliskan argumen bahwa paper JOSS merupakan dasar lisensi bobot, tetapi itu tetap merupakan interpretasi penerbit konversi, bukan klarifikasi baru dari Deezer. Untuk itu Spleeter 5-stem tetap kandidat yang lebih menjanjikan, belum dinyatakan lolos audit lisensi final.

- [Paper JOSS Spleeter](https://github.com/deezer/spleeter/blob/master/paper.md)
- [README resmi Spleeter](https://github.com/deezer/spleeter)
- [Issue klarifikasi lisensi #959](https://github.com/deezer/spleeter/issues/959)
- [NOTICE konversi ONNX](https://github.com/matthewbae/spleeter-onnx-models/blob/main/NOTICE)

## Keputusan yang masih diperlukan

Belum ditemukan model enam stem alternatif yang sekaligus:
- mempunyai izin bobot yang jelas untuk tujuan penggunaan yang dimaksud;
- memiliki kualitas yang sesuai target;
- dapat dijalankan secara offline di perangkat ARM64 dengan RAM 6 GB.

Sebelum mengganti mesin atau mengaktifkan model apa pun, tentukan apakah target harus tetap enam stem, atau boleh turun sementara menjadi lima stem Spleeter / empat stem Open-Unmix.
