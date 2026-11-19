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
        System.out.println("MDX DSP round-trip OK; maxError=" + maxError + ", rms=" + rms);
    }
}
