# Perbandingan kandidat model pemisah

Tanggal pemeriksaan: 19 November 2026.

Tujuan awal proyek tetap enam stem: vocals, drums, bass, other, guitar, dan piano. Mesin tidak boleh diganti dengan model lima atau empat stem tanpa persetujuan perubahan target.

## Matriks kandidat

| Kandidat | Keluaran | Ukuran bobot yang ditemukan | Status hak bobot | Kecocokan perangkat |
|---|---|---:|---|---|
| HTDemucs 6S | 6: vocals, drums, bass, other, guitar, piano | sekitar 55 MB | Pemelihara Demucs menyatakan bobot pretrained tidak tercakup MIT dan hanya untuk tujuan ilmiah | Mesin C++ ARM64 sudah berhasil dibangun; runtime ponsel belum diuji |
| BS-RoFormer SW | 6: vocals, drums, bass, other, guitar, piano | ONNX FP16 sekitar 336 MB | Tidak ada lisensi/provenance bobot yang dapat diverifikasi; metadata MIT pada ekspor tidak mencakup bobot asal secara otomatis | Risiko tinggi; dokumentasi desktop menyebut sekitar 6 GB RAM bebas dan sekitar 11 menit CPU untuk lagu 3 menit pada PC 12-core |
| Spleeter 5-stem ONNX | 5: vocals, drums, bass, piano, other | total sekitar 98.3 MB untuk lima graph FP16 | Paper JOSS menyebut pretrained models MIT; README kini hanya menyebut kode MIT dan issue klarifikasi #959 belum dijawab | Kandidat paling ringan/realistis untuk prototipe Android; belum diuji pada perangkat |
| Open-Unmix UMX-HQ | 4: vocals, drums, bass, other | sekitar 143.6 MB untuk empat bobot resmi | Ada petunjuk metadata Zenodo `mit-license` dari laporan pihak ketiga; perlu verifikasi API langsung dan arsip notice | Kandidat 4-stem, bukan pengganti target enam stem; belum diuji di ponsel |

## Keputusan teknis saat ini

1. Jangan aktifkan HTDemucs 6S hanya karena kode mesin MIT. Hak bobot merupakan hal terpisah.
2. Jangan gunakan BS-RoFormer SW sebagai pengganti enam stem sebelum ada bukti hak bobot dan benchmark memori yang layak untuk ponsel.
3. Spleeter 5-stem adalah opsi teknis paling ringan yang ditemukan, tetapi tidak memiliki stem gitar. Selain itu, perbedaan pernyataan lisensi di paper dan README masih perlu ditangani dengan hati-hati.
4. Jangan membuat stem gitar palsu dengan menyalin atau mengganti nama stem `other`. Itu akan menyesatkan pengguna dan tidak benar-benar memisahkan gitar.
5. Pertahankan target enam stem pada roadmap sampai pengguna menyetujui perubahan target atau ada model enam stem dengan hak bobot yang dapat dibuktikan.

## Dua jalur yang dapat dipilih setelah audit

- **Jalur A — tetap enam stem:** menunggu izin tertulis/hak penggunaan yang sesuai untuk HTDemucs 6S atau menemukan model enam stem lain yang lisensinya jelas dan layak dijalankan di Android.
- **Jalur B — prototipe lima stem:** menggunakan Spleeter 5-stem ONNX setelah lisensi bobot dinilai cukup jelas, dengan menerima bahwa gitar tidak dipisahkan sebagai stem tersendiri dan berada dalam `other`.

Tidak ada jalur yang diaktifkan otomatis. Sampai keputusan dan pengujian selesai, paket PPK tetap development-only dan tidak menghasilkan WAV.



## Penilaian mutu khusus produk komersial

