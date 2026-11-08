// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.aim;

public final class AimMath {
   private static final double EPSILON = 1.0E-9;
   private static final double UNREACHABLE = -1000000.0;

   private AimMath() {
   }

   public static double[] rotateTowards(double[] u, double[] t, double maxDegrees) {
      if (u != null && t != null && u.length == 3 && t.length == 3) {
         double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
         double tl = Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
         if (!(ul < 1.0E-9) && !(tl < 1.0E-9)) {
            double ux = u[0] / ul;
            double uy = u[1] / ul;
            double uz = u[2] / ul;
            double tx = t[0] / tl;
            double ty = t[1] / tl;
            double tz = t[2] / tl;
            double dot = clamp(ux * tx + uy * ty + uz * tz, -1.0, 1.0);
            double angle = Math.acos(dot);
            double step = Math.min(angle, Math.toRadians(Math.max(0.0, maxDegrees)));
            if (step < 1.0E-9) {
               return new double[]{ux, uy, uz};
            }

            double ax = uy * tz - uz * ty;
            double ay = uz * tx - ux * tz;
            double az = ux * ty - uy * tx;
            double al = Math.sqrt(ax * ax + ay * ay + az * az);
            if (al < 1.0E-9) {
               if (dot > 0.0) {
                  return new double[]{ux, uy, uz};
               }

               double[] perp = perpendicular(ux, uy, uz);
               ax = perp[0];
               ay = perp[1];
               az = perp[2];
            } else {
               ax /= al;
               ay /= al;
               az /= al;
            }

            double cos = Math.cos(step);
            double sin = Math.sin(step);
            double rx = ux * cos + (ay * uz - az * uy) * sin;
            double ry = uy * cos + (az * ux - ax * uz) * sin;
            double rz = uz * cos + (ax * uy - ay * ux) * sin;
            double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
            return rl < 1.0E-9 ? new double[]{ux, uy, uz} : new double[]{rx / rl, ry / rl, rz / rl};
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double[] perpendicular(double x, double y, double z) {
      double ax = Math.abs(x);
      double ay = Math.abs(y);
      double az = Math.abs(z);
      double helperX;
      double helperY;
      double helperZ;
      if (ax <= ay && ax <= az) {
         helperX = 1.0;
         helperY = 0.0;
         helperZ = 0.0;
      } else if (ay <= az) {
         helperX = 0.0;
         helperY = 1.0;
         helperZ = 0.0;
      } else {
         helperX = 0.0;
         helperY = 0.0;
         helperZ = 1.0;
      }

      double px = y * helperZ - z * helperY;
      double py = z * helperX - x * helperZ;
      double pz = x * helperY - y * helperX;
      double pl = Math.sqrt(px * px + py * py + pz * pz);
      return pl < 1.0E-9 ? new double[]{0.0, 0.0, 1.0} : new double[]{px / pl, py / pl, pz / pl};
   }

   public static double ticksToImpact(double distance, double speed, double drag) {
      if (drag >= 0.999999999) {
         return ticksToImpact(distance, speed);
      }

      if (!(speed < 1.0E-9) && !(distance <= 0.0)) {
         double reach = speed / (1.0 - drag);
         if (distance >= reach) {
            return Math.log(1.0E-9) / Math.log(drag);
         }

         double ratio = 1.0 - distance * (1.0 - drag) / speed;
         return Math.log(ratio) / Math.log(drag);
      } else {
         return 0.0;
      }
   }

   public static double ticksToImpact(double distance, double speed) {
      return !(speed < 1.0E-9) && !(distance <= 0.0) ? distance / speed : 0.0;
   }

   public static double[] leadPoint(double[] centre, double[] targetVelocity, double ticks, double maxLeadBlocks) {
      if (centre != null && targetVelocity != null && centre.length == 3 && targetVelocity.length == 3) {
         double lx = targetVelocity[0] * ticks;
         double ly = targetVelocity[1] * ticks;
         double lz = targetVelocity[2] * ticks;
         double leadLength = Math.sqrt(lx * lx + ly * ly + lz * lz);
         if (leadLength < 1.0E-9) {
            return new double[]{centre[0], centre[1], centre[2]};
         }

         double allowed = maxLeadBlocks > 0.0 ? Math.min(leadLength, maxLeadBlocks) : leadLength;
         double scale = allowed / leadLength;
         return new double[]{centre[0] + lx * scale, centre[1] + ly * scale, centre[2] + lz * scale};
      } else {
         return centre;
      }
   }

   public static double turnBudgetDegrees(double maxTurnDegrees, double speed, double referenceSpeed, double maxScale, double hardCapDegrees) {
      double scale = 1.0;
      if (referenceSpeed > 1.0E-9 && speed > 0.0) {
         scale = clamp(speed / referenceSpeed, 1.0, Math.max(1.0, maxScale));
      }

      return clamp(maxTurnDegrees * scale, 0.0, hardCapDegrees);
   }

   public static double angleDegrees(double[] a, double[] b) {
      double al = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
      double bl = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
      if (!(al < 1.0E-9) && !(bl < 1.0E-9)) {
         double dot = clamp((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (al * bl), -1.0, 1.0);
         return Math.toDegrees(Math.acos(dot));
      } else {
         return 180.0;
      }
   }

   public static double distanceToLine(double[] origin, double[] dir, double[] point) {
      double ox = point[0] - origin[0];
      double oy = point[1] - origin[1];
      double oz = point[2] - origin[2];
      double dl = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
      if (dl < 1.0E-9) {
         return Double.MAX_VALUE;
      }

      double along = (ox * dir[0] + oy * dir[1] + oz * dir[2]) / dl;
      double px = ox - dir[0] / dl * along;
      double py = oy - dir[1] / dl * along;
      double pz = oz - dir[2] / dl * along;
      return Math.sqrt(px * px + py * py + pz * pz);
   }

   public static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public static double[] ballisticAim(double[] from, double[] target, double speed, double gravity) {
      if (from == null || target == null || from.length != 3 || target.length != 3) {
         return null;
      }

      if (speed > 1.0E-9 && Double.isFinite(speed)) {
         double dx = target[0] - from[0];
         double dy = target[1] - from[1];
         double dz = target[2] - from[2];
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         if (horizontal < 1.0E-9) {
            return new double[]{0.0, Math.signum(dy), 0.0};
         } else {
            double straightX = dx / horizontal;
            double straightZ = dz / horizontal;
            if (!(gravity > 0.0)) {
               double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
               return new double[]{dx / length, dy / length, dz / length};
            } else {
               double s2 = speed * speed;
               double a = gravity * horizontal * horizontal / (2.0 * s2);
               double discriminant = horizontal * horizontal - 4.0 * a * (a + dy);
               if (discriminant < 0.0) {
                  double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  return new double[]{dx / length, dy / length, dz / length};
               } else {
                  double tan = (horizontal - Math.sqrt(discriminant)) / (2.0 * a);
                  double cos = 1.0 / Math.sqrt(1.0 + tan * tan);
                  return new double[]{straightX * cos, tan * cos, straightZ * cos};
               }
            }
         }
      } else {
         return null;
      }
   }

   public static double[] ballisticAimWithDrag(double[] from, double[] target, double speed, double gravity, double drag) {
      if (from == null || target == null || from.length != 3 || target.length != 3) {
         return null;
      }

      if (speed > 1.0E-9 && Double.isFinite(speed)) {
         double dx = target[0] - from[0];
         double dy = target[1] - from[1];
         double dz = target[2] - from[2];
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         if (horizontal < 1.0E-9) {
            return new double[]{0.0, Math.signum(dy), 0.0};
         }

         double straightX = dx / horizontal;
         double straightZ = dz / horizontal;
         double straightLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
         double[] straight = new double[]{dx / straightLength, dy / straightLength, dz / straightLength};
         if (gravity > 0.0 && !(drag >= 0.999999999)) {
            double lowest = Math.toRadians(-60.0);
            double highest = Math.toRadians(89.0);
            double step = Math.toRadians(2.0);
            double previousAngle = Double.NaN;
            double previousError = Double.NaN;
            double bracketLow = Double.NaN;
            double bracketHigh = Double.NaN;
            double bestAngle = Double.NaN;
            double bestError = Double.MAX_VALUE;

            for (double angle = lowest; angle <= highest + 1.0E-9; angle += step) {
               double error = heightError(horizontal, dy, speed, gravity, drag, angle);
               if (!Double.isNaN(error) && !(error <= -1000000.0)) {
                  if (Math.abs(error) < Math.abs(bestError)) {
                     bestError = Math.abs(error);
                     bestAngle = angle;
                  }

                  if (!Double.isNaN(previousError) && previousError <= 0.0 && error >= 0.0 && Double.isNaN(bracketLow)) {
                     bracketLow = previousAngle;
                     bracketHigh = angle;
                  }

                  previousAngle = angle;
                  previousError = error;
               } else {
                  previousAngle = Double.NaN;
               }
            }

            if (Double.isNaN(bracketLow)) {
               if (Double.isNaN(bestAngle)) {
                  return straight;
               }

               bracketLow = bestAngle;
               bracketHigh = bestAngle;
            }

            double zero = bracketLow;

            for (int iteration = 0; iteration < 40 && bracketHigh > bracketLow; iteration++) {
               double middle = (bracketLow + bracketHigh) * 0.5;
               double error = heightError(horizontal, dy, speed, gravity, drag, middle);
               if (!Double.isNaN(error) && !(error <= -1000000.0)) {
                  zero = middle;
                  if (Math.abs(error) < 1.0E-4) {
                     break;
                  }

                  if (error < 0.0) {
                     bracketLow = middle;
                  } else {
                     bracketHigh = middle;
                  }
               } else {
                  bracketHigh = middle;
               }
            }

            double cos = Math.cos(zero);
            return new double[]{straightX * cos, Math.sin(zero), straightZ * cos};
         } else {
            return ballisticAim(from, target, speed, gravity);
         }
      } else {
         return null;
      }
   }

   private static double heightError(double horizontal, double dy, double speed, double gravity, double drag, double elevation) {
      double cos = Math.cos(elevation);
      double vx = cos * speed;
      double vy = Math.sin(elevation) * speed;
      double travelled = 0.0;
      double height = 0.0;
      double previousTravelled = 0.0;
      double previousHeight = 0.0;

      for (int tick = 0; tick < 400; tick++) {
         previousTravelled = travelled;
         previousHeight = height;
         travelled += vx;
         height += vy;
         vy -= gravity;
         vx *= drag;
         vy *= drag;
         if (travelled >= horizontal) {
            double span = travelled - previousTravelled;
            if (span < 1.0E-9) {
               return height - dy;
            }

            double fraction = (horizontal - previousTravelled) / span;
            return previousHeight + (height - previousHeight) * fraction - dy;
         }

         if (vx < 1.0E-6) {
            return -1000000.0;
         }
      }

      return -1000000.0;
   }
}
