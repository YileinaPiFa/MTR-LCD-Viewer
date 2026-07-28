package lcdviewer.mock;

import lcdviewer.mock.MtrWorld.PathData;
import lcdviewer.mock.MtrWorld.PlatformInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 ANTE 传给 JS 的 train 对象。
 * 方法名与 ANTE 文档 js-train.md 严格一致。
 */
public final class MockTrain {

    public String trainTypeId = "gzmtr_a9lcd";
    public String baseTrainType = "train_9_2";
    public long id = 1234567890123L;

    public int trainCars = 6;
    public int spacing = 21;
    public int width = 2;

    public boolean isOnRoute = true;
    public boolean isReversed = false;
    public boolean shouldRender = true;

    public float speed = 0f;                 // m/tick
    public float doorValue = 0f;             // 0~1
    public boolean isDoorOpening = false;

    public boolean[] doorLeftOpen = new boolean[]{false};
    public boolean[] doorRightOpen = new boolean[]{false};

    public double railProgress = 0;

    /** 全程站台（可能跨多条线路） */
    public final List<PlatformInfo> allPlatforms = new ArrayList<>();
    public int allPlatformsNextIndex = 0;

    /** 本线路站台 */
    public final List<PlatformInfo> thisRoutePlatforms = new ArrayList<>();
    public int thisRoutePlatformsNextIndex = 0;

    public final List<PathData> path = new ArrayList<>();

    public MtrWorld.Depot depot = new MtrWorld.Depot(1L, "车辆段|Depot");

    // ------------------------------------------------------ 基础属性

    public String trainTypeId() {
        return trainTypeId;
    }

    public String baseTrainType() {
        return baseTrainType;
    }

    public long id() {
        return id;
    }

    public int trainCars() {
        return trainCars;
    }

    public int spacing() {
        return spacing;
    }

    public int width() {
        return width;
    }

    public float accelerationConstant() {
        return 0.01f;
    }

    public boolean manualAllowed() {
        return false;
    }

    public int maxManualSpeed() {
        return 4;
    }

    public int manualToAutomaticTime() {
        return 0;
    }

    public boolean isCurrentlyManual() {
        return false;
    }

    // ------------------------------------------------------ 渲染相关

    public boolean shouldRender() {
        return shouldRender;
    }

    /** ANTE 中已废弃，一律返回 true。 */
    public boolean shouldRenderDetail() {
        return true;
    }

    // ------------------------------------------------------ 运行状态

    public boolean isOnRoute() {
        return isOnRoute;
    }

    public boolean isReversed() {
        return isReversed;
    }

    public float speed() {
        return speed;
    }

    public double railProgress() {
        return railProgress;
    }

    public float doorValue() {
        return doorValue;
    }

    public boolean isDoorOpening() {
        return isDoorOpening;
    }

    // ------------------------------------------------------ 路径 / 站台

    public List<PathData> path() {
        return path;
    }

    public double getRailProgress(int car) {
        return railProgress;
    }

    public int getRailIndex(double railProgress, boolean roundDown) {
        return 0;
    }

    public float getRailSpeed(int railIndex) {
        return 2f;
    }

    public List<PlatformInfo> getAllPlatforms() {
        return allPlatforms;
    }

    public int getAllPlatformsNextIndex() {
        return allPlatformsNextIndex;
    }

    public List<PlatformInfo> getThisRoutePlatforms() {
        return thisRoutePlatforms;
    }

    public int getThisRoutePlatformsNextIndex() {
        return thisRoutePlatformsNextIndex;
    }

    // ---------------------------------------------- 部分脚本会访问的扩展

    public MtrWorld.Depot getDepot() {
        return depot;
    }

    public MtrWorld.Siding siding = new MtrWorld.Siding(9001L, "01号停车线");

    /** 停车线对象。脚本会用 train.siding().id 反查车厂。 */
    public MtrWorld.Siding siding() {
        return siding;
    }

    /** 指定位置的侧滚角，用于倾斜显示。静态预览恒为 0。 */
    public float getRollAngleAt(double railProgress) {
        return 0f;
    }

    public float getRollAngleAt(int carIndex, double railProgress) {
        return 0f;
    }
    /** 保证 doorLeftOpen / doorRightOpen 数组长度与车厢数一致。 */
    public void syncDoorArrays() {
        if (doorLeftOpen.length != trainCars) {
            boolean[] l = new boolean[trainCars];
            boolean[] r = new boolean[trainCars];
            for (int i = 0; i < trainCars; i++) {
                l[i] = doorLeftOpen.length > 0 && doorLeftOpen[Math.min(i, doorLeftOpen.length - 1)];
                r[i] = doorRightOpen.length > 0 && doorRightOpen[Math.min(i, doorRightOpen.length - 1)];
            }
            doorLeftOpen = l;
            doorRightOpen = r;
        }
    }

    public void setDoorSide(boolean left, boolean right) {
        syncDoorArrays();
        for (int i = 0; i < trainCars; i++) {
            doorLeftOpen[i] = left;
            doorRightOpen[i] = right;
        }
    }
}