Kualitas vokal dan kualitas enam stem adalah dua sasaran berbeda. Dokumentasi benchmark penerbit StemSplit untuk HTDemucs menunjukkan HTDemucs 6S mempunyai median SDR vokal sekitar 8,5 dB pada MUSDB18-HQ, sedangkan HTDemucs FT empat stem sekitar 9,19 dB. Penerbit juga menjelaskan bahwa 6S menambah kepala gitar dan piano sehingga karakter stem other berubah dan SDR vokal/other lebih rendah. Ini bukan hasil uji independen dan bukan jaminan kualitas pada lagu Indonesia, tetapi cukup untuk mencegah asumsi keliru bahwa model enam stem otomatis menjadi model penghapus vokal terbaik.

Sumber: https://github.com/StemSplit/demucs-onnx/blob/main/docs/models.md

Untuk produk ini, rencana evaluasi mutu harus mengukur dua keluaran terpisah:
1. **Karaoke/instrumental:** residu vokal pada instrumental serendah mungkin, tanpa merusak transien drum, bass, dan instrumen.
2. **Enam stem:** kebocoran vokal ke stem instrumen, kebocoran instrumen ke vocals, konsistensi fase/waktu, artefak, dan apakah keenam stem dapat dijumlahkan kembali mendekati campuran sumber.

Jangan menganggap stem guitar/piano yang terpisah sebagai bukti vokal sudah terhapus optimal. Jika evaluasi berlisensi kelak menunjukkan model vocal-specialist lebih bersih daripada model enam stem, arsitektur dapat menyediakan dua tahap atau dua mode: mode Karaoke berkualitas tinggi dan mode Pisahkan Enam Stem. Namun, model tambahan hanya boleh dipakai setelah hak komersial bobotnya terbukti, ukuran memori terukur, dan hasil gabungan tidak menimbulkan artefak atau ketidaksinkronan.

### Dataset uji mutu wajib

Gunakan hanya audio yang dibuat sendiri, direkam dengan izin, atau memiliki lisensi yang mengizinkan pengujian produk komersial. Siapkan pasangan campuran dan stem sumber asli agar hasil dapat dinilai secara objektif. Dataset harus mencakup:
- pop Indonesia: vokal pria/wanita, backing vocal, reverb, chorus, dan vokal berlapis;
- dangdut/koplo: kendang, bass, keyboard, gitar, suling, dan vokal;
- musik Jawa: rebab solo, rebab bersama vokal, suling, gender, saron, bonang, gong, dan campuran gamelan;
- kasus sulit: vokal tanpa jeda, vokal berbisik/rap, falsetto, suara manusia latar, serta instrumen yang spektrumnya menyerupai suara manusia.

### Kriteria penerimaan mutu

- Semua enam keluaran harus mempunyai durasi, sample rate, channel count, dan sinkronisasi yang sama.
- Tidak boleh ada segmen awal/akhir yang hilang, jeda buatan, klik sambungan, atau perubahan gain mendadak akibat overlap-add.
- Nilai SDR/SI-SDR dan leakage dihitung terhadap stem sumber pada dataset berlisensi; hasil dengar buta juga dilakukan karena metrik numerik tidak sepenuhnya menggambarkan artefak musik.
- Rebab/suling/gamelan solo tidak boleh secara sistematis dipindahkan ke vocals. Kasus rebab bersama penyanyi harus dinilai terpisah.
- Untuk mode Karaoke, bandingkan instrumental hasil pemisahan dengan sumber instrumental asli, bukan hanya menilai seberapa kecil suara penyanyi.
- Lakukan uji lagu 5 menit dan beberapa genre, bukan hanya potongan pendek.

Tidak ada model yang dinyatakan siap jual hanya berdasarkan benchmark penerbit atau hasil satu lagu. Perlu pengujian pada perangkat sasaran dan dataset yang mewakili penggunaan pelanggan.

## Gerbang rilis komersial

Rilis berbayar dilarang diaktifkan sebelum seluruh poin ini terpenuhi:
- izin komersial untuk kode, bobot, konversi ONNX, dan dependensi native telah didokumentasikan secara terpisah;
- provenance dan izin dataset pelatihan model dapat diterima, atau ada lisensi/izin eksplisit dari pemegang hak model;
- seluruh atribusi, notice, kewajiban redistribusi, dan batas penggunaan dipenuhi;
- tidak ada materi lagu contoh atau dataset uji tanpa izin yang disertakan dalam paket;
- pengujian mutu, stabilitas, keamanan file, performa, panas, dan aksesibilitas selesai.

