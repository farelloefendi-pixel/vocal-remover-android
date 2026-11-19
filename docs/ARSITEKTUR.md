# Arsitektur Vocal Remover CSR — status Kim Vocal 2

## Produk

Ekstensi Jieshuo/CSR Android ARM64. Antarmuka dan pengelolaan tugas menggunakan Lua; Java menjadi penghubung Android dan ONNX Runtime. Mesin pemisahan final harus menjalankan Kim Vocal 2 MDX-Net ONNX asli.

Hasil produk hanya:
- `vocals.wav`
- `instrumental.wav`

HTDemucs enam stem yang masih ada di sebagian kode/build lama adalah peninggalan prototipe dan tidak boleh dipakai untuk menghasilkan keluaran produk.

## Alur target

```text
Jieshuo / CSR
  main.lua
    pemilih audio, status, pembatalan, aksesibilitas
       |
       v
  DJLBridge.lua
    pemuatan DEX/SO, worker, status dan hasil
       |
       v
  DJLEngine.dex
    decode audio Android
    resample stereo 44.1 kHz
    STFT MDX / Bluestein FFT 7680
    susun tensor [1,4,3072,256]
    inferensi ONNX Runtime
    iSTFT, overlap-add, kompensasi
    tulis vocals.wav
    instrumental = mix - vocals
       |
       v
  ONNX Runtime Android ARM64
    Kim_Vocal_2.onnx
```

Diagram di atas adalah arsitektur sasaran. Tidak semua tahap DSP sudah diimplementasikan.

## Kontrak model yang dipatok

- File: `Kim_Vocal_2.onnx`
- SHA-256: `ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b`
- Sample rate: 44.100 Hz stereo
- Node input/output: `input` / `output`
- Bentuk input/output: `[1,4,3072,256]`, float32
- `n_fft=7680`, `hop_length=1024`, `dim_f=3072`, `dim_t=256`

Empat bidang tensor adalah real kiri, imajiner kiri, real kanan, dan imajiner kanan. Pemrosesan harus mengikuti konvensi STFT/iSTFT MDX yang sesuai model.

## Status lapisan

### Lua
UI sudah diselaraskan dengan dua keluaran. Model masih dinonaktifkan di adapter sampai pipeline dan validasi siap.

### Java
Nama file model, checksum, dan smoke test tensor telah diarahkan ke Kim Vocal 2. Fungsi mulai pemisahan sengaja mengembalikan pesan belum selesai agar kode lama tidak berjalan secara keliru.

### Native dan workflow
Workflow baru membangun paket PPK Kim Vocal 2 dan menyertakan helper JNI audio lama untuk tahap penyiapan PCM saja. Pemanggilan inferensi Demucs tidak digunakan. Helper JNI tersebut masih perlu diganti dengan implementasi penyiapan audio yang berdiri sendiri. Jangan mengemas artefak workflow lama sebagai rilis.

## Kinerja dan aksesibilitas

- Inferensi dan I/O harus berjalan di worker latar depan/worker terkelola, bukan thread UI.
- Satu pekerjaan aktif, pembatalan kooperatif, dan pembersihan file sementara.
- Progres diumumkan secara terbatas agar pembaca layar tidak dibanjiri.
- Fokus Jieshuo/CSR harus tetap dapat dinavigasi dan diuji langsung pada perangkat sasaran.
- Target perangkat: Redmi 13C ARM64, RAM 6 GB. Kebutuhan RAM dan waktu proses belum diukur pada perangkat.

## Referensi

Lihat [REFERENSI_DAN_KRITERIA_UJI.md](REFERENSI_DAN_KRITERIA_UJI.md) untuk referensi Demucs, UVR, ONNX Runtime, ncnn, MUSDB18, Slakh2100, dan referensi musik Jawa.

## Kriteria sebelum rilis

1. FFT 7680 dan STFT/iSTFT diuji numerik terhadap implementasi referensi.
2. Tensor Kim Vocal 2 lolos inferensi nyata.
3. File 10 detik, 1 menit, dan beberapa menit diproses tanpa celah, duplikasi, atau pergeseran.
4. Dua WAV PCM valid, durasi sama dengan sumber setelah resampling yang terdefinisi, dan instrumental residual tidak mengalami clipping yang tidak terkendali.
5. Pembatalan menghapus keluaran parsial dan file sementara.
6. RAM, suhu, waktu, dan stabilitas diuji pada perangkat sasaran.
7. Navigasi pembaca layar diuji langsung.
8. Hak model, kode, pustaka, dan atribusi diverifikasi.
