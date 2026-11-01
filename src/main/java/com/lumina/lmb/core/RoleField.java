package com.lumina.lmb.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The table "cell → role" of one structure at one size, in the structure's own coordinates. A 5×5×5
 * tank: 27 inside cells, 97 casing cells and the controller. Cells without a role are not part of the
 * structure ("don't care"): the four empty corners around a hexagram, for example. A cell that must stay
 * empty is a role whose matcher only accepts air.
 *
 * <p>The anchor is the controller's cell: a placed structure is found from it and saved by it.
 */
public final class RoleField<S> {
    /** Cell offsets from the anchor are packed into 11 bits per axis (see Definition#orientations). */
    public static final int MAX_SIZE = 1024;

    private final int sx, sy, sz;
    private final int ax, ay, az;
    private final byte[] cells;
    private final List<Role<S>> palette;   // index 0 is "not part of the structure"
    private final int count;

    private RoleField(int sx, int sy, int sz, int ax, int ay, int az, byte[] cells, List<Role<S>> palette) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.cells = cells;
        this.palette = palette;
        int n = 0;
        for (byte c : cells) if (c != 0) n++;
        this.count = n;
    }

    public int sizeX() {
        return sx;
    }

    public int sizeY() {
        return sy;
    }

    public int sizeZ() {
        return sz;
    }

    public int anchorX() {
        return ax;
    }

    public int anchorY() {
        return ay;
    }

    public int anchorZ() {
        return az;
    }

    /** Cells in the bounding box, including the ones that are not part of the structure. */
    public int volume() {
        return cells.length;
    }

    /** Cells that are part of the structure. */
    public int count() {
        return count;
    }

    public int paletteSize() {
        return palette.size();
    }

    /** Null for id 0. */
    public Role<S> paletteRole(int id) {
        return palette.get(id);
    }

    /** Palette id of the cell, 0 when it is not part of the structure. */
    public int roleId(int index) {
        return cells[index] & 0xFF;
    }

    /** Null when the cell is not part of the structure. */
    public Role<S> role(int index) {
        return palette.get(cells[index] & 0xFF);
    }

    public Role<S> anchorRole() {
        return role(index(ax, ay, az));
    }

    /** −1 outside the box. */
    public int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return -1;
        return (y * sz + z) * sx + x;
    }

    public int x(int index) {
        return index % sx;
    }

    public int y(int index) {
        return index / (sx * sz);
    }

    public int z(int index) {
        return index / sx % sz;
    }

    public static <S> Builder<S> builder(int sx, int sy, int sz) {
        return new Builder<>(sx, sy, sz);
    }

    /**
     * From character layers, bottom layer first; in each layer a string is a row along x, and the rows go
     * along z. Characters missing from {@code key} (such as a space) are not part of the structure; the one
     * {@code anchor} character marks the controller and must be in {@code key} too.
     */
    @SafeVarargs
    public static <S> RoleField<S> layers(Map<Character, Role<S>> key, char anchor, String[]... layers) {
        int sy = layers.length, sz = 0, sx = 0;
        for (String[] layer : layers) {
            sz = Math.max(sz, layer.length);
            for (String row : layer) sx = Math.max(sx, row.length());
        }
        Builder<S> b = builder(sx, sy, sz);
        boolean anchored = false;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    Role<S> role = key.get(c);
                    if (role != null) b.set(x, y, z, role);
                    if (c == anchor) {
                        if (anchored) throw new IllegalArgumentException("more than one anchor '" + anchor + "'");
                        b.anchor(x, y, z);
                        anchored = true;
                    }
                }
            }
        }
        if (!anchored) throw new IllegalArgumentException("no anchor '" + anchor + "'");
        return b.build();
    }

    public static final class Builder<S> {
        private final int sx, sy, sz;
        private final byte[] cells;
        private final List<Role<S>> palette = new ArrayList<>();
        private int ax = -1, ay = -1, az = -1;

        private Builder(int sx, int sy, int sz) {
            if (sx <= 0 || sy <= 0 || sz <= 0 || sx > MAX_SIZE || sy > MAX_SIZE || sz > MAX_SIZE) {
                throw new IllegalArgumentException("size " + sx + "×" + sy + "×" + sz + " (1.." + MAX_SIZE + " per axis)");
            }
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.cells = new byte[sx * sy * sz];
            palette.add(null);
        }

        /** {@code role} null takes the cell out of the structure. */
        public Builder<S> set(int x, int y, int z, Role<S> role) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                throw new IndexOutOfBoundsException(x + ", " + y + ", " + z);
            }
            cells[(y * sz + z) * sx + x] = (byte) id(role);
            return this;
        }

        /** Every cell of the box between the two corners (inclusive). */
        public Builder<S> fill(int x0, int y0, int z0, int x1, int y1, int z1, Role<S> role) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, role);
                }
            }
            return this;
        }

        public Builder<S> anchor(int x, int y, int z) {
            ax = x;
            ay = y;
            az = z;
            return this;
        }

        private int id(Role<S> role) {
            if (role == null) return 0;
            int i = palette.indexOf(role);
            if (i >= 0) return i;
            if (palette.size() == 256) throw new IllegalStateException("more than 255 roles");
            palette.add(role);
            return palette.size() - 1;
        }

        public RoleField<S> build() {
            if (ax < 0) throw new IllegalStateException("no anchor");
            if (cells[(ay * sz + az) * sx + ax] == 0) throw new IllegalStateException("the anchor cell has no role");
            // not List.copyOf: entry 0 is null
            return new RoleField<>(sx, sy, sz, ax, ay, az, cells.clone(), Collections.unmodifiableList(new ArrayList<>(palette)));
        }
    }
}
