package lcdviewer.ante;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTE 的模型/矩阵相关 API 的轻量替身。
 *
 * 本程序不做 3D 渲染 —— LCD 的画面完全由脚本用 Java2D 画在 GraphicsTexture 上。
 * 因此这些类只需保证脚本调用链不报错，并记录顶点/贴图信息供调试。
 */
public final class AnteModel {

    private AnteModel() {
    }

    // ----------------------------------------------------- RawMeshBuilder

    public static final class RawMeshBuilder {
        public final int vertsPerFace;
        public final String stage;
        public final Object textureId;

        private float r = 1, g = 1, b = 1, a = 1;
        private float vx, vy, vz, nx, ny, nz, u, v;
        private final List<float[]> vertices = new ArrayList<>();

        public RawMeshBuilder(int vertsPerFace, String stage, Object textureId) {
            this.vertsPerFace = vertsPerFace;
            this.stage = stage;
            this.textureId = textureId;
        }

        public RawMeshBuilder color(double r, double g, double b, double a) {
            this.r = (float) r;
            this.g = (float) g;
            this.b = (float) b;
            this.a = (float) a;
            return this;
        }

        public RawMeshBuilder vertex(double x, double y, double z) {
            this.vx = (float) x;
            this.vy = (float) y;
            this.vz = (float) z;
            return this;
        }

        public RawMeshBuilder normal(double x, double y, double z) {
            this.nx = (float) x;
            this.ny = (float) y;
            this.nz = (float) z;
            return this;
        }

        public RawMeshBuilder uv(double u, double v) {
            this.u = (float) u;
            this.v = (float) v;
            return this;
        }

        public RawMeshBuilder endVertex() {
            vertices.add(new float[]{vx, vy, vz, nx, ny, nz, u, v, r, g, b, a});
            return this;
        }

        public RawMesh getMesh() {
            return new RawMesh(new ArrayList<>(vertices), stage, textureId);
        }
    }

    public static final class RawMesh {
        public final List<float[]> vertices;
        public final String stage;
        public Object textureId;

        RawMesh(List<float[]> vertices, String stage, Object textureId) {
            this.vertices = vertices;
            this.stage = stage;
            this.textureId = textureId;
        }
    }

    // ---------------------------------------------------------- RawModel

    public static final class RawModel {
        public final List<RawMesh> meshes = new ArrayList<>();

        public RawModel append(RawMesh mesh) {
            if (mesh != null) meshes.add(mesh);
            return this;
        }

        public RawModel append(RawModel other) {
            if (other != null) meshes.addAll(other.meshes);
            return this;
        }

        public RawModel triangulate() {
            return this;
        }

        public RawModel applyTranslation(double x, double y, double z) {
            return this;
        }

        // ---- 以下为 3D 模型变换类方法：本程序不做 3D 渲染，保持链式可调用即可 ----
        public RawModel applyUVMirror(boolean u, boolean v) {
            return this;
        }

        public RawModel applyScale(double x, double y, double z) {
            return this;
        }

        public RawModel applyRotation(double x, double y, double z, double angle) {
            return this;
        }

        public RawModel applyMirror(boolean x, boolean y, boolean z) {
            return this;
        }

        public RawModel applyUVOffset(double u, double v) {
            return this;
        }

        public RawModel sourceVertexColor() {
            return this;
        }

        public RawModel distinct() {
            return this;
        }

        public RawModel clone_() {
            return this;
        }

        public RawModel copy() {
            RawModel m = new RawModel();
            m.meshes.addAll(this.meshes);
            return m;
        }

        public RawModel replaceAllTexture(Object tex) {
            for (RawMesh m : meshes) m.textureId = tex;
            return this;
        }

        public RawModel replaceAllTexture(String name, Object tex) {
            for (RawMesh m : meshes) m.textureId = tex;
            return this;
        }

        public RawModel setTextureAll(Object tex) {
            return replaceAllTexture(tex);
        }

        public int size() {
            return meshes.size();
        }
    }

    // ------------------------------------------------------ ModelCluster

    /** 上传后的模型句柄。 */
    public static final class ModelCluster {
        public final RawModel source;
        public Object textureId;

        ModelCluster(RawModel source) {
            this.source = source;
        }

        public ModelCluster copyForMaterialChanges() {
            ModelCluster c = new ModelCluster(source);
            c.textureId = this.textureId;
            return c;
        }

        public ModelCluster replaceAllTexture(Object newTexture) {
            this.textureId = newTexture;
            return this;
        }

        public ModelCluster replaceAllTexture(String name, Object newTexture) {
            this.textureId = newTexture;
            return this;
        }

        public void close() {
        }
    }

    // ------------------------------------------------------ ModelManager

    public static final class ModelManager {
        public static ModelCluster uploadVertArrays(RawModel model) {
            return new ModelCluster(model);
        }

        public static RawModel loadRawModel(Object... args) {
            return new RawModel();
        }

        public static ModelCluster uploadVertArrays(RawModel model, Object... extra) {
            return new ModelCluster(model);
        }
    }

    // ---------------------------------------------------------- Matrices

    /** 只需支持链式调用不报错。 */
    public static final class Matrices {
        public Matrices() {
        }

        public Matrices pushPose() {
            return this;
        }

        public Matrices popPose() {
            return this;
        }

        public Matrices popPushPose() {
            return this;
        }

        public Matrices setIdentity() {
            return this;
        }

        public Matrices translate(double x, double y, double z) {
            return this;
        }

        public Matrices scale(double x, double y, double z) {
            return this;
        }

        public Matrices rotateX(double r) {
            return this;
        }

        public Matrices rotateY(double r) {
            return this;
        }

        public Matrices rotateZ(double r) {
            return this;
        }
    }

    // ---------------------------------------------------------- Vector3f

    public static final class Vector3f {
        private float x, y, z;

        public Vector3f() {
        }

        public Vector3f(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
        }

        public Vector3f(Vector3f o) {
            this.x = o.x;
            this.y = o.y;
            this.z = o.z;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float z() {
            return z;
        }

        public Vector3f add(Vector3f o) {
            x += o.x;
            y += o.y;
            z += o.z;
            return this;
        }

        public Vector3f sub(Vector3f o) {
            x -= o.x;
            y -= o.y;
            z -= o.z;
            return this;
        }

        public Vector3f rotX(double rad) {
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float ny = y * c - z * s;
            float nz = y * s + z * c;
            y = ny;
            z = nz;
            return this;
        }

        public Vector3f rotY(double rad) {
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float nx = x * c + z * s;
            float nz = -x * s + z * c;
            x = nx;
            z = nz;
            return this;
        }

        public Vector3f rotZ(double rad) {
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float nx = x * c - y * s;
            float ny = x * s + y * c;
            x = nx;
            y = ny;
            return this;
        }
    }
}
