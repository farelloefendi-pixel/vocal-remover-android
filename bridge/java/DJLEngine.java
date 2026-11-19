import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.system.Os;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.TensorInfo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DJLEngine {
    public interface ProgressListener {
        boolean onProgress(float fraction, String stage);
    }

    private static final Map<String, Thread> WORKERS = new HashMap<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static final AtomicBoolean CANCEL_DOWNLOAD = new AtomicBoolean(false);
    private static volatile boolean libraryLoaded;
    private static volatile OrtEnvironment ortEnvironment;
    private static volatile String modelPath;
    private static volatile String verifiedModelPath;
    private static volatile long verifiedModelLength = -1;
    private static volatile long verifiedModelModified = -1;

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

    
    public static synchronized String muat(String soPath, String ortDirectory) {
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
            modelPath = new File(parent, "Kim_Vocal_2.onnx").getAbsolutePath();
            if (ortDirectory == null || ortDirectory.length() == 0) {
                return "GALAT|Folder pustaka ONNX Runtime kosong";
            }
            File ortFolder = new File(ortDirectory);
            File ortCore = new File(ortFolder, "libonnxruntime.so");
            File ortJni = new File(ortFolder, "libonnxruntime4j_jni.so");
            if (!ortCore.isFile() || !ortJni.isFile()) {
                return "GALAT|Dua pustaka native ONNX Runtime tidak lengkap";
            }
            System.setProperty("onnxruntime.native.onnxruntime.path", ortCore.getAbsolutePath());
            System.setProperty("onnxruntime.native.onnxruntime4j_jni.path", ortJni.getAbsolutePath());
            ortEnvironment = OrtEnvironment.getEnvironment();
            return "OK|ONNX Runtime Android siap; decoder dan resampler native tersedia";
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    
    public static String lokasiModel() {
        return modelPath == null ? "" : modelPath;
    }

    
    public static String modelSiap() {
        if (modelPath == null || !new File(modelPath).isFile()) {
            return "BELUM|Model belum diunduh";
        }
        try {
            verifyModel(modelPath);
            return "OK|Model siap dan checksum cocok";
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    
    public static String unduhModel(String url, String statusPath) {
        if (!libraryLoaded || modelPath == null) {
            return "GALAT|Mesin native belum dimuat";
        }
        if (RUNNING.get()) {
            return "GALAT|Pemisahan audio sedang berjalan";
        }
        if (!DOWNLOADING.compareAndSet(false, true)) {
            return "GALAT|Pengunduhan model lain masih berjalan";
        }

        CANCEL_DOWNLOAD.set(false);
        writeStatus(new File(statusPath), "jalan", 0,
                "Menghubungkan", "Menghubungkan ke server model");
        File target = new File(modelPath);
        File temporary = new File(modelPath + ".download");
        HttpURLConnection connection = null;
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Folder model tidak bisa dibuat");
            }
            temporary.delete();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "VocalRemover-CSR/1.0");
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Server model memberi HTTP " + code);
            }
            long expected = connection.getContentLengthLong();
            long received = 0;
            long lastWrite = 0;
            try (InputStream input = connection.getInputStream();
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(temporary, false), 1024 * 1024)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (CANCEL_DOWNLOAD.get()) {
                        throw new CancellationException("Pengunduhan model dibatalkan");
                    }
                    output.write(buffer, 0, count);
                    received += count;
                    long now = System.currentTimeMillis();
                    if (now - lastWrite >= 500) {
                        int percent = expected > 0
                                ? (int) Math.min(99, received * 100L / expected) : 0;
                        String detail = expected > 0
                                ? (received / 1048576L) + " dari " + (expected / 1048576L) + " MB"
                                : (received / 1048576L) + " MB diterima";
                        writeStatus(new File(statusPath), "jalan", percent,
                                "Mengunduh model", detail);
                        lastWrite = now;
                    }
                }
                output.flush();
            }
            if (CANCEL_DOWNLOAD.get()) {
                throw new CancellationException("Pengunduhan model dibatalkan");
            }
            writeStatus(new File(statusPath), "jalan", 99,
                    "Memeriksa model", "Memverifikasi SHA-256");
            verifyModel(temporary.getAbsolutePath());
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            verifiedModelPath = target.getAbsolutePath();
            verifiedModelLength = target.length();
            verifiedModelModified = target.lastModified();
            writeStatus(new File(statusPath), "selesai", 100,
                    "Model siap", "Model berhasil diunduh dan diverifikasi");
            return "OK|Model berhasil diunduh dan diverifikasi";
        } catch (CancellationException error) {
            temporary.delete();
            writeStatus(new File(statusPath), "batal", 0,
                    "Pengunduhan dibatalkan", message(error));
            return "BATAL|" + message(error);
        } catch (Throwable error) {
            temporary.delete();
            writeStatus(new File(statusPath), "galat", 0,
                    "Unduhan model gagal", message(error));
            return "GALAT|" + message(error);
        } finally {
            if (connection != null) {
                try { connection.disconnect(); } catch (Exception ignored) { }
            }
            DOWNLOADING.set(false);
            CANCEL_DOWNLOAD.set(false);
        }
    }

    public static String batalUnduhModel() {
        CANCEL_DOWNLOAD.set(true);
        return "OK";
    }

    
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
            verifiedModelPath = target.getAbsolutePath();
            verifiedModelLength = target.length();
            verifiedModelModified = target.lastModified();
            return "OK|Model berhasil disalin dan checksum cocok";
        } catch (Throwable error) {
            temporary.delete();
            return "GALAT|" + message(error);
        }
    }

    public static String infoMesin() {
        if (!libraryLoaded) return "Mesin native belum dimuat";
        if (ortEnvironment == null) return "ONNX Runtime belum siap";
        return "ONNX Runtime Android siap. Kontrak Kim Vocal 2 telah ditetapkan; pemrosesan audio lengkap belum diaktifkan.";
    }

    public static synchronized String ujiModelKimVocal2(String path) {
        if (ortEnvironment == null) return "GALAT|ONNX Runtime belum dimuat";
        if (path == null || path.length() == 0 || !new File(path).isFile()) {
            return "GALAT|Berkas Kim Vocal 2 ONNX tidak ditemukan";
        }
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            options.setMemoryPatternOptimization(true);
            try (OrtSession session = ortEnvironment.createSession(path, options)) {
                if (!session.getInputNames().contains("input")) {
                    return "GALAT|Input model bukan bernama input";
                }
                if (!session.getOutputNames().contains("output")) {
                    return "GALAT|Output model bukan bernama output";
                }
                TensorInfo inputInfo = (TensorInfo) session.getInputInfo().get("input").getInfo();
                TensorInfo outputInfo = (TensorInfo) session.getOutputInfo().get("output").getInfo();
                long[] inputShape = inputInfo.getShape();
                long[] outputShape = outputInfo.getShape();
                long[] expectedShape = new long[] { 1, 4, 3072, 256 };
                if (!java.util.Arrays.equals(inputShape, expectedShape)) {
                    return "GALAT|Bentuk input Kim Vocal 2 tidak sesuai kontrak [1,4,3072,256]";
                }
                if (!java.util.Arrays.equals(outputShape, expectedShape)) {
                    return "GALAT|Bentuk output Kim Vocal 2 tidak sesuai kontrak [1,4,3072,256]";
                }
                int values = 4 * 3072 * 256;
                ByteBuffer memory = ByteBuffer.allocateDirect(values * 4)
                        .order(ByteOrder.nativeOrder());
                FloatBuffer inputData = memory.asFloatBuffer();
                for (int i = 0; i < values; i++) inputData.put(0f);
                inputData.flip();
                try (OnnxTensor input = OnnxTensor.createTensor(
                            ortEnvironment, inputData, expectedShape)) {
                    Map<String, OnnxTensor> feeds = new HashMap<>();
                    feeds.put("input", input);
                    try (OrtSession.Result result = session.run(feeds)) {
                        OnnxTensor outputTensor = (OnnxTensor) result.get("output").orElse(null);
                        if (outputTensor == null) {
                            return "GALAT|Model tidak mengembalikan tensor output";
                        }
                        FloatBuffer outputData = outputTensor.getFloatBuffer();
                        if (outputData == null || outputData.remaining() != values) {
                            return "GALAT|Jumlah nilai output Kim Vocal 2 tidak sesuai";
                        }
                        for (int i = 0; i < values; i++) {
                            float value = outputData.get(i);
                            if (Float.isNaN(value) || Float.isInfinite(value)) {
                                return "GALAT|Output Kim Vocal 2 mengandung nilai tidak valid";
                            }
                        }
                        return "OK|Inferensi uji satu segmen Kim Vocal 2 berhasil; seluruh nilai output valid";
                    }
                }
            }
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    public static String mulai(final String audioPath, final String workDir, final String id) {
        if (!libraryLoaded) {
            return "GALAT|Mesin native belum dimuat";
        }
        if (audioPath == null || workDir == null || id == null
                || audioPath.length() == 0 || workDir.length() == 0 || id.length() == 0) {
            return "GALAT|Parameter pekerjaan tidak lengkap";
        }
        if (modelPath == null || !new File(modelPath).isFile()) {
            return "GALAT|Model Kim Vocal 2 ONNX belum ditemukan di folder mesin";
        }
        if (DOWNLOADING.get()) {
            return "GALAT|Model sedang diunduh";
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return "GALAT|Pekerjaan pemisahan lain masih berjalan";
        }

        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                } catch (Throwable ignored) {
                }
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
            OrtSession.RunOptions current = activeRunOptions;
            if (current != null) {
                try { current.setTerminate(true); } catch (Throwable ignored) { }
            }
            if (libraryLoaded) {
                nativeCancel();
            }
            return "OK";
        } catch (Throwable error) {
            return "GALAT|" + message(error);
        }
    }

    
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
            writeStatus(status, "jalan", 0, "Memeriksa model", "Memverifikasi model ONNX Kim Vocal 2");
            checkCancelled(cancel);
            verifyModel(modelPath);

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
                    if (cancel.exists()) return false;
                    long now = System.currentTimeMillis();
                    if (now - lastWrite >= 500 || fraction >= 1f) {
                        int percent = 15 + Math.round(clamp(fraction) * 15f);
                        writeStatus(status, "jalan", percent, "Menyiapkan audio", safeStage(stage));
                        lastWrite = now;
                    }
                    return true;
                }
            };

            writeStatus(status, "jalan", 15, "Menyiapkan audio", "Mengubah audio menjadi stereo 44,1 kHz");
            String prepareError = nativePrepareInput(
                    raw.getAbsolutePath(), info.sampleRate, info.channels,
                    prepared.getAbsolutePath(), prepareProgress);
            if (prepareError != null && prepareError.length() > 0) {
                if ("cancelled".equalsIgnoreCase(prepareError)) {
                    throw new CancellationException("Pekerjaan dibatalkan");
                }
                throw new IllegalStateException(prepareError);
            }
            checkCancelled(cancel);

            if (!output.exists() && !output.mkdirs()) {
                throw new IOException("Folder hasil tidak bisa dibuat");
            }
            separateOnnxStreaming(prepared, output, status, cancel);
            checkCancelled(cancel);

            writeResult(new File(root, "hasil_" + id + ".json"), output);
            writeStatus(status, "selesai", 100, "Selesai",
                    "Enam stem dan instrumental sepanjang audio berhasil dibuat");
        } catch (CancellationException cancelled) {
            writeStatus(status, "batal", 0, "Dibatalkan", "Pekerjaan dihentikan atas permintaan pengguna");
            deleteTree(output);
            new File(root, "hasil_" + id + ".json").delete();
        } catch (Throwable error) {
            if (cancel.exists()) {
                writeStatus(status, "batal", 0, "Dibatalkan",
                        "Pekerjaan dihentikan atas permintaan pengguna");
            } else {
                writeStatus(status, "galat", 0, "Gagal", message(error));
            }
            deleteTree(output);
            new File(root, "hasil_" + id + ".json").delete();
        } finally {
            raw.delete();
            prepared.delete();
            cancel.delete();
            activeRunOptions = null;
            synchronized (WORKERS) {
                WORKERS.remove(id);
            }
            RUNNING.set(false);
        }
    }

    private static final int ONNX_SAMPLES = 343980;
    private static final int ONNX_OVERLAP = ONNX_SAMPLES / 4;
    private static final int ONNX_STRIDE = ONNX_SAMPLES - ONNX_OVERLAP;
    private static final String[] STEM_NAMES = {
            "drums", "bass", "other", "vocals", "guitar", "piano"
    };
    private static volatile OrtSession.RunOptions activeRunOptions;

    private static final class WavPcm16Writer implements AutoCloseable {
        private final File file;
        private final BufferedOutputStream stream;
        private final byte[] bytes = new byte[ONNX_STRIDE * 4];
        private long frames;
        private boolean closed;

        WavPcm16Writer(File target) throws IOException {
            file = target;
            stream = new BufferedOutputStream(new FileOutputStream(target, false), 1 << 20);
            stream.write(new byte[44]);
        }

        void writeNormalized(float[] left, float[] right, float[] weights, int count)
                throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                float weight = Math.max(weights[i], 1e-8f);
                buffer.putShort(toPcm16(left[i] / weight));
                buffer.putShort(toPcm16(right[i] / weight));
            }
            stream.write(bytes, 0, buffer.position());
            frames += count;
        }

        void write(float[] left, float[] right, int count) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                buffer.putShort(toPcm16(left[i]));
                buffer.putShort(toPcm16(right[i]));
            }
            stream.write(bytes, 0, buffer.position());
            frames += count;
        }

        private static short toPcm16(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) value = 0f;
            value = Math.max(-1f, Math.min(1f, value));
            return (short) Math.round(value * 32767f);
        }

        long frameCount() {
            return frames;
        }

        void validate(long expectedFrames) throws IOException {
            long expectedBytes = 44L + expectedFrames * 4L;
            if (frames != expectedFrames || !file.isFile()
                    || file.length() != expectedBytes) {
                throw new IOException("Ukuran atau durasi WAV hasil tidak sesuai");
            }
            byte[] header = new byte[44];
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                raf.readFully(header);
            }
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            String riff = new String(header, 0, 4, StandardCharsets.US_ASCII);
            String wave = new String(header, 8, 4, StandardCharsets.US_ASCII);
            String data = new String(header, 36, 4, StandardCharsets.US_ASCII);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave) || !"data".equals(data)
                    || h.getInt(24) != 44100 || Integer.toUnsignedLong(h.getInt(40)) != expectedFrames * 4L) {
                throw new IOException("Header WAV hasil tidak valid");
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            stream.flush();
            stream.close();
            long dataBytes = frames * 4L;
            if (dataBytes > 0xffffffffL - 36L) {
                throw new IOException("Audio terlalu panjang untuk WAV PCM standar");
            }
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
            header.putInt((int) (36L + dataBytes));
            header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
            header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
            header.putInt(16);
            header.putShort((short) 1);
            header.putShort((short) 2);
            header.putInt(44100);
            header.putInt(44100 * 4);
            header.putShort((short) 4);
            header.putShort((short) 16);
            header.put("data".getBytes(StandardCharsets.US_ASCII));
            header.putInt((int) dataBytes);
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw")) {
                raf.seek(0);
                raf.write(header.array());
            }
        }
    }

    private static void separateOnnxStreaming(
            File prepared, File output, File status, File cancel) throws Exception {
        long bytes = prepared.length();
        if (bytes <= 0 || bytes % 8L != 0) {
            throw new IOException("PCM siap harus berupa stereo float32 44,1 kHz");
        }
        final long total = bytes / 8L;
        if (total < 44100L) throw new IOException("Audio lebih pendek dari satu detik");
        final long chunks = (total + ONNX_STRIDE - 1L) / ONNX_STRIDE;

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(2);
        options.setInterOpNumThreads(1);
        options.setMemoryPatternOptimization(true);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        WavPcm16Writer[] writers = new WavPcm16Writer[7];
        try (OrtSession session = ortEnvironment.createSession(modelPath, options);
             OrtSession.RunOptions runOptions = new OrtSession.RunOptions();
             java.io.RandomAccessFile inputFile = new java.io.RandomAccessFile(prepared, "r")) {
            if (!session.getInputNames().contains("mix")
                    || !session.getOutputNames().contains("stems")) {
                throw new IOException("Model tidak memiliki node mix dan stems");
            }
            TensorInfo inInfo = (TensorInfo) session.getInputInfo().get("mix").getInfo();
            TensorInfo outInfo = (TensorInfo) session.getOutputInfo().get("stems").getInfo();
            if (!sameShape(inInfo.getShape(), new long[] {1, 2, ONNX_SAMPLES})
                    || !sameShape(outInfo.getShape(), new long[] {1, 6, 2, ONNX_SAMPLES})) {
                throw new IOException("Bentuk tensor model tidak sesuai kontrak HTDemucs 6S");
            }

            for (int i = 0; i < 6; i++) {
                writers[i] = new WavPcm16Writer(new File(output, STEM_NAMES[i] + ".wav"));
            }
            writers[6] = new WavPcm16Writer(new File(output, "instrumental.wav"));

            float[][][] accum = new float[6][2][ONNX_SAMPLES];
            float[] weights = new float[ONNX_SAMPLES];
            float[] instrumentalL = new float[ONNX_STRIDE];
            float[] instrumentalR = new float[ONNX_STRIDE];
            byte[] audioBlock = new byte[ONNX_SAMPLES * 8];
            ByteBuffer tensorBytes = ByteBuffer.allocateDirect(ONNX_SAMPLES * 2 * 4)
                    .order(ByteOrder.nativeOrder());
            FloatBuffer tensorData = tensorBytes.asFloatBuffer();
            float[] fade = new float[ONNX_OVERLAP];
            for (int i = 0; i < ONNX_OVERLAP; i++) {
                fade[i] = i / (float) (ONNX_OVERLAP - 1);
            }

            activeRunOptions = runOptions;
            long start = 0;
            for (long chunk = 0; chunk < chunks; chunk++) {
                checkCancelled(cancel);
                int available = (int) Math.min(ONNX_SAMPLES, total - start);
                java.util.Arrays.fill(audioBlock, (byte) 0);
                inputFile.seek(start * 8L);
                inputFile.readFully(audioBlock, 0, available * 8);
                FloatBuffer rawInterleaved = ByteBuffer.wrap(audioBlock)
                        .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                rawInterleaved.limit(available * 2);

                tensorData.clear();
                for (int i = 0; i < ONNX_SAMPLES; i++) {
                    tensorData.put(i < available ? rawInterleaved.get(i * 2) : 0f);
                }
                for (int i = 0; i < ONNX_SAMPLES; i++) {
                    tensorData.put(i < available ? rawInterleaved.get(i * 2 + 1) : 0f);
                }
                tensorData.flip();

                try (OnnxTensor tensor = OnnxTensor.createTensor(
                        ortEnvironment, tensorData, new long[] {1, 2, ONNX_SAMPLES})) {
                    Map<String, OnnxTensor> feeds = new HashMap<>();
                    feeds.put("mix", tensor);
                    try (OrtSession.Result result = session.run(feeds, runOptions)) {
                        OnnxTensor outputTensor = (OnnxTensor) result.get("stems").orElse(null);
                        if (outputTensor == null
                                || !sameShape(outputTensor.getInfo().getShape(),
                                        new long[] {1, 6, 2, ONNX_SAMPLES})) {
                            throw new IOException("Output inferensi tidak sesuai enam stem");
                        }
                        FloatBuffer values = outputTensor.getFloatBuffer();
                        int expected = 6 * 2 * ONNX_SAMPLES;
                        if (values == null || values.remaining() != expected) {
                            throw new IOException("Buffer output inferensi tidak lengkap");
                        }
                        for (int i = 0; i < ONNX_SAMPLES; i++) {
                            float weight;
                            if (i < ONNX_OVERLAP) weight = fade[i];
                            else if (i >= ONNX_SAMPLES - ONNX_OVERLAP) {
                                weight = fade[ONNX_SAMPLES - 1 - i];
                            } else weight = 1f;
                            if (i >= available) continue;
                            weights[i] += weight;
                            for (int stem = 0; stem < 6; stem++) {
                                for (int channel = 0; channel < 2; channel++) {
                                    float value = values.get((stem * 2 + channel)
                                            * ONNX_SAMPLES + i);
                                    if (Float.isNaN(value) || Float.isInfinite(value)) {
                                        throw new IOException("Model menghasilkan nilai audio tidak valid");
                                    }
                                    accum[stem][channel][i] += value * weight;
                                }
                            }
                            if ((i & 8191) == 0) checkCancelled(cancel);
                        }
                    }
                }

                long remaining = total - start;
                int flush = (int) Math.min(ONNX_STRIDE, remaining);
                for (int stem = 0; stem < 6; stem++) {
                    writers[stem].writeNormalized(
                            accum[stem][0], accum[stem][1], weights, flush);
                }
                for (int i = 0; i < flush; i++) {
                    float weight = Math.max(weights[i], 1e-8f);
                    float left = 0f;
                    float right = 0f;
                    for (int stem = 0; stem < 6; stem++) {
                        if (stem == 3) continue;
                        left += accum[stem][0][i] / weight;
                        right += accum[stem][1][i] / weight;
                    }
                    instrumentalL[i] = left;
                    instrumentalR[i] = right;
                }
                writers[6].write(instrumentalL, instrumentalR, flush);

                if (remaining > ONNX_STRIDE) {
                    for (int stem = 0; stem < 6; stem++) {
                        for (int channel = 0; channel < 2; channel++) {
                            System.arraycopy(accum[stem][channel], ONNX_STRIDE,
                                    accum[stem][channel], 0, ONNX_OVERLAP);
                            java.util.Arrays.fill(accum[stem][channel],
                                    ONNX_OVERLAP, ONNX_SAMPLES, 0f);
                        }
                    }
                    System.arraycopy(weights, ONNX_STRIDE, weights, 0, ONNX_OVERLAP);
                    java.util.Arrays.fill(weights, ONNX_OVERLAP, ONNX_SAMPLES, 0f);
                    start += ONNX_STRIDE;
                }

                int percent = 30 + (int) ((chunk + 1L) * 70L / chunks);
                writeStatus(status, "jalan", percent, "Memisahkan audio",
                        "Segmen " + (chunk + 1L) + " dari " + chunks);
            }
            for (WavPcm16Writer writer : writers) writer.close();
            for (WavPcm16Writer writer : writers) {
                writer.validate(total);
            }
        } finally {
            activeRunOptions = null;
            for (WavPcm16Writer writer : writers) {
                if (writer != null) {
                    try { writer.close(); } catch (Exception ignored) { }
                }
            }
            options.close();
        }
    }

    private static boolean sameShape(long[] actual, long[] expected) {
        if (actual == null || actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) return false;
        }
        return true;
    }
    private static final class AudioInfo {
        final int sampleRate;
        final int channels;

        AudioInfo(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    
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

    private static synchronized void verifyModel(String path) throws Exception {
        File model = new File(path);
        long length = model.length();
        long modified = model.lastModified();
        if (path.equals(verifiedModelPath)
                && length == verifiedModelLength
                && modified == verifiedModelModified) {
            return;
        }
        final String expected = "7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869";
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
            verifiedModelPath = null;
            verifiedModelLength = -1;
            verifiedModelModified = -1;
            throw new IOException("Checksum Kim Vocal 2 tidak cocok. Unduh ulang model.");
        }
        verifiedModelPath = path;
        verifiedModelLength = length;
        verifiedModelModified = modified;
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
        String[] names = { "drums", "bass", "other", "vocals", "guitar", "piano", "instrumental" };
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
