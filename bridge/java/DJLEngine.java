import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.system.Os;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java bridge loaded by the CSR extension through DexClassLoader.
 * Keep the class in the default package: the native build registers on DJLEngine.
 *
 * Lua calls muat(String), mulai(String,String,String), batal(String,String),
 * and hidup(String). Long work runs on this class's own Java worker thread.
 */
public final class DJLEngine {
    public interface ProgressListener {
        boolean onProgress(float fraction, String stage);
    }

    private static final Map<String, Thread> WORKERS = new HashMap<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean libraryLoaded;
    private static volatile String modelPath;

    private DJLEngine() {
    }

    private static native String nativeLoadModel(String path);
    private static native String nativePrepareInput(
            String raw, int sampleRate, int channels, String out,
            ProgressListener listener);
    private static native double[] nativeAnalyze(String path);
    private static native double[] nativeSeparate(
            String prepared, String outDir, boolean[] write, int threads,
            int computeThreads, boolean gpu, ProgressListener listener);
    private static native String nativeComputeInfo(int computeThreads, boolean gpu);
    private static native void nativeCancel();

    /** Matches the exact one-String method invoked by MD.siapkan() in the reference Lua. */
    public static synchronized String muat(String soPath) {
        try {
            if (soPath == null || soPath.length() == 0) {
                return "GALAT|Lokasi pustaka native kosong";
            }
            if (!libraryLoaded) {
                System.load(soPath);
                libraryLoaded = true;
            }
            File so = new File(soPath);
            File parent = so.getParentFile();
            if (parent == null) {
                return "GALAT|Folder mesin tidak ditemukan";
            }
            modelPath = new File(parent, "5c90dfd2.safetensors").getAbsolutePath();
            String info = nativeComputeInfo(1, false);
            return "OK|" + (info == null ? "Mesin C++ siap" : info);
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    /** Copy the separately downloaded model into the private engine folder without loading it into RAM. */
    public static synchronized String pasangModel(String sourcePath) {
        if (modelPath == null || sourcePath == null || sourcePath.length() == 0) {
            return "GALAT|Lokasi model belum ditentukan";
        }
        File source = new File(sourcePath);
        if (!source.isFile()) {
            return "GALAT|Berkas model tidak ditemukan";
        }
        File target = new File(modelPath);
        if (source.getAbsolutePath().equals(target.getAbsolutePath())) {
            try {
                verifyModel(modelPath);
                return "OK|Model terverifikasi";
            } catch (Throwable error) {
                return "GALAT|" + message(error);
            }
        }
        File temporary = new File(modelPath + ".part");
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return "GALAT|Folder model tidak bisa dibuat";
            }
            try (java.io.BufferedInputStream input =
                         new java.io.BufferedInputStream(new java.io.FileInputStream(source), 1024 * 1024);
                 BufferedOutputStream output =
                         new BufferedOutputStream(new FileOutputStream(temporary, false), 1024 * 1024)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            verifyModel(temporary.getAbsolutePath());
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            return "OK|Model berhasil disalin dan checksum cocok";
        } catch (Throwable error) {
            temporary.delete();
            return "GALAT|" + message(error);
        }
    }

    /** Matches the three-String asynchronous entry point called by MD.mulai(). */
    public static String mulai(final String audioPath, final String workDir, final String id) {
        if (!libraryLoaded) {
            return "GALAT|Mesin native belum dimuat";
        }
        if (audioPath == null || workDir == null || id == null
                || audioPath.length() == 0 || workDir.length() == 0 || id.length() == 0) {
            return "GALAT|Parameter pekerjaan tidak lengkap";
        }
        if (modelPath == null || !new File(modelPath).isFile()) {
            return "GALAT|Model 5c90dfd2.safetensors belum ditemukan di folder mesin";
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return "GALAT|Pekerjaan pemisahan lain masih berjalan";
        }

        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                kerjakan(audioPath, workDir, id);
            }
        }, "VocalRemover-" + id);

        synchronized (WORKERS) {
            WORKERS.put(id, worker);
        }
        try {
            worker.start();
            return "OK";
        } catch (Throwable error) {
            RUNNING.set(false);
            synchronized (WORKERS) {
                WORKERS.remove(id);
            }
            return "GALAT|" + message(error);
        }
    }

    /** Matches the two-String cancellation method called by MD.batal(). */
    public static String batal(String workDir, String id) {
        try {
            File cancel = new File(workDir, "batal_" + id + ".flag");
            File parent = cancel.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream stream = new FileOutputStream(cancel, false)) {
                stream.write("cancel".getBytes(StandardCharsets.UTF_8));
            }
            if (libraryLoaded) {
                nativeCancel();
            }
            return "OK";
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    /** Matches the one-String liveness check called by MD.hidup(). */
    public static String hidup(String id) {
        synchronized (WORKERS) {
            Thread worker = WORKERS.get(id);
            return worker != null && worker.isAlive() ? "1" : "0";
        }
    }

    private static void kerjakan(String audioPath, String workDir, String id) {
        File root = new File(workDir);
        File status = new File(root, "status_" + id + ".txt");
        File cancel = new File(root, "batal_" + id + ".flag");
        File raw = new File(root, "audio_" + id + ".f32");
        File prepared = new File(root, "siap_" + id + ".f32");
        File output = new File(root, "stem_" + id);

        try {
            if (!root.exists() && !root.mkdirs()) {
                throw new IOException("Folder kerja tidak bisa dibuat");
            }
            cancel.delete();
            writeStatus(status, "jalan", 0, "Membuka audio", "Menyiapkan pekerjaan");
            checkCancelled(cancel);

            verifyModel(modelPath);
            String modelError = nativeLoadModel(modelPath);
            if (modelError != null && modelError.length() > 0) {
                throw new IllegalStateException(modelError);
            }
            checkCancelled(cancel);

            writeStatus(status, "jalan", 1, "Mendekode audio", "Membaca sumber audio");
            AudioInfo info = decodeToFloat32(audioPath, raw, status, cancel);
            if (info.sampleRate < 8000 || info.channels < 1) {
                throw new IOException("Format audio hasil decode tidak valid");
            }
            checkCancelled(cancel);

            ProgressListener prepareProgress = new ProgressListener() {
                private long lastWrite;
                @Override
                public boolean onProgress(float fraction, String stage) {
                    if (cancel.exists()) {
                        return false;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastWrite >= 500 || fraction >= 1f) {
                        int percent = 15 + Math.round(clamp(fraction) * 15f);
                        writeStatus(status, "jalan", percent, "Menyiapkan audio", safeStage(stage));
                        lastWrite = now;
                    }
                    return true;
                }
            };

            writeStatus(status, "jalan", 15, "Menyiapkan audio", "Mengubah audio ke format mesin");
            String prepareError = nativePrepareInput(
                    raw.getAbsolutePath(), info.sampleRate, info.channels,
                    prepared.getAbsolutePath(), prepareProgress);
            if (prepareError != null && prepareError.length() > 0) {
                throw new IllegalStateException(prepareError);
            }
            checkCancelled(cancel);

            if (!output.exists() && !output.mkdirs()) {
                throw new IOException("Folder hasil tidak bisa dibuat");
            }

            ProgressListener separationProgress = new ProgressListener() {
                private long lastWrite;
                @Override
                public boolean onProgress(float fraction, String stage) {
                    if (cancel.exists()) {
                        return false;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastWrite >= 500 || fraction >= 1f) {
                        int percent = 30 + Math.round(clamp(fraction) * 70f);
                        writeStatus(status, "jalan", percent, "Memisahkan audio", safeStage(stage));
                        lastWrite = now;
                    }
                    return true;
                }
            };

            writeStatus(status, "jalan", 30, "Memisahkan audio", "Mesin Demucs sedang bekerja");
            boolean[] write = new boolean[] { true, true, true, true, true, true };
            int computeThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            nativeSeparate(prepared.getAbsolutePath(), output.getAbsolutePath(),
                    write, 1, computeThreads, false, separationProgress);
            checkCancelled(cancel);

            writeResult(new File(root, "hasil_" + id + ".json"), output);
            writeStatus(status, "selesai", 100, "Selesai", "Enam stem audio berhasil dibuat");
        } catch (CancellationException cancelled) {
            writeStatus(status, "batal", 0, "Dibatalkan", "Pekerjaan dihentikan atas permintaan pengguna");
            deleteTree(output);
            new File(root, "hasil_" + id + ".json").delete();
        } catch (Throwable error) {
            writeStatus(status, "galat", 0, "Gagal", message(error));
            deleteTree(output);
            new File(root, "hasil_" + id + ".json").delete();
        } finally {
            raw.delete();
            prepared.delete();
            cancel.delete();
            synchronized (WORKERS) {
                WORKERS.remove(id);
            }
            RUNNING.set(false);
        }
    }

    private static final class AudioInfo {
        final int sampleRate;
        final int channels;

        AudioInfo(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    /** Decode compressed audio to interleaved little-endian float32 without retaining the song in RAM. */
    private static AudioInfo decodeToFloat32(
            String inputPath, File rawFile, File status, File cancel) throws Exception {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        BufferedOutputStream output = null;
        boolean codecStarted = false;
        int track = -1;
        int sampleRate = 0;
        int channels = 0;
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        long durationUs = 0;
        long lastStatus = 0;
        long bytesWritten = 0;
        long lastActivity = System.currentTimeMillis();

        try {
            extractor.setDataSource(inputPath);
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    inputFormat = candidate;
                    break;
                }
            }
            if (track < 0 || inputFormat == null) {
                throw new IOException("Tidak ditemukan jalur audio pada berkas");
            }

            sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION);
            }

            extractor.selectTrack(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            codecStarted = true;
            output = new BufferedOutputStream(new FileOutputStream(rawFile, false), 1024 * 1024);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            boolean outputFormatKnown = false;

            while (!outputDone) {
                checkCancelled(cancel);

                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Buffer input decoder tidak tersedia");
                        }
                        inputBuffer.clear();
                        int size = extractor.readSampleData(inputBuffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        lastActivity = System.currentTimeMillis();
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0
                            && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        ByteBuffer decoded = codec.getOutputBuffer(outputIndex);
                        if (decoded == null) {
                            throw new IOException("Buffer output decoder tidak tersedia");
                        }
                        if (!outputFormatKnown) {
                            MediaFormat outputFormat = codec.getOutputFormat();
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                            if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                            }
                            outputFormatKnown = true;
                        }
                        bytesWritten += writeFloatPcm(decoded, bufferInfo, pcmEncoding, output);
                        long now = System.currentTimeMillis();
                        if (now - lastStatus >= 400) {
                            int percent = durationUs > 0
                                    ? 1 + (int) Math.min(13, (bufferInfo.presentationTimeUs * 14L) / durationUs)
                                    : 5;
                            writeStatus(status, "jalan", percent, "Mendekode audio",
                                    "Audio sedang dibaca dan dikonversi");
                            lastStatus = now;
                        }
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    lastActivity = System.currentTimeMillis();
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    int newRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int newChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int newEncoding = outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                    if (bytesWritten > 0 && (newRate != sampleRate
                            || newChannels != channels || newEncoding != pcmEncoding)) {
                        throw new IOException("Format PCM decoder berubah di tengah audio");
                    }
                    sampleRate = newRate;
                    channels = newChannels;
                    pcmEncoding = newEncoding;
                    outputFormatKnown = true;
                    lastActivity = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - lastActivity > 60000) {
                    throw new IOException("Decoder tidak merespons selama 60 detik");
                }
            }

            output.flush();
            if (bytesWritten == 0) {
                throw new IOException("Audio kosong atau tidak dapat didekode");
            }
            return new AudioInfo(sampleRate, channels);
        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) { }
            }
            if (codec != null) {
                if (codecStarted) {
                    try { codec.stop(); } catch (Exception ignored) { }
                }
                try { codec.release(); } catch (Exception ignored) { }
            }
            try { extractor.release(); } catch (Exception ignored) { }
        }
    }

    private static long writeFloatPcm(
            ByteBuffer source, MediaCodec.BufferInfo info, int encoding,
            BufferedOutputStream output) throws IOException {

        ByteBuffer data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.position(info.offset);
        data.limit(info.offset + info.size);

        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            if ((info.size & 3) != 0) {
                throw new IOException("Ukuran PCM float tidak valid");
            }
            byte[] bytes = new byte[info.size];
            data.get(bytes);
            output.write(bytes);
            return bytes.length;
        }

        if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            ShortBuffer samples = data.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            ByteBuffer converted = ByteBuffer.allocate(samples.remaining() * 4)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (samples.hasRemaining()) {
                converted.putFloat(samples.get() / 32768.0f);
            }
            output.write(converted.array(), 0, converted.position());
            return converted.position();
        }

        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            ByteBuffer converted = ByteBuffer.allocate(data.remaining() * 4)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (data.hasRemaining()) {
                converted.putFloat(((data.get() & 0xff) - 128) / 128.0f);
            }
            output.write(converted.array(), 0, converted.position());
            return converted.position();
        }

        if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
            if (data.remaining() % 3 != 0) {
                throw new IOException("Ukuran PCM 24-bit tidak valid");
            }
            ByteBuffer converted = ByteBuffer.allocate((data.remaining() / 3) * 4)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (data.remaining() >= 3) {
                int value = (data.get() & 0xff)
                        | ((data.get() & 0xff) << 8)
                        | (data.get() << 16);
                converted.putFloat(value / 8388608.0f);
            }
            output.write(converted.array(), 0, converted.position());
            return converted.position();
        }

        if (encoding == AudioFormat.ENCODING_PCM_32BIT) {
            if ((data.remaining() & 3) != 0) {
                throw new IOException("Ukuran PCM 32-bit tidak valid");
            }
            ByteBuffer converted = ByteBuffer.allocate(data.remaining())
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (data.remaining() >= 4) {
                converted.putFloat(data.getInt() / 2147483648.0f);
            }
            output.write(converted.array(), 0, converted.position());
            return converted.position();
        }

        throw new IOException("Format PCM decoder belum didukung: " + encoding);
    }


    private static void verifyModel(String path) throws Exception {
        final String expected = "d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411";
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.FileInputStream input = new java.io.FileInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) {
            actual.append(String.format("%02x", value & 0xff));
        }
        if (!expected.equals(actual.toString())) {
            throw new IOException("Checksum model tidak cocok. Unduh ulang 5c90dfd2.safetensors.");
        }
    }

    private static void writeStatus(File target, String state, int percent,
                                    String stage, String message) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File temporary = new File(target.getAbsolutePath() + ".tmp");
            String line = clean(state) + "|" + Math.max(0, Math.min(100, percent))
                    + "|" + clean(stage) + "|" + clean(message) + "|"
                    + System.currentTimeMillis();
            try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
                stream.write(line.getBytes(StandardCharsets.UTF_8));
            }
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
        } catch (Throwable ignored) {
        }
    }

    private static void writeResult(File file, File outputDirectory) throws IOException {
        String[] names = { "drums", "bass", "other", "vocals", "guitar", "piano" };
        StringBuilder json = new StringBuilder("{\"v\":1,\"outputs\":{");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) json.append(',');
            File stem = new File(outputDirectory, names[i] + ".wav");
            json.append('\"').append(names[i]).append("\":\"")
                    .append(jsonEscape(stem.getAbsolutePath())).append('\"');
        }
        json.append("}}");
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static String safeStage(String value) {
        return value == null || value.length() == 0 ? "Memproses audio" : clean(value);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return clean(value == null || value.length() == 0
                ? error.getClass().getSimpleName() : value);
    }

    private static void checkCancelled(File cancel) {
        if (cancel.exists()) {
            throw new CancellationException("Pekerjaan dibatalkan");
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        file.delete();
    }
}
