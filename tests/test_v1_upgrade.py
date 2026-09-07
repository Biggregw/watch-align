import unittest

import cv2
import numpy as np

from v1_upgrade import perspective_diagnostics, stable_ecc_refinement


def synthetic_watch(size: int = 800):
    image = np.zeros((size, size, 3), np.uint8) + 30
    centre = size // 2
    radius = 250
    cv2.circle(image, (centre, centre), radius, (220, 220, 220), 4)
    for i in range(12):
        angle = np.deg2rad(i * 30.0)
        x = int(round(centre + 195 * np.sin(angle)))
        y = int(round(centre - 195 * np.cos(angle)))
        cv2.circle(image, (x, y), 10, (230, 230, 230), -1)
    return image, (float(centre), float(centre), float(radius))


class V1GeometryTests(unittest.TestCase):
    def test_perspective_guard_separates_front_from_compressed_view(self):
        reference, circle = synthetic_watch()
        front = perspective_diagnostics(reference, circle)
        compression = np.array([[1.0, 0.0, 0.0], [0.0, 0.92, 32.0]], dtype=np.float32)
        compressed = cv2.warpAffine(reference, compression, (800, 800))
        skewed = perspective_diagnostics(compressed, circle)
        self.assertTrue(front["available"])
        self.assertTrue(skewed["available"])
        self.assertLess(front["tilt_deg"], 6.0)
        self.assertGreater(skewed["tilt_deg"], 15.0)
        self.assertGreater(skewed["tilt_deg"] - front["tilt_deg"], 10.0)

    def test_stable_ecc_recovers_small_safe_residual(self):
        reference, circle = synthetic_watch()
        target = cv2.getRotationMatrix2D((400, 400), 0.25, 1.003)
        target[0, 2] += 2.0
        target[1, 2] -= 1.0
        candidate = cv2.warpAffine(reference, cv2.invertAffineTransform(target), (800, 800))
        refined, metrics = stable_ecc_refinement(reference, candidate, np.eye(2, 3, dtype=np.float64), circle)
        self.assertTrue(metrics["attempted"])
        self.assertTrue(metrics["applied"])
        self.assertGreater(metrics["improvement"], 0.05)
        self.assertLess(abs(metrics["rotation_correction_deg"]), 0.65)
        self.assertLess(metrics["translation_px"], 20.0)
        self.assertEqual(refined.shape, (2, 3))


if __name__ == "__main__":
    unittest.main()
