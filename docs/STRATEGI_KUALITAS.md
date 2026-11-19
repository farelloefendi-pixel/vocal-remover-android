# Strategi kualitas dan arsitektur pemisahan

Status: jalur inferensi streaming dan writer tujuh WAV sudah ditulis dalam Java; belum dibuktikan melalui build CI terbaru atau uji runtime pada perangkat. Model tetap dikunci di adapter Lua sampai dasar lisensi bobot dipastikan.

## Sasaran produk

Produk harus menyediakan:
- enam stem penuh dan tersinkron: vocals, drums, bass, other, guitar, piano;
- satu berkas instrumental penuh yang menggabungkan drums + bass + other + guitar + piano, tanpa memasukkan vocals;
- pemrosesan seluruh durasi sumber, termasuk lagu lima menit atau lebih;
- keluaran dengan durasi dan posisi sampel yang sama, tanpa potongan segmen yang hilang;
- pemrosesan offline setelah model dan runtime terpasang.

## Temuan penting dari referensi model

Referensi inferensi HTDemucs 6S ONNX menetapkan input stereo float32 44,1 kHz dengan bentuk [1,2,343980] dan keluaran [1,6,2,343980]. Satu segmen adalah 7,8 detik. Implementasi referensi memakai overlap 25%, stride 257.985 sampel, jendela fade linear komplementer (0→1 di awal, 1→0 di akhir), dan normalisasi bobot overlap. Urutan keluaran adalah drums, bass, other, vocals, guitar, piano.

Implementasi Android yang ditulis mengikuti kontrak ini tanpa menampung seluruh lagu sebagai satu tensor. Worker mendekode audio bertahap, menyiapkan stereo 44,1 kHz, menjalankan satu segmen per giliran, melakukan overlap-add pada buffer bergulir, lalu menulis bagian yang sudah final ke WAV. Penggunaan RAM dan ketepatan blending masih harus diverifikasi dengan audio nyata.

Sumber referensi:
- https://huggingface.co/StemSplitio/htdemucs-6s-onnx/blob/main/infer.py
- https://huggingface.co/StemSplitio/htdemucs-6s-onnx

## Jangan menyamakan mode enam stem dengan mode karaoke

Model 6S memberikan enam kategori, tetapi penambahan kepala guitar dan piano mengubah perilaku stem other. Model card penerbit sendiri menyarankan model HTDemucs FT specialist untuk kualitas vokal/drum tertinggi dan model 6S bila pemisahan gitar/piano memang diperlukan. Ini adalah klaim dan angka benchmark penerbit, bukan jaminan untuk musik Indonesia.

Karena itu, arsitektur produk harus memisahkan dua sasaran:
1. **Mode Enam Stem:** satu model 6S menghasilkan keenam stem dalam satu sesi.
2. **Mode Karaoke:** jalur vocal-specialist khusus dapat dievaluasi untuk menghasilkan vocal stem yang lebih bersih; instrumental dibentuk dengan subtract vocal estimate dari campuran asli atau dengan metode rekonstruksi yang tervalidasi. Jangan sekadar menjumlahkan stem non-vokal dari model lain jika itu membuat timbre, fase, atau loudness berbeda.

Mode Karaoke tidak boleh dinyatakan lebih baik sebelum uji dengar buta dan pengukuran pada dataset berlisensi membuktikannya. Jika mode tambahan memerlukan model kedua, sediakan pengunduhan terpisah dan peringatan ruang penyimpanan; jangan memuat dua model sekaligus di RAM. Lepaskan sesi model pertama sebelum memuat model berikutnya.

## Pertimbangan memori dan kinerja Android

