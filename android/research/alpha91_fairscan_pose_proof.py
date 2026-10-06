#!/usr/bin/env python3
"""
Alpha91 watch-pose oracle proof, adapted from FairScan's camera-intrinsics / pinhole
perspective model (Perspective.kt, GPL-3.0).

Purpose
-------
This is RESEARCH-ONLY. It does not alter Alpha90 production code.

FairScan's document-specific four-corner observation model is replaced with watch-specific
trusted geometry:
  * projected dial outer ring (dial plane)
  * projected inner-bezel ring (front plane)
  * a single absolute 12-o'clock direction anchor

Hour-marker centres are HOLDOUT ONLY and never participate in fitting. The script compares
predicted marker locations with manually inspected genuine-watch marker centres.

The goal is a fast go/no-go test for a physical camera model before Android integration.

FairScan reference:
  https://github.com/pynicolas/FairScan/blob/main/imageprocessing/src/main/java/org/fairscan/imageprocessing/Perspective.kt
"""

import json
import math
from dataclasses import dataclass

import cv2
import numpy as np
from scipy.optimize import least_squares


@dataclass(frozen=True)
class CameraIntrinsics:
    """Same focal-length conversion used by FairScan."""
    focal_length_mm: float
    sensor_width_mm: float

    def focal_length_px(self, image_width_px: int) -> float:
        return self.focal_length_mm / self.sensor_width_mm * image_width_px


# Genuine oracle observations collected from the frozen Alpha90 validation pack.
# Ellipse tuple: ((cx, cy), (diameter_a, diameter_b), angle_deg)
DIAL_ELLIPSES = {
    "EXT_EXT_GEN_BLRO_WEX_01": ((756.3635, 557.5099), (278.0684, 304.5063), 103.8452),
    "EXT_EXT_GEN_BLRO_WEX_02": ((758.9951, 558.4521), (311.9083, 323.4848), 79.6023),
    "POOL_GEN_HO_01": ((677.2990, 767.4677), (408.2277, 424.2052), 160.5453),
    "POOL_GEN_HO_02": ((1008.0164, 612.7130), (280.9004, 304.8970), 76.8809),
}

BEZEL_ELLIPSES = {
    "EXT_EXT_GEN_BLRO_WEX_01": ((754.0453, 556.3372), (304.7649, 323.5672), 177.7343),
    "EXT_EXT_GEN_BLRO_WEX_02": ((756.7130, 558.9326), (350.6725, 356.5676), 178.2416),
    "POOL_GEN_HO_01": ((673.2114, 753.9669), (448.6257, 461.0948), 91.8663),
    "POOL_GEN_HO_02": ((1012.4217, 611.4498), (317.7021, 325.6230), 144.7885),
}

PINIONS = {
    "EXT_EXT_GEN_BLRO_WEX_01": (760.94, 553.19),
    "EXT_EXT_GEN_BLRO_WEX_02": (754.92, 559.13),
    "POOL_GEN_HO_01": (675.86, 761.15),
    "POOL_GEN_HO_02": (1012.90, 616.12),
}

IMAGE_SIZES = {
    "EXT_EXT_GEN_BLRO_WEX_01": (1500, 1125),
    "EXT_EXT_GEN_BLRO_WEX_02": (1500, 1125),
    "POOL_GEN_HO_01": (1362, 1600),
    "POOL_GEN_HO_02": (1600, 1280),
}

# Manually inspected genuine marker centres. 3 is date window and intentionally absent.
MARKERS = {
    "EXT_EXT_GEN_BLRO_WEX_01": {
        12:(756.8387,451.5183), 1:(815.8630,460.0548), 2:(857.0499,502.7230),
        4:(858.6667,613.9179), 5:(816.6581,655.8803), 6:(756.8693,665.4824),
        7:(698.8188,657.1790), 8:(657.7928,615.2162), 9:(649.9404,557.1805),
        10:(658.8919,500.5608), 11:(700.9694,458.4424),
    },
    "EXT_EXT_GEN_BLRO_WEX_02": {
        12:(755.1414,435.9236), 1:(817.9364,450.9500), 2:(864.3492,497.5034),
        4:(866.1656,622.4268), 5:(817.8194,670.3928), 6:(754.5000,679.0000),
        7:(691.3656,670.5793), 8:(644.9957,624.3914), 9:(635.9348,560.4288),
        10:(644.1805,497.3568), 11:(691.0065,450.7146),
    },
    "POOL_GEN_HO_01": {
        12:(677.6432,598.1876), 1:(759.5636,617.0233), 2:(817.4016,682.2065),
        4:(823.5938,838.9770), 5:(764.3024,900.2544), 6:(680.3628,912.4449),
        7:(596.4701,901.0120), 8:(533.3570,838.7688), 9:(522.0028,756.7507),
        10:(534.0978,674.0949), 11:(595.2073,614.9613),
    },
    "POOL_GEN_HO_02": {
        12:(1032.4122,503.1101), 1:(1086.0408,527.1073), 2:(1119.6300,577.1675),
        4:(1096.9577,690.1784), 5:(1046.6811,723.2254), 6:(988.0037,719.5746),
        7:(932.3599,699.7565), 8:(899.0416,649.4886), 9:(902.8168,591.4489),
        10:(921.9333,536.7011), 11:(971.9641,503.6659),
    },
}

