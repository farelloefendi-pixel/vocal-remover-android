import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class MDXNetDSPTest {
    public static void main(String[] args) throws Exception {
        float[] left = new float[MDXNetDSP.CHUNK];
        float[] right = new float[MDXNetDSP.CHUNK];
        for (int i = 0; i < left.length; i++) {
            left[i] = (float) (0.31 * Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0)
                    + 0.07 * Math.sin(2.0 * Math.PI * 997.0 * i / 44100.0));
            right[i] = (float) (0.23 * Math.sin(2.0 * Math.PI * 311.0 * i / 44100.0)
                    - 0.09 * Math.cos(2.0 * Math.PI * 1703.0 * i / 44100.0));
        }

        float[] window = MDXNetDSP.createWindow();
        float[] envelope = MDXNetDSP.createEnvelope(window);
        FloatBuffer tensor = ByteBuffer.allocateDirect(MDXNetDSP.TENSOR_SIZE * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        MDXNetDSP.BluesteinFFT fft = new MDXNetDSP.BluesteinFFT(MDXNetDSP.N_FFT);
        MDXNetDSP.createInput(left, right, tensor, window, fft);

        int frame = 37;
        int bin = 83;
        double expectedReal = 0d;
        double expectedImag = 0d;
        for (int n = 0; n < MDXNetDSP.N_FFT; n++) {
            int sample = MDXNetDSP.reflectIndex(
                    frame * MDXNetDSP.HOP + n - MDXNetDSP.TRIM, MDXNetDSP.CHUNK);
            double value = left[sample] * window[n];
            double angle = 2d * Math.PI * bin * n / MDXNetDSP.N_FFT;
            expectedReal += value * Math.cos(angle);
            expectedImag -= value * Math.sin(angle);
        }
        int realIndex = (bin * MDXNetDSP.DIM_T) + frame;
        int imagIndex = (MDXNetDSP.DIM_F * MDXNetDSP.DIM_T)
                + realIndex;
        if (Math.abs(tensor.get(realIndex) - expectedReal) > 0.01d
                || Math.abs(tensor.get(imagIndex) - expectedImag) > 0.01d) {
            throw new AssertionError("STFT spectrum differs from direct DFT reference");
        }

        float[] restoredLeft = new float[MDXNetDSP.CHUNK];
        float[] restoredRight = new float[MDXNetDSP.CHUNK];
        MDXNetDSP.synthesize(tensor, 0, restoredLeft, window, envelope, fft, null);
        MDXNetDSP.synthesize(tensor, 1, restoredRight, window, envelope, fft, null);

        float maxError = 0f;
        double squareError = 0d;
        long samples = 0;
        for (int i = MDXNetDSP.TRIM; i < MDXNetDSP.TRIM + MDXNetDSP.GEN; i++) {
            float a = Math.abs(left[i] - restoredLeft[i]);
            float b = Math.abs(right[i] - restoredRight[i]);
            maxError = Math.max(maxError, Math.max(a, b));
            squareError += (double) a * a + (double) b * b;
            samples += 2;
        }
        double rms = Math.sqrt(squareError / samples);
        if (!Float.isFinite(maxError) || !Double.isFinite(rms)
                || maxError > 0.002f || rms > 0.0002d) {
            throw new AssertionError("STFT/iSTFT round-trip failed: max="
                    + maxError + ", rms=" + rms);
        }
        float[] blend = MDXNetDSP.createBlendWindow();
        long[] lengths = new long[] {
                44100L, 130559L, 130560L, 195840L, 391680L,
                500000L, 1000000L, 44100L * 180L
        };
        for (long length : lengths) {
            long padded = length + 2L * MDXNetDSP.BORDER + 1L;
            long chunks = padded <= MDXNetDSP.CHUNK ? 1L
                    : ((padded - MDXNetDSP.CHUNK + MDXNetDSP.STEP - 1L)
                            / MDXNetDSP.STEP) + 1L;
            for (long sample = 0; sample < length; sample++) {
                long paddedSample = MDXNetDSP.BORDER + sample;
                double weight = 0d;
                long firstChunk = paddedSample < MDXNetDSP.CHUNK ? 0L
                        : (paddedSample - MDXNetDSP.CHUNK) / MDXNetDSP.STEP + 1L;
                long lastChunk = Math.min(chunks - 1L, paddedSample / MDXNetDSP.STEP);
                for (long chunk = firstChunk; chunk <= lastChunk; chunk++) {
                    long local = paddedSample - chunk * MDXNetDSP.STEP;
                    if (local >= 0 && local < MDXNetDSP.CHUNK) {
                        weight += blend[(int) local];
                    }
                }
                if (!(weight > 1e-6d)) {
                    throw new AssertionError("Overlap-add has an uncovered sample at length "
                            + length + ", sample " + sample);
                }
            }
        }
        System.out.println("MDX overlap-add coverage OK; overlap="
                + MDXNetDSP.OVERLAP + ", step=" + MDXNetDSP.STEP
                + ", border=" + MDXNetDSP.BORDER);
        System.out.println("MDX DSP round-trip OK; maxError=" + maxError + ", rms=" + rms);
    }
}