Jika izin bobot enam stem tidak bisa diperoleh, jalur yang benar untuk produk komersial adalah mencari model dengan hak komersial tertulis atau melatih model sendiri dari dataset yang memang berlisensi untuk penggunaan tersebut. Mengubah format bobot menjadi ONNX tidak menghapus kewajiban lisensi model asal.

## Sumber primer dan teknis

- [Pernyataan pemelihara Demucs tentang bobot pretrained](https://github.com/facebookresearch/demucs/issues/327#issuecomment-1134828611)
- [Paper JOSS Spleeter](https://github.com/deezer/spleeter/blob/master/paper.md)
- [README resmi Spleeter](https://github.com/deezer/spleeter)
- [Issue klarifikasi lisensi bobot Spleeter #959](https://github.com/deezer/spleeter/issues/959)
- [Paket Spleeter 5-stem ONNX dan checksum](https://github.com/matthewbae/spleeter-onnx-models)
- [Record UMX-HQ di Zenodo](https://zenodo.org/records/3370489)
- [Laporan metadata API UMX-HQ](https://github.com/ContextualWisdomLab/bandscope/issues/1180) (petunjuk pihak ketiga, belum diverifikasi langsung)
- [Audit provenance BS-RoFormer SW](https://github.com/openmirlab/bs-roformer-infer/issues/3)
- [Model card BS-RoFormer SW 6-stem ONNX](https://huggingface.co/elicwhite/bs-roformer-sw-6stem-onnx)
- [Panduan kebutuhan komputasi BS-RoFormer](https://tucktuckg00se.github.io/INTERSECT/stem-separation/)


## Kandidat enam stem ONNX yang ditemukan pada pemeriksaan lanjutan

Kandidat teknis paling dekat dengan target enam stem adalah [StemSplitio HTDemucs 6S ONNX](https://huggingface.co/StemSplitio/htdemucs-6s-onnx). Model card menyediakan varian FP16-weights sekitar 136 MB dan keluaran enam stem: drums, bass, other, vocals, guitar, piano. Dokumentasi penerbit menyebut input stereo 44,1 kHz dalam segmen sekitar 7,8 detik dan overlap-add saat menyusun kembali hasil.

- [Model card dan berkas model](https://huggingface.co/StemSplitio/htdemucs-6s-onnx)
- [Dokumentasi model dan benchmark penerbit](https://github.com/StemSplit/demucs-onnx/blob/main/docs/models.md)
- [ONNX Runtime Android](https://onnxruntime.ai/docs/install/)
- [NNAPI Execution Provider](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider)

Penerbit menyatakan ekspor ONNX mereka MIT. Namun, metadata MIT pada repositori hasil ekspor bukan bukti mandiri bahwa hak bobot dasar dan data pelatihannya sudah mendapat izin yang sesuai. Ada sumber independen yang juga menyebut status hak bobot Demucs masih belum terselesaikan. Karena itu kandidat ini **belum diaktifkan, belum diunduh otomatis, dan belum dimasukkan ke PPK**.

Secara teknis, integrasi ONNX memerlukan perubahan nyata: menambahkan ONNX Runtime Android AAR dan pustaka ARM64, memuat model ONNX, membuat tensor input sesuai bentuk model, menjalankan inferensi per segmen, melakukan overlap-add, menulis enam WAV, serta menguji penggunaan memori dan waktu pada Redmi 13C. Mesin C++ Demucs yang ada tidak dapat menjalankan file ONNX hanya dengan mengganti nama model atau URL.

Angka performa yang dipublikasikan penerbit berasal dari Apple M4 Pro, bukan Redmi 13C. Angka tersebut tidak boleh dipakai sebagai jaminan performa ponsel sasaran. NNAPI tersedia pada Android, tetapi dukungan operator dan perangkat keras tetap perlu diuji; CPU fallback harus dipertahankan.

