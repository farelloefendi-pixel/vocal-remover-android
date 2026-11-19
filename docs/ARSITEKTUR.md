# Arsitektur Vocal Remover CSR — Kim Vocal 2

## Produk
Ekstensi Jieshuo/CSR Android ARM64. Antarmuka dan pengelolaan tugas menggunakan Lua; Java menghubungkan Android MediaCodec, ONNX Runtime, dan DSP. Model pemisahan adalah Kim Vocal 2 MDX-Net ONNX asli.

Hasil produk hanya:
- vocals.wav
- instrumental.wav

HTDemucs enam stem merupakan peninggalan prototipe dan tidak digunakan oleh jalur pemisahan aktif.

## Alur implementasi
```text
Jieshuo / CSR
  main.lua
    pemilih audio, status, pembatalan, akses hasil
       |
       v
  DJLBridge.lua
    pemuatan DEX/SO, status, unduhan dan verifikasi model
       |
       v
  DJLEngine.dex
    decode MediaCodec
    helper JNI menyiapkan PCM stereo 44,1 kHz
    STFT MDX / Bluestein FFT 7680
    nolkan tiga bin frekuensi terendah
    tensor [1,4,3072,256]
    inferensi ONNX Runtime
    iSTFT dan Hann weighted overlap-add 25%
    kompensasi vokal 1.009
    tulis vocals.wav
    instrumental = mix - vocals
       |
       v
  ONNX Runtime Android ARM64
    Kim_Vocal_2.onnx
```

Helper JNI dari proyek audio lama hanya dipakai untuk persiapan PCM/resampling. Fungsi pemisahan Demucs tidak dipanggil.

## Kontrak model
- File: Kim_Vocal_2.onnx
- SHA-256: ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b
- Sample rate: 44.100 Hz stereo
- Node input/output: input / output
- Bentuk tensor: [1,4,3072,256], float32
- n_fft=7680, hop_length=1024, dim_f=3072, dim_t=256
- Compensation: 1.009
- Tensor planes: real kiri, imajiner kiri, real kanan, imajiner kanan
- Tiga bin frekuensi pertama (0, 1, 2) diisi nol sebelum inferensi

## DSP dan rekonstruksi
- Bluestein FFT menangani panjang 7680 yang bukan pangkat dua.
- STFT memakai Hann periodik, center=True, dan padding refleksi setengah ukuran FFT.
- iSTFT merekonstruksi spektrum Hermitian dan menormalisasi envelope Hann kuadrat.
- Segmen input berukuran 261.120 sampel; overlap-add memakai overlap 25%, step 195.840, dan border 65.280.
- Vokal dikalikan compensation 1.009; instrumental dihitung dari mix dikurangi vokal.
- Dua WAV PCM16 divalidasi terhadap sample rate dan jumlah frame yang diharapkan.

Uji CI telah mencakup direct-DFT, round-trip STFT/iSTFT, dan cakupan overlap-add. Itu belum membuktikan kualitas pemisahan musik nyata atau ketepatan batas segmen pada semua durasi.

## Status lapisan
### Lua
UI menyediakan pemilihan audio, pengunduhan model satu kali, pemantauan progres, pembatalan, dan daftar dua keluaran. Navigasi pembaca layar tetap harus diuji pada Jieshuo/CSR di perangkat sasaran.

### Java dan ONNX Runtime
Java menjalankan pekerjaan pada worker, memeriksa checksum model, memeriksa node dan bentuk tensor, melakukan inferensi per segmen, menulis dua WAV, dan membersihkan file kerja setelah selesai atau gagal.

### Native
Library JNI lama masih dipakai hanya untuk menyiapkan PCM/resampling. Pemisahan vokal sepenuhnya dilakukan melalui Kim Vocal 2 ONNX Runtime dan DSP Java.

## Kinerja dan aksesibilitas
- Inferensi dan I/O berjalan di worker, bukan thread UI.
- Satu pekerjaan pemisahan aktif pada satu waktu.
- Pembatalan menggunakan flag pekerjaan dan RunOptions ONNX.
- Progres diumumkan secara berkala agar tidak membanjiri pembaca layar.
- Target perangkat Redmi 13C ARM64, RAM 6 GB; waktu, RAM puncak, suhu, dan respons UI belum diukur di perangkat nyata.

## Kriteria sebelum rilis
1. Uji pemisahan audio nyata untuk durasi pendek dan panjang.
2. Validasi sinkronisasi, durasi, header WAV, clipping, dan kualitas residual.
3. Uji pembatalan, pembersihan file sementara, dan pemulihan setelah gagal.
4. Uji instalasi dan pemuatan DEX/SO/ORT pada Redmi 13C.
5. Uji navigasi TalkBack/Jieshuo/CSR dan fokus UI.
6. Ukur RAM, suhu, baterai, dan waktu pemrosesan.
7. Uji musik Jawa: vokal dengan rebab, rebab solo, suling, gender, saron, bonang, gong, kendang, dan gamelan.
8. Arsipkan bukti izin model dan seluruh notice dependensi.