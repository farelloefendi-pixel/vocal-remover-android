# Pemeriksaan lisensi model dan dependensi

Tanggal pemeriksaan: 19 November 2026.

## Keputusan produk saat ini

Model utama yang dipilih untuk proyek ini adalah **Kim Vocal 2 MDX-Net dalam format ONNX asli**, dengan keluaran hanya `vocals.wav` dan `instrumental.wav`. Pengguna menyatakan telah memperoleh izin tertulis dari pembuat model untuk penggunaan komersial pada **2 Oktober 2026**. Izin tertulis tersebut adalah dasar izin khusus yang harus disimpan bersama arsip rilis dan atribusi; metadata MIT pada repositori mirror tidak boleh dianggap sebagai satu-satunya bukti hak atas bobot.

Sebelum rilis, simpan bukti izin asli, pastikan izin itu secara eksplisit mencakup file/checkpoint Kim Vocal 2 yang digunakan, konversi ONNX, distribusi melalui ekstensi, dan penggunaan komersial yang dimaksud. Jangan mengganti model atau formatnya tanpa persetujuan pengguna.

HTDemucs 6-stem bukan lagi model produk. Audit lisensinya di bawah tetap dicatat sebagai pembelajaran historis dan alasan mengapa mesin lama tidak boleh dipakai.

## Bukti primer