- Ukuran file bobot bukan ukuran RAM saat inferensi. Varian bobot FP16 dapat mengurangi ukuran unduhan tetapi referensi penerbit menyatakan kebutuhan RAM dan latensi inferensi tetap hampir sama karena komputasi berjalan float32.
- Angka sekitar 1,1 GB RAM per sesi yang dilaporkan penerbit pada Apple M4 Pro bukan benchmark Redmi 13C. Ukur peak RSS pada perangkat sasaran dengan Android 16, aplikasi CSR, dan screen reader tetap aktif.
- Implementasi awal memakai CPU execution provider, dua thread intra-op dan satu thread inter-op. Uji NNAPI sebagai opsi terpisah; gunakan hanya jika benar-benar didukung perangkat, stabil, dan hasil numeriknya setara. CPU fallback wajib tersedia.
- Jangan membuat enam array keluaran sepanjang lagu di RAM. Streaming overlap-add dan WAV writer harus digunakan.
- Batalkan pekerjaan secara kooperatif di antara segmen, tutup session, file, codec, dan hapus file sementara saat batal atau gagal.
- UI CSR tidak boleh melakukan inference atau I/O berat pada thread UI. Kemajuan diumumkan dengan interval terbatas agar screen reader tidak dibanjiri pengumuman.

## Berkas instrumental dan integritas audio

- Kode saat ini menulis keenam stem sebagai WAV PCM 16-bit stereo 44.100 Hz, dengan frame count sama seperti berkas PCM hasil resampling.
- Kode saat ini menjumlahkan lima stem non-vokal setelah overlap-add, lalu menulis instrumental PCM 16-bit. Nilai di luar rentang PCM16 dipotong; peak/headroom analysis dan limiter global masih menjadi pekerjaan kualitas berikutnya. Tidak ada normalisasi loudness independen per stem.
- Jika keluaran sumber awal memiliki sample rate lain, kembalikan semua keluaran ke sample rate sumber dengan resampler yang sama dan panjang sampel yang dikunci ke durasi sumber.
- Kode memeriksa nilai output non-finite dan frame count WAV. Pemeriksaan peak/clipping, keselarasan awal/akhir, dan pengukuran loudness masih harus ditambahkan sebelum rilis.
- Simpan file hasil sementara dengan nama sementara; ubah menjadi nama final hanya setelah seluruh file tervalidasi.

## Pengujian penerimaan

1. Tensor uji sintetis: bentuk input/output, urutan enam stem, nilai finite, dan inferensi CPU.
2. Audio 10 detik, 1 menit, 3 menit, dan 5 menit: durasi hasil, sambungan overlap, batas segmen terakhir, pembatalan, dan penggunaan memori.
3. Audio mono, stereo, sample rate 44,1 kHz dan non-44,1 kHz; multichannel harus ditangani eksplisit dan tidak boleh salah memetakan kanal.
4. Uji musik pop, dangdut/koplo, rebab solo, rebab dengan vokal, suling, gamelan, gender, saron, bonang, gong, dan campuran tradisional.
5. Uji instrumental: ukur kebocoran vokal; uji stem vocals: ukur kebocoran instrumen. Gunakan stem sumber asli berizin jika metrik SDR/SI-SDR dihitung.
6. Uji dengar buta oleh beberapa pendengar; nilai artefak, transien, fase, timbre, dan kealamian.
7. Uji perangkat: peak RAM, durasi proses, panas, baterai, stabilitas, ruang penyimpanan, serta aksesibilitas fokus CSR/Jieshuo.

## Status lisensi dan rilis

Label MIT pada halaman model ONNX tidak dengan sendirinya membuktikan bahwa seluruh bobot turunan memiliki izin komersial yang sah. Sebelum memasukkan bobot ke paket yang dijual, dapatkan konfirmasi tertulis dari pihak yang berwenang atas checkpoint asal dan konversinya, serta verifikasi kewajiban atribusi dan redistribusi. Jika tidak dapat dipastikan, jangan bundel atau mengunduh otomatis bobot tersebut untuk rilis komersial. Pilih model dengan provenance dan lisensi komersial jelas atau latih model sendiri menggunakan data yang memiliki hak penggunaan yang sesuai.

Rilis hanya setelah jalur inferensi nyata, keluaran audio lengkap, uji mutu, uji perangkat, dan pemeriksaan lisensi lulus.