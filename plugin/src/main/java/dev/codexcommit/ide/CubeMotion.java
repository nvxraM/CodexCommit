package dev.codexcommit.ide;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Fixed, local vector geometry and motion for the supplied three-cube logo. */
final class CubeMotion {
    static final double PERIOD_SECONDS = 9.6;
    static final double RETURN_SECONDS = .72;
    private static final double TAU = 2 * Math.PI;
    private static final double EXTENT = 2.16;
    private static final Vec DIAGONAL = new Vec(1, -1, -1).normalized();
    private static final Vec VIEW = new Vec(.4, .4, -1).normalized();
    private static final Vec TILT_AXIS = DIAGONAL.cross(VIEW).normalized();
    private static final double TILT_ANGLE = Math.acos(DIAGONAL.dot(VIEW));
    private static final Vec LIGHT = new Vec(-.48, .85, -.66).normalized();
    private static final Vec HALF_LIGHT = LIGHT.add(VIEW).normalized();
    private static final Vec FILL_LIGHT = new Vec(.35, -.2, .75);
    private static final Quaternion IDENTITY = new Quaternion(1, 0, 0, 0);
    private static final Pose HOME = new Pose(IDENTITY, IDENTITY, IDENTITY);

    record Vec(double x, double y, double z) {
        Vec add(Vec value) { return new Vec(x + value.x, y + value.y, z + value.z); }
        Vec scale(double value) { return new Vec(x * value, y * value, z * value); }
        double dot(Vec value) { return x * value.x + y * value.y + z * value.z; }
        Vec cross(Vec value) { return new Vec(y * value.z - z * value.y, z * value.x - x * value.z, x * value.y - y * value.x); }
        Vec normalized() {
            double length = Math.sqrt(dot(this));
            return length < 1e-15 ? new Vec(0, 0, 0) : scale(1 / length);
        }
    }

    record Quaternion(double w, double x, double y, double z) {
        Quaternion normalized() {
            double length = Math.sqrt(dot(this));
            return length < 1e-15 ? IDENTITY : new Quaternion(w / length, x / length, y / length, z / length);
        }
        double dot(Quaternion value) { return w * value.w + x * value.x + y * value.y + z * value.z; }
        Quaternion scale(double value) { return new Quaternion(w * value, x * value, y * value, z * value); }
        Quaternion add(Quaternion value) { return new Quaternion(w + value.w, x + value.x, y + value.y, z + value.z); }
        Quaternion conjugate() { return new Quaternion(w, -x, -y, -z); }
        Quaternion multiply(Quaternion value) {
            return new Quaternion(w * value.w - x * value.x - y * value.y - z * value.z,
                    w * value.x + x * value.w + y * value.z - z * value.y,
                    w * value.y - x * value.z + y * value.w + z * value.x,
                    w * value.z + x * value.y - y * value.x + z * value.w);
        }
        Vec rotate(Vec value) {
            Vec vector = new Vec(x, y, z);
            Vec cross = vector.cross(value).scale(2);
            return value.add(cross.scale(w)).add(vector.cross(cross));
        }
        Quaternion pow(double exponent) {
            Quaternion value = normalized();
            if (value.w < 0) value = value.scale(-1);
            double length = Math.sqrt(value.x * value.x + value.y * value.y + value.z * value.z);
            if (length < 1e-12) return IDENTITY;
            double angle = Math.atan2(length, value.w) * exponent;
            double sine = Math.sin(angle) / length;
            return new Quaternion(Math.cos(angle), value.x * sine, value.y * sine, value.z * sine);
        }
        static Quaternion around(Vec axis, double angle) {
            Vec unit = axis.normalized();
            double sine = Math.sin(angle / 2);
            return new Quaternion(Math.cos(angle / 2), unit.x * sine, unit.y * sine, unit.z * sine);
        }
        static Quaternion slerp(Quaternion from, Quaternion to, double fraction) {
            if (fraction <= 0) return from;
            if (fraction >= 1) return to;
            double dot = from.dot(to);
            if (dot < 0) { to = to.scale(-1); dot = -dot; }
            dot = Math.max(-1, Math.min(1, dot));
            if (dot > .9995) return from.scale(1 - fraction).add(to.scale(fraction)).normalized();
            double angle = Math.acos(dot), sine = Math.sin(angle);
            return from.scale(Math.sin((1 - fraction) * angle) / sine)
                    .add(to.scale(Math.sin(fraction * angle) / sine)).normalized();
        }
    }