# Oracle roll only. It is deliberately the only marker-derived fitting input and exists solely
# to test perspective/pose independently of Alpha90's known image-up phase lock.
PHASE_DEG = {
    "EXT_EXT_GEN_BLRO_WEX_01": -1.1736019707,
    "EXT_EXT_GEN_BLRO_WEX_02": 0.1029794029,
    "POOL_GEN_HO_01": 0.6269236752,
    "POOL_GEN_HO_02": 9.7960646583,
}

MARKER_RADII = {1:.816, 2:.816, 4:.816, 5:.816, 6:.758, 7:.816, 8:.816,
                9:.758, 10:.816, 11:.816, 12:.750}


def ellipse_params(ellipse):
    (cx, cy), (w, h), angle = ellipse
    return cx, cy, w/2.0, h/2.0, math.radians(angle)


def ellipse_signed_distance(x, y, ellipse):
    cx, cy, a, b, theta = ellipse_params(ellipse)
    ca, sa = np.cos(theta), np.sin(theta)
    dx, dy = x-cx, y-cy
    u, v = ca*dx + sa*dy, -sa*dx + ca*dy
    rho = np.sqrt((u/a)**2 + (v/b)**2)
    return (rho-1.0) * 0.5*(a+b)


def ray_to_ellipse(origin, direction, ellipse):
    ox, oy = origin
    dx, dy = direction
    cx, cy, a, b, theta = ellipse_params(ellipse)
    ca, sa = math.cos(theta), math.sin(theta)
    X, Y = ca*(ox-cx)+sa*(oy-cy), -sa*(ox-cx)+ca*(oy-cy)
    U, V = ca*dx+sa*dy, -sa*dx+ca*dy
    A = U*U/(a*a) + V*V/(b*b)
    B = 2.0*(X*U/(a*a) + Y*V/(b*b))
    C = X*X/(a*a) + Y*Y/(b*b) - 1.0
    disc = B*B - 4*A*C
    if disc < 0:
        return None
    roots = [(-B+math.sqrt(disc))/(2*A), (-B-math.sqrt(disc))/(2*A)]
    positive = [t for t in roots if t > 0]
    if not positive:
        return None
    t = min(positive)
    return np.array([ox+t*dx, oy+t*dy])


def rotation_matrix(rvec):
    R, _ = cv2.Rodrigues(np.asarray(rvec, dtype=np.float64).reshape(3,1))
    return R


def project(camera, points_xyz, image_size):
    """FairScan-style pinhole projection. principal point = image centre, zero skew."""
    log_f = camera[0]
    rvec = camera[1:4]
    tx, ty = camera[4:6]
    log_tz = camera[6]
    f = math.exp(log_f)
    tz = math.exp(log_tz)
    R = rotation_matrix(rvec)
    P = (R @ points_xyz.T).T + np.array([tx, ty, tz])
    w, h = image_size
    return np.c_[w/2.0 + f*P[:,0]/P[:,2], h/2.0 + f*P[:,1]/P[:,2]]


def initial_camera(case):
    ellipse = DIAL_ELLIPSES[case]
    image_size = IMAGE_SIZES[case]
    phase = math.radians(PHASE_DEG[case])
    cx, cy, a, b, _ = ellipse_params(ellipse)
    projected_r = 0.5*(a+b)
    f = max(image_size) * 1.5
    tz = f/projected_r
    tx = (cx-image_size[0]/2.0)*tz/f
    ty = (cy-image_size[1]/2.0)*tz/f
    return np.array([math.log(f), 0.0, 0.0, phase, tx, ty, math.log(tz)])