1. Isu resmi Demucs [#327 — License of pre-trained models](https://github.com/facebookresearch/demucs/issues/327) menanyakan apakah bobot model ikut MIT.
2. Pemelihara Demucs, `adefossez`, menjawab pada 23 Mei 2022: bobot model tidak tercakup MIT dan hanya disediakan untuk tujuan ilmiah. [Komentar asli](https://github.com/facebookresearch/demucs/issues/327#issuecomment-1134828611).
3. Pertanyaan lanjutan pada Juni 2024 tentang bobot yang dikonversi/didistribusikan ulang tidak memperoleh jawaban dari pemelihara.
4. Repositori ekspor StemSplitio/htdemucs-6s-onnx saat ini menampilkan metadata License: mit dan model card menyatakan repositori ONNX berlisensi MIT, matching the original HT-Demucs. Ini adalah klaim penerbit ekspor yang relevan; namun klaim itu bertentangan dengan jawaban pemelihara Demucs pada issue #327 yang secara eksplisit mengecualikan bobot pretrained dari MIT. Metadata repositori ekspor tidak dengan sendirinya membuktikan bahwa penerbit memiliki hak untuk memberikan ulang lisensi atas bobot dasar.
5. README repositori Demucs Android menyebut bobot 55 MB sebagai MIT, tetapi pernyataan itu bertentangan dengan jawaban pemelihara model di repositori Demucs asli. Untuk kehati-hatian, klaim README turunan dan metadata ekspor pihak ketiga tidak diperlakukan sebagai pemberian hak yang pasti dari pemilik bobot.

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

Bobot UMX-HQ di Zenodo menyediakan empat target: vocals, drums, bass, other. Halaman record Zenodo yang dibuka menampilkan bagian Rights/License tetapi nilai lisensinya tidak tampak pada halaman tersebut. Ada laporan teknis pihak ketiga yang menyatakan metadata API record 3370489 berisi `metadata.license.id = mit-license`, dan model card ONNX lain juga menyebut bobot UMX-HQ sebagai MIT. Ini petunjuk kuat untuk ditindaklanjuti, tetapi nilai API harus diverifikasi langsung dan notice asli harus diarsipkan sebelum model diterima. UMX-HQ tetap tidak memenuhi target enam stem karena tidak memiliki piano atau gitar tersendiri. Kualitas dan kelayakan port Android C++/ONNX juga harus diuji.

- [Record bobot UMX-HQ di Zenodo](https://zenodo.org/records/3370489)
- [Open-Unmix model documentation](https://sigsep.github.io/open-unmix/)
- [Laporan pemeriksaan metadata API Zenodo](https://github.com/ContextualWisdomLab/bandscope/issues/1180) (laporan pihak ketiga; verifikasi langsung masih diperlukan)
- [Contoh model card ONNX yang menyebut MIT](https://huggingface.co/edgetools/umx-hq)

## Pemeriksaan tambahan atas Spleeter

Paper JOSS Spleeter menyatakan source code dan pretrained models didistribusikan di bawah MIT. Namun README repositori saat ini hanya menyebut kode MIT. Issue #959, dibuka pada 13 September 2026, meminta konfirmasi apakah bobot pretrained juga tercakup MIT, termasuk untuk penggunaan komersial; pada pemeriksaan ini belum ada jawaban maintainer yang menyelesaikan perbedaan tersebut.

Repositori konversi ONNX pihak ketiga menuliskan argumen bahwa paper JOSS merupakan dasar lisensi bobot, tetapi itu tetap merupakan interpretasi penerbit konversi, bukan klarifikasi baru dari Deezer. Untuk itu Spleeter 5-stem tetap kandidat yang lebih menjanjikan, belum dinyatakan lolos audit lisensi final.

- [Paper JOSS Spleeter](https://github.com/deezer/spleeter/blob/master/paper.md)
- [README resmi Spleeter](https://github.com/deezer/spleeter)
- [Issue klarifikasi lisensi #959](https://github.com/deezer/spleeter/issues/959)
- [NOTICE konversi ONNX](https://github.com/matthewbae/spleeter-onnx-models/blob/main/NOTICE)


### HTDemucs 6-stem ONNX dari StemSplitio

Repositori https://huggingface.co/StemSplitio/htdemucs-6s-onnx menyediakan dua file: ONNX FP32 sekitar 258 MB dan varian FP16-weights sekitar 136 MB. Model card menyebut enam keluaran (drums, bass, other, vocals, guitar, piano), input stereo 44,1 kHz dengan segmen 343.980 sampel (7,8 detik), serta menyatakan repositori MIT yang cocok dengan HT-Demucs asli. Skrip inferensi infer.py juga tersedia di repositori yang sama.

Pernyataan MIT ini belum menyelesaikan konflik hak bobot. Issue resmi Demucs #327 memuat jawaban pemelihara bahwa pretrained weights tidak tercakup MIT dan hanya untuk tujuan ilmiah. Sampai ada konfirmasi yang dapat mengatasi konflik tersebut atau izin tertulis yang sesuai, model ONNX ini tetap tidak diunduh, tidak dibundel, dan tidak diaktifkan. Ukuran 136 MB adalah ukuran unduhan, bukan kebutuhan RAM saat inferensi; penerbit melaporkan sekitar 1,1 GB RAM per segmen pada Apple M4 Pro, bukan hasil uji Android.

- Model card: https://huggingface.co/StemSplitio/htdemucs-6s-onnx
- Skrip inferensi: https://huggingface.co/StemSplitio/htdemucs-6s-onnx/blob/main/infer.py
- Issue lisensi bobot Demucs #327: https://github.com/facebookresearch/demucs/issues/327

## Keputusan yang sudah dikunci

- Kim Vocal 2 MDX-Net ONNX asli adalah model utama.
- Produk hanya menghasilkan vocals dan instrumental.
- HTDemucs, Spleeter, Open-Unmix, BS-RoFormer, dan model enam stem lain bukan pengganti otomatis; bagian di atas hanya catatan audit kandidat historis.
- Mesin HTDemucs lama tetap diblokir dan tidak boleh dijalankan dengan file Kim Vocal 2.
- Izin tertulis dari pembuat Kim Vocal 2 bertanggal 2 Oktober 2026 harus diarsipkan sebagai bukti utama sebelum paket komersial didistribusikan.
- Model dan pemisahan belum boleh diaktifkan sampai pipeline Kim Vocal 2 benar-benar selesai, lolos build, dan diuji.
