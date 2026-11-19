import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java bridge for the CSR Lua extension.
 *
 * Compile this class into the DEX loaded by the extension. Build the native
 * library with SDENGINE_JNI=ON and SDENGINE_JNI_CLASS="DJLEngine".
 *
 * All long-running methods are synchronous by design: call run() from the
 * extension's dedicated HandlerThread, never from the UI thread.
 */
public final class DJLEngine {
    public interface ProgressListener {
        boolean onProgress(float fraction, String stage);
    }

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean libraryLoaded;

    private DJLEngine() {
    }

    /** Load the JNI library by absolute path from the extension's private directory. */
    public static synchronized void loadNativeLibrary(String absolutePath) {
        if (libraryLoaded) {
            return;
        }
        if (absolutePath == null || absolutePath.length() == 0) {
            throw new IllegalArgumentException("Path pustaka native kosong.");
        }
        System.load(absolutePath);
        libraryLoaded = true;
    }

    private static void requireLibrary() {
        if (!libraryLoaded) {
            throw new IllegalStateException("Pustaka mesin belum dimuat.");
        }
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

    private static void checkCancelled(String cancelPath) {
        if (cancelPath != null && new File(cancelPath).exists()) {
            throw new CancellationException("Proses dibatalkan.");
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    /** Atomic status update using a temporary file and rename in the same directory. */
    private static void writeStatus(String statusPath, String stage,
                                    int percent, String message) {
        if (statusPath == null || statusPath.length() == 0) {
            return;
        }
        File target = new File(statusPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        File temporary = new File(statusPath + ".tmp");
        String data = clean(stage) + "|" + Math.max(0, Math.min(100, percent))
                + "|" + clean(message) + "|" + System.currentTimeMillis();
        try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
            stream.write(data.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } catch (Exception ignored) {
            return;
        }
        try {
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
        } catch (Exception error) {
            // Keep the previous complete status if atomic replacement fails.
            // The temporary file is harmless and can be cleaned by Lua later.
        }
    }

    /**
     * Full separation pipeline. Call this method from a dedicated worker thread.
     *
     * rawPath must point to interleaved float32 PCM decoded by MediaCodec.
     * The engine resamples it to 44.1 kHz stereo, then writes selected WAV stems.
     * A single active job is allowed because the upstream engine has global
     * cancellation state.
     */
    public static double[] run(
            String modelPath,
            String rawPath,
            int sampleRate,
            int channels,
            String preparedPath,
            String outputDirectory,
            String statusPath,
            String cancelPath,
            int segmentThreads,
            int computeThreads,
            boolean gpu,
            boolean[] selectedStems) {

        requireLibrary();
        if (!RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("Masih ada pekerjaan pemisahan yang berjalan.");
        }

        try {
            if (cancelPath != null) {
                File cancel = new File(cancelPath);
                if (cancel.exists()) {
                    cancel.delete();
                }
            }

            writeStatus(statusPath, "loading_model", 0, "Memuat model pemisah audio");
            String modelError = nativeLoadModel(modelPath);
            if (modelError != null && modelError.length() > 0) {
                throw new IllegalStateException(modelError);
            }
            checkCancelled(cancelPath);

            ProgressListener prepareProgress = new ProgressListener() {
                @Override
                public boolean onProgress(float fraction, String stage) {
                    checkCancelled(cancelPath);
                    int percent = 2 + Math.round(Math.max(0f, Math.min(1f, fraction)) * 18f);
                    writeStatus(statusPath, "preparing", percent,
                            stage == null ? "Menyiapkan audio" : stage);
                    return true;
                }
            };

            writeStatus(statusPath, "preparing", 2, "Menyiapkan audio untuk mesin");
            String prepareError = nativePrepareInput(
                    rawPath, sampleRate, channels, preparedPath, prepareProgress);
            if (prepareError != null && prepareError.length() > 0) {
                throw new IllegalStateException(prepareError);
            }
            checkCancelled(cancelPath);

            boolean[] write = selectedStems;
            if (write == null || write.length != 6) {
                write = new boolean[] { true, true, true, true, true, true };
            }

            ProgressListener separationProgress = new ProgressListener() {
                @Override
                public boolean onProgress(float fraction, String stage) {
                    checkCancelled(cancelPath);
                    int percent = 20 + Math.round(
                            Math.max(0f, Math.min(1f, fraction)) * 80f);
                    writeStatus(statusPath, "separating", percent,
                            stage == null ? "Memisahkan sumber audio" : stage);
                    return true;
                }
            };

            writeStatus(statusPath, "separating", 20, "Memulai pemisahan enam stem");
            double[] rms = nativeSeparate(
                    preparedPath, outputDirectory, write, Math.max(1, segmentThreads),
                    Math.max(0, computeThreads), gpu, separationProgress);

            writeStatus(statusPath, "completed", 100, "Pemisahan audio selesai");
            return rms;
        } catch (CancellationException cancelled) {
            writeStatus(statusPath, "cancelled", 0, "Pekerjaan dibatalkan");
            throw cancelled;
        } catch (Throwable error) {
            writeStatus(statusPath, "failed", 0,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw new RuntimeException(error);
        } finally {
            RUNNING.set(false);
        }
    }

    /** Request cooperative cancellation of the active native operation. */
    public static void cancel(String cancelPath) {
        if (cancelPath != null && cancelPath.length() > 0) {
            try (FileOutputStream stream = new FileOutputStream(cancelPath, false)) {
                stream.write("cancel".getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            } catch (Exception ignored) {
            }
        }
        if (libraryLoaded) {
            try {
                nativeCancel();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String computeInfo(int computeThreads, boolean gpu) {
        requireLibrary();
        return nativeComputeInfo(Math.max(0, computeThreads), gpu);
    }

    public static double[] analyze(String preparedPath) {
        requireLibrary();
        return nativeAnalyze(preparedPath);
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }
}
