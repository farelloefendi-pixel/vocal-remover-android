import java.nio.FloatBuffer;

final class MDXNetDSP {
    static final int N_FFT = 7680;
    static final int HOP = 1024;
    static final int DIM_F = 3072;
    static final int DIM_T = 256;
    static final int TRIM = N_FFT / 2;
    static final int CHUNK = HOP * (DIM_T - 1);
    static final int GEN = CHUNK - 2 * TRIM;
    static final float OVERLAP = 0.25f;
    static final int STEP = Math.round(CHUNK * (1f - OVERLAP));
    static final int BORDER = CHUNK - STEP;
    static final int TENSOR_SIZE = 4 * DIM_F * DIM_T;

    private MDXNetDSP() {
    }

    static float[] createBlendWindow() {
        float[] window = new float[CHUNK];
        for (int i = 0; i < CHUNK; i++) {
            window[i] = (float) (0.5d - 0.5d * Math.cos(2d * Math.PI * i / (CHUNK - 1)));
        }
        return window;
    }

    static float[] createWindow() {
        float[] window = new float[N_FFT];
        for (int i = 0; i < N_FFT; i++) {
            window[i] = (float) (0.5d - 0.5d * Math.cos(2d * Math.PI * i / N_FFT));
        }
        return window;
    }

    static void createInput(float[] left, float[] right, FloatBuffer target,
                            float[] window, BluesteinFFT fft) {
        if (left.length != CHUNK || right.length != CHUNK
                || target.capacity() < TENSOR_SIZE) {
            throw new IllegalArgumentException("Ukuran buffer STFT tidak sesuai");
        }
        float[] re = new float[N_FFT];
        float[] im = new float[N_FFT];
        for (int channel = 0; channel < 2; channel++) {
            float[] source = channel == 0 ? left : right;
            int realPlane = channel * 2;
            int imagPlane = realPlane + 1;
            for (int frame = 0; frame < DIM_T; frame++) {
                int offset = frame * HOP;
                for (int n = 0; n < N_FFT; n++) {
                    int sample = reflectIndex(offset + n - TRIM, CHUNK);
                    re[n] = source[sample] * window[n];
                    im[n] = 0f;
                }
                fft.forward(re, im);
                for (int bin = 0; bin < DIM_F; bin++) {
                    int realIndex = (realPlane * DIM_F + bin) * DIM_T + frame;
                    int imagIndex = (imagPlane * DIM_F + bin) * DIM_T + frame;
                    target.put(realIndex, re[bin]);
                    target.put(imagIndex, im[bin]);
                }
            }
        }
        target.position(0);
        target.limit(TENSOR_SIZE);
    }

    static void synthesize(FloatBuffer spectrum, int channel, float[] destination,
                           float[] window, float[] envelope, BluesteinFFT fft,
                           FileCancellation cancellation) throws Exception {
        if (destination.length < CHUNK || spectrum.remaining() < TENSOR_SIZE) {
            throw new IllegalArgumentException("Ukuran buffer iSTFT tidak sesuai");
        }
        float[] re = new float[N_FFT];
        float[] im = new float[N_FFT];
        float[] overlap = new float[CHUNK + N_FFT];
        int realPlane = channel * 2;
        int imagPlane = realPlane + 1;
        for (int frame = 0; frame < DIM_T; frame++) {
            if ((frame & 15) == 0 && cancellation != null) cancellation.check();
            java.util.Arrays.fill(re, 0f);
            java.util.Arrays.fill(im, 0f);
            for (int bin = 0; bin < DIM_F; bin++) {
                int realIndex = (realPlane * DIM_F + bin) * DIM_T + frame;
                int imagIndex = (imagPlane * DIM_F + bin) * DIM_T + frame;
                float real = spectrum.get(realIndex);
                float imaginary = spectrum.get(imagIndex);
                if (!Float.isFinite(real) || !Float.isFinite(imaginary)) {
                    throw new IllegalStateException("Output model mengandung nilai tidak valid");
                }
                re[bin] = real;
                im[bin] = imaginary;
                if (bin > 0) {
                    int mirror = N_FFT - bin;
                    re[mirror] = real;
                    im[mirror] = -imaginary;
                }
            }
            fft.inverse(re, im);
            int start = frame * HOP;
            for (int n = 0; n < N_FFT; n++) {
                overlap[start + n] += re[n] * window[n];
            }
        }
        if (envelope == null || envelope.length != CHUNK) {
            throw new IllegalArgumentException("Envelope iSTFT tidak valid");
        }
        for (int i = 0; i < CHUNK; i++) {
            float weight = envelope[i];
            if (!(weight > 1e-8f)) {
                throw new IllegalStateException("Normalisasi iSTFT tidak valid");
            }
            destination[i] = overlap[TRIM + i] / weight;
        }
    }