    record Pose(Quaternion outer, Quaternion middle, Quaternion inner) {
        Quaternion cube(int index) { return index == 0 ? outer : index == 1 ? middle : inner; }
    }

    static Pose home() { return HOME; }

    /** The approved 9.6-second curve, with continuous opposite inner rotations. */
    static Pose busyPose(double seconds) {
        double time = Math.max(0, seconds) % PERIOD_SECONDS;
        double u = time / PERIOD_SECONDS;
        double tilt = u < .3 ? smooth(u / .3) : u > .7 ? smooth((1 - u) / .3) : 1;
        Vec axis = DIAGONAL.scale(tilt).add(new Vec(0, 1 - tilt, 0)).normalized();
        double angle = TAU * 4 * (u - Math.sin(TAU * u) / TAU);
        Quaternion outer = Quaternion.around(TILT_AXIS, TILT_ANGLE * tilt)
                .multiply(Quaternion.around(axis, angle)).normalized();
        double phase = TAU * time / 3.2;
        return new Pose(outer, Quaternion.around(new Vec(1, 0, 0), phase),
                Quaternion.around(new Vec(1, 0, 0), -phase));
    }

    static Pose blend(Pose from, Pose to, double fraction) {
        return new Pose(Quaternion.slerp(from.outer, to.outer, fraction),
                Quaternion.slerp(from.middle, to.middle, fraction), Quaternion.slerp(from.inner, to.inner, fraction));
    }

    /** Match the current angular velocity at the first spherical Bezier control point. */
    static Pose returnControl(Pose start, Pose ahead, double sampleSeconds) {
        double factor = RETURN_SECONDS / (3 * sampleSeconds);
        return new Pose(control(start.outer, ahead.outer, factor), control(start.middle, ahead.middle, factor),
                control(start.inner, ahead.inner, factor));
    }

    private static Quaternion control(Quaternion start, Quaternion ahead, double factor) {
        return start.multiply(start.conjugate().multiply(ahead).pow(factor)).normalized();
    }

    /** Shortest-arc spherical Bezier interpolation reaches home with zero final angular velocity. */
    static Pose returnPose(Pose start, Pose control, double fraction) {
        if (fraction <= 0) return start;
        if (fraction >= 1) return HOME;
        return new Pose(bezier(start.outer, control.outer, fraction), bezier(start.middle, control.middle, fraction),
                bezier(start.inner, control.inner, fraction));
    }

    private static Quaternion bezier(Quaternion start, Quaternion control, double fraction) {
        Quaternion a = Quaternion.slerp(start, control, fraction);
        Quaternion b = Quaternion.slerp(control, IDENTITY, fraction);
        Quaternion c = Quaternion.slerp(b, IDENTITY, fraction);
        return Quaternion.slerp(Quaternion.slerp(a, b, fraction), c, fraction);
    }

    static double smooth(double fraction) {
        double value = Math.max(0, Math.min(1, fraction));
        return value * value * value * (value * (value * 6 - 15) + 10);
    }

    private record Cube(double size, double beam, Vec color) { }
    private record Face(int cube, Vec[] points, Vec normal, Vec color) { }
    private static final Face[] MESH = mesh();

