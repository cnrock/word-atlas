export const TAP_SLOP = 14;

/** Classify a tap by distance from its start, not accumulated finger jitter. */
export function isTap(start: { x: number; y: number }, end: { x: number; y: number }, multiTouch: boolean) {
  return !multiTouch && Math.hypot(end.x - start.x, end.y - start.y) <= TAP_SLOP;
}
