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