    private static Face[] mesh() {
        Cube[] cubes = { new Cube(2, .112, new Vec(236 / 255.0, 39 / 255.0, 196 / 255.0)),
                new Cube(.8, .074, new Vec(121 / 255.0, 96 / 255.0, 235 / 255.0)),
                new Cube(.258, .033, new Vec(163 / 255.0, 185 / 255.0, 1)) };
        int[][] ids = { {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6} };
        List<Face> result = new ArrayList<>();
        for (int index = 0; index < cubes.length; index++) {
            Cube cube = cubes[index];
            for (int axis = 0; axis < 3; axis++) {
                int a = (axis + 1) % 3, b = (axis + 2) % 3;
                for (int sa : new int[]{-1, 1}) for (int sb : new int[]{-1, 1}) {
                    double[] centre = new double[3], extent = {cube.beam / 2, cube.beam / 2, cube.beam / 2};
                    centre[a] = sa * cube.size / 2; centre[b] = sb * cube.size / 2;
                    extent[axis] = cube.size / 2 + cube.beam / 2;
                    Vec[] points = new Vec[8];
                    int offset = 0;
                    for (int z : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int x : new int[]{-1, 1})
                        points[offset++] = new Vec(x * extent[0] + centre[0], y * extent[1] + centre[1], z * extent[2] + centre[2]);
                    for (int face = 0; face < ids.length; face++) {
                        double[] normal = new double[3]; normal[face / 2] = face % 2 == 0 ? -1 : 1;
                        Vec[] vertices = new Vec[4];
                        for (int vertex = 0; vertex < 4; vertex++) vertices[vertex] = points[ids[face][vertex]];
                        result.add(new Face(index, vertices, new Vec(normal[0], normal[1], normal[2]), cube.color));
                    }
                }
            }
        }
        return result.toArray(Face[]::new);
    }

    /** Reuse projected paths and sorting storage. All drawing remains vector and follows the display scale. */
    static final class Renderer {
        private static final Comparator<ProjectedFace> DEPTH = Comparator.comparingDouble(face -> face.depth);
        private final ProjectedFace[] faces = new ProjectedFace[MESH.length];
        private final ProjectedFace[] order = new ProjectedFace[MESH.length];
        private Pose projectedPose;

        Renderer() {
            for (int index = 0; index < faces.length; index++) order[index] = faces[index] = new ProjectedFace();
        }

        synchronized void paint(Graphics graphics, int x, int y, int width, int height, Pose pose) {
            if (!(graphics instanceof Graphics2D original) || width <= 0 || height <= 0) return;
            if (!pose.equals(projectedPose)) project(pose);
            Graphics2D target = (Graphics2D) original.create();
            try {
                target.translate(x, y); target.clipRect(0, 0, width, height); target.scale(width, height);
                target.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                target.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                for (ProjectedFace face : order) if (face.visible) {
                    target.setColor(face.color); target.fill(face.path);
                }
            } finally { target.dispose(); }
        }

        private void project(Pose pose) {
            for (int index = 0; index < MESH.length; index++) {
                Face face = MESH[index]; ProjectedFace projected = faces[index];
                Quaternion rotation = pose.cube(face.cube);
                Vec normal = rotation.rotate(face.normal);
                projected.visible = normal.dot(VIEW) > 1e-10;
                if (!projected.visible) continue;
                Vec centre = new Vec(0, 0, 0);
                projected.path.reset();
                for (int vertex = 0; vertex < face.points.length; vertex++) {
                    Vec point = rotation.rotate(face.points[vertex]);
                    centre = centre.add(point.scale(.25));
                    double px = .5 + (point.x + .4 * point.z) / (2 * EXTENT);
                    double py = .5 - (point.y + .4 * point.z) / (2 * EXTENT);
                    if (vertex == 0) projected.path.moveTo(px, py); else projected.path.lineTo(px, py);
                }
                projected.path.closePath();
                projected.depth = centre.dot(VIEW);
                projected.color = shade(face.color, normal, centre);
            }
            Arrays.sort(order, DEPTH);
            projectedPose = pose;
        }

        private static Color shade(Vec base, Vec normal, Vec point) {
            double lambert = Math.max(0, normal.dot(LIGHT));
            double specular = Math.pow(Math.max(0, normal.dot(HALF_LIGHT)), 36) * .16;
            double fill = Math.max(0, normal.dot(FILL_LIGHT));
            double tone = 1.01 + .065 * point.y - .055 * point.x;
            double glow = Math.exp(-point.dot(point) * 1.7);
            return new Color(channel((base.x * (.32 + 1.10 * lambert) + specular * .70 + fill * .035) * tone + glow * .012),
                    channel((base.y * (.32 + 1.10 * lambert) + specular * .76 + fill * .025) * tone + glow * .014),
                    channel((base.z * (.32 + 1.10 * lambert) + specular + fill * .085) * tone + glow * .035));
        }

        private static float channel(double value) { return (float) Math.max(0, Math.min(1, value)); }
        private static final class ProjectedFace {
            final Path2D.Double path = new Path2D.Double();
            boolean visible;
            double depth;
            Color color;
        }
    }
}
