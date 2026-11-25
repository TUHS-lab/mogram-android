package org.telegram.messenger;

// Mogram: градиент фона чата. Палитры считаются в OKLab так же, как на десктопе:
// яркость и насыщенность отдельно, оттенок задаёт палитру.
public final class MogramBackground {

    public static final int PRESET_OFF = 0;
    public static final int PRESET_GRAPHITE = 1;
    public static final int PRESET_AURORA = 2;
    public static final int PRESET_SUNSET = 3;
    public static final int PRESET_OCEAN = 4;
    public static final int PRESET_FOREST = 5;
    public static final int COUNT = 6;

    private static final double[] LIGHTNESS = {0.50, 0.41, 0.33, 0.24};
    private static final double[] CHROMA = {0.15, 0.13, 0.11, 0.06};

    private MogramBackground() {
    }

    public static int preset() {
        return MogramSettings.prefs().getInt("mogram_bg_preset", PRESET_GRAPHITE);
    }

    public static void setPreset(int value) {
        MogramSettings.prefs().edit().putInt("mogram_bg_preset", value).apply();
    }

    // Четыре цвета ARGB для пресета или null, если оформление выключено.
    public static int[] colors(int preset) {
        final double[] hues;
        double scale = 1;
        switch (preset) {
            case PRESET_GRAPHITE:
                hues = new double[]{270, 270, 270, 270};
                scale = 0.12;
                break;
            case PRESET_AURORA:
                hues = new double[]{190, 160, 270, 230};
                break;
            case PRESET_SUNSET:
                hues = new double[]{25, 45, 340, 300};
                break;
            case PRESET_OCEAN:
                hues = new double[]{230, 215, 255, 200};
                break;
            case PRESET_FOREST:
                hues = new double[]{150, 130, 170, 110};
                break;
            default:
                return null;
        }
        final int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = toArgb(LIGHTNESS[i], CHROMA[i] * scale, hues[i]);
        }
        return out;
    }

    private static int toArgb(double lightness, double chroma, double hueDegrees) {
        double c = chroma;
        int result = fromLch(lightness, c, hueDegrees);
        for (int i = 0; i < 18 && result == 0; i++) {
            c *= 0.8;
            result = fromLch(lightness, c, hueDegrees);
        }
        return result;
    }

    // Возвращает 0, если цвет не помещается в sRGB.
    private static int fromLch(double l0, double c, double hueDegrees) {
        final double h = Math.toRadians(hueDegrees);
        final double a = c * Math.cos(h);
        final double b = c * Math.sin(h);
        final double l = Math.pow(l0 + 0.3963377774 * a + 0.2158037573 * b, 3);
        final double m = Math.pow(l0 - 0.1055613458 * a - 0.0638541728 * b, 3);
        final double s = Math.pow(l0 - 0.0894841775 * a - 1.2914855480 * b, 3);
        final double[] rgb = {
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        };
        int packed = 0xff000000;
        for (int i = 0; i < 3; i++) {
            if (rgb[i] < -0.001 || rgb[i] > 1.001) {
                return 0;
            }
            final double v = Math.max(0, Math.min(1, rgb[i]));
            final double g = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
            packed |= (int) Math.round(g * 255) << (16 - 8 * i);
        }
        return packed;
    }
}
