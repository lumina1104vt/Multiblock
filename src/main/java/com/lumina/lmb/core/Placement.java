package com.lumina.lmb.core;

/**
 * A role field put into the world: anchor, turn and parameters. Maps world positions to cells and back.
 * The client builds these from what the server sends, to know which role each block of a formed
 * structure has.
 */
public class Placement<S> {
    final Definition<S> def;
    final RoleField<S> field;
    final Transform transform;
    private final Transform inverse;
    final long anchor;
    final int[] params;
    private final int ax, ay, az;
    final int minX, minY, minZ, maxX, maxY, maxZ;

    public Placement(Definition<S> def, RoleField<S> field, Transform transform, long anchor, int[] params) {
        this.def = def;
        this.field = field;
        this.transform = transform;
        this.inverse = transform.inverse();
        this.anchor = anchor;
        this.params = params;
        this.ax = Pos.x(anchor);
        this.ay = Pos.y(anchor);
        this.az = Pos.z(anchor);
        int[] b = bounds(field, transform, anchor);
        minX = b[0];
        minY = b[1];
        minZ = b[2];
        maxX = b[3];
        maxY = b[4];
        maxZ = b[5];
    }

    /** For saved or sent placements; null when the parameters do not fit the definition's shape. */
    public static <S> Placement<S> of(Definition<S> def, int transform, long anchor, int[] params) {
        if (transform < 0 || transform >= 48 || !Definition.validParams(def.shape(), params)) return null;
        return new Placement<>(def, def.field(params), Transform.get(transform), anchor, params.clone());
    }

    /** World bounding box {minX, minY, minZ, maxX, maxY, maxZ} of a placement. */
    static int[] bounds(RoleField<?> field, Transform t, long anchor) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int cx : new int[]{0, field.sizeX() - 1}) {
            for (int cy : new int[]{0, field.sizeY() - 1}) {
                for (int cz : new int[]{0, field.sizeZ() - 1}) {
                    int lx = cx - field.anchorX(), ly = cy - field.anchorY(), lz = cz - field.anchorZ();
                    int wx = Pos.x(anchor) + t.x(lx, ly, lz), wy = Pos.y(anchor) + t.y(lx, ly, lz), wz = Pos.z(anchor) + t.z(lx, ly, lz);
                    b[0] = Math.min(b[0], wx);
                    b[1] = Math.min(b[1], wy);
                    b[2] = Math.min(b[2], wz);
                    b[3] = Math.max(b[3], wx);
                    b[4] = Math.max(b[4], wy);
                    b[5] = Math.max(b[5], wz);
                }
            }
        }
        return b;
    }

    public Definition<S> definition() {
        return def;
    }

    public long anchor() {
        return anchor;
    }

    public Transform transform() {
        return transform;
    }

    public int[] params() {
        return params.clone();
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} */
    public int[] bounds() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    long worldPos(int index) {
        int lx = field.x(index) - field.anchorX(), ly = field.y(index) - field.anchorY(), lz = field.z(index) - field.anchorZ();
        return Pos.pack(ax + transform.x(lx, ly, lz), ay + transform.y(lx, ly, lz), az + transform.z(lx, ly, lz));
    }

    /** The cell at a world position, −1 when the position is not part of this placement. */
    int indexOf(long pos) {
        int x = Pos.x(pos), y = Pos.y(pos), z = Pos.z(pos);
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return -1;
        int dx = x - ax, dy = y - ay, dz = z - az;
        int i = field.index(inverse.x(dx, dy, dz) + field.anchorX(), inverse.y(dx, dy, dz) + field.anchorY(),
                inverse.z(dx, dy, dz) + field.anchorZ());
        return i >= 0 && field.roleId(i) != 0 ? i : -1;
    }

    public RoleField<S> field() {
        return field;
    }

    /** Every cell of the structure: world position and role. */
    public void forEachCell(CellConsumer<S> action) {
        for (int i = 0; i < field.volume(); i++) {
            Role<S> role = field.role(i);
            if (role != null) action.accept(worldPos(i), role);
        }
    }

    @FunctionalInterface
    public interface CellConsumer<S> {
        void accept(long pos, Role<S> role);
    }

    /** The role of the cell at a world position, null when it is not part of the structure. */
    public Role<S> roleAt(long pos) {
        int i = indexOf(pos);
        return i < 0 ? null : field.role(i);
    }
}