    static float[] createEnvelope(float[] window) {
        float[] full = new float[CHUNK + N_FFT];
        for (int frame = 0; frame < DIM_T; frame++) {
            int start = frame * HOP;
            for (int n = 0; n < N_FFT; n++) {
                float value = window[n];
                full[start + n] += value * value;
            }
        }
        float[] result = new float[CHUNK];
        System.arraycopy(full, TRIM, result, 0, CHUNK);
        return result;
    }

    static int reflectIndex(int index, int length) {
        if (index < 0) return -index;
        if (index >= length) return (2 * length) - 2 - index;
        return index;
    }

    interface FileCancellation {
        void check() throws Exception;
    }

    static final class BluesteinFFT {
        private final int n;
        private final int m;
        private final float[] chirpCos;
        private final float[] chirpSin;
        private final float[] kernelReal;
        private final float[] kernelImag;
        private final float[] workReal;
        private final float[] workImag;
        private final float[] cosTable;
        private final float[] sinTable;
        private final int[] bitReverse;

        BluesteinFFT(int size) {
            n = size;
            int length = 1;
            while (length < (2 * size - 1)) length <<= 1;
            m = length;
            chirpCos = new float[n];
            chirpSin = new float[n];
            kernelReal = new float[m];
            kernelImag = new float[m];
            workReal = new float[m];
            workImag = new float[m];
            cosTable = new float[m / 2];
            sinTable = new float[m / 2];
            bitReverse = new int[m];
            for (int i = 0; i < m / 2; i++) {
                double angle = 2d * Math.PI * i / m;
                cosTable[i] = (float) Math.cos(angle);
                sinTable[i] = (float) Math.sin(angle);
            }
            int bits = Integer.numberOfTrailingZeros(m);
            for (int i = 0; i < m; i++) {
                bitReverse[i] = Integer.reverse(i) >>> (32 - bits);
            }
            for (int i = 0; i < n; i++) {
                long square = ((long) i * (long) i) % (2L * n);
                double angle = Math.PI * square / n;
                chirpCos[i] = (float) Math.cos(angle);
                chirpSin[i] = (float) Math.sin(angle);
                float c = chirpCos[i];
                float s = chirpSin[i];
                kernelReal[i] = c;
                kernelImag[i] = s;
                if (i != 0) {
                    kernelReal[m - i] = c;
                    kernelImag[m - i] = s;
                }
            }
            radix2(kernelReal, kernelImag, false);
        }

        void forward(float[] real, float[] imaginary) {
            java.util.Arrays.fill(workReal, 0f);
            java.util.Arrays.fill(workImag, 0f);
            for (int i = 0; i < n; i++) {
                float c = chirpCos[i];
                float s = chirpSin[i];
                float a = real[i];
                float b = imaginary[i];
                workReal[i] = a * c + b * s;
                workImag[i] = b * c - a * s;
            }
            radix2(workReal, workImag, false);
            for (int i = 0; i < m; i++) {
                float a = workReal[i];
                float b = workImag[i];
                workReal[i] = a * kernelReal[i] - b * kernelImag[i];
                workImag[i] = a * kernelImag[i] + b * kernelReal[i];
            }
            radix2(workReal, workImag, true);
            for (int i = 0; i < n; i++) {
                float c = chirpCos[i];
                float s = chirpSin[i];
                float a = workReal[i];
                float b = workImag[i];
                real[i] = a * c + b * s;
                imaginary[i] = b * c - a * s;
            }
        }

        void inverse(float[] real, float[] imaginary) {
            for (int i = 0; i < n; i++) imaginary[i] = -imaginary[i];
            forward(real, imaginary);
            float scale = 1f / n;
            for (int i = 0; i < n; i++) {
                real[i] *= scale;
                imaginary[i] = -imaginary[i] * scale;
            }
        }

        private void radix2(float[] real, float[] imaginary, boolean inverse) {
            for (int i = 0; i < m; i++) {
                int j = bitReverse[i];
                if (j > i) {
                    float value = real[i];
                    real[i] = real[j];
                    real[j] = value;
                    value = imaginary[i];
                    imaginary[i] = imaginary[j];
                    imaginary[j] = value;
                }
            }
            for (int size = 2; size <= m; size <<= 1) {
                int half = size >>> 1;
                int stride = m / size;
                for (int base = 0; base < m; base += size) {
                    for (int j = 0; j < half; j++) {
                        int table = j * stride;
                        float wr = cosTable[table];
                        float wi = inverse ? sinTable[table] : -sinTable[table];
                        int even = base + j;
                        int odd = even + half;
                        float or = real[odd] * wr - imaginary[odd] * wi;
                        float oi = real[odd] * wi + imaginary[odd] * wr;
                        float er = real[even];
                        float ei = imaginary[even];
                        real[even] = er + or;
                        imaginary[even] = ei + oi;
                        real[odd] = er - or;
                        imaginary[odd] = ei - oi;
                    }
                }
            }
            if (inverse) {
                float scale = 1f / m;
                for (int i = 0; i < m; i++) {
                    real[i] *= scale;
                    imaginary[i] *= scale;
                }
            }
        }
    }
}