def fit_shared_geometry():
    cases = list(DIAL_ELLIPSES)
    angles = np.linspace(0, 2*math.pi, 240, endpoint=False)
    dial = np.c_[np.cos(angles), np.sin(angles), np.zeros_like(angles)]

    orientation_targets = {}
    for case in cases:
        phase = math.radians(PHASE_DEG[case])
        direction = np.array([math.sin(phase), -math.cos(phase)])
        target = ray_to_ellipse(np.array(PINIONS[case]), direction, DIAL_ELLIPSES[case])
        if target is None:
            raise RuntimeError(f"Could not build orientation target for {case}")
        orientation_targets[case] = target

    # Shared watch geometry: bezel radius / dial radius, and bezel-front depth / dial radius.
    x0 = np.r_[1.14, 0.12, *[initial_camera(c) for c in cases]]

    def unpack(x):
        bezel_radius, bezel_depth = x[0], x[1]
        cameras = {}
        off = 2
        for case in cases:
            cameras[case] = x[off:off+7]
            off += 7
        return bezel_radius, bezel_depth, cameras

    def residual(x):
        bezel_radius, bezel_depth, cameras = unpack(x)
        bezel = np.c_[bezel_radius*np.cos(angles), bezel_radius*np.sin(angles),
                      np.full_like(angles, -bezel_depth)]
        result = []
        for case in cases:
            cam = cameras[case]
            qd = project(cam, dial, IMAGE_SIZES[case])
            qb = project(cam, bezel, IMAGE_SIZES[case])
            result.extend(ellipse_signed_distance(qd[:,0], qd[:,1], DIAL_ELLIPSES[case]))
            result.extend(ellipse_signed_distance(qb[:,0], qb[:,1], BEZEL_ELLIPSES[case]))

            # One directional anchor only; marker positions remain holdout.
            q12 = project(cam, np.array([[0.0, -1.0, 0.0]]), IMAGE_SIZES[case])[0]
            result.extend((q12-orientation_targets[case])*3.0)
        return np.asarray(result)

    lower = [1.05, 0.005]
    upper = [1.30, 0.40]
    for case in cases:
        m = max(IMAGE_SIZES[case])
        lower += [math.log(m*0.25), -1.0, -1.0, -1.5, -20.0, -20.0, math.log(2.0)]
        upper += [math.log(m*10.0), 1.0, 1.0, 1.5, 20.0, 20.0, math.log(100.0)]

    solution = least_squares(
        residual, x0, bounds=(np.asarray(lower), np.asarray(upper)),
        loss="soft_l1", f_scale=1.0, max_nfev=50000,
        xtol=1e-12, ftol=1e-12, gtol=1e-12,
    )
    bezel_radius, bezel_depth, cameras = unpack(solution.x)
    return solution, bezel_radius, bezel_depth, cameras


def evaluate():
    solution, bezel_radius, bezel_depth, cameras = fit_shared_geometry()
    print(f"shared bezel_radius={bezel_radius:.6f} dialR, bezel_depth={bezel_depth:.6f} dialR")
    print(f"fit cost={solution.cost:.3f}, optimality={solution.optimality:.6f}")

    all_errors = []
    for case, camera in cameras.items():
        errors = []
        for hour in (1,2,4,5,6,7,8,9,10,11):  # 12 is orientation anchor; 3 is date.
            angle = math.radians(hour*30.0 - 90.0)
            radius = MARKER_RADII[hour]
            point = np.array([[radius*math.cos(angle), radius*math.sin(angle), 0.0]])
            pred = project(camera, point, IMAGE_SIZES[case])[0]
            actual = np.asarray(MARKERS[case][hour])
            errors.append(float(np.linalg.norm(pred-actual)))
        all_errors.extend(errors)
        print(f"{case}: marker holdout mean={np.mean(errors):.3f}px "
              f"median={np.median(errors):.3f}px max={np.max(errors):.3f}px")

    print(f"ALL HOLDOUTS: mean={np.mean(all_errors):.3f}px "
          f"median={np.median(all_errors):.3f}px max={np.max(all_errors):.3f}px")
    print("NOTE: If shared geometry lands on bounds or holdout error remains several pixels, "
          "the observed rings/physical stack need better definition before Android integration.")


if __name__ == "__main__":
    evaluate()
