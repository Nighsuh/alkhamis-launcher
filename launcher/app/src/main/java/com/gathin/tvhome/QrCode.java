package com.gathin.tvhome;

/**
 * QR Code, byte mode, error correction L, versions 1 to 6. Enough for a local http address.
 * The three finder squares are the check that a scan app looks for first.
 */
final class QrCode {
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];
    /** ECC bytes for versions 1..6 at level L, one block. */
    private static final int[] ECC = {0, 7, 10, 15, 20, 26, 36};
    /** Total codewords for versions 1..6. */
    private static final int[] TOTAL = {0, 26, 44, 70, 100, 134, 172};
    /** Alignment centre for versions 2..6. Version 1 has none. */
    private static final int[] ALIGN = {0, 0, 18, 22, 26, 30, 34};
    /** Format bits for level L and masks 0..7, already masked with 0x5412. */
    private static final int[] FORMAT = {
            0x77c4, 0x72f3, 0x7daa, 0x789d, 0x662f, 0x6318, 0x6c41, 0x6976
    };

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if (x >= 256) x ^= 0x11d;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    final int size;
    final boolean[][] dark;

    private QrCode(int size, boolean[][] dark) {
        this.size = size;
        this.dark = dark;
    }

    static QrCode encode(String text) {
        byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int version = 1;
        while (version < 6 && data.length + 2 > (TOTAL[version] - ECC[version])) version++;
        int capacity = TOTAL[version] - ECC[version];
        if (data.length > capacity - 2) data = java.util.Arrays.copyOf(data, capacity - 2);
        int[] words = new int[TOTAL[version]];
        int bit = 0;
        bit = put(words, bit, 0x4, 4);
        bit = put(words, bit, data.length, 8);
        for (byte b : data) bit = put(words, bit, b & 0xff, 8);
        int term = Math.min(4, capacity * 8 - bit);
        bit = put(words, bit, 0, term);
        while (bit % 8 != 0) bit = put(words, bit, 0, 1);
        int byteAt = bit / 8;
        int pad = 0xEC;
        while (byteAt < capacity) {
            words[byteAt++] = pad;
            pad ^= 0xEC ^ 0x11;
        }
        int[] ecc = remainder(words, capacity, ECC[version]);
        System.arraycopy(ecc, 0, words, capacity, ECC[version]);
        return draw(version, words);
    }

    private static int put(int[] words, int bit, int value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            if (((value >> i) & 1) != 0) words[bit / 8] |= 1 << (7 - (bit % 8));
            bit++;
        }
        return bit;
    }

    private static int[] remainder(int[] data, int dataLen, int eccLen) {
        int[] gen = new int[]{1};
        for (int i = 0; i < eccLen; i++) {
            int[] next = new int[gen.length + 1];
            for (int j = 0; j < gen.length; j++) {
                next[j] ^= gen[j];
                next[j + 1] ^= mul(gen[j], EXP[i]);
            }
            gen = next;
        }
        int[] rem = new int[eccLen];
        for (int i = 0; i < dataLen; i++) {
            int feedback = rem[0] ^ data[i];
            System.arraycopy(rem, 1, rem, 0, eccLen - 1);
            rem[eccLen - 1] = 0;
            if (feedback == 0) continue;
            for (int j = 0; j < eccLen; j++) rem[j] ^= mul(gen[j + 1], feedback);
        }
        return rem;
    }

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    private static QrCode draw(int version, int[] words) {
        int size = 17 + 4 * version;
        Boolean[][] mod = new Boolean[size][size];
        Boolean[][] func = new Boolean[size][size];
        finder(mod, func, 0, 0);
        finder(mod, func, size - 7, 0);
        finder(mod, func, 0, size - 7);
        for (int i = 0; i < size; i++) {
            if (func[6][i] == null) set(mod, func, i, 6, i % 2 == 0);
            if (func[i][6] == null) set(mod, func, 6, i, i % 2 == 0);
        }
        if (version >= 2) alignment(mod, func, ALIGN[version]);
        reserveFormat(mod, func);
        boolean[][] best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] grid = new boolean[size][size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (func[y][x] != null) grid[y][x] = mod[y][x];
                }
            }
            placeData(grid, func, words, mask);
            placeFormat(grid, mask);
            int score = penalty(grid);
            if (score < bestScore) {
                bestScore = score;
                best = grid;
            }
        }
        return new QrCode(size, best);
    }

    private static void finder(Boolean[][] mod, Boolean[][] func, int x, int y) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= mod.length || yy >= mod.length) continue;
                boolean dark = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
                        && (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                set(mod, func, xx, yy, dark);
            }
        }
    }

    private static void alignment(Boolean[][] mod, Boolean[][] func, int at) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (func[at + dy][at + dx] != null) return;
            }
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean dark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                set(mod, func, at + dx, at + dy, dark);
            }
        }
    }

    private static void set(Boolean[][] mod, Boolean[][] func, int x, int y, boolean dark) {
        mod[y][x] = dark;
        func[y][x] = dark;
    }

    /** Format areas stay empty until the mask is chosen, so data bits are not written there. */
    private static void reserveFormat(Boolean[][] mod, Boolean[][] func) {
        int size = mod.length;
        for (int i = 0; i < 9; i++) {
            if (func[i][8] == null) set(mod, func, 8, i, false);
            if (func[8][i] == null) set(mod, func, i, 8, false);
        }
        for (int i = 0; i < 8; i++) {
            if (func[size - 1 - i][8] == null) set(mod, func, 8, size - 1 - i, false);
            if (func[8][size - 1 - i] == null) set(mod, func, size - 1 - i, 8, false);
        }
    }

    private static void placeData(boolean[][] grid, Boolean[][] func, int[] words, int mask) {
        int bit = 0;
        int totalBits = words.length * 8;
        int size = grid.length;
        for (int col = size - 1; col > 0; col -= 2) {
            int right = col <= 6 ? col - 1 : col;
            if (right < 1) break;
            boolean upwards = (right & 2) == 0;
            for (int vertical = 0; vertical < size; vertical++) {
                for (int z = 0; z < 2; z++) {
                    int x = right - z;
                    boolean up = upwards ^ (x < 6);
                    int y = up ? size - 1 - vertical : vertical;
                    if (func[y][x] != null) continue;
                    boolean dark = false;
                    if (bit < totalBits) dark = ((words[bit / 8] >> (7 - (bit % 8))) & 1) != 0;
                    bit++;
                    if (maskBit(mask, x, y)) dark = !dark;
                    grid[y][x] = dark;
                }
            }
        }
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (y / 2 + x / 3) % 2 == 0;
            case 5: return (x * y) % 2 + (x * y) % 3 == 0;
            case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            default: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
        }
    }

    private static void placeFormat(boolean[][] grid, int mask) {
        int bits = FORMAT[mask];
        int size = grid.length;
        int voffset = 0;
        int hoffset = 0;
        for (int i = 0; i < 8; i++) {
            boolean vbit = ((bits >> i) & 1) != 0;
            boolean hbit = ((bits >> (14 - i)) & 1) != 0;
            if (i == 6) {
                voffset += 1;
                hoffset = 1;
            }
            grid[i + voffset][8] = vbit;
            grid[8][i + hoffset] = hbit;
            grid[8][size - 1 - i] = vbit;
            grid[size - 1 - i][8] = hbit;
        }
        grid[size - 8][8] = true;
    }

    private static int penalty(boolean[][] grid) {
        int score = 0;
        int n = grid.length;
        for (int y = 0; y < n; y++) {
            int run = 1;
            for (int x = 1; x < n; x++) {
                if (grid[y][x] == grid[y][x - 1]) run++;
                else { if (run >= 5) score += run - 2; run = 1; }
            }
            if (run >= 5) score += run - 2;
        }
        return score;
    }
}
